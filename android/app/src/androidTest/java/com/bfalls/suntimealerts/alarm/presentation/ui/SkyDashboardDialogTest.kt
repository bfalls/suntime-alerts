package com.bfalls.suntimealerts.alarm.presentation.ui

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoMetrics
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.HomeViewModel
import com.bfalls.suntimealerts.ui.theme.SuntimeAlertsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SkyDashboardDialogTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val coordinate = Coordinate(43.61211, -116.39151)
    private val afternoon = ZonedDateTime.of(2026, 10, 4, 15, 1, 0, 0, ZoneId.of("America/Boise"))

    @Test
    fun bannerOpensDashboardAndOnlyOutsideBackOrCloseDismissIt() {
        showHome(mutableStateOf(SkyInfoCalculator().calculate(afternoon, coordinate)))
        open()
        rule.onNodeWithTag("sky_dashboard_panel").performTouchInput { click(Offset(width / 2f, 5f)) }
        rule.onNodeWithText("Today's sky").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close sky information").performClick()
        rule.onNodeWithTag("sky_dashboard_panel").assertDoesNotExist()
        open()
        rule.onNodeWithTag("sky_dashboard_scrim").performTouchInput { click(Offset(5f, 5f)) }
        rule.onNodeWithTag("sky_dashboard_panel").assertDoesNotExist()
        open()
        Espresso.pressBack()
        rule.onNodeWithTag("sky_dashboard_panel").assertDoesNotExist()
        rule.onNodeWithTag("sky_top_bar").assertIsDisplayed()
    }

    @Test
    fun openDashboardUpdatesCountdownWithoutCollapsingExpandedSections() {
        val calculator = SkyInfoCalculator()
        val sunset = calculator.calculate(afternoon, coordinate).today.sunset!!
        val metrics = mutableStateOf(calculator.calculate(sunset.minusMinutes(42), coordinate))
        showHome(metrics)
        open()
        rule.onNodeWithTag("sky_dashboard_countdown").assertTextEquals("Sunset in 42 minutes")
        rule.onNodeWithTag("sky_dashboard_section_photography").performScrollTo().performClick()
        rule.onNodeWithTag("sky_dashboard_photography_evening").assertExists()
        rule.runOnIdle { metrics.value = calculator.calculate(sunset.minusMinutes(41), coordinate) }
        rule.onNodeWithTag("sky_dashboard_photography_evening").assertExists()
        rule.onNodeWithTag("sky_dashboard_countdown").assertTextEquals("Sunset in 41 minutes")
        rule.onNodeWithContentDescription("Close sky information").assertIsDisplayed()
    }

    @Test
    fun largeTextKeepsHeaderReachableAfterScrollingAndPanelWithinScreen() {
        showHome(mutableStateOf(SkyInfoCalculator().calculate(afternoon, coordinate)), fontScale = 1.6f)
        open()
        val panel = rule.onNodeWithTag("sky_dashboard_panel").fetchSemanticsNode().boundsInRoot
        val screen = rule.onNodeWithTag("sky_dashboard_scrim").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.height <= screen.height * 0.85f + 2f)
        rule.onNodeWithTag("sky_dashboard_section_direction").performScrollTo().performClick()
        rule.onNodeWithText("Solar midnight").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Close sky information").assertIsDisplayed().performClick()
        rule.onNodeWithTag("sky_dashboard_panel").assertDoesNotExist()
    }

    @Test
    fun expandingEachSectionAlignsItsBottomWithTheViewport() {
        val calculator = SkyInfoCalculator()
        val metrics = mutableStateOf(calculator.calculate(afternoon, coordinate))
        showHome(metrics, fontScale = 1.6f)
        open()
        listOf("photography", "twilight", "moon", "direction").forEach { id ->
            rule.onNodeWithTag("sky_dashboard_section_$id").performScrollTo().performClick()
            rule.waitForIdle()
            val bottom = rule.onNodeWithTag("sky_dashboard_card_$id").getUnclippedBoundsInRoot().bottom.value
            val viewportBottom = rule.onNodeWithTag("sky_dashboard_scroll").getUnclippedBoundsInRoot().bottom.value
            assertTrue("$id bottom should align with the dashboard bottom", abs(bottom - (viewportBottom - 16f)) <= 2f)
            rule.onNodeWithContentDescription("Close sky information").assertIsDisplayed()
            rule.runOnIdle { metrics.value = calculator.calculate(metrics.value.now.plusMinutes(1), coordinate) }
            rule.waitForIdle()
            val updatedBottom = rule.onNodeWithTag("sky_dashboard_card_$id").getUnclippedBoundsInRoot().bottom.value
            assertTrue("Clock updates should preserve the scroll position", abs(updatedBottom - bottom) <= 2f)
        }
    }

    @Test
    fun dashboardUsesSystemTimeFormatInsteadOfStoredAppFormat() {
        val metrics = SkyInfoCalculator().calculate(afternoon, coordinate)
        val system24h = DateFormat.is24HourFormat(rule.activity)
        showHome(mutableStateOf(metrics), stored24h = !system24h)
        open()
        val pattern = if (system24h) "HH:mm" else "h:mm a"
        rule.onNodeWithText(metrics.today.sunset!!.format(DateTimeFormatter.ofPattern(pattern))).assertIsDisplayed()
        rule.onNodeWithTag("sky_dashboard_section_direction").performScrollTo().performClick()
        rule.onNodeWithText(metrics.today.solarNoon!!.time.format(DateTimeFormatter.ofPattern(pattern))).assertIsDisplayed()
    }

    @Test
    fun changingSystemTimeFormatUpdatesHeadersAndOpenDashboardWithoutClockTick() {
        val original = Settings.System.getString(rule.activity.contentResolver, Settings.System.TIME_12_24)
        val metrics = SkyInfoCalculator().calculate(afternoon, coordinate)
        try {
            setSystemTimeFormat("12")
            showHome(mutableStateOf(metrics))
            val sunset12 = metrics.today.sunset!!.format(DateTimeFormatter.ofPattern("h:mm a"))
            val sunset24 = metrics.today.sunset!!.format(DateTimeFormatter.ofPattern("HH:mm"))
            rule.onNodeWithText("Sunset at $sunset12").assertIsDisplayed()
            open()
            rule.onNodeWithText(sunset12).assertIsDisplayed()
            rule.onNodeWithTag("sky_dashboard_section_direction").performScrollTo().performClick()
            val noon24 = metrics.today.solarNoon!!.time.format(DateTimeFormatter.ofPattern("HH:mm"))
            setSystemTimeFormat("24")
            waitForText(noon24)
            rule.onNodeWithText(noon24).assertIsDisplayed()
            rule.onNodeWithTag("sky_dashboard_section_direction").assertExists()
            rule.onNodeWithContentDescription("Close sky information").performClick()
            rule.onNodeWithText("Sunset at $sunset24").assertIsDisplayed()
            setSystemTimeFormat("12")
            waitForText("Sunset at $sunset12")
            rule.onNodeWithText("Sunset at $sunset12").assertIsDisplayed()
        } finally {
            setSystemTimeFormat(original)
        }
    }

    private fun waitForText(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun setSystemTimeFormat(format: String?) {
        val command = if (format == null) "settings delete system time_12_24" else "settings put system time_12_24 $format"
        val result = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
    }

    @Test
    fun rendersDashboardInLightDarkAndPolarStates() {
        val calculator = SkyInfoCalculator()
        val metrics = mutableStateOf(calculator.calculate(afternoon, coordinate))
        val dark = mutableStateOf(false)
        showHome(metrics, dark)
        open()
        savePanel("sky-dashboard-light.png")
        rule.onNodeWithTag("sky_dashboard_section_photography").performScrollTo().performClick()
        rule.onNodeWithTag("sky_dashboard_photography_evening").performScrollTo()
        savePanel("sky-dashboard-photography.png")
        rule.onNodeWithTag("sky_dashboard_section_photography").performScrollTo().performClick()
        rule.onNodeWithTag("sky_dashboard_countdown").performScrollTo()
        rule.runOnIdle { dark.value = true }
        savePanel("sky-dashboard-dark.png")
        rule.runOnIdle { metrics.value = calculator.calculate(ZonedDateTime.parse("2026-06-21T12:00:00Z"), Coordinate(85.0, 0.0)) }
        rule.onNodeWithText("No sunset").assertExists()
        savePanel("sky-dashboard-polar.png")
    }

    private fun open() {
        rule.onNodeWithTag("sky_info_overlay").performClick()
        rule.onNodeWithText("Today's sky").assertIsDisplayed()
    }

    private fun savePanel(filename: String) {
        val image = rule.onNodeWithTag("sky_dashboard_panel").captureToImage().asAndroidBitmap()
        File(rule.activity.getExternalFilesDir(null), filename).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun showHome(metrics: MutableState<SkyInfoMetrics>, dark: MutableState<Boolean> = mutableStateOf(false), fontScale: Float = 1f, stored24h: Boolean = false) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SuntimeAlertsTheme(darkTheme = dark.value, dynamicColor = false) {
                    val current = metrics.value
                    HomeScreenContent(
                        state = HomeViewModel.State(isLoading = false, coordinateUsed = current.coordinate,
                            sunriseTime = current.today.sunrise, sunsetTime = current.today.sunset,
                            now = current.now, skyInfoMetrics = current, timeFormat24h = stored24h,
                            moonRiseTime = current.moonWindow.rise, moonSetTime = current.moonWindow.set,
                            moonMaxAltDeg = current.moonWindow.maxAltDeg,
                            moonIllumination01 = current.moonPhase.illumination01, moonIsWaxing = current.moonPhase.isWaxing),
                        onAddAlarm = {}, onUpdateAlarm = {}, onToggleAlarmEnabled = { _, _ -> },
                        onDeleteAlarm = {}, onDuplicateAlarm = {}, onRestoreAlarm = { _, _ -> },
                        onOpenSettings = {}, onOpenNotificationSettings = {}, onOpenNotificationChannelSettings = {},
                        onOpenExactAlarmSettings = {}, onOpenFullScreenIntentSettings = {}
                    )
                }
            }
        }
    }
}
