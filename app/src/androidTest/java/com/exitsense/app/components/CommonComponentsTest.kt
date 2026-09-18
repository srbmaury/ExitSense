package com.exitsense.app.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exitsense.app.presentation.components.ConfidenceBar
import com.exitsense.app.presentation.components.SignalChip
import com.exitsense.app.presentation.theme.ExitSenseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommonComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confidenceBarShowsScoreAndThreshold() {
        composeRule.setContent {
            ExitSenseTheme { ConfidenceBar(confidence = 42.6f, threshold = 75f) }
        }

        composeRule.onNodeWithText("Confidence: 42%").assertIsDisplayed()
        composeRule.onNodeWithText("Threshold: 75%").assertIsDisplayed()
    }

    @Test
    fun signalChipShowsPositiveScoreWithPlus() {
        composeRule.setContent {
            ExitSenseTheme { SignalChip(label = "Walking", score = 15f) }
        }

        composeRule.onNodeWithText("Walking (+15)").assertIsDisplayed()
    }

    @Test
    fun signalChipShowsZeroScoreWithoutPlus() {
        composeRule.setContent {
            ExitSenseTheme { SignalChip(label = "At Home Wi-Fi", score = 0f) }
        }

        composeRule.onNodeWithText("At Home Wi-Fi (0)").assertIsDisplayed()
    }
}
