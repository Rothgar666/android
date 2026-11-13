package io.homeassistant.companion.android.util

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TailscaleUrlTest {

    @ParameterizedTest(name = "{0} is a Tailscale address")
    @ValueSource(
        strings = [
            "http://100.64.0.1:8123",
            "http://100.100.100.100",
            "http://100.127.255.254:8123",
            "http://homeassistant.tail1234.ts.net:8123",
            "https://HomeAssistant.Tail1234.TS.NET",
            "http://[fd7a:115c:a1e0:ab12:4843:cd96:6258:1234]:8123",
        ],
    )
    fun `Given a Tailscale address when checking then it is recognized`(url: String) {
        assertTrue(url.toHttpUrl().isTailscaleAddress())
    }

    @ParameterizedTest(name = "{0} is not a Tailscale address")
    @ValueSource(
        strings = [
            "http://100.63.255.255",
            "http://100.128.0.1",
            "http://99.64.0.1",
            "http://192.168.1.10:8123",
            "http://10.0.0.5",
            "http://homeassistant.local:8123",
            "http://example.com",
            "http://evil-ts.net",
            "http://100.64.0.1.example.com",
            "http://example.ts.net.evil.com",
            "http://[fd00::1]:8123",
        ],
    )
    fun `Given another address when checking then it is not recognized`(url: String) {
        assertFalse(url.toHttpUrl().isTailscaleAddress())
    }
}
