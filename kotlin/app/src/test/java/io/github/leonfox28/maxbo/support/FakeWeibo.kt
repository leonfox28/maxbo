package io.github.leonfox28.maxbo.support

import androidx.test.core.app.ApplicationProvider
import io.github.leonfox28.maxbo.core.net.RequestThrottle
import io.github.leonfox28.maxbo.core.net.WeiboCookieJar
import io.github.leonfox28.maxbo.core.net.WeiboHttp
import io.github.leonfox28.maxbo.data.api.WeiboApi
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.SocketFactory
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

/**
 * Weibo and every other host, played by one local HTTPS server: requests go through the whole of
 * OkHttp (TLS, redirects, cookies, interceptors) as they would to the real thing, and [respond]
 * answers them. Use [client] as the base of the clients under test.
 *
 * Tests using it run under Robolectric: OkHttp reads the public suffix list (which it checks a
 * cookie's domain against) from the app's assets.
 */
class FakeWeibo : Closeable {
    init {
        OkHttp.initialize(ApplicationProvider.getApplicationContext())
    }

    /** Answers each request; called on the server's threads, so it may block to hold one back. */
    @Volatile var respond: (RecordedRequest) -> MockResponse = { json("""{"ok":1}""") }

    /** Every request received, in order. */
    val requests: MutableList<RecordedRequest> = CopyOnWriteArrayList()

    private val loopback = InetAddress.getByName("127.0.0.1")
    private val certificate =
        HeldCertificate.Builder()
            .addSubjectAlternativeName("weibo.com")
            .addSubjectAlternativeName("*.weibo.com")
            .addSubjectAlternativeName("*.api.weibo.com")
            .addSubjectAlternativeName("weibo.cn")
            .addSubjectAlternativeName("*.weibo.cn")
            .addSubjectAlternativeName("*.sina.com.cn")
            .addSubjectAlternativeName("example.com")
            .build()

    private val server =
        MockWebServer().apply {
            useHttps(
                HandshakeCertificates.Builder()
                    .heldCertificate(certificate)
                    .build()
                    .sslSocketFactory()
            )
            protocols = listOf(Protocol.HTTP_1_1)
            dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        requests += request
                        return respond(request)
                    }
                }
            start(loopback, 0)
        }

    val client: OkHttpClient = run {
        val trust =
            HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        OkHttpClient.Builder()
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .dns { listOf(loopback) }
            .socketFactory(ToPort(server.port))
            .build()
    }

    /** A client on [jar], without the throttle's spacing. */
    fun http(jar: WeiboCookieJar): WeiboHttp =
        WeiboHttp.create(client, jar, RequestThrottle(minIntervalMs = 0))

    /** An API signed in with a `SUB`, or with [cookies]. */
    fun api(
        cookies: Map<String, Map<String, String>> = mapOf(WeiboCookieJar.WEB to mapOf("SUB" to "x"))
    ) = WeiboApi(http(WeiboCookieJar(cookies)))

    override fun close() {
        server.close()
    }

    /** Connects to the server's port, whatever port the URL has. */
    private class ToPort(private val serverPort: Int) : SocketFactory() {
        override fun createSocket(): Socket =
            object : Socket() {
                override fun connect(endpoint: SocketAddress, timeout: Int) {
                    super.connect(
                        InetSocketAddress((endpoint as InetSocketAddress).address, serverPort),
                        timeout,
                    )
                }
            }

        override fun createSocket(host: String, port: Int): Socket = unsupported()

        override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int) =
            unsupported()

        override fun createSocket(host: InetAddress, port: Int): Socket = unsupported()

        override fun createSocket(
            address: InetAddress,
            port: Int,
            local: InetAddress,
            localPort: Int,
        ): Socket = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException()
    }
}

fun json(body: String, status: Int = 200, vararg headers: Pair<String, String>): MockResponse =
    MockResponse.Builder()
        .code(status)
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .apply { for ((name, value) in headers) addHeader(name, value) }
        .body(body)
        .build()

fun html(body: String, status: Int = 200): MockResponse =
    MockResponse(status, headersOf("Content-Type", "text/html; charset=utf-8"), body)

fun redirect(to: String): MockResponse = MockResponse(302, headersOf("Location", to))

/** A weibo.com home page whose embedded `$CONFIG` names [uid] (0 = visitor). */
fun homePage(uid: Int = 42): MockResponse {
    val user = if (uid == 0) "null" else """{"idstr":"$uid","screen_name":"测试用户"}"""
    return html("<script>try{window.\$CONFIG = {\"user\":$user,\"uid\":$uid};}catch(e){}</script>")
}

/** The host a request was sent to. */
val RecordedRequest.host: String
    get() = url.host

/** The form fields of a POST. */
val RecordedRequest.form: Map<String, String>
    get() =
        body
            ?.utf8()
            .orEmpty()
            .split('&')
            .filter { it.isNotEmpty() }
            .associate {
                val (name, value) = it.split('=', limit = 2)
                java.net.URLDecoder.decode(name, "UTF-8") to
                    java.net.URLDecoder.decode(value, "UTF-8")
            }
