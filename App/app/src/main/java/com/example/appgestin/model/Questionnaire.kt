package com.example.appgestin.model

import org.json.JSONArray
import org.json.JSONObject

data class Questionnaire(
    val code: String,
    val title: String,
    val questions: List<String>,
    val introduction: String = "Durante las últimas 2 semanas, ¿qué tan seguido ha tenido molestias debido a los siguientes problemas?",
    val minimum: Int = 0,
    val responseLabels: List<String> = listOf("Ningún día", "Varios días", "Más de la mitad de los días", "Casi todos los días"),
    val attribution: String = "Elaborado por los doctores Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke y colegas, mediante una subvención educativa otorgada por Pfizer Inc.",
    val remoteItems: List<FormItem>? = null,
    val version: String = "local"
) {
    val items: List<FormItem> get() = remoteItems ?: questions.mapIndexed { index, text ->
        FormItem(index + 1, "item_${index + 1}", text,
            responseLabels.mapIndexed { i, label -> AnswerOption(minimum + i, label) })
    }

    fun visibleIndices(answers: List<Int>): List<Int> {
        val visible = mutableListOf<Int>()
        val activeAnswers = mutableMapOf<String, Int>()
        items.forEachIndexed { i, item ->
            val show = (item.dependsOn == null || activeAnswers[item.dependsOn] == item.expectedValue) &&
                (!item.activateOnAnyPositive || activeAnswers.values.any { it > 0 })
            if (show) {
                visible.add(i)
                if (answers.getOrElse(i) { -1 } >= 0) activeAnswers[item.code] = answers[i]
            }
        }
        return visible
    }

    fun updateAnswer(answers: List<Int>, index: Int, value: Int): List<Int> {
        require(value in items[index].options.map { it.value })
        val next = answers.toMutableList().also { it[index] = value }
        val visible = visibleIndices(next).toSet()
        return next.mapIndexed { i, answer -> if (i in visible) answer else -1 }
    }

    fun complete(answers: List<Int>): Boolean = answers.size == items.size &&
        visibleIndices(answers).all { i -> items[i].optional && answers[i] == -1 ||
            items[i].options.any { it.value == answers[i] } }

    fun needsSupport(answers: List<Int>): Boolean = visibleIndices(answers).any { i ->
        answers[i] > 0 && (code == "PHQ9" && items[i].number == 9 || code == "CSSRS_SCREEN")
    }
}

data class AnswerOption(val value: Int, val label: String)
data class FormItem(val number: Int, val code: String, val text: String,
    val options: List<AnswerOption>, val timeFrame: String = "", val dependsOn: String? = null,
    val expectedValue: Int = 1, val activateOnAnyPositive: Boolean = false, val optional: Boolean = false)

/** Parses data only: branching expressions from the server are never executed. */
fun parseQuestionnaire(json: JSONObject): Questionnaire {
    fun numeric(value: Any): Int = when (value) {
        is Boolean -> if (value) 1 else 0
        is Number -> value.toInt()
        else -> error("Valor de respuesta no admitido")
    }
    fun options(array: JSONArray): List<AnswerOption> = (0 until array.length()).map { i ->
        val entry = array.getJSONObject(i)
        AnswerOption(numeric(entry.get("value")), entry.getString("label"))
    }.also { list -> require(list.isNotEmpty() && list.map { it.value }.distinct().size == list.size) }
    val common = json.optJSONObject("scale")?.optJSONArray("options")?.let(::options)
    val parsed = mutableListOf<FormItem>()
    fun add(entry: JSONObject, inherited: List<AnswerOption>?, impact: Boolean = false) {
        val opts = entry.optJSONArray("options")?.let(::options) ?: inherited
            ?: error("Faltan las opciones del cuestionario")
        val condition = entry.optJSONObject("depends_on")
        require(!entry.optBoolean("is_conditional") || impact || condition != null) {
            "Regla condicional no admitida"
        }
        parsed.add(FormItem(entry.getInt("backend_item"), entry.getString("code"),
            entry.getString("text"), opts, entry.optString("time_frame"),
            condition?.getString("item_code"), condition?.get("expected_value")?.let(::numeric) ?: 1,
            impact, impact))
        entry.optJSONArray("sub_items")?.let { arr ->
            for (i in 0 until arr.length()) add(arr.getJSONObject(i), opts)
        }
    }
    val entries = json.getJSONArray("items")
    for (i in 0 until entries.length()) add(entries.getJSONObject(i), common)
    json.optJSONObject("functional_impact_item")?.let { add(it, common, true) }
    require(parsed.isNotEmpty() && parsed.map { it.number }.distinct().size == parsed.size)
    val prior = mutableSetOf<String>()
    parsed.forEach { item ->
        require(item.dependsOn == null || item.dependsOn in prior) { "Dependencia de pregunta inválida" }
        require(item.number > 0 && item.options.all { it.value >= 0 })
        require(prior.add(item.code))
    }
    val code = json.getJSONObject("backend").getString("instrument")
    return Questionnaire(code, json.getString("name"), parsed.map { it.text },
        introduction = json.optString("time_frame", "Responde según el periodo indicado en cada pregunta."),
        attribution = listOfNotNull(Questionnaires.attributions[code],
            json.optString("translation_note").takeIf { it.isNotBlank() },
            "Contenido del catálogo del estudio · versión ${json.getString("version")}").joinToString("\n"),
        remoteItems = parsed, version = json.getString("version"))
}

/** The participant app currently consumes only these four server-defined forms. */
object Questionnaires {
    val codes = listOf("PHQ9", "GAD7", "ULS6", "CSSRS_SCREEN")
    val attributions = mapOf(
        "PHQ9" to "Elaborado por los doctores Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke y colegas, mediante una subvención educativa otorgada por Pfizer Inc.",
        "GAD7" to "Elaborado por los doctores Robert L. Spitzer, Janet B.W. Williams, Kurt Kroenke y colegas, mediante una subvención educativa otorgada por Pfizer Inc."
    )

    // Also filters old cached catalogs; no local wording is added as a fallback.
    fun supported(forms: List<Questionnaire>): List<Questionnaire> = codes.mapNotNull { code ->
        forms.firstOrNull { it.code == code && it.remoteItems != null }
    }
}

fun questionnairePayload(responseId: String, questionnaire: Questionnaire, startedAt: String,
    answers: List<Int>): JSONObject = JSONObject().apply {
    put("client_response_id", responseId)
    put("instrument", questionnaire.code)
    put("context", "adhoc")
    put("started_at", startedAt)
    put("completed_at", timestampNow())
    put("answers", JSONArray().apply {
        require(questionnaire.complete(answers)) { "Respuestas incompletas o fuera de rango" }
        questionnaire.visibleIndices(answers).forEach { index ->
            val value = answers[index]
            if (value >= 0) put(JSONObject().put("item", questionnaire.items[index].number).put("value", value))
        }
    })
}
