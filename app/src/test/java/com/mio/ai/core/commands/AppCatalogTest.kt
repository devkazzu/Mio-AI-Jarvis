package com.mio.ai.core.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppCatalogTest {

    @Test
    fun `finds by alias`() {
        assertEquals("com.instagram.android", AppCatalog.find("insta")?.packageName)
        assertEquals("com.google.android.youtube", AppCatalog.find("you tube")?.packageName)
        assertEquals("com.whatsapp", AppCatalog.find("whats app")?.packageName)
    }

    @Test
    fun `finds by display name`() {
        assertEquals("Spotify", AppCatalog.find("spotify")?.displayName)
    }

    @Test
    fun `unknown apps return null`() {
        assertNull(AppCatalog.find("flibbertigibbet"))
        assertNull(AppCatalog.find(""))
    }
}
