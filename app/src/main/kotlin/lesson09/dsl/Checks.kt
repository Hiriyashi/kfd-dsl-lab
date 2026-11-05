package lesson09.dsl

import lesson09.HttpResponse

fun check01(r: HttpResponse) = validate(r) {
    status { equalTo(204) }
    body { absentOrEmpty() }
}

fun check02(r: HttpResponse) = validate(r) {
    status { equalTo(200) }
    header("Content-Type") { startsWith("application/json") }
    body { notEmpty() }
}

fun check03(r: HttpResponse) = validate(r) {
    status { equalTo(200) }
    header("Accept") { equalTo("application/json") }
    header("Accept") { equalTo("text/html") }
}

fun check04(r: HttpResponse) = validate(r) {
    status { equalTo(200) }
    body { json { field("id") { present() }; field("name") { equalTo("Ivanov") } } }
}

fun check05(r: HttpResponse) = validate(r) {
    status { equalTo(200) }
    body { json { field("id") { equalTo(42) }; field("active") { equalTo(true) } } }
}

fun check06(r: HttpResponse) = validate(r) {
    status { equalTo(422) }
    header("Content-Type") { startsWith("application/json") }
    body { json { field("error") { equalTo("invalid") }; field("retry") { equalTo(false) } } }
}
