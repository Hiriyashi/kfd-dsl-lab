package lesson09.dsl

import lesson09.HttpResponse
import lesson09.support.json.JsonParseResult
import lesson09.support.json.parseJsonObject

@DslMarker annotation class Dsl

data class Discrepancy(val path: String, val expected: String, val actual: String) {
    override fun toString() = "$path: expected $expected, actual $actual"
}

data class ValidationResult(val discrepancies: List<Discrepancy>) {
    val isSuccess get() = discrepancies.isEmpty()
}

fun validate(response: HttpResponse, block: ResponseDsl.() -> Unit): ValidationResult {
    val out = mutableListOf<Discrepancy>()
    ResponseDsl(response, out).block()
    return ValidationResult(out.toList())
}

private fun HttpResponse.headerValues(name: String): List<String>? =
    headers.keys.filter { it.equals(name, ignoreCase = true) }
        .takeIf { it.isNotEmpty() }
        ?.flatMap { headers.getValue(it) }

private fun render(value: Any?): String = if (value is String) "\"$value\"" else "$value"

@Dsl class ResponseDsl(private val response: HttpResponse, private val out: MutableList<Discrepancy>) {
    fun status(block: StatusDsl.() -> Unit) {
        val e = StatusDsl().apply(block).expected ?: return
        if (response.status != e) out += Discrepancy("status", "$e", "${response.status}")
    }
    fun header(name: String, block: HeaderDsl.() -> Unit) {
        val path = "headers.$name"
        val values = response.headerValues(name)
        HeaderDsl().apply(block).checks.forEach { it(values, path, out) }
    }
    fun body(block: BodyDsl.() -> Unit) {
        BodyDsl().apply(block).checks.forEach { it(response.body, out) }
    }
}

@Dsl class StatusDsl {
    var expected: Int? = null
    fun equalTo(value: Int) { expected = value }
}

@Dsl class HeaderDsl {
    val checks = mutableListOf<(List<String>?, String, MutableList<Discrepancy>) -> Unit>()

    fun equalTo(value: String) = check("value $value") { it == value }
    fun startsWith(prefix: String) = check("prefix $prefix") { it.startsWith(prefix) }
    fun satisfies(description: String, predicate: (String) -> Boolean) = check(description, predicate)

    private fun check(description: String, predicate: (String) -> Boolean) {
        checks += { values, path, out ->
            if (values == null || values.none(predicate))
                out += Discrepancy(path, description, values?.toString() ?: "<missing>")
        }
    }
}

@Dsl class BodyDsl {
    val checks = mutableListOf<(String?, MutableList<Discrepancy>) -> Unit>()

    fun absentOrEmpty() {
        checks += { b, out -> if (b != null && b != "") out += Discrepancy("body", "absent or empty", "\"$b\"") }
    }
    fun notEmpty() {
        checks += { b, out ->
            if (b.isNullOrEmpty()) out += Discrepancy("body", "non-empty", if (b == null) "null" else "\"\"")
        }
    }
    fun json(block: JsonDsl.() -> Unit) {
        val fields = JsonDsl().apply(block).fields
        checks += { b, out ->
            if (b == null) out += Discrepancy("body.json", "valid JSON object", "null")
            else when (val p = parseJsonObject(b)) {
                is JsonParseResult.Success -> fields.forEach { it(p.fields, out) }
                is JsonParseResult.Failure -> out += Discrepancy("body.json", "valid JSON object", p.reason)
            }
        }
    }
}

@Dsl class JsonDsl {
    val fields = mutableListOf<(Map<String, Any?>, MutableList<Discrepancy>) -> Unit>()
    fun field(name: String, block: FieldDsl.() -> Unit) {
        val ops = FieldDsl().apply(block).ops
        fields += { m, out ->
            val present = m.containsKey(name)
            ops.forEach { it(name, present, m[name], out) }
        }
    }
}

@Dsl class FieldDsl {
    val ops = mutableListOf<(String, Boolean, Any?, MutableList<Discrepancy>) -> Unit>()

    fun present() = check("present") { true }
    fun equalTo(v: String)  = check("\"$v\"") { it == v }
    fun equalTo(v: Long)    = check("$v")     { it == v }
    fun equalTo(v: Int)     = equalTo(v.toLong())
    fun equalTo(v: Boolean) = check("$v")     { it == v }

    private fun check(description: String, predicate: (Any?) -> Boolean) {
        ops += { name, present, value, out ->
            if (!present || !predicate(value))
                out += Discrepancy("body.json.$name", description, if (!present) "<missing>" else render(value))
        }
    }
}
