package com.example.lilspeaker.features.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lilspeaker.MainActivity
import com.example.lilspeaker.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rendersTopBarToggles() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.telemetry_label)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.diagnostics_label)).assertIsDisplayed()
    }
}
