package app.gamenative.ui.screen.wow

import app.gamenative.ui.screen.wow.WowClientDownloader.PatchFail
import app.gamenative.ui.screen.wow.WowClientDownloader.PatchWhy
import java.io.File
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Issue 150: bounded direct-install retries and useful errors, using a local HTTP server, not Blizzard. */
class WowPatchAnswerTest {
    private lateinit var server: MockWebServer
    private val answers = ArrayDeque<Int>()
    private var versionsAsked = 0
    private val savedBackoff = WowClientDownloader.patchBackoffMs
    private val savedPatchUrl = WowClientDownloader.patchUrlFor
    private val statuses = mutableListOf<String>()
    private var versions = "Region!STRING:0|BuildConfig!HEX:16|CDNConfig!HEX:16|KeyRing!HEX:16|BuildId!DEC:4|VersionsName!String:0|" +
        "ProductConfig!HEX:16\n## seqn = 1\nus|0123456789abcdef0123456789abcdef|fedcba9876543210fedcba9876543210||70532|1.60.1.70532|2ced73c1\n"

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path.orEmpty()) {
                "/patch/wowf/versions" -> {
                    val code = synchronized(answers) { versionsAsked++; answers.removeFirstOrNull() } ?: 200
                    if (code == 200) MockResponse().setResponseCode(200).setBody(versions) else MockResponse().setResponseCode(code)
                }
                "/patch/wowf/cdns" -> MockResponse().setResponseCode(200).setBody("Name!STRING:0|Path!STRING:0|Hosts!STRING:0|Servers!STRING:0\n" +
                    "us|tpr/wow|127.0.0.1|http://127.0.0.1/?maxhosts=4\n")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        WowClientDownloader.patchUrlFor = { "http://127.0.0.1:${server.port}/patch/${it.code}" }
        WowClientDownloader.patchBackoffMs = listOf(0L, 0L)
    }

    @After fun tearDown() {
        WowClientDownloader.patchUrlFor = savedPatchUrl
        WowClientDownloader.patchBackoffMs = savedBackoff
        server.shutdown()
    }

    private fun target() = WowClientDownloader.installTarget(mapOf("Branch" to "us")) { statuses += it }
    private fun failure(): WowClientDownloader.PatchServerException = try {
        target(); fail("the install's check must fail"); throw AssertionError()
    } catch (e: WowClientDownloader.PatchServerException) { e }

    @Test fun aPassingErrorIsTriedAgain() {
        answers.addAll(listOf(503, 503))
        assertEquals("1.60.1.70532", target().remoteVersion)
        assertEquals(3, versionsAsked)
        assertEquals(listOf("Checking the latest build... (try 2 of 3)", "Checking the latest build... (try 3 of 3)"), statuses)
    }

    @Test fun threeFailedTriesSayWhy() {
        answers.addAll(listOf(503, 503, 503))
        val e = failure()
        assertEquals(3, versionsAsked)
        assertEquals(3, e.tries)
        assertEquals(PatchWhy(PatchFail.HTTP, 503), e.why)
        assertEquals("Blizzard's patch server did not answer: it sent back an error (HTTP 503). Try again later.", e.message)
        assertEquals("patch server: HTTP 503, 3 tries", e.detail)
    }

    @Test fun aRefusalThatWillNotPassIsNotRepeated() {
        answers.add(404)
        val e = failure()
        assertEquals(1, versionsAsked)
        assertEquals(1, e.tries)
        assertEquals("patch server: HTTP 404, 1 try", e.detail)
        assertTrue(statuses.isEmpty())
    }

    @Test fun aMissingBuildIsNotRepeated() {
        versions = "Region!STRING:0|BuildConfig!HEX:16|CDNConfig!HEX:16|VersionsName!STRING:0\n"
        val e = failure()
        assertEquals(PatchFail.NO_BUILD, e.why.fail)
        assertEquals(1, versionsAsked)
        assertEquals(1, e.tries)
    }

    @Test fun noServerAtAllIsABlockedConnection() {
        WowClientDownloader.patchUrlFor = { "http://127.0.0.1:1/patch/${it.code}" }
        val e = failure()
        assertEquals(3, e.tries)
        assertEquals(PatchFail.CONNECT, e.why.fail)
        assertTrue(e.message!!.startsWith(WowClientDownloader.PATCH_NO_ANSWER + ": the connection was blocked"))
    }

    @Test fun aFailureThatIsNotIoNeverEscapes() {
        WowClientDownloader.patchUrlFor = { "not a url" }
        assertEquals(PatchFail.OTHER, failure().why.fail)
        assertEquals(null, WowClientDownloader.regionNeedsDownload(File("no-such-folder"), "us"))
    }

    @Test fun eachFailureHasItsKind() {
        assertEquals(PatchFail.DNS, WowClientDownloader.patchWhy(java.net.UnknownHostException("x")).fail)
        assertEquals(PatchFail.CONNECT, WowClientDownloader.patchWhy(java.net.ConnectException("x")).fail)
        assertEquals(PatchFail.CONNECT, WowClientDownloader.patchWhy(java.net.NoRouteToHostException("x")).fail)
        assertEquals(PatchFail.TLS, WowClientDownloader.patchWhy(javax.net.ssl.SSLHandshakeException("x")).fail)
        assertEquals(PatchFail.TLS, WowClientDownloader.patchWhy(java.io.IOException("x", java.security.cert.CertificateException("y"))).fail)
        assertEquals(PatchFail.TIMEOUT, WowClientDownloader.patchWhy(java.net.SocketTimeoutException("x")).fail)
        assertEquals(PatchFail.TIMEOUT, WowClientDownloader.patchWhy(java.io.InterruptedIOException("timeout")).fail)
        assertEquals(PatchFail.DNS, WowClientDownloader.patchWhy(java.io.IOException("x", java.net.UnknownHostException("y"))).fail)
        assertEquals(PatchFail.OTHER, WowClientDownloader.patchWhy(java.io.IOException("x")).fail)
        assertEquals(listOf(2_000L, 5_000L), savedBackoff)
        assertTrue(WowClientDownloader.patchRetries(PatchWhy(PatchFail.TIMEOUT)))
        for (code in listOf(408, 429, 500, 503)) assertTrue(WowClientDownloader.patchRetries(PatchWhy(PatchFail.HTTP, code)))
        for (code in listOf(400, 401, 403, 404)) assertFalse(WowClientDownloader.patchRetries(PatchWhy(PatchFail.HTTP, code)))
        assertFalse(WowClientDownloader.patchRetries(PatchWhy(PatchFail.NO_BUILD)))
    }

    @Test fun everyLineIsTranslated() {
        val dir = listOf("../companion/app/src/main/assets/companion/i18n", "companion/app/src/main/assets/companion/i18n")
            .map { File(it) }.first { it.isDirectory }
        for (lang in listOf("es", "fr")) {
            val json = File(dir, "$lang.json").readText(Charsets.UTF_8)
            for (f in PatchFail.values()) {
                val key = "Install failed: " + WowClientDownloader.patchFailText(PatchWhy(f, 503)).replace("HTTP 503", "HTTP %s")
                assertTrue("$lang: $key", "\"$key\":" in json)
            }
            assertTrue(lang, "\"Checking the latest build... (try %s of %s)\":" in json)
        }
        val source = File(listOf("src/main/java/app/gamenative/ui/screen/wow", "app/src/main/java/app/gamenative/ui/screen/wow")
            .first { File(it).isDirectory }, "WoWForeverScreen.kt").readText()
        assertTrue(source.contains("errorMessage = \"Install failed: \${e.message}\""))
        assertTrue(source.contains("e as? WowClientDownloader.PatchServerException"))
        val loading = File(dir.parentFile.parentFile.parentFile, "java/com/jrees/wowcompanion/LoadingTopView.kt").readText()
        assertTrue(loading.contains("Tr.t(it)"))
    }
}
