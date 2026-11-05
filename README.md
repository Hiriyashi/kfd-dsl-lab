# DSL для проверки HTTP-ответов

## Запуск тестов

    ./gradlew test

## Пример использования

    val result = validate(response) {
        status { equalTo(200) }
        header("Content-Type") { startsWith("application/json") }
        body { json { field("id") { present() } } }
    }

Пользовательский предикат:

    validate(response) {
        header("X-Trace") { satisfies("non-blank value") { it.isNotBlank() } }
    }

## Пример расхождений

Для HttpResponse(500, {"Content-Type": ["text/plain"]}, "{}"):

    status: expected 200, actual 500
    headers.Content-Type: expected prefix application/json, actual [text/plain]
    body.json.id: expected present, actual <missing>

## Пути

status, headers.<Имя>, body, body.json, body.json.<Поле>.

## Устройство

Получатели и операции:

- ResponseDsl — status, header, body
- StatusDsl — equalTo(Int)
- HeaderDsl — equalTo, startsWith, satisfies
- BodyDsl — absentOrEmpty, notEmpty, json
- JsonDsl — field
- FieldDsl — present, equalTo(String/Long/Boolean)

Все получатели помечены @DslMarker (@Dsl): неявно войти во внешний блок
из внутреннего нельзя, this@validate.status { ... } — можно.

Пользовательский предикат передаётся как (String) -> Boolean в
satisfies(description, predicate). Подходит литерал лямбды, переменная
и ссылка на функцию.

Независимость запусков: validate(response) { ... } на каждый вызов
создаёт новый MutableList<Discrepancy>, поэтому результаты разных
запусков не пересекаются, а входной HttpResponse только читается.

check01..check06 в lesson09.dsl повторяют условия
lesson09.legacy.check01..check06 и возвращают ValidationResult;
тесты сравнивают legacy(r) == dsl(r).isSuccess на одинаковых данных.
