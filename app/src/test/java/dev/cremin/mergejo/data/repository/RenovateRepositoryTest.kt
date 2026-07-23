package dev.cremin.mergejo.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.cremin.mergejo.data.remote.api.ForgejoApi
import dev.cremin.mergejo.domain.model.CiStatus
import dev.cremin.mergejo.domain.model.UpdateType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

@OptIn(ExperimentalSerializationApi::class)
class RenovateRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: RenovateRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = (request.path ?: "").substringBefore('?')
                val body = when {
                    path.startsWith("/api/v1/user/repos") -> REPOS_JSON
                    path.endsWith("/pulls") -> PULLS_JSON
                    path.contains("/commits/") && path.endsWith("/status") -> STATUS_JSON
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setBody(body).addHeader("Content-Type", "application/json")
            }
        }
        server.start()

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            namingStrategy = JsonNamingStrategy.SnakeCase
        }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ForgejoApi::class.java)
        repository = RenovateRepository(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `refresh keeps only renovate PRs and enriches them`() = runBlocking {
        repository.refresh()

        val prs = repository.pullRequests.value
        assertNull(repository.error.value)
        assertEquals(1, prs.size)

        val pr = prs.first()
        assertEquals("jonathan/app", pr.repoSlug)
        assertEquals(42L, pr.number)
        assertEquals("renovate/react-18.x", pr.branch)
        assertEquals("react", pr.dependency)
        assertEquals(UpdateType.MINOR, pr.updateType)
        assertEquals(CiStatus.SUCCESS, pr.ciStatus)
        assertEquals(true, pr.mergeable)
        assertTrue(pr.checks.isNotEmpty())
    }

    private companion object {
        const val REPOS_JSON =
            """[{"id":1,"name":"app","full_name":"jonathan/app","owner":{"login":"jonathan"},"archived":false}]"""

        const val PULLS_JSON = """
            [
              {
                "number":42,
                "title":"Update dependency react to v18.3.1",
                "body":"| Package | Change |\n|---|---|\n| react | `18.2.0` -> `18.3.1` |",
                "html_url":"https://cremin.dev/jonathan/app/pulls/42",
                "state":"open",
                "user":{"login":"renovate"},
                "head":{"ref":"renovate/react-18.x","sha":"abc123"},
                "base":{"ref":"main"},
                "mergeable":true,
                "created_at":"2026-07-01T10:00:00Z"
              },
              {
                "number":7,
                "title":"Fix a bug",
                "body":"",
                "state":"open",
                "user":{"login":"jonathan"},
                "head":{"ref":"feature/foo","sha":"def456"},
                "base":{"ref":"main"}
              }
            ]
        """

        const val STATUS_JSON =
            """{"state":"success","sha":"abc123","total_count":1,"statuses":[{"status":"success","context":"ci/build","description":"passed"}]}"""
    }
}
