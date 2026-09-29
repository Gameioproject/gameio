package com.nendo.argosy.data.remote.romm

import android.content.Context
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val DOWNLOAD_STALL_TIMEOUT_SECONDS = 300
private const val HTTP_CACHE_DIR = "romm-http"
private const val HTTP_CACHE_BYTES = 50L * 1024 * 1024

/** Save and state bodies are bulk transfers; a slow uplink must not trip the API timeout. */
private fun isAssetTransfer(path: String): Boolean =
    path.contains("/api/saves") || path.contains("/api/states")

/**
 * Binary bodies stream straight to disk and must never be copied into the HTTP cache.
 */
private fun isBulkBody(path: String): Boolean =
    path.contains("/content") || path.contains("/api/assets/") || isAssetTransfer(path)

/**
 * Builds a RomM client bound to one base URL and token.
 *
 * Extracted so a client can be built for an account that is not the live one, which is what
 * lets queued work upload under the identity that created it rather than whoever is signed in.
 * Every client shares one connection pool and one HTTP cache, so reconnecting keeps warm
 * connections and unchanged API responses revalidate as bodiless 304s.
 */
@Singleton
class RomMApiFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val moshi: Moshi by lazy { Moshi.Builder().build() }

    private val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, HTTP_CACHE_DIR), HTTP_CACHE_BYTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .dns(okhttp3.Dns.SYSTEM)
            .withUserCertTrust(true)
            .build()
    }

    fun create(baseUrl: String, token: String?): RomMApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }

        val authInterceptor = Interceptor { chain ->
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val downloadTimeoutInterceptor = Interceptor { chain ->
            val path = chain.request().url.encodedPath
            when {
                isBulkBody(path) -> chain
                    .withReadTimeout(DOWNLOAD_STALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .withWriteTimeout(DOWNLOAD_STALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .proceed(
                        chain.request().newBuilder()
                            .cacheControl(CacheControl.Builder().noStore().build())
                            .build()
                    )
                path.endsWith("/api/roms") -> chain
                    .withReadTimeout(DOWNLOAD_STALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .withWriteTimeout(DOWNLOAD_STALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .proceed(chain.request())
                else -> chain.proceed(chain.request())
            }
        }

        val client = sharedClient.newBuilder()
            .addInterceptor(authInterceptor)
            .addInterceptor(downloadTimeoutInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RomMApi::class.java)
    }
}
