package dev.cremin.gitops.data.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to every Forgejo API request, sourcing a
 * fresh (auto-refreshed) access token from [AuthManager]. Requests made before
 * login simply go out unauthenticated and the API returns 401.
 */
class AuthInterceptor(private val authManager: AuthManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = authManager.freshAccessTokenBlocking()
        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
