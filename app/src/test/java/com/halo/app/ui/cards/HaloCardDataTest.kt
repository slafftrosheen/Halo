package com.halo.app.ui.cards

import com.halo.runtime.cognition.interaction.PresentationNode
import com.halo.runtime.cognition.interaction.StructuredPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaloCardDataTest {
    private val source = HaloCardSource("Committed reply", "Provider")

    private fun cards(text: String, question: String? = "Question", projection: StructuredPresentation? = null) =
        homeHaloCards("turn", question, text, projection, source)

    @Test fun boundedChooserProjectsTwelveTypesFromSupportedReplyContent() {
        val examples = listOf(
            Triple(HaloCardType.FACT, "How tall?", "The tower is 368 m tall."),
            Triple(HaloCardType.STEPS, "How do I reset this?", "1. Unplug it.\n2. Plug it back in."),
            Triple(HaloCardType.COMPARE, "Compare the options", "|Bus|Taxi|\n|---|---|\n|25 minutes|15 minutes|"),
            Triple(HaloCardType.TIMELINE, "When?", "1918 — Independence\n1991 — Independence restored"),
            Triple(HaloCardType.PLACE, "Где библиотека?", "На улице Бривибас."),
            Triple(HaloCardType.QUICK_CHECK, "Which animal?", "## Quick check: Which animal?\n- [ ] Frog\n- [x] Bat — Bats are mammals."),
            Triple(HaloCardType.FLIP, "What word?", "## Flip\npaldies\nthank you"),
            Triple(HaloCardType.CHART, "Rainfall", "|Day|Rain (mm)|\n|---|---|\n|Mon|2|\n|Tue|12|"),
            Triple(HaloCardType.CHECKLIST, "What should I pack?", "- [ ] Water\n- [x] Hat"),
            Triple(HaloCardType.WORD, "What does kafija mean?", "Kafija\nCoffee."),
            Triple(HaloCardType.PLAN, "Plan the trip", "1. Leave home.\n2. Take the train."),
        )
        examples.forEach { (type, question, text) ->
            val card = cards(text, question).single()
            assertEquals(type, card.type)
            assertEquals(source, card.source)
            assertTrue(card.speak.isNotBlank())
        }
        val visual = StructuredPresentation(root = PresentationNode.ImageReference("node", "workspace-object", "A photograph"))
        val picture = cards("Here is a photograph.", "What does it look like?", visual).single()
        assertEquals(HaloCardType.PICTURES, picture.type)
        assertEquals("workspace-object", (picture.body as HaloCardBody.Pictures).items.single().assetId)
    }

    @Test fun replyProseStaysFirstAndQuickCheckKeyStaysInTheCard() {
        val intro = "Mammals are warm-blooded."
        val text = "$intro\n\n## Quick check: Which one is a mammal?\n- [ ] Frog\n- [x] Bat — Bats are mammals."
        assertEquals(intro, cardPlainReply(text))
        val card = cards(text).single()
        assertEquals("Which one is a mammal?", card.title)
        assertEquals("Bat. Bats are mammals.", cardPracticeAnswer(card))
        assertFalse(card.speak.contains("Bats are mammals"))
    }

    @Test fun keepsOnlyThreeBoundedCardsAndDoesNotInventPictureReferences() {
        val text = (1..6).joinToString("\n\n") { index -> "## ${"Long title ".repeat(20)}$index\n${index * 100} m" }
        val projected = cards(text)
        assertEquals(3, projected.size)
        assertTrue(projected.all { it.title.length <= 80 })
        assertEquals(3, projected.map { it.id }.distinct().size)
        assertTrue(cards("![A lynx](https://example.test/lynx.png)", "What does a lynx look like?").isEmpty())
    }

    @Test fun explicitUncertaintyAndIncompleteChoiceDataStayTruthful() {
        assertTrue(cards("368 m\nI'm not sure.", "How tall?").single().uncertainty != null)
        val ungraded = "Try these later.\n## Quick check: Which animal?\n- [ ] Frog\n- [ ] Bat"
        assertEquals(ungraded, cardPlainReply(ungraded))
        assertTrue(cards(ungraded).none { it.type == HaloCardType.QUICK_CHECK })
    }
}
