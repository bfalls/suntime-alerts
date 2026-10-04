package com.bfalls.suntimealerts.alarm.presentation.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bfalls.suntimealerts.alarm.domain.service.PhotoLight
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoMetrics
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoPeriod
import com.bfalls.suntimealerts.alarm.domain.service.SkyLightPeriod
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Android-only dashboard. Every clock update uses the current shared snapshot. */
@Composable
fun SkyDashboardDialog(metrics: SkyInfoMetrics, use24h: Boolean, onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    val bottomInset = with(LocalDensity.current) { 16.dp.toPx() }
    var viewportBottom by remember { mutableStateOf(0f) }
    val revealExpandedBottom: (Float) -> Unit = { cardBottom ->
        val target = (scrollState.value + cardBottom - viewportBottom + bottomInset).roundToInt()
        scrollScope.launch { scrollState.animateScrollTo(target.coerceAtLeast(0), tween(300)) }
    }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = remember(dark) {
        if (dark) SkyDashboardPalette(Color(0xFFFFCF83), Color(0xFFBFC8FF), Color(0xFF927B49),
            Color(0xFF8AABC9), Color(0xFF59769C), Color(0xFF465373), Color(0xFF303A55))
        else SkyDashboardPalette(Color(0xFFA86519), Color(0xFF6971B0), Color(0xFFEAD49D),
            Color(0xFFCADBEA), Color(0xFFA8BEE0), Color(0xFF8999BD), Color(0xFF626E94))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maximumHeight = maxHeight * 0.85f
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))
                .testTag("sky_dashboard_scrim")
                .semantics { contentDescription = "Dismiss sky information" }
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp)
                    .widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maximumHeight)
                    .testTag("sky_dashboard_panel")
                    // Keep empty areas inside the panel in this hit-test path;
                    // only taps on the sibling scrim dismiss the dialog.
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp
            ) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("Today's sky", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(metrics.now.format(DateTimeFormatter.ofPattern(if (use24h) "MMM d · HH:mm z" else "MMM d · h:mm a z")),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(SkyDashboardText.coordinate(metrics.coordinate.latitude, metrics.coordinate.longitude),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp), CircleShape)) {
                            Icon(Icons.Default.Close, contentDescription = "Close sky information")
                        }
                    }
                    Column(Modifier.weight(1f, fill = false)
                        .onGloballyPositioned { viewportBottom = it.positionInRoot().y + it.size.height }
                        .verticalScroll(scrollState)
                        .testTag("sky_dashboard_scroll").padding(horizontal = 16.dp).padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DaylightCard(metrics, use24h, palette)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardCard(Modifier.weight(1f)) {
                                DashboardHeading("Sun now", false, palette)
                                SkySunElevation(metrics.sunPosition.altitudeDeg, palette)
                                Text(SkyDashboardText.altitude(metrics.sunPosition.altitudeDeg), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                DashboardCaption(SkyDashboardText.bearing(metrics.sunPosition.azimuthDeg))
                            }
                            DashboardCard(Modifier.weight(1f)) {
                                DashboardHeading("Moon now", true, palette)
                                SkyMoonDisc(metrics, palette)
                                Text(SkyDashboardText.altitude(metrics.moonPosition.altitudeDeg), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                DashboardCaption(SkyDashboardText.bearing(metrics.moonPosition.azimuthDeg))
                                DashboardCaption("${(metrics.moonPhase.illumination01 * 100).roundToInt()}% lit")
                            }
                        }
                        DashboardSection("Photography windows", "photography", revealExpandedBottom) { PhotographyDetails(metrics, use24h, palette) }
                        DashboardSection("Twilight & darkness", "twilight", revealExpandedBottom) { TwilightDetails(metrics, use24h) }
                        DashboardSection("Moon phase & rise / set", "moon", revealExpandedBottom) { MoonDetails(metrics, use24h) }
                        DashboardSection("Where to look right now", "direction", revealExpandedBottom) {
                            SkyCompass(metrics, palette)
                            DashboardMetricRow("Solar noon", SkyDashboardText.time(metrics.today.solarNoon?.time, metrics.now, use24h))
                            metrics.today.solarNoon?.let { DashboardMetricRow("Altitude at solar noon", "${it.altitudeDeg.roundToInt()}°") }
                            DashboardMetricRow("Solar midnight", SkyDashboardText.time(metrics.today.solarMidnight?.time, metrics.now, use24h))
                            DashboardMetricRow("Sunrise bearing", SkyDashboardText.bearing(metrics.today.sunriseAzimuthDeg))
                            DashboardMetricRow("Sunset bearing", SkyDashboardText.bearing(metrics.today.sunsetAzimuthDeg))
                            DashboardCaption("Solar noon is the Sun's upper meridian crossing; solar midnight is its lower crossing.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaylightCard(metrics: SkyInfoMetrics, use24h: Boolean, palette: SkyDashboardPalette) {
    val daylight = metrics.today.daylight ?: Duration.ofMillis(metrics.today.lightSegments
        .filter { it.period == SkyLightPeriod.DAYLIGHT }.sumOf { Duration.between(it.start, it.end).toMillis() })
    DashboardCard {
        DashboardHeading("Today's light", false, palette)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(SkyDashboardText.duration(daylight), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                DashboardCaption("Daylight")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(metrics.today.sunset?.let { SkyDashboardText.time(it, metrics.now, use24h) } ?: "No sunset",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                DashboardCaption("Sunset")
            }
        }
        val event = if (metrics.period == SkyInfoPeriod.DAYLIGHT) metrics.nextSunset else metrics.nextSunrise
        event?.let { Text("${if (metrics.period == SkyInfoPeriod.DAYLIGHT) "Sunset" else "Sunrise"} ${SkyDashboardText.until(metrics.now, it)}",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp).testTag("sky_dashboard_countdown")) }
        SkyDayTimeline(metrics, use24h, palette)
        DashboardMetricRow("Sunrise", SkyDashboardText.time(metrics.today.sunrise, metrics.now, use24h))
        DashboardMetricRow("Compared with yesterday", SkyDashboardText.daylightChange(metrics.daylightChange))
    }
}

@Composable
private fun PhotographyDetails(metrics: SkyInfoMetrics, use24h: Boolean, palette: SkyDashboardPalette) {
    DashboardCaption("Blue light −6° to −4° · golden light −4° to +6°")
    SkyPhotoWindowBars(metrics, metrics.photoWindows, palette, use24h)
    listOf(true, false).forEach { morning ->
        Text(if (morning) "Morning" else "Evening", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp).testTag("sky_dashboard_photography_${if (morning) "morning" else "evening"}"))
        listOf(PhotoLight.BLUE, PhotoLight.GOLDEN).forEach { light ->
            val window = metrics.photoWindows.firstOrNull { it.isMorning == morning && it.light == light }
            val name = if (light == PhotoLight.GOLDEN) "Golden hour" else "Blue hour"
            DashboardMetricRow(name, window?.let { "${SkyDashboardText.time(it.start, metrics.now, use24h)} – ${SkyDashboardText.time(it.end, metrics.now, use24h)}" } ?: "No complete window today")
            window?.let { DashboardCaption("${SkyDashboardText.duration(Duration.between(it.start, it.end))}${if (!metrics.now.isBefore(it.start) && metrics.now.isBefore(it.end)) " · Ends ${SkyDashboardText.until(metrics.now, it.end)}" else ""}") }
        }
    }
    metrics.nextPhotoWindow?.let {
        val name = if (it.light == PhotoLight.GOLDEN) "Golden hour" else "Blue hour"
        DashboardMetricRow(if (metrics.now.isBefore(it.start)) "Next opportunity" else "Active now", name)
        DashboardCaption(if (metrics.now.isBefore(it.start)) "Starts ${SkyDashboardText.until(metrics.now, it.start)} · ${SkyDashboardText.time(it.start, metrics.now, use24h)}"
            else "Ends ${SkyDashboardText.until(metrics.now, it.end)}")
    }
}

@Composable
private fun TwilightDetails(metrics: SkyInfoMetrics, use24h: Boolean) {
    listOf(true, false).forEach { morning ->
        Text(if (morning) "Morning" else "Evening", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        listOf(-6.0 to "Civil", -12.0 to "Nautical", -18.0 to "Astronomical").forEach { (altitude, label) ->
            val boundary = metrics.today.crossings.firstOrNull { it.rising == morning && it.altitudeDeg == altitude }
            DashboardMetricRow("$label ${if (morning) "dawn" else "dusk"}", SkyDashboardText.time(boundary?.time, metrics.now, use24h))
            val period = when (altitude) { -6.0 -> SkyLightPeriod.CIVIL_TWILIGHT; -12.0 -> SkyLightPeriod.NAUTICAL_TWILIGHT; else -> SkyLightPeriod.ASTRONOMICAL_TWILIGHT }
            metrics.today.twilightWindows.firstOrNull { it.period == period && it.isMorning == morning }?.let {
                DashboardCaption("${SkyDashboardText.duration(Duration.between(it.start, it.end))} of ${label.lowercase()} twilight")
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 10.dp))
    DashboardMetricRow("Night · sunset to sunrise", SkyDashboardText.duration(metrics.sunsetToSunrise))
    DashboardMetricRow("Astronomical darkness", SkyDashboardText.duration(metrics.astronomicalDarkness))
    DashboardCaption("Darkness spans astronomical dusk to dawn for the current or upcoming night.")
}

@Composable
private fun MoonDetails(metrics: SkyInfoMetrics, use24h: Boolean) {
    DashboardMetricRow("Phase", SkyInfoMessages.phaseName(metrics.moonPhase))
    DashboardMetricRow("Illuminated disk", "${(metrics.moonPhase.illumination01 * 100).roundToInt()}%")
    DashboardMetricRow("Moonrise", metrics.moonWindow.rise?.let { SkyDashboardText.time(it, metrics.now, use24h) } ?: "No rise in this passage")
    DashboardMetricRow("Rise bearing", SkyDashboardText.bearing(metrics.moonRiseAzimuthDeg))
    DashboardMetricRow("Moonset", metrics.moonWindow.set?.let { SkyDashboardText.time(it, metrics.now, use24h) } ?: "No set in this passage")
    DashboardMetricRow("Set bearing", SkyDashboardText.bearing(metrics.moonSetAzimuthDeg))
    DashboardMetricRow("Peak altitude of passage", if (metrics.moonWindow.rise != null && metrics.moonWindow.set != null) "${metrics.moonWindow.maxAltDeg.roundToInt()}°" else "Unavailable")
    DashboardCaption("Rise and set describe the current passage above the horizon, or the next passage when the Moon is below it.")
}

@Composable
private fun DashboardCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun DashboardHeading(title: String, moon: Boolean, palette: SkyDashboardPalette) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(if (moon) Icons.Outlined.NightsStay else Icons.Outlined.WbSunny, contentDescription = null,
            tint = if (moon) palette.moon else palette.sun, modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DashboardSection(title: String, id: String, onExpandedBottom: (Float) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    var revealAfterLayout by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.testTag("sky_dashboard_card_$id").onGloballyPositioned {
        // Reveal once after the expanded content is measured; minute updates retain the user's scroll position.
        if (expanded && revealAfterLayout) {
            revealAfterLayout = false
            onExpandedBottom(it.positionInRoot().y + it.size.height)
        }
    }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().testTag("sky_dashboard_section_$id")
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .clickable(onClickLabel = if (expanded) "Collapse $title" else "Expand $title") {
                    expanded = !expanded
                    revealAfterLayout = expanded
                }
                .padding(horizontal = 14.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            if (expanded) Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), content = content)
        }
    }
}

@Composable
private fun DashboardMetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

@Composable
private fun DashboardCaption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
