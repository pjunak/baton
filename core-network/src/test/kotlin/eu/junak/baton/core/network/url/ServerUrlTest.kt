package eu.junak.baton.core.network.url

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `bare host defaults to https`() {
        val result = ServerUrl.normalize("music.example.com")
        assertTrue(result is UrlValidation.Valid)
        assertEquals("https://music.example.com/", (result as UrlValidation.Valid).baseUrl.toString())
    }

    @Test
    fun `http is rejected (HTTPS-only)`() {
        assertTrue(ServerUrl.normalize("http://music.example.com") is UrlValidation.Invalid)
    }

    @Test
    fun `blank input is invalid`() {
        assertTrue(ServerUrl.normalize("   ") is UrlValidation.Invalid)
    }

    @Test
    fun `garbage input is invalid`() {
        assertTrue(ServerUrl.normalize("https://") is UrlValidation.Invalid)
    }

    @Test
    fun `api url is built under the base`() {
        val base = (ServerUrl.normalize("https://music.example.com") as UrlValidation.Valid).baseUrl
        assertEquals("https://music.example.com/api/health", ServerUrl.apiUrl(base, "health").toString())
    }

    @Test
    fun `sub-path mount is preserved for ws url`() {
        val base = (ServerUrl.normalize("https://host.example/music") as UrlValidation.Valid).baseUrl
        assertEquals("https://host.example/music/api/ws", ServerUrl.wsUrl(base).toString())
    }
}
