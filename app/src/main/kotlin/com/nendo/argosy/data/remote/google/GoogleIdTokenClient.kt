package com.nendo.argosy.data.remote.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.nendo.argosy.util.Logger

sealed class GoogleIdTokenResult {
    data class Success(val idToken: String) : GoogleIdTokenResult()
    data object Cancelled : GoogleIdTokenResult()
    data object NoAccount : GoogleIdTokenResult()
    data object Unsupported : GoogleIdTokenResult()
    data object Failed : GoogleIdTokenResult()
}

/**
 * Asks Google, through Credential Manager, for an ID token made out to [serverClientId]. The
 * account picker is system UI anchored to an activity, so [activityContext] must be one.
 */
suspend fun requestGoogleIdToken(activityContext: Context, serverClientId: String): GoogleIdTokenResult {
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
        .build()
    return try {
        val credential = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)
            .credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            Logger.info(TAG, "unexpected credential type ${credential.type}")
            GoogleIdTokenResult.Failed
        }
    } catch (e: GetCredentialCancellationException) {
        GoogleIdTokenResult.Cancelled
    } catch (e: NoCredentialException) {
        GoogleIdTokenResult.NoAccount
    } catch (e: GetCredentialProviderConfigurationException) {
        GoogleIdTokenResult.Unsupported
    } catch (e: GetCredentialUnsupportedException) {
        GoogleIdTokenResult.Unsupported
    } catch (e: GetCredentialException) {
        Logger.info(TAG, "getCredential failed: ${e.type}: ${e.message}")
        GoogleIdTokenResult.Failed
    } catch (e: GoogleIdTokenParsingException) {
        Logger.info(TAG, "token parse failed: ${e.message}")
        GoogleIdTokenResult.Failed
    }
}

private const val TAG = "GoogleIdToken"
