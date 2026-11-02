package com.example.appgestin

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.appgestin.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuestionnaireIntegrationTest {
    private fun form(name: String): Questionnaire {
        val json = InstrumentationRegistry.getInstrumentation().context.assets.open(name).bufferedReader().use { it.readText() }
        return parseQuestionnaire(JSONObject(json))
    }
    @Test fun allServerFormsParseWithExplicitMappings() {
        assertEquals(10, form("PHQ-9.json").items.size)
        assertEquals(8, form("GAD-7.json").items.size)
        assertEquals(6, form("ULS-6.json").items.size)
        val cssrs = form("C-SSRS.json")
        assertEquals((1..7).toList(), cssrs.items.map { it.number })
        assertEquals(listOf(1, 0), cssrs.items.first().options.map { it.value })
    }
    @Test fun cssrsSkipsAreExcludedFromPayload() {
        val q = form("C-SSRS.json")
        var answers = List(q.items.size) { -1 }
        answers = q.updateAnswer(answers, 0, 0)
        answers = q.updateAnswer(answers, 1, 0)
        answers = q.updateAnswer(answers, 5, 0)
        assertEquals(listOf(0, 1, 5), q.visibleIndices(answers))
        assertTrue(q.complete(answers))
        val sent = questionnairePayload(java.util.UUID.randomUUID().toString(), q, timestampNow(), answers).getJSONArray("answers")
        assertEquals(listOf(1, 2, 6), (0 until sent.length()).map { sent.getJSONObject(it).getInt("item") })
    }
    @Test fun changingParentClearsDependentResponses() {
        val q = form("C-SSRS.json")
        var answers = List(q.items.size) { -1 }
        answers = q.updateAnswer(answers, 1, 1)
        answers = q.updateAnswer(answers, 2, 1)
        answers = q.updateAnswer(answers, 5, 1)
        answers = q.updateAnswer(answers, 6, 1)
        assertTrue(q.needsSupport(answers))
        answers = q.updateAnswer(answers, 1, 0)
        answers = q.updateAnswer(answers, 5, 0)
        assertEquals(-1, answers[2])
        assertEquals(-1, answers[6])
        assertFalse(q.needsSupport(answers))
    }
    @Test fun impactActivatesOnlyForPositiveAnswersAndRemainsOptional() {
        for(name in listOf("PHQ-9.json", "GAD-7.json")) {
            val q = form(name)
            var answers = List(q.items.size) { if(it==q.items.lastIndex) -1 else 0 }
            assertEquals(q.items.size-1, q.visibleIndices(answers).size)
            answers = q.updateAnswer(answers, 0, 1)
            assertEquals(q.items.size, q.visibleIndices(answers).size)
            assertTrue(q.complete(answers))
            answers = q.updateAnswer(answers, q.items.lastIndex, 2)
            answers = q.updateAnswer(answers, 0, 0)
            assertEquals(-1, answers.last())
        }
    }
    @Test fun nonSequentialBackendIdentifiersArePreserved() {
        val q = Questionnaire("TEST", "Test", listOf("A"), remoteItems=listOf(FormItem(42,"a","A",listOf(AnswerOption(3,"Option")))))
        val payload = questionnairePayload(java.util.UUID.randomUUID().toString(),q,timestampNow(),listOf(3))
        assertEquals(42, payload.getJSONArray("answers").getJSONObject(0).getInt("item"))
    }
    @Test fun liveBackendCatalogMatchesAndroidParser() = runBlocking {
        val api = GstiApi("http://10.0.2.2:8000")
        val docs = api.questionnaireDocuments()
        assertEquals(listOf("PHQ9","GAD7","ULS6","CSSRS_SCREEN"),
            Questionnaires.supported(docs.map(::parseQuestionnaire)).map { it.code })
        val instruments = api.instruments().map { it.code }.toSet()
        assertTrue(docs.all {parseQuestionnaire(it).code in instruments})
        assertTrue(api.metrics().any {it.code=="steps"})
    }
}
