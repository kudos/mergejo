package dev.cremin.mergejo.data.auth

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites each API request to the configured server (scheme/host/port) and adds
 * `Authorization: token <token>`. Because the host is applied here, the Retrofit
 * base URL is only a placeholder and any Forgejo instance works at runtime.
 *
 * Assumes the instance is served at the host root (no path prefix).
 */
class AuthInterceptor(private val authManager: AuthManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        authManager.serverUrl?.toHttpUrlOrNull()?.let { server ->
            val rewritten = original.url.newBuilder()
                .scheme(server.scheme)
                .host(server.host)
                .port(server.port)
                .build()
            builder.url(rewritten)
        }
        authManager.token?.let { builder.header("Authorization", "token $it") }

        return chain.proceed(builder.build())
    }
}
