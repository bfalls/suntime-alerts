package com.bfalls.suntimealerts.alarm.presentation.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.formatOffset
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.HomeViewModel
import com.bfalls.suntimealerts.ui.theme.SuntimeAlertsTheme
import com.bfalls.suntimealerts.alarm.presentation.ui.MoonVisibleKey
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

        composeTestRule.onNodeWithTag("sky_appbar_background").assertExists()
        composeTestRule.onNodeWithText("Sunrise").assertExists()
        composeTestRule.onNodeWithText("Sunset").assertExists()
        composeTestRule.onNodeWithText(formatOffset(30)).assertExists()
        composeTestRule.onNodeWithText(formatOffset(-15)).assertExists()
    }

    @Test
    fun addSheetShowsOffsetControlsAndCanCancel() {
        val sunrise = ZonedDateTime.of(2024, 6, 1, 6, 0, 0, 0, ZoneId.of("UTC"))
        val sunset = ZonedDateTime.of(2024, 6, 1, 20, 0, 0, 0, ZoneId.of("UTC"))
        val state = HomeViewModel.State(
            isLoading = false,
            sunriseTime = sunrise,
            sunsetTime = sunset,
            sunriseTimeText = "06:00",
            sunsetTimeText = "20:00",
            sunriseAlarms = emptyList(),
            sunsetAlarms = emptyList(),
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

        composeTestRule.onNodeWithContentDescription("Add alarm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Before").assertExists()
        composeTestRule.onNodeWithText("After").assertExists()
        composeTestRule.onNodeWithText("Hours").assertExists()
        composeTestRule.onNodeWithText("Minutes").assertExists()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithText("Label").assertDoesNotExist()
    }

    @Test
    fun showsMoonVisibleSemanticsWhenWithinArc() {
        val zoneId = ZoneId.of("UTC")
        val now = ZonedDateTime.now(zoneId)
        val sunrise = now.withHour(6).withMinute(0).withSecond(0).withNano(0)
        val sunset = now.withHour(20).withMinute(0).withSecond(0).withNano(0)
        val state = HomeViewModel.State(
            isLoading = false,
            sunriseTime = sunrise,
            sunsetTime = sunset,
            sunriseTimeText = "06:00",
            sunsetTimeText = "20:00",
            sunriseAlarms = emptyList(),
            sunsetAlarms = emptyList(),
            coordinateUsed = Coordinate(0.0, 0.0),
            moonRiseTime = now.minusHours(1),
            moonSetTime = now.plusHours(5),
            moonMaxAltDeg = 45.0,
            moonIllumination01 = 0.5,
            moonIsWaxing = true,
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

        composeTestRule.onNodeWithTag("sky_appbar_background")
            .assert(SemanticsMatcher.expectValue(MoonVisibleKey, true))
    }
}
