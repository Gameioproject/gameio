package com.nendo.argosy.data.remote.romm

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.preferences.CatalogServerModeRepository
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.data.sync.AccountRemovalResult
import com.nendo.argosy.data.sync.UnflushedQueuePolicy
import android.net.ConnectivityManager
import android.net.Network
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMConnectionManager"

const val DEFAULT_SERVER_URL = "https://playgameio.com"
private const val MIN_DEVICE_API_VERSION = "4.7.0"
private const val DOWNLOAD_STALL_TIMEOUT_SECONDS = 300

private val RECONNECT_BACKOFF_MS = listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L)

private val CLIENT_TOKEN_SCOPES = listOf(
    "me.read", "me.write",
    "platforms.read", "platforms.write",
    "roms.read", "roms.write",
    "roms.user.read", "roms.user.write",
    "assets.read", "assets.write",
    "firmware.read", "firmware.write",
    "collections.read", "collections.write",
    "devices.read", "devices.write",
)

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(
        val version: String,
        val capabilities: RomMCapabilities = RomMCapabilities.from(version)
    ) : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
}

enum class SignInFailureReason { INVALID_CREDENTIALS, ACCOUNT_UNAVAILABLE, UNAVAILABLE }

/** What a username-and-password sign-in produced. */
sealed class SignInResult {
    /** Signed in and this connection is now the live one. */
    data class Connected(val token: String) : SignInResult()

    /** Credentials were good, but the account was stored alongside the live one, not activated. */
    data class AddedAccount(val accountId: Long) : SignInResult()

    data class Failed(
        val message: String,
        val reason: SignInFailureReason = SignInFailureReason.UNAVAILABLE
    ) : SignInResult()
}

@Singleton
class RomMConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val saveSyncRepository: dagger.Lazy<com.nendo.argosy.data.repository.SaveSyncRepository>,
    private val biosRepository: BiosRepository,
    private val rommAccountRepository: dagger.Lazy<com.nendo.argosy.data.repository.RomMAccountRepository>,
    private val accountRemovalService: dagger.Lazy<com.nendo.argosy.data.sync.AccountRemovalService>,
    private val syncCoordinator: dagger.Lazy<com.nendo.argosy.data.sync.SyncCoordinator>,
    private val retroAchievementsRepository: dagger.Lazy<com.nendo.argosy.data.repository.RetroAchievementsRepository>,
    private val apiFactory: RomMApiFactory,
    private val catalogServerMode: CatalogServerModeRepository
) {
    private var api: RomMApi? = null
    private var baseUrl: String = ""
    private var accessToken: String? = null
    private var cachedDeviceId: String? = null
    private val detailAdapter by lazy { Moshi.Builder().build().adapter(RomMDetailResponse::class.java) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()
    private var reconnectJob: Job? = null
    private var networkCallbackRegistered = false
    @Volatile private var reconnectPending = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun getApi(): RomMApi? = api

    fun getBaseUrl(): String = baseUrl

    fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected

    fun getDeviceId(): String? = cachedDeviceId

    fun getConnectedVersion(): String? {
        return (_connectionState.value as? ConnectionState.Connected)?.version
    }

    fun getCapabilities(): RomMCapabilities {
        return (_connectionState.value as? ConnectionState.Connected)?.capabilities
            ?: RomMCapabilities.NONE
    }

    fun usesAddonSources(): Boolean = catalogServerMode.usesAddonSources()

    fun isVersionAtLeast(minVersion: String): Boolean {
        val current = getConnectedVersion() ?: return false
        return RomMCapabilities.compareVersions(current, minVersion) >= 0
    }

    suspend fun initialize() {
        val prefs = userPreferencesRepository.preferences.first()
        Logger.info(TAG, "initialize: baseUrl=${prefs.rommBaseUrl?.take(30)}, hasToken=${prefs.rommToken != null}")
        rommAccountRepository.get().adoptLegacyCredentialsIfNeeded()
        cachedDeviceId = prefs.rommDeviceId
        if (cachedDeviceId != null) {
            saveSyncRepository.get().setDeviceId(cachedDeviceId)
        }
        if (prefs.rommBaseUrl.isNullOrBlank()) return
        registerNetworkCallback()
        val result = attemptConnection(prefs.rommBaseUrl, prefs.rommToken)
        Logger.info(TAG, "initialize: connect result=$result, state=${_connectionState.value}")
        if (result is RomMResult.Error) scheduleReconnect() else backfillIdentityIfMissing(prefs.rommToken)
    }

    /**
     * Gives an install that predates account identity a user id and an account row.
     *
     * Such an install reconnects with a stored token and never passes through a pairing path, so
     * nothing would ever record who it belongs to and every owner stamp would stay null. Only the
     * absence of an id triggers this, so it costs one request once.
     */
    private suspend fun backfillIdentityIfMissing(token: String?) {
        if (token.isNullOrBlank()) return
        val stored = userPreferencesRepository.preferences.first()
        if (stored.rommUserId != null) return
        val currentApi = api ?: return
        val user = fetchCurrentUser(currentApi) ?: return
        Logger.info(TAG, "backfillIdentityIfMissing: adopting user ${user.id} for an install with no stored identity")
        persistRommCredentials(baseUrl, token, user)
    }

    /**
     * Retries the persisted connection on a backoff ladder, preserving the current
     * connection state until the ladder is exhausted so transient network loss
     * (sleep/wake, spotty wifi) does not read as a dead server.
     */
    private fun scheduleReconnect() {
        reconnectPending = true
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            for (backoffMs in RECONNECT_BACKOFF_MS) {
                delay(backoffMs)
                if (!reconnectPending) return@launch
                val prefs = userPreferencesRepository.preferences.first()
                val url = prefs.rommBaseUrl
                if (url.isNullOrBlank()) return@launch
                Logger.info(TAG, "scheduleReconnect: retrying after ${backoffMs}ms")
                if (attemptConnection(url, prefs.rommToken) is RomMResult.Success) return@launch
            }
            if (!reconnectPending) return@launch
            Logger.info(TAG, "scheduleReconnect: exhausted retries, marking disconnected")
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        networkCallbackRegistered = true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (isConnected()) return
                scope.launch {
                    val prefs = userPreferencesRepository.preferences.first()
                    val url = prefs.rommBaseUrl
                    if (url.isNullOrBlank()) return@launch
                    Logger.info(TAG, "network available, attempting reconnect")
                    if (attemptConnection(url, prefs.rommToken) is RomMResult.Error) scheduleReconnect()
                }
            }
        })
    }

    private suspend fun fetchCurrentUser(target: RomMApi): RomMUser? = try {
        val response = target.getCurrentUser()
        if (response.isSuccessful) response.body() else null
    } catch (_: Exception) {
        null
    }

    private fun normalizeServerKey(url: String): String =
        url.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')

    /**
     * Refuses a sign-in that would put a second server's library alongside the first.
     *
     * Rom ids are only unique within one RomM instance, so two servers on one device collide on
     * every id the library, saves and sync queues are keyed by. Accounts are the supported way to
     * hold more than one identity, and they share the server the device is already registered to.
     * Nothing is deleted here; the existing library stays exactly as it is and the sign-in simply
     * does not happen.
     */
    private suspend fun requireSameServer(newBaseUrl: String) {
        val accounts = rommAccountRepository.get().accounts()
        val known = accounts.map { normalizeServerKey(it.baseUrl) }.filter { it.isNotBlank() }.toSet()
        if (known.isEmpty()) return
        val newKey = normalizeServerKey(newBaseUrl)
        if (newKey in known) return
        Logger.info(TAG, "persistRommCredentials: refused sign-in to $newKey, device is registered to ${known.joinToString()}")
        throw IllegalStateException(
            "This device is already signed in to a different RomM server. Remove the existing accounts before connecting to another server."
        )
    }

    private suspend fun persistRommCredentials(newBaseUrl: String, token: String, user: RomMUser?) {
        requireSameServer(newBaseUrl)
        userPreferencesRepository.setRomMCredentials(newBaseUrl, token, user?.username, user?.id)
        if (user != null) {
            val stored = userPreferencesRepository.preferences.first()
            rommAccountRepository.get().onSignedIn(
                rommUserId = user.id,
                username = user.username,
                baseUrl = newBaseUrl,
                token = token,
                deviceId = stored.rommDeviceId,
                deviceClientVersion = stored.rommDeviceClientVersion
            )
        }
    }

    suspend fun connect(url: String, token: String? = null): RomMResult<String> {
        _connectionState.value = ConnectionState.Connecting
        val result = attemptConnection(url, token)
        if (result is RomMResult.Error) {
            _connectionState.value = ConnectionState.Failed(result.message)
        }
        return result
    }

    /** Probes a server URL with a throwaway client, leaving the live session untouched. */
    suspend fun probeServerVersion(url: String): RomMResult<String> {
        var lastError: String? = null
        for (candidateUrl in buildUrlsToTry(url)) {
            val normalizedUrl = candidateUrl.trimEnd('/') + "/"
            try {
                val response = createApi(normalizedUrl, null).heartbeat()
                if (response.isSuccessful) {
                    return RomMResult.Success(response.body()?.version ?: "unknown")
                }
                lastError = "Server returned ${response.code()}"
            } catch (e: Exception) {
                lastError = e.message ?: "Connection failed"
            }
        }
        return RomMResult.Error(lastError ?: "Connection failed")
    }

    private suspend fun attemptConnection(
        url: String,
        token: String?,
        registerDevice: Boolean = true
    ): RomMResult<String> = connectMutex.withLock {
        val urlsToTry = buildUrlsToTry(url)
        var lastError: String? = null

        for (candidateUrl in urlsToTry) {
            val normalizedUrl = candidateUrl.trimEnd('/') + "/"
            try {
                catalogServerMode.select(normalizedUrl)
                val newApi = createApi(normalizedUrl, token)
                val response = newApi.heartbeat()

                if (response.isSuccessful) {
                    baseUrl = normalizedUrl
                    accessToken = token
                    api = newApi
                    saveSyncRepository.get().setApi(api)
                    biosRepository.setApi(api)
                    val body = response.body()
                    if (body != null) catalogServerMode.remember(normalizedUrl, body.catalogOnly)
                    val version = body?.version ?: "unknown"
                    val capabilities = RomMCapabilities.from(version, body?.libretroApiEnabled, body?.steamGridDbEnabled, body?.catalogOnly == true, body?.frontend?.supportUrl)
                    _connectionState.value = ConnectionState.Connected(version, capabilities)
                    saveSyncRepository.get().setCapabilities(capabilities)
                    reconnectPending = false
                    Logger.info(TAG, "connect: success at $normalizedUrl, version=$version, capabilities=$capabilities")
                    if (registerDevice && token != null && isVersionAtLeast(MIN_DEVICE_API_VERSION)) {
                        registerDeviceIfNeeded()
                    }
                    return RomMResult.Success(normalizedUrl)
                } else {
                    lastError = "Server returned ${response.code()}"
                    Logger.info(TAG, "connect: heartbeat failed at $normalizedUrl with ${response.code()}")
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Connection failed"
                Logger.info(TAG, "connect: exception at $normalizedUrl: ${e.message}")
            }
        }

        return RomMResult.Error(lastError ?: "Connection failed")
    }

    suspend fun connectWithToken(url: String, token: String): RomMResult<String> {
        _connectionState.value = ConnectionState.Connecting
        val connectResult = attemptConnection(url, token, registerDevice = false)
        if (connectResult is RomMResult.Error) {
            _connectionState.value = ConnectionState.Failed(connectResult.message)
            return connectResult
        }

        val currentApi = api ?: return RomMResult.Error("Not connected")
        return try {
            persistRommCredentials(baseUrl, token, fetchCurrentUser(currentApi))

            if (isVersionAtLeast(MIN_DEVICE_API_VERSION)) {
                registerDeviceIfNeeded()
            }

            RomMResult.Success(token)
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "Failed to verify token")
        }
    }

    /**
     * Signs in with a username and password.
     *
     * The credentials are sent once, as HTTP Basic, to mint a long-lived client token; only that
     * token is kept. [activate] false stores the account without making it the live one, which is
     * how a second account gets added from Settings.
     */
    suspend fun connectWithPassword(
        url: String,
        username: String,
        password: String,
        activate: Boolean = true
    ): SignInResult {
        if (username.isBlank() || password.isBlank()) {
            return SignInResult.Failed("Enter your username and password")
        }

        val basic = "Basic " + Base64.encodeToString(
            "$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP
        )
        val request = RomMClientTokenRequest(
            name = deviceDisplayName(),
            scopes = CLIENT_TOKEN_SCOPES
        )

        var lastError: String? = null
        for (candidateUrl in buildUrlsToTry(url)) {
            val normalizedUrl = candidateUrl.trimEnd('/') + "/"
            try {
                val tempApi = createApi(normalizedUrl, null)
                val hb = tempApi.heartbeat()
                if (!hb.isSuccessful) {
                    lastError = "Server returned ${hb.code()}"
                    continue
                }

                var response = tempApi.createClientToken(basic, request)

                // A non-admin account holds a narrower scope set than the full list above,
                // and the server rejects the whole request naming the ones it refused. Drop
                // exactly those and ask again, so a restricted account still signs in.
                if (response.code() == 403) {
                    val refused = refusedScopes(parseDetail(response.errorBody()?.string()))
                    if (refused.isNotEmpty()) {
                        val allowed = CLIENT_TOKEN_SCOPES - refused
                        Logger.info(TAG, "connectWithPassword: server refused $refused, retrying with ${allowed.size} scopes")
                        response = tempApi.createClientToken(basic, request.copy(scopes = allowed))
                    }
                }

                if (!response.isSuccessful) {
                    // A reachable server that rejects the credentials is an answer, not a reason
                    // to keep trying other addresses for the same host.
                    return SignInResult.Failed(
                        when (response.code()) {
                            401 -> "Wrong username or password"
                            403 -> "That account is disabled"
                            400 -> parseDetail(response.errorBody()?.string())
                                ?: "Too many sign-ins on this account. Remove one in the web app."
                            else -> "Sign-in failed (${response.code()})"
                        },
                        when (response.code()) {
                            401 -> SignInFailureReason.INVALID_CREDENTIALS
                            403 -> SignInFailureReason.ACCOUNT_UNAVAILABLE
                            else -> SignInFailureReason.UNAVAILABLE
                        }
                    )
                }

                val token = response.body()?.rawToken
                    ?: return SignInResult.Failed("Server returned no token")

                return if (activate) {
                    when (val result = connectWithToken(normalizedUrl, token)) {
                        is RomMResult.Success -> SignInResult.Connected(token)
                        is RomMResult.Error -> SignInResult.Failed(result.message)
                    }
                } else {
                    val accountId = registerAdditionalAccount(normalizedUrl, token)
                        ?: return SignInResult.Failed("Could not read the account on that server")
                    SignInResult.AddedAccount(accountId)
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Connection failed"
            }
        }

        return SignInResult.Failed(lastError ?: "Could not reach that server")
    }

    private suspend fun registerAdditionalAccount(base: String, token: String): Long? {
        val newApi = createApi(base, token)
        val user = fetchCurrentUser(newApi) ?: return null
        val accountId = rommAccountRepository.get().registerAdditional(
            rommUserId = user.id,
            username = user.username,
            baseUrl = base,
            token = token,
            deviceId = null,
            deviceClientVersion = BuildConfig.VERSION_NAME
        )
        Logger.info(TAG, "registerAdditionalAccount: stored account $accountId for user ${user.id} without activating")
        return accountId
    }


    /**
     * Scope names out of "Requested scopes exceed your permissions: a, b, c". An empty result
     * means the 403 was about something else and the request should not be retried.
     */
    private fun refusedScopes(detail: String?): Set<String> {
        val marker = "exceed your permissions:"
        val at = detail?.indexOf(marker) ?: -1
        if (at < 0) return emptySet()
        return detail!!.substring(at + marker.length)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun parseDetail(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try { detailAdapter.fromJson(body)?.detail } catch (_: Exception) { null }
    }

    @SuppressLint("HardwareIds")
    private fun clientDeviceIdentifier(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (!androidId.isNullOrBlank()) "argosy-$androidId" else "argosy-${java.util.UUID.randomUUID()}"
    }

    private fun deviceDisplayName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    fun disconnect() {
        catalogServerMode.clearSelection()
        reconnectPending = false
        reconnectJob?.cancel()
        reconnectJob = null
        api = null
        biosRepository.setApi(null)
        saveSyncRepository.get().setCapabilities(RomMCapabilities.NONE)
        accessToken = null
        baseUrl = ""
        cachedDeviceId = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Signs the active account out: its own rows, cached saves, preferences and credentials go,
     * and nothing another account or the shared library owns is touched.
     *
     * This is removal aimed at the account that happens to be live, so it runs through the same
     * service rather than a second, weaker path - that is what brings the switch-in-progress
     * guard and the unflushed-work policy with it.
     *
     * The queue is drained first, while the token still authenticates. Everything the account
     * cached and never sent can only be sent as that account, so an upload deferred past sign-out
     * is an upload that never happens; refusing here is what keeps the local copy that is still
     * the only copy. [discardUnflushed] gives up on whatever the drain could not deliver.
     *
     * Refusing only makes sense while the work could still be sent. With the server unreachable
     * the drain can never succeed, so refusing would strand the account signed in forever, which
     * is what happens to anyone whose token was revoked server-side. Unreachable therefore
     * discards: the queue is already unsendable, and being unable to sign out costs more than the
     * rows do.
     */
    suspend fun signOut(discardUnflushed: Boolean = false): AccountRemovalResult {
        val active = rommAccountRepository.get().activeAccount()
            ?: run {
                disconnect()
                userPreferencesRepository.clearRomMCredentials()
                return AccountRemovalResult.UnknownAccount
            }
        val drained = syncCoordinator.get().processQueue()
        Logger.info(TAG, "signOut: drained queued work before removal, result=$drained")
        val reachable = isConnected()
        val policy = if (discardUnflushed || !reachable) {
            UnflushedQueuePolicy.DISCARD
        } else {
            UnflushedQueuePolicy.REFUSE
        }
        if (!reachable && !discardUnflushed) {
            Logger.info(TAG, "signOut: server unreachable, discarding what the drain could not send")
        }
        val result = accountRemovalService.get().remove(active.id, policy)
        if (result is AccountRemovalResult.Refused || result is AccountRemovalResult.SwitchInProgress) {
            Logger.info(TAG, "signOut: not signed out, $result")
            return result
        }
        disconnect()
        userPreferencesRepository.clearRomMCredentials()
        userPreferencesRepository.clearLastNegotiateAt()
        retroAchievementsRepository.get().syncRetroArchCredentials()
        Logger.info(TAG, "signOut: removed user ${active.rommUserId} and cleared the stored identity")
        return result
    }

    /**
     * Repoints the live session at whichever account is now stored as active.
     *
     * The teardown of the previous session runs first and unconditionally: leaving the old api
     * object, token or device id in place while the stored identity says otherwise is the
     * split-brain that makes one account's uploads land under the other's device.
     */
    suspend fun rebindToActiveAccount(): RomMResult<String> {
        disconnect()
        val prefs = userPreferencesRepository.preferences.first()
        val url = prefs.rommBaseUrl
        if (url.isNullOrBlank()) {
            Logger.info(TAG, "rebindToActiveAccount: no stored server for the active account")
            return RomMResult.Error("No server configured for this account")
        }
        cachedDeviceId = prefs.rommDeviceId
        saveSyncRepository.get().setDeviceId(cachedDeviceId)
        val result = attemptConnection(url, prefs.rommToken)
        if (result is RomMResult.Error) {
            Logger.info(TAG, "rebindToActiveAccount: offline after swap, scheduling reconnect")
            scheduleReconnect()
        }
        return result
    }

    suspend fun checkConnection() {
        val currentApi = api
        if (currentApi == null) {
            Logger.info(TAG, "checkConnection: api is null, initializing")
            initialize()
            return
        }

        try {
            val response = currentApi.heartbeat()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) catalogServerMode.remember(baseUrl, body.catalogOnly)
                val version = body?.version ?: "unknown"
                val capabilities = RomMCapabilities.from(version, body?.libretroApiEnabled, body?.steamGridDbEnabled, body?.catalogOnly == true, body?.frontend?.supportUrl)
                _connectionState.value = ConnectionState.Connected(version, capabilities)
                saveSyncRepository.get().setCapabilities(capabilities)
                reconnectPending = false
                Logger.info(TAG, "checkConnection: connected, version=$version")
            } else {
                Logger.info(TAG, "checkConnection: heartbeat failed with ${response.code()}, scheduling reconnect")
                scheduleReconnect()
            }
        } catch (e: Exception) {
            Logger.info(TAG, "checkConnection: exception: ${e.message}, scheduling reconnect")
            scheduleReconnect()
        }
    }

    private suspend fun registerDeviceIfNeeded() {
        val currentApi = api ?: return
        val clientVersion = BuildConfig.VERSION_NAME

        val prefs = userPreferencesRepository.preferences.first()
        val existingDeviceId = prefs.rommDeviceId
        val existingClientVersion = prefs.rommDeviceClientVersion

        if (existingDeviceId != null && existingClientVersion == clientVersion) {
            cachedDeviceId = existingDeviceId
            saveSyncRepository.get().setDeviceId(existingDeviceId)
            Logger.info(TAG, "Device already registered: $existingDeviceId")
            return
        }

        try {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val caps = getCapabilities()
            val registration = RomMDeviceRegistration(
                name = deviceName,
                clientVersion = clientVersion,
                syncMode = if (caps.supportsDeviceSyncMode) "api" else null
            )

            if (existingDeviceId != null) {
                val updateResponse = currentApi.updateDevice(existingDeviceId, registration)
                if (updateResponse.isSuccessful) {
                    val device = updateResponse.body()
                    if (device != null) {
                        cachedDeviceId = device.id
                        saveSyncRepository.get().setDeviceId(device.id)
                        userPreferencesRepository.setRommDeviceId(device.id, clientVersion)
                        rommAccountRepository.get().recordDeviceRegistration(device.id, clientVersion)
                        Logger.info(TAG, "Device updated: ${device.id}")
                        return
                    }
                }
            }

            val response = currentApi.registerDevice(registration)
            if (response.isSuccessful) {
                val device = response.body()
                if (device != null) {
                    cachedDeviceId = device.deviceId
                    saveSyncRepository.get().setDeviceId(device.deviceId)
                    userPreferencesRepository.setRommDeviceId(device.deviceId, clientVersion)
                    rommAccountRepository.get().recordDeviceRegistration(device.deviceId, clientVersion)
                    Logger.info(TAG, "Device registered: ${device.deviceId}")
                }
            } else {
                Logger.error(TAG, "Device registration failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Device registration error: ${e.message}")
        }
    }

    private fun absolutizeUrl(value: String, base: String): String {
        if (value.isBlank()) return value
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        return base.trimEnd('/') + "/" + value.trimStart('/')
    }

    private fun buildUrlsToTry(url: String): List<String> {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return listOf(trimmed)
        }

        val hostPart = trimmed.removePrefix("//")
        val isIpAddress = hostPart.split("/").first().split(":").first().let { host ->
            host.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")) ||
                host == "localhost"
        }

        return if (isIpAddress) {
            listOf("http://$hostPart", "https://$hostPart")
        } else {
            listOf("https://$hostPart", "http://$hostPart")
        }
    }

    fun createApi(baseUrl: String, token: String?): RomMApi = apiFactory.create(baseUrl, token)
}
