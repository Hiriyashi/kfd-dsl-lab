package lesson09

import lesson09.dsl.*
import lesson09.legacy.check01 as l01
import lesson09.legacy.check02 as l02
import lesson09.legacy.check03 as l03
import lesson09.legacy.check04 as l04
import lesson09.legacy.check05 as l05
import lesson09.legacy.check06 as l06
import kotlin.test.*

class DslTest {
    private fun r(s: Int = 200, h: Map<String, List<String>> = emptyMap(), b: String? = null) =
        HttpResponse(s, h, b)

    @Test fun matchesLegacy() {
        val cases = listOf(
            r(204), r(204, b = ""), r(204, b = " "),
            r(200, mapOf("Content-Type" to listOf("application/json")), "x"),
            r(200, mapOf("Content-Type" to listOf("application/json")), null),
            r(200, mapOf("Content-Type" to listOf("application/json")), "not json"),
            r(200, mapOf("Accept" to listOf("application/json", "text/html"))),
            r(200, mapOf("Accept" to listOf("application/json,text/html"))),
            r(200, b = """{"id":1,"name":"Ivanov"}"""),
            r(200, b = """{"id":null,"name":"Ivanov"}"""),
            r(200, b = """{"id":42,"active":true}"""),
            r(200, b = """{"id":42,"active":false}"""),
            r(422, mapOf("Content-Type" to listOf("application/json")), """{"error":"invalid","retry":false}"""),
            r(422, mapOf("Content-Type" to listOf("text/plain")), """{"error":"invalid","retry":false}"""),
        )
        for (x in cases) {
            assertEquals(l01(x), check01(x).isSuccess); assertEquals(l02(x), check02(x).isSuccess)
            assertEquals(l03(x), check03(x).isSuccess); assertEquals(l04(x), check04(x).isSuccess)
            assertEquals(l05(x), check05(x).isSuccess); assertEquals(l06(x), check06(x).isSuccess)
        }
    }

    @Test fun bodyRules() {
        assertTrue(check01(r(204)).isSuccess)
        assertTrue(check01(r(204, b = "")).isSuccess)
        assertFalse(check01(r(204, b = " ")).isSuccess)
        val ct = mapOf("Content-Type" to listOf("application/json"))
        assertTrue(check02(r(200, ct, " ")).isSuccess)
        assertFalse(check02(r(200, ct, null)).isSuccess)
        assertFalse(check02(r(200, ct, "")).isSuccess)
    }

    @Test fun headerRules() {
        assertTrue(validate(r(h = mapOf("X" to listOf("a"), "x" to listOf("b")))) {
            header("X") { equalTo("a") }
            header("X") { equalTo("b") }
        }.isSuccess)
        assertEquals("<missing>", validate(r()) { header("X") { equalTo("v") } }.discrepancies.single().actual)
        assertEquals("[]", validate(r(h = mapOf("X" to emptyList()))) { header("X") { equalTo("v") } }.discrepancies.single().actual)
        assertTrue(validate(r(h = mapOf("Content-Type" to listOf("application/jsonp")))) {
            header("Content-Type") { startsWith("application/json") }
        }.isSuccess)
        assertFalse(validate(r(h = mapOf("Accept" to listOf("application/json,text/html")))) {
            header("Accept") { equalTo("application/json") }
        }.isSuccess)
        assertFalse(validate(r(h = mapOf("X" to listOf(" Value ")))) {
            header("X") { equalTo("Value") }
        }.isSuccess)
    }

    @Test fun jsonRules() {
        assertTrue(validate(r(b = """{"id":null}""")) { body { json { field("id") { present() } } } }.isSuccess)
        assertFalse(validate(r(b = """{"id":"42"}""")) { body { json { field("id") { equalTo(42) } } } }.isSuccess)
        assertEquals(1, validate(r(b = "not json")) { body { json { field("id") { present() } } } }.discrepancies.size)
        val both = validate(r(500, b = "not json")) {
            status { equalTo(200) }
            body { json { field("id") { present() } } }
        }
        assertEquals(2, both.discrepancies.size)
    }

    @Test fun exampleAndDuplicates() {
        val res = validate(r(500, mapOf("Content-Type" to listOf("text/plain")), "{}")) {
            status { equalTo(200) }
            header("Content-Type") { startsWith("application/json") }
            body { json { field("id") { present() } } }
        }
        assertEquals(
            listOf(
                "status: expected 200, actual 500",
                "headers.Content-Type: expected prefix application/json, actual [text/plain]",
                "body.json.id: expected present, actual <missing>",
            ),
            res.discrepancies.map { it.toString() }
        )
        assertEquals(2, validate(r(500)) { status { equalTo(200) }; status { equalTo(200) } }.discrepancies.size)
    }

    private fun nonBlank(s: String) = s.isNotBlank()

    @Test fun predicateThreeForms() {
        val resp = r(h = mapOf("X-Trace" to listOf("abc")))
        val f: (String) -> Boolean = { it.isNotBlank() }
        val a = validate(resp) { header("X-Trace") { satisfies("non-blank") { it.isNotBlank() } } }
        val b = validate(resp) { header("X-Trace") { satisfies("non-blank", f) } }
        val c = validate(resp) { header("X-Trace") { satisfies("non-blank", ::nonBlank) } }
        assertEquals(a.discrepancies, b.discrepancies)
        assertEquals(a.discrepancies, c.discrepancies)
        assertTrue(a.isSuccess)
    }

    @Test fun independentRunsAndInputUnchanged() {
        val block: ResponseDsl.() -> Unit = { status { equalTo(200) } }
        val headers = mapOf("Content-Type" to listOf("application/json"))
        val resp = HttpResponse(200, headers, """{"id":42}""")
        val before = resp.copy(headers = headers.mapValues { it.value.toList() })

        assertTrue(validate(resp, block).isSuccess)
        val bad = validate(HttpResponse(500, emptyMap(), null), block)
        assertFalse(bad.isSuccess)
        assertEquals(1, bad.discrepancies.size)
        assertTrue(validate(resp, block).isSuccess)
        assertEquals(before, resp)
    }
}
