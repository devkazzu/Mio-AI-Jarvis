package com.mio.ai.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MiniJsonTest {

    @Test
    fun `parses nested objects`() {
        val root = MiniJson.asMap(MiniJson.parse("""{"a":1,"b":{"c":"hi","d":[true,null,2.5]}}"""))
        assertEquals(1, MiniJson.asInt(root["a"]))
        val b = MiniJson.asMap(root["b"])
        assertEquals("hi", MiniJson.asString(b["c"]))
        val d = MiniJson.asList(b["d"])
        assertEquals(true, d[0])
        assertNull(d[1])
    }

    @Test
    fun `extracts json from prose`() {
        val raw = "Sure! Here you go:\n```json\n{\"mode\":\"chat\",\"reply\":\"Hi!\"}\n```"
        val found = MiniJson.extractFirstJson(raw)
        assertNotNull(found)
        val root = MiniJson.asMap(MiniJson.parse(found!!))
        assertEquals("chat", MiniJson.asString(root["mode"]))
        assertEquals("Hi!", MiniJson.asString(root["reply"]))
    }

    @Test
    fun `stringify round trip`() {
        val payload = mapOf(
            "model" to "x",
            "messages" to listOf(mapOf("role" to "user", "content" to "he said \"hi\"\nbye")),
            "n" to 3,
        )
        val text = MiniJson.stringify(payload)
        val back = MiniJson.asMap(MiniJson.parse(text))
        assertEquals("x", MiniJson.asString(back["model"]))
        val msgs = MiniJson.asList(back["messages"])
        val content = MiniJson.asString(MiniJson.asMap(msgs[0])["content"])
        assertEquals("he said \"hi\"\nbye", content)
    }

    @Test
    fun `tolerates trailing commas`() {
        val list = MiniJson.asList(MiniJson.parse("""[1,2,]"""))
        assertEquals(2, list.size)
    }
}
