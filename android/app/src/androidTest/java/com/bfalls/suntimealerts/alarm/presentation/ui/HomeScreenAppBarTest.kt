package com.bfalls.suntimealerts.alarm.presentation.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.bfalls.suntimealerts.alarm.data.LocationProvider
import com.bfalls.suntimealerts.alarm.data.SettingsRepository
import com.bfalls.suntimealerts.alarm.data.SunScheduler
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarmConfig
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.UserSettings
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.ui.theme.SuntimeAlertsTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

class HomeScreenAppBarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersBackgroundAndLists() = runBlocking {
        val alarms = listOf(
            SunAlarm(
                type = SunEventType.SUNRISE,
                offsetMinutes = 30,
                label = "Test alarm",
                enabled = true
            ),
            SunAlarm(
                type = SunEventType.SUNSET,
                offsetMinutes = -15,
                label = "Evening",
                enabled = true
            )
        )
        val settingsRepo = object : SettingsRepository {
            override suspend fun load(): UserSettings = UserSettings(
                locationMode = LocationMode.FIXED,
                fixedLocation = Coordinate(0.0, 0.0),
                sunriseConfig = SunAlarmConfig(true, SunEventType.SUNRISE, 0),
                sunsetConfig = SunAlarmConfig(true, SunEventType.SUNSET, 0),
                timeFormat24h = true,
                onboardingComplete = true,
                alarms = alarms
            )

            override suspend fun save(settings: UserSettings) = Unit
            override suspend fun loadAlarms(): List<SunAlarm> = alarms
            override suspend fun saveAlarms(alarms: List<SunAlarm>) = Unit
        }
        val viewModel = HomeViewModel(
            locationService = object : LocationProvider {
                override suspend fun currentCoordinate(): Coordinate? = Coordinate(0.0, 0.0)
            },
            settingsStore = settingsRepo,
            scheduleService = object : SunScheduler {
                override suspend fun schedule(coordinate: Coordinate, zoneId: ZoneId) = Unit
            },
            sunTimesCalculator = SunTimesCalculator()
        )

        composeRule.setContent {
            SuntimeAlertsTheme {
                HomeScreen(viewModel)
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sun_appbar_background").assertIsDisplayed()
        composeRule.onNodeWithText("Sunrise").assertExists()
        composeRule.onNodeWithText("Sunset").assertExists()
        composeRule.onNodeWithText("+30m").assertExists()
    }
}
