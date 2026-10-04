package com.bfalls.suntimealerts.alarm.presentation.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoMetrics
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.HomeViewModel
import com.bfalls.suntimealerts.ui.theme.SuntimeAlertsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class SkyInfoOverlayTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val coordinate = Coordinate(43.615, -116.2023)
    private val noon = LocalDate.of(2026, 10, 4).atTime(12, 0).atZone(ZoneId.of("America/Boise"))

    @Test
    fun rotatesAfterTimeoutButMinuteUpdatesDoNotResetTheTimer() {
        val calculator = SkyInfoCalculator()
        val sunset = calculator.calculate(noon, coordinate).today.sunset!!
        val metrics = mutableStateOf(calculator.calculate(sunset.minusMinutes(42), coordinate))
        rule.mainClock.autoAdvance = false
        rule.setContent { SuntimeAlertsTheme { Box { SkyInfoOverlay(metrics.value) } } }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Sunset in 42 minutes").assertExists()
        rule.mainClock.advanceTimeBy(4_000)
        rule.runOnIdle { metrics.value = calculator.calculate(sunset.minusMinutes(41), coordinate) }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Sunset in 41 minutes").assertExists()
        rule.onNodeWithText("Sunset in 42 minutes").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(5_000)
        val nextMessage = SkyInfoMessages.forMetrics(metrics.value)[1]
        rule.onNodeWithText(nextMessage.text).assertExists()
        rule.onNodeWithText("Sunset in 41 minutes").assertDoesNotExist()
        rule.onNodeWithTag("sky_info_overlay").assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun preparedTapHandlerReceivesCompleteCurrentMetrics() {
        val metrics = SkyInfoCalculator().calculate(noon, coordinate)
        var opened: SkyInfoMetrics? = null
        rule.setContent { SuntimeAlertsTheme { SkyInfoOverlay(metrics, onOpenAdvancedInfo = { opened = it }) } }
        rule.onNodeWithTag("sky_info_overlay").performClick()
        rule.runOnIdle { assertEquals(metrics, opened) }
    }

    @Test
    fun overlaysAllFourPeriodsWithoutGrowingTheBanner() {
        val calculator = SkyInfoCalculator()
        val day = calculator.calculate(noon, coordinate).today
        val metrics = mutableStateOf(calculator.calculate(day.sunrise!!.minusMinutes(10), coordinate))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SuntimeAlertsTheme {
                val current = metrics.value
                HomeScreenContent(
                    state = HomeViewModel.State(
                        isLoading = false, coordinateUsed = coordinate,
                        sunriseTime = current.today.sunrise, sunsetTime = current.today.sunset,
                        now = current.now, skyInfoMetrics = current,
                        moonRiseTime = current.moonWindow.rise, moonSetTime = current.moonWindow.set,
                        moonMaxAltDeg = current.moonWindow.maxAltDeg,
                        moonIllumination01 = current.moonPhase.illumination01, moonIsWaxing = current.moonPhase.isWaxing
                    ),
                    onAddAlarm = {}, onUpdateAlarm = {}, onToggleAlarmEnabled = { _, _ -> },
                    onDeleteAlarm = {}, onDuplicateAlarm = {}, onRestoreAlarm = { _, _ -> },
                    onOpenSettings = {}, onOpenNotificationSettings = {}, onOpenNotificationChannelSettings = {},
                    onOpenExactAlarmSettings = {}, onOpenFullScreenIntentSettings = {}
                )
            }
        }
        val views = listOf(
            "dawn" to day.sunrise.minusMinutes(10),
            "daylight" to noon,
            "twilight" to day.sunset!!.plusMinutes(10),
            "night" to noon.withHour(23)
        )
        views.forEach { (name, time) ->
            rule.runOnIdle { metrics.value = calculator.calculate(time, coordinate) }
            rule.mainClock.advanceTimeBy(1_000)
            rule.onNodeWithTag("sky_top_bar").assertHeightIsEqualTo(200.dp)
            rule.onNodeWithTag("sky_info_overlay").assertHeightIsEqualTo(48.dp)
            rule.onNodeWithText(SkyInfoMessages.forMetrics(metrics.value).first().text).assertExists()
            val image = rule.onNodeWithTag("sky_top_bar").captureToImage().asAndroidBitmap()
            File(rule.activity.getExternalFilesDir(null), "sky-info-$name.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
