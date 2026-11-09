package dev.cremin.mergejo.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.cremin.mergejo.data.remote.api.ForgejoApi
import dev.cremin.mergejo.domain.model.CiStatus
import dev.cremin.mergejo.domain.model.CommentBlock
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
                    path == "/api/v1/repos/jonathan/app/issues/42/comments" -> COMMENTS_JSON
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

    @Test
    fun `comments are parsed and empty ones dropped`() = runBlocking {
        repository.refresh()
        val comments = repository.comments(repository.pullRequests.value.single()).getOrThrow()

        assertEquals(1, comments.size)
        val comment = comments.single()
        assertEquals("forgejo-actions", comment.author)
        assertTrue(comment.edited)
        val details = comment.blocks.single() as CommentBlock.Details
        assertEquals("**desktop**: no changes", details.summary)
        assertEquals("<<< a\n>>> b", details.code)
    }

    @Test
    fun `refresh follows Link headers across pages of repos and PRs`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val page = url.queryParameter("page")?.toInt() ?: 1
                val path = url.encodedPath
                fun page(body: String, hasNext: Boolean) = MockResponse()
                    .setBody(body)
                    .addHeader("Content-Type", "application/json")
                    .apply {
                        if (hasNext) addHeader("Link", "<${url.newBuilder().setQueryParameter("page", "${page + 1}").build()}>; rel=\"next\"")
                    }
                return when {
                    path == "/api/v1/user/repos" -> when (page) {
                        1 -> page("""[${repo("app")}]""", hasNext = true)
                        else -> page("""[${repo("lib")}]""", hasNext = false)
                    }
                    path == "/api/v1/repos/jonathan/app/pulls" -> when (page) {
                        1 -> page("[${renovatePr(1)}]", hasNext = true)
                        else -> page("[${renovatePr(2)}]", hasNext = false)
                    }
                    path == "/api/v1/repos/jonathan/lib/pulls" -> page("[${renovatePr(3)}]", hasNext = false)
                    path.endsWith("/status") -> page(STATUS_JSON, hasNext = false)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        repository.refresh()

        assertNull(repository.error.value)
        assertEquals(
            listOf("jonathan/app#1", "jonathan/app#2", "jonathan/lib#3"),
            repository.pullRequests.value.map { it.id }.sorted(),
        )
    }

    private fun repo(name: String) =
        """{"id":${name.hashCode()},"name":"$name","full_name":"jonathan/$name","owner":{"login":"jonathan"},"archived":false}"""

    private fun renovatePr(number: Int) = """
        {"number":$number,"title":"Update dependency x to v$number","body":"","state":"open",
         "user":{"login":"renovate"},"head":{"ref":"renovate/x-$number","sha":"sha$number"},"base":{"ref":"main"}}
    """

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

        const val COMMENTS_JSON = """
            [
              {"id":1,"body":"<!-- marker -->","user":{"login":"renovate"},"created_at":"2026-07-01T10:00:00Z","updated_at":"2026-07-01T10:00:00Z"},
              {"id":2,"body":"<details><summary><b>desktop</b>: no changes</summary>\n\n```\n<<< a\n>>> b\n```\n\n</details>\n",
               "user":{"login":"forgejo-actions"},"created_at":"2026-07-01T10:00:00Z","updated_at":"2026-07-01T11:00:00Z"}
            ]
        """

        const val STATUS_JSON =
            """{"state":"success","sha":"abc123","total_count":1,"statuses":[{"status":"success","context":"ci/build","description":"passed"}]}"""
    }
}
