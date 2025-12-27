package com.bfalls.suntimealerts.alarm.presentation.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.formatOffset
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.HomeViewModel
import com.bfalls.suntimealerts.ui.theme.SuntimeAlertsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class HomeScreenAppBarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersAppBarBackgroundAndAlarmRows() {
        val sunrise = ZonedDateTime.of(2024, 6, 1, 6, 0, 0, 0, ZoneId.of("UTC"))
        val sunset = ZonedDateTime.of(2024, 6, 1, 20, 0, 0, 0, ZoneId.of("UTC"))
        val state = HomeViewModel.State(
            isLoading = false,
            sunriseTime = sunrise,
            sunsetTime = sunset,
            sunriseTimeText = "06:00",
            sunsetTimeText = "20:00",
            sunriseAlarms = listOf(
                SunAlarm(
                    id = "sunrise-1",
                    type = SunEventType.SUNRISE,
                    offsetMinutes = 30,
                    label = "Morning walk",
                    enabled = true
                )
            ),
            sunsetAlarms = listOf(
                SunAlarm(
                    id = "sunset-1",
                    type = SunEventType.SUNSET,
                    offsetMinutes = -15,
                    label = "Evening wind down",
                    enabled = true
                )
            ),
            error = null
        )

        composeTestRule.setContent {
            SuntimeAlertsTheme {
                HomeScreenContent(
                    state = state,
                    onAddAlarm = { _, _, _, _ -> },
                    onUpdateAlarm = {},
                    onToggleAlarmEnabled = { _, _ -> },
                    onDeleteAlarm = {},
                    onDuplicateAlarm = {},
                    onRestoreAlarm = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("sun_appbar_background").assertExists()
        composeTestRule.onNodeWithText("Sunrise").assertExists()
        composeTestRule.onNodeWithText("Sunset").assertExists()
        composeTestRule.onNodeWithText(formatOffset(30)).assertExists()
        composeTestRule.onNodeWithText(formatOffset(-15)).assertExists()
    }
}
