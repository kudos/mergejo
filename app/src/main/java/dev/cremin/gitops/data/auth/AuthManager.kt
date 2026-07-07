package dev.cremin.gitops.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

/**
 * Owns the OAuth2 (Authorization Code + PKCE) lifecycle against Forgejo using
 * AppAuth. Discovery is resolved from the issuer's `/.well-known/openid-configuration`.
 * Tokens are refreshed transparently via [AuthState.performActionWithFreshTokens].
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: AuthStateStore,
) {
    private val authService = AuthorizationService(context)

    @Volatile
    private var current: AuthState = store.load()

    private val _isAuthorized = MutableStateFlow(current.isAuthorized)

    /** Emits whether we currently hold a usable authorization. Drives the login gate. */
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    private fun persist(state: AuthState) {
        current = state
        store.save(state)
        _isAuthorized.value = state.isAuthorized
    }

    /** Builds the browser intent that starts the authorization flow. */
    suspend fun authorizationRequestIntent(
        issuer: String,
        clientId: String,
        redirectUri: String,
        scopes: String,
    ): Intent {
        val config = fetchServiceConfig(issuer)
        val request = AuthorizationRequest.Builder(
            config,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(redirectUri),
        ).setScope(scopes).build()
        return authService.getAuthorizationRequestIntent(request)
    }

    /** Completes login: records the authorization response and exchanges the code for tokens. */
    suspend fun handleAuthorizationResponse(data: Intent): Result<Unit> {
        val response = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        val state = current
        state.update(response, ex)
        if (response == null) {
            persist(state)
            return Result.failure(ex ?: IllegalStateException("Authorization was cancelled"))
        }
        return exchangeToken(response, state)
    }

    fun signOut() {
        store.clear()
        persist(AuthState())
    }

    /**
     * Blocking bridge used by [AuthInterceptor] (which runs on an OkHttp thread).
     * Returns a fresh access token, refreshing it if necessary, or null if not authorized.
     */
    fun freshAccessTokenBlocking(): String? {
        val state = current
        if (!state.isAuthorized) return null
        val latch = CountDownLatch(1)
        val tokenRef = AtomicReference<String?>()
        state.performActionWithFreshTokens(authService) { accessToken, _, _ ->
            tokenRef.set(accessToken)
            persist(state) // capture any refreshed tokens
            latch.countDown()
        }
        latch.await(30, TimeUnit.SECONDS)
        return tokenRef.get()
    }

    private suspend fun fetchServiceConfig(issuer: String): AuthorizationServiceConfiguration =
        suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(issuer)) { config, ex ->
                if (config != null) {
                    cont.resume(config)
                } else {
                    cont.resumeWithException(ex ?: IllegalStateException("OIDC discovery failed"))
                }
            }
        }

    private suspend fun exchangeToken(
        response: AuthorizationResponse,
        state: AuthState,
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, ex ->
            state.update(tokenResponse, ex)
            persist(state)
            if (tokenResponse != null) {
                cont.resume(Result.success(Unit))
            } else {
                cont.resume(Result.failure(ex ?: IllegalStateException("Token exchange failed")))
            }
        }
    }
}
