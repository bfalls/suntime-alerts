package com.bfalls.suntimealerts.alarm.presentation.ui

import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.widget.EditText
import android.widget.NumberPicker
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
//import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.bfalls.suntimealerts.R
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.formatOffset
import com.bfalls.suntimealerts.alarm.domain.service.MoonArcPositionCalculator
import com.bfalls.suntimealerts.alarm.domain.service.MoonXY
import com.bfalls.suntimealerts.alarm.domain.service.SunArcPositionCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SunXY
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@VisibleForTesting
val MoonVisibleKey = SemanticsPropertyKey<Boolean>("MoonVisible")

private fun Modifier.moonVisible(isVisible: Boolean): Modifier = semantics {
    this[MoonVisibleKey] = isVisible
}

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    HomeScreenContent(
        state = state,
        onAddAlarm = viewModel::addAlarm,
        onUpdateAlarm = viewModel::updateAlarm,
        onToggleAlarmEnabled = viewModel::toggleAlarmEnabled,
        onDeleteAlarm = viewModel::deleteAlarm,
        onDuplicateAlarm = viewModel::duplicateAlarm,
        onRestoreAlarm = viewModel::restoreAlarm
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreenContent(
    state: HomeViewModel.State,
    onAddAlarm: (SunEventType, Int, String, Boolean) -> Unit,
    onUpdateAlarm: (SunAlarm) -> Unit,
    onToggleAlarmEnabled: (String, Boolean) -> Unit,
    onDeleteAlarm: (String) -> Unit,
    onDuplicateAlarm: (String) -> Unit,
    onRestoreAlarm: (SunAlarm) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingAlarm by remember { mutableStateOf<SunAlarm?>(null) }
    var sheetType by remember { mutableStateOf(SunEventType.SUNRISE) }
    var showSheet by remember { mutableStateOf(false) }

    val openSheet: (SunAlarm?, SunEventType) -> Unit = { alarm, type ->
        editingAlarm = alarm
        sheetType = type
        showSheet = true
    }
    val hasSunTimes = state.sunriseTime != null && state.sunsetTime != null
    val readyToRender = !state.isLoading && hasSunTimes

    Scaffold(
        topBar = {
            if (readyToRender) {
                SkyTopBar(
                    sunrise = state.sunriseTime,
                    sunset = state.sunsetTime,
                    moonRise = state.moonRiseTime,
                    moonSet = state.moonSetTime,
                    moonMaxAltDeg = state.moonMaxAltDeg,
                    moonIllumination01 = state.moonIllumination01,
                    moonIsWaxing = state.moonIsWaxing,
                    coordinateUsed = state.coordinateUsed,
                    sunTimesResolved = readyToRender
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { openSheet(null, SunEventType.SUNRISE) }) {
                Icon(Icons.Default.Add, contentDescription = "Add alarm")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (!readyToRender) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading…")
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    stickyHeader {
                        AlarmSectionHeader(title = "Sunrise", timeText = state.sunriseTimeText)
                    }
                    items(state.sunriseAlarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onToggle = { enabled -> onToggleAlarmEnabled(alarm.id, enabled) },
                            onEdit = { openSheet(alarm, alarm.type) },
                            onDelete = {
                                onDeleteAlarm(alarm.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Sunrise alarm deleted",
                                        actionLabel = "Undo"
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onRestoreAlarm(alarm)
                                    }
                                }
                            },
                            onDuplicate = { onDuplicateAlarm(alarm.id) }
                        )
                    }
                    if (state.sunriseAlarms.isEmpty()) {
                        item {
                            Text(
                                text = "No alarms yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    item {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            thickness = 1.dp
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    stickyHeader {
                        AlarmSectionHeader(title = "Sunset", timeText = state.sunsetTimeText)
                    }
                    items(state.sunsetAlarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onToggle = { enabled -> onToggleAlarmEnabled(alarm.id, enabled) },
                            onEdit = { openSheet(alarm, alarm.type) },
                            onDelete = {
                                onDeleteAlarm(alarm.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Sunset alarm deleted",
                                        actionLabel = "Undo"
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onRestoreAlarm(alarm)
                                    }
                                }
                            },
                            onDuplicate = { onDuplicateAlarm(alarm.id) }
                        )
                    }
                    if (state.sunsetAlarms.isEmpty()) {
                        item {
                            Text(
                                text = "No alarms yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        AlarmEditorSheet(
            initialAlarm = editingAlarm,
            defaultType = sheetType,
            onDismiss = { showSheet = false },
            onSave = { alarm ->
                if (editingAlarm == null) {
                    onAddAlarm(alarm.type, alarm.offsetMinutes, alarm.label, alarm.enabled)
                } else {
                    onUpdateAlarm(alarm)
                }
                showSheet = false
            }
        )
    }
}

@Composable
private fun AlarmSectionHeader(
    title: String,
    timeText: String?,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            timeText?.let {
                Text(text = it, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: SunAlarm,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(
                text = formatOffset(alarm.offsetMinutes),
                fontWeight = FontWeight.Bold
            )
            if (alarm.label.isNotBlank()) {
                Text(
                    text = alarm.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate alarm")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete alarm")
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditorSheet(
    initialAlarm: SunAlarm?,
    defaultType: SunEventType,
    onDismiss: () -> Unit,
    onSave: (SunAlarm) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by rememberSaveable { mutableStateOf(initialAlarm?.type ?: defaultType) }
    var isAfter by rememberSaveable { mutableStateOf((initialAlarm?.offsetMinutes ?: 0) >= 0) }
    var hours by rememberSaveable { mutableStateOf(abs(initialAlarm?.offsetMinutes ?: 0) / 60) }
    var minutes by rememberSaveable { mutableStateOf(abs(initialAlarm?.offsetMinutes ?: 0) % 60) }
    var label by rememberSaveable { mutableStateOf(initialAlarm?.label ?: "") }
    var enabled by rememberSaveable { mutableStateOf(initialAlarm?.enabled ?: true) }

    LaunchedEffect(initialAlarm?.id, defaultType) {
        type = initialAlarm?.type ?: defaultType
        val offset = initialAlarm?.offsetMinutes ?: 0
        isAfter = offset >= 0
        hours = abs(offset) / 60
        minutes = abs(offset) % 60
        label = initialAlarm?.label ?: ""
        enabled = initialAlarm?.enabled ?: true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = if (initialAlarm == null) "Add alarm" else "Edit alarm", fontWeight = FontWeight.Bold)
            Text(text = "Event")
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = type == SunEventType.SUNRISE,
                    onClick = { type = SunEventType.SUNRISE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Sunrise")
                }
                SegmentedButton(
                    selected = type == SunEventType.SUNSET,
                    onClick = { type = SunEventType.SUNSET },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Sunset")
                }
            }
            Text(text = "Timing")
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = !isAfter,
                    onClick = { isAfter = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Before")
                }
                SegmentedButton(
                    selected = isAfter,
                    onClick = { isAfter = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("After")
                }
            }
            OffsetPicker(
                hours = hours,
                minutes = minutes,
                onHoursChanged = { hours = it },
                onMinutesChanged = { minutes = it }
            )
            TextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enabled")
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val totalMinutes = hours * 60 + minutes
                        val offset = if (isAfter) totalMinutes else -totalMinutes
                        val updated = initialAlarm?.copy(
                            type = type,
                            offsetMinutes = offset,
                            label = label,
                            enabled = enabled
                        ) ?: SunAlarm(
                            type = type,
                            offsetMinutes = offset,
                            label = label,
                            enabled = enabled
                        )
                        onSave(updated)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OffsetPicker(
    hours: Int,
    minutes: Int,
    onHoursChanged: (Int) -> Unit,
    onMinutesChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NumberPickerColumn(
            label = "Hours",
            value = hours,
            range = 0..23,
            onValueChange = onHoursChanged
        )
        NumberPickerColumn(
            label = "Minutes",
            value = minutes,
            range = 0..59,
            onValueChange = onMinutesChanged
        )
    }
}

@Composable
private fun NumberPickerColumn(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    val pickerTextColor = MaterialTheme.colorScheme.onSurface
    val pickerDividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontWeight = FontWeight.Bold)
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = range.first
                    maxValue = range.last
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setOnValueChangedListener { _, _, newVal -> onValueChange(newVal) }
                    styleNumberPicker(this, pickerTextColor, pickerDividerColor)
                }
            },
            update = {
                if (it.value != value) {
                    it.value = value.coerceIn(range)
                }
                styleNumberPicker(it, pickerTextColor, pickerDividerColor)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkyTopBar(
    sunrise: ZonedDateTime?,
    sunset: ZonedDateTime?,
    moonRise: ZonedDateTime?,
    moonSet: ZonedDateTime?,
    moonMaxAltDeg: Double,
    moonIllumination01: Double,
    moonIsWaxing: Boolean,
    coordinateUsed: Coordinate?,
    sunTimesResolved: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        SkyAppBarBackground(
            sunrise = sunrise,
            sunset = sunset,
            moonRise = moonRise,
            moonSet = moonSet,
            moonMaxAltDeg = moonMaxAltDeg,
            moonIllumination01 = moonIllumination01,
            moonIsWaxing = moonIsWaxing,
            coordinateUsed = coordinateUsed,
            sunTimesResolved = sunTimesResolved,
            modifier = Modifier
                .matchParentSize()
                .testTag("sky_appbar_background")
        )
        TopAppBar(
            title = { Text("Suntime Alerts") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )
    }
}

@Composable
private fun SkyAppBarBackground(
    sunrise: ZonedDateTime?,
    sunset: ZonedDateTime?,
    moonRise: ZonedDateTime?,
    moonSet: ZonedDateTime?,
    moonMaxAltDeg: Double,
    moonIllumination01: Double,
    moonIsWaxing: Boolean,
    coordinateUsed: Coordinate?,
    sunTimesResolved: Boolean,
    modifier: Modifier = Modifier
) {
    val zone = sunrise?.zone ?: sunset?.zone ?: ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    val hasSunTimes = sunTimesResolved && sunrise != null && sunset != null
    val moonWindowComplete = coordinateUsed != null && moonRise != null && moonSet != null && moonRise.isBefore(moonSet)
    val moonIsAboveHorizon = moonWindowComplete && !now.isBefore(moonRise) && !now.isAfter(moonSet)
    val moonImage: ImageBitmap = ImageBitmap.imageResource(id = R.drawable.moon_full)
    val placeholderTop = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
    val placeholderBottom = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp)
    Canvas(modifier = modifier.moonVisible(moonIsAboveHorizon)) {
        val dayLengthMinutes = if (sunrise != null && sunset != null) {
            Duration.between(sunrise, sunset).toMinutes()
        } else {
            12L * 60
        }
        val arcScale = SunArcPositionCalculator.computeArcScale(dayLengthMinutes)
        val horizonY = size.height * 0.75f
        val sunArcHeight = size.height * 0.45f * arcScale.toFloat()
        val moonArcHeight = calculateMoonArcHeight(
            moonMaxAltDeg = moonMaxAltDeg,
            horizonY = horizonY,
            height = size.height
        )
        val t = SunArcPositionCalculator.computeSunT(now, sunrise, sunset)
        val sunPosition: SunXY = SunArcPositionCalculator.computeSunXY(
            t = t,
            width = size.width,
            horizonY = horizonY,
            arcHeight = sunArcHeight,
            horizontalPadding = size.width * 0.1f
        )
        val moonPosition: MoonXY = if (moonWindowComplete) {
            MoonArcPositionCalculator.computeMoonXY(
                now = now,
                rise = moonRise,
                set = moonSet,
                width = size.width,
                horizonY = horizonY,
                arcHeight = moonArcHeight,
                horizontalPadding = size.width * 0.1f
            )
        } else {
            MoonArcPositionCalculator.computeMoonXY(
                now = now,
                rise = null,
                set = null,
                width = size.width,
                horizonY = horizonY,
                arcHeight = moonArcHeight,
                horizontalPadding = size.width * 0.1f
            )
        }
        val isDay = sunPosition.isDay && hasSunTimes
        val gradient = when {
            isDay -> {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF64B5F6),
                        Color(0xFFBBDEFB)
                    )
                )
            }

            hasSunTimes -> {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0D1B2A),
                        Color(0xFF001219)
                    )
                )
            }

            else -> {
                Brush.verticalGradient(
                    listOf(
                        placeholderTop,
                        placeholderBottom
                    )
                )
            }
        }
        drawRect(brush = gradient, size = size)

        if (hasSunTimes && !isDay) {
            drawStars(horizonY, now.toLocalDate().toEpochDay())
        }

        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(0f, horizonY),
            end = Offset(size.width, horizonY),
            strokeWidth = 2f
        )

        val moonShouldDraw = moonWindowComplete && moonPosition.isUp
        if (moonShouldDraw) {
            val moonBaseDiameter = size.minDimension * 0.10f
            val minMoonSize = 20.dp.toPx()
            val maxMoonSize = 36.dp.toPx()
            val moonDiameter = moonBaseDiameter.coerceIn(minMoonSize, maxMoonSize)
            val moonRadius = moonDiameter / 2f
            val topLeft = Offset(moonPosition.x - moonRadius, moonPosition.y - moonRadius)
            val moonAlpha = if (isDay) 0.65f else 0.95f
            drawImage(
                image = moonImage,
                dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                dstSize = IntSize(moonDiameter.roundToInt(), moonDiameter.roundToInt()),
                alpha = moonAlpha
            )

            val darkness = (1f - moonIllumination01.toFloat().coerceIn(0f, 1f)).coerceIn(0f, 1f)
            if (darkness > 0f) {
                val circlePath = Path().apply {
                    addOval(Rect(topLeft, Size(moonDiameter, moonDiameter)))
                }
                val direction = if (moonIsWaxing) 1f else -1f
                val phaseShift = (0.5f - moonIllumination01.toFloat()).coerceIn(-0.5f, 0.5f)
                val gradientStart = Offset(
                    x = moonPosition.x - direction * moonRadius * (1f + phaseShift),
                    y = moonPosition.y - moonRadius
                )
                val gradientEnd = Offset(
                    x = moonPosition.x + direction * moonRadius * (1f - phaseShift),
                    y = moonPosition.y + moonRadius
                )
                val overlayAlpha = 0.85f * (0.3f + 0.7f * darkness)
                val gradient = Brush.linearGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = overlayAlpha),
                        Color.Black.copy(alpha = overlayAlpha * 0.35f),
                        Color.Transparent
                    ),
                    start = gradientStart,
                    end = gradientEnd,
                    tileMode = TileMode.Clamp
                )
                clipPath(circlePath) {
                    drawRect(
                        brush = gradient,
                        topLeft = Offset(moonPosition.x - moonRadius, moonPosition.y - moonRadius),
                        size = Size(moonDiameter, moonDiameter),
                        alpha = moonAlpha
                    )
                }
            }
        }

        if (isDay) {
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = size.minDimension * 0.06f,
                center = Offset(sunPosition.x, sunPosition.y)
            )
        }
    }
}

private fun calculateMoonArcHeight(
    moonMaxAltDeg: Double,
    horizonY: Float,
    height: Float
): Float {
    val topPadding = height * 0.08f
    val maxArcSpan = (horizonY - topPadding).coerceAtLeast(height * 0.2f)
    val altitudeRad = Math.toRadians(moonMaxAltDeg.coerceIn(0.0, 90.0))
    val altitudeFactor = sin(altitudeRad).toFloat().coerceIn(0.25f, 1.1f)
    val desiredArcHeight = maxArcSpan * altitudeFactor
    val minArcHeight = height * 0.2f
    val maxArcHeight = maxArcSpan * 1.05f
    return desiredArcHeight.coerceIn(minArcHeight, maxArcHeight)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStars(
    horizonY: Float,
    seed: Long
) {
    val random = Random(seed)
    repeat(60) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * (horizonY * 0.9f)
        val radius = (random.nextDouble(1.0, 3.0)).toFloat()
        drawCircle(
            color = Color.White.copy(alpha = random.nextFloat().coerceIn(0.3f, 0.8f)),
            radius = radius,
            center = Offset(x, y)
        )
    }
}

private fun styleNumberPicker(
    numberPicker: NumberPicker,
    textColor: Color,
    dividerColor: Color
) {
    val textColorInt = textColor.toArgb()
    for (index in 0 until numberPicker.childCount) {
        val child = numberPicker.getChildAt(index)
        if (child is EditText) {
            child.setTextColor(textColorInt)
        }
    }
    try {
        val selectorWheelPaintField = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
        selectorWheelPaintField.isAccessible = true
        val paint = selectorWheelPaintField.get(numberPicker) as Paint
        paint.color = textColorInt
    } catch (_: Exception) {
        // Best-effort styling for OEM variations.
    }
    try {
        val selectionDividerField = NumberPicker::class.java.getDeclaredField("mSelectionDivider")
        selectionDividerField.isAccessible = true
        selectionDividerField.set(numberPicker, ColorDrawable(dividerColor.toArgb()))
    } catch (_: Exception) {
        // Ignore if the field is not available.
    }
    numberPicker.invalidate()
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmLists(
    state: HomeViewModel.State,
    onToggleAlarmEnabled: (String, Boolean) -> Unit,
    onEditAlarm: (SunAlarm?, SunEventType) -> Unit,
    onDeleteAlarm: (String) -> Unit,
    onRestoreAlarm: (SunAlarm) -> Unit,
    onDuplicateAlarm: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        stickyHeader {
            AlarmSectionHeader(title = "Sunrise", timeText = state.sunriseTimeText)
        }
        items(state.sunriseAlarms, key = { it.id }) { alarm ->
            AlarmRow(
                alarm = alarm,
                onToggle = { enabled -> onToggleAlarmEnabled(alarm.id, enabled) },
                onEdit = { onEditAlarm(alarm, alarm.type) },
                onDelete = {
                    onDeleteAlarm(alarm.id)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Sunrise alarm deleted",
                            actionLabel = "Undo"
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onRestoreAlarm(alarm)
                        }
                    }
                },
                onDuplicate = { onDuplicateAlarm(alarm.id) }
            )
        }
        if (state.sunriseAlarms.isEmpty()) {
            item {
                Text(
                    text = "No alarms yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                thickness = 1.dp
            )
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
        stickyHeader {
            AlarmSectionHeader(title = "Sunset", timeText = state.sunsetTimeText)
        }
        items(state.sunsetAlarms, key = { it.id }) { alarm ->
            AlarmRow(
                alarm = alarm,
                onToggle = { enabled -> onToggleAlarmEnabled(alarm.id, enabled) },
                onEdit = { onEditAlarm(alarm, alarm.type) },
                onDelete = {
                    onDeleteAlarm(alarm.id)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Sunset alarm deleted",
                            actionLabel = "Undo"
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onRestoreAlarm(alarm)
                        }
                    }
                },
                onDuplicate = { onDuplicateAlarm(alarm.id) }
            )
        }
        if (state.sunsetAlarms.isEmpty()) {
            item {
                Text(
                    text = "No alarms yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Loading…",
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
