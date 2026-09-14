package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerFrontendLinksTest {
    @Test fun `heartbeat decodes optional owner supplied support page`() {
        val body = Moshi.Builder().build().adapter(RomMHeartbeatResponse::class.java).fromJson(
            """{"SYSTEM":{"VERSION":"5.1.0"},"FRONTEND":{"SUPPORT_URL":"https://support.example/gameio"}}"""
        )!!
        assertEquals("https://support.example/gameio", RomMCapabilities.from(body.version, supportUrl = body.frontend?.supportUrl).supportUrl)
    }

    @Test fun `older server or explicit null has no support destination`() {
        val adapter = Moshi.Builder().build().adapter(RomMHeartbeatResponse::class.java)
        listOf("{}", """{"FRONTEND":{"SUPPORT_URL":null}}""").forEach {
            val body = adapter.fromJson(it)!!
            assertNull(RomMCapabilities.from(body.version, supportUrl = body.frontend?.supportUrl).supportUrl)
        }
    }

    @Test fun `support links require HTTPS without credentials`() {
        listOf(null, "", "http://support.example", "javascript:alert(1)", "intent://support",
            "https://user:password@support.example/", "https://@support.example/", "https://support.example/path\nmore")
            .forEach { assertNull(it, normalizeSupportUrl(it)) }
    }

    @Test fun `hosted links retain owner path and query after normalization`() {
        assertEquals("https://support.example/Gameio?campaign=launcher", normalizeSupportUrl(" https://SUPPORT.example/Gameio?campaign=launcher "))
    }

    @Test fun `support configuration does not depend on version based sync capabilities`() {
        val caps = RomMCapabilities.from(null, supportUrl = "https://support.example/")
        assertEquals(RomMCapabilities.NONE.copy(supportUrl = "https://support.example/"), caps)
    }
}
