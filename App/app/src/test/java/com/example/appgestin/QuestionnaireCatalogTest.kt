package com.example.appgestin

import com.example.appgestin.model.AnswerOption
import com.example.appgestin.model.FormItem
import com.example.appgestin.model.Questionnaire
import com.example.appgestin.model.Questionnaires
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireCatalogTest {
    private fun remote(code: String) = Questionnaire(code, code, listOf("Pregunta"),
        remoteItems = listOf(FormItem(1, "item_1", "Pregunta", listOf(AnswerOption(0, "No")))))

    @Test fun onlyFourRemoteFormsSurviveMixedOldCatalogs() {
        val oldCatalog = listOf(remote("SUS"), remote("CSSRS_SCREEN"), remote("UCLA3"),
            remote("GAD7"), remote("MSPSS"), remote("ULS6"), remote("NSSI"), remote("PHQ9"), remote("EMA_DAILY"))
        assertEquals(listOf("PHQ9", "GAD7", "ULS6", "CSSRS_SCREEN"),
            Questionnaires.supported(oldCatalog).map { it.code })
    }

    @Test fun localFormsAreNeverUsedAsFallbackForServerQuestions() {
        val local = Questionnaire("PHQ9", "Texto local antiguo", listOf("Pregunta local"))
        assertTrue(Questionnaires.supported(listOf(local)).isEmpty())
        assertTrue(Questionnaires.supported(emptyList()).isEmpty())
    }
}
