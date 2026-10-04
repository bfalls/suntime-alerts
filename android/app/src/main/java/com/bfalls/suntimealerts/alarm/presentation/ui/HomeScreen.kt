package com.bfalls.suntimealerts.alarm.presentation.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.lazy.stickyHeader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import android.text.format.DateFormat
import com.bfalls.suntimealerts.R
import com.bfalls.suntimealerts.alarm.domain.model.Coordinate
import com.bfalls.suntimealerts.alarm.domain.model.SkyBodySize
import com.bfalls.suntimealerts.alarm.domain.model.SkyFacingMode
import com.bfalls.suntimealerts.alarm.domain.model.SunAlarm
import com.bfalls.suntimealerts.alarm.domain.model.SunEventType
import com.bfalls.suntimealerts.alarm.domain.model.ALL_DAYS_MASK
import com.bfalls.suntimealerts.alarm.domain.model.includesDay
import com.bfalls.suntimealerts.alarm.domain.model.toBitMask
import com.bfalls.suntimealerts.alarm.services.AlarmRepairAction
import com.bfalls.suntimealerts.alarm.domain.service.MoonArcPositionCalculator
import com.bfalls.suntimealerts.alarm.domain.service.MoonPhaseMask
import com.bfalls.suntimealerts.alarm.domain.service.MoonXY
import com.bfalls.suntimealerts.alarm.domain.service.SkyBackgroundModel
import com.bfalls.suntimealerts.alarm.domain.service.SkyInfoMetrics
import com.bfalls.suntimealerts.alarm.domain.service.SunArcPositionCalculator
import com.bfalls.suntimealerts.alarm.domain.service.SunXY
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@VisibleForTesting
val MoonVisibleKey = SemanticsPropertyKey<Boolean>("MoonVisible")

private const val MOON_BRIGHTNESS_SCALE = 1.2f
private const val SKY_HORIZON_HAZE_SCALE = 0.75f
private const val SKY_HILL_SHADOW_AMOUNT = 0.25f
private const val SKY_GROUND_GRADIENT_AMOUNT = 0.75f
private const val SKY_CLOUD_PATCH_AMOUNT = 0.18f

private fun Modifier.moonVisible(isVisible: Boolean): Modifier = semantics {
    this[MoonVisibleKey] = isVisible
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenNotificationChannelSettings: (String?) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenAdvancedInfo: ((SkyInfoMetrics) -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refresh()
            launch {
                while (true) {
                    delay(60_000L - System.currentTimeMillis() % 60_000L)
                    viewModel.updateClock()
                }
            }
            while (true) {
                delay(15 * 60 * 1000L)
                viewModel.refreshSunMoonPositions()
            }
        }
    }

    HomeScreenContent(
        state = state,
        onAddAlarm = viewModel::addAlarm,
        onUpdateAlarm = viewModel::updateAlarm,
        onToggleAlarmEnabled = viewModel::toggleAlarmEnabled,
        onDeleteAlarm = viewModel::deleteAlarm,
        onDuplicateAlarm = viewModel::duplicateAlarm,
        onRestoreAlarm = viewModel::restoreAlarm,
        onOpenSettings = onOpenSettings,
        onOpenNotificationSettings = onOpenNotificationSettings,
        onOpenNotificationChannelSettings = onOpenNotificationChannelSettings,
        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
        onOpenFullScreenIntentSettings = onOpenFullScreenIntentSettings,
        onOpenAdvancedInfo = onOpenAdvancedInfo
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreenContent(
    state: HomeViewModel.State,
    onAddAlarm: (SunAlarm) -> Unit,
    onUpdateAlarm: (SunAlarm) -> Unit,
    onToggleAlarmEnabled: (String, Boolean) -> Unit,
    onDeleteAlarm: (String) -> Unit,
    onDuplicateAlarm: (SunAlarm) -> Unit,
    onRestoreAlarm: (SunAlarm, Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenNotificationChannelSettings: (String?) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenAdvancedInfo: ((SkyInfoMetrics) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingAlarm by remember { mutableStateOf<SunAlarm?>(null) }
    var sheetType by remember { mutableStateOf(SunEventType.SUNRISE) }
    var showSheet by remember { mutableStateOf(false) }
    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }

    val openSheet: (SunAlarm?, SunEventType) -> Unit = { alarm, type ->
        editingAlarm = alarm
        sheetType = type
        showSheet = true
    }
    val hasSunTimes = state.sunriseTime != null && state.sunsetTime != null
    val readyToRender = !state.isLoading && (hasSunTimes || state.coordinateUsed != null)
    val typeLabel: (SunAlarm) -> String = { alarm -> if (alarm.type == SunEventType.SUNRISE) "Sunrise" else "Sunset" }
    val handleDelete: (SunAlarm) -> Unit = { alarm ->
        val deleteIndex = (state.sunriseAlarms + state.sunsetAlarms).indexOfFirst { it.id == alarm.id }
        onDeleteAlarm(alarm.id)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "${typeLabel(alarm)} alarm deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onRestoreAlarm(alarm, deleteIndex)
            }
        }
    }
    val handleDuplicate: (SunAlarm) -> Unit = { alarm ->
        onDuplicateAlarm(alarm)
        scope.launch {
            snackbarHostState.showSnackbar("${typeLabel(alarm)} alarm duplicated")
        }
    }

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
                    moonLitDirectionRadians = state.moonLitDirectionRadians,
                    coordinateUsed = state.coordinateUsed,
                    sunTimesResolved = readyToRender,
                    now = state.now,
                    skyBodySize = state.skyBodySize,
                    onOpenSettings = onOpenSettings,
                    skyInfoMetrics = state.skyInfoMetrics,
                    onOpenAdvancedInfo = onOpenAdvancedInfo
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
                    item {
                        AlarmReadinessBanner(
                            state = state,
                            onOpenSettings = onOpenSettings,
                            onOpenNotificationSettings = onOpenNotificationSettings,
                            onOpenNotificationChannelSettings = onOpenNotificationChannelSettings,
                            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                            onOpenFullScreenIntentSettings = onOpenFullScreenIntentSettings
                        )
                    }
                    stickyHeader {
                        AlarmSectionHeader(title = "Sunrise", time = state.sunriseTime)
                    }
                    items(state.sunriseAlarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onToggle = { enabled -> onToggleAlarmEnabled(alarm.id, enabled) },
                            onEdit = { openSheet(alarm, alarm.type) }
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
                        AlarmSectionHeader(title = "Sunset", time = state.sunsetTime)
                    }
                    items(state.sunsetAlarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onToggle = { enabled -> onToggleAlarmEnabled(alarm.id, enabled) },
                            onEdit = { openSheet(alarm, alarm.type) }
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

                ExtendedFloatingActionButton(
                    onClick = { showFeedbackDialog = true },
                    icon = {
                        Icon(
                            Icons.Default.Feedback,
                            contentDescription = "Send feedback"
                        )
                    },
                    text = { Text("Feedback") },
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
    }

    if (showFeedbackDialog) {
        FeedbackDialog(
            onDismiss = { showFeedbackDialog = false },
            onEmailUnavailable = {
                scope.launch {
                    snackbarHostState.showSnackbar("No email app is available on this device.")
                }
            }
        )
    }

    if (showSheet) {
        AlarmEditorSheet(
            initialAlarm = editingAlarm,
            defaultType = sheetType,
            sunriseTime = state.sunriseTime,
            sunsetTime = state.sunsetTime,
            now = state.now,
            onDismiss = { showSheet = false },
            onSave = { alarm ->
                if (editingAlarm == null) {
                    onAddAlarm(alarm)
                } else {
                    onUpdateAlarm(alarm)
                }
                showSheet = false
            },
            onDelete = { alarm ->
                handleDelete(alarm)
                showSheet = false
            },
            onDuplicate = { alarm ->
                handleDuplicate(alarm)
                showSheet = false
            }
        )
    }
}

@Composable
private fun AlarmReadinessBanner(
    state: HomeViewModel.State,
    onOpenSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenNotificationChannelSettings: (String?) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit
) {
    val readiness = state.alarmReadiness ?: return
    val needsRepair =
        !readiness.canDeliverReliableAlerts || !readiness.fullScreenIntentReady
    if (!needsRepair) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Alerts need attention",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = when {
                    !readiness.locationReady ->
                        "Location data is unavailable, so sunrise and sunset alerts cannot be calculated reliably right now."
                    !readiness.bootRescheduleReady ->
                        "Alarm recovery after device restart is degraded until a usable location is saved."
                    !readiness.fullScreenIntentReady ->
                        "Full-screen alarm screens are disabled, so Android will use this app's notification category settings instead of opening over the lock screen."
                    !readiness.exactAlarmReady ->
                        "Exact alarm access is off, so Android will not schedule reliable sunrise and sunset alerts."
                    !readiness.notificationsReady ->
                        "Android is blocking app notifications, so sunrise and sunset alerts cannot fire."
                    else ->
                        "An alarm notification channel is blocked, so some alerts cannot fire."
                },
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (readiness.repairActions.contains(AlarmRepairAction.REQUEST_EXACT_ALARM_PERMISSION)) {
                    Button(onClick = onOpenExactAlarmSettings) {
                        Text("Alarms & reminders")
                    }
                }
                if (readiness.repairActions.contains(AlarmRepairAction.REQUEST_LOCATION_PERMISSION)) {
                    Button(onClick = onOpenSettings) {
                        Text("Location")
                    }
                }
                if (readiness.repairActions.contains(AlarmRepairAction.OPEN_FULL_SCREEN_INTENT_SETTINGS)) {
                    Button(onClick = onOpenFullScreenIntentSettings) {
                        Text("Full-screen alarms")
                    }
                }
                if (readiness.repairActions.contains(AlarmRepairAction.OPEN_NOTIFICATION_SETTINGS)) {
                    Button(onClick = onOpenNotificationSettings) {
                        Text("App settings")
                    }
                }
                if (readiness.repairActions.contains(AlarmRepairAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS)) {
                    Button(onClick = {
                        onOpenNotificationChannelSettings(readiness.blockedNotificationChannelId)
                    }) {
                        Text("Channel settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmSectionHeader(
    title: String,
    time: ZonedDateTime?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeText = time?.let {
        val is24Hour = DateFormat.is24HourFormat(context)
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        val formatter = DateTimeFormatter.ofPattern(pattern)
        it.format(formatter)
    }
    val headerText = timeText?.let { "$title at $it" } ?: title

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Text(
            text = headerText,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun AlarmRow(
    alarm: SunAlarm,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alarmPrimaryText(alarm),
                fontWeight = FontWeight.Bold
            )
            val description = alarmSecondaryText(alarm)
            if (description.isNotBlank()) {
                Text(text = description)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = alarm.enabled,
            onCheckedChange = onToggle
        )
    }
}

private data class AlarmEditorValues(
    val type: SunEventType,
    val isAfter: Boolean,
    val hours: Int,
    val minutes: Int,
    val label: String,
    val enabled: Boolean,
    val recurrenceMask: Int,
    val soundUriValue: String?,
    val vibrate: Boolean
) {
    fun toAlarm(existing: SunAlarm?): SunAlarm {
        val totalMinutes = hours * 60 + minutes
        val offset = if (isAfter) totalMinutes else -totalMinutes
        return existing?.copy(
            type = type,
            offsetMinutes = offset,
            label = label,
            enabled = enabled,
            recurrenceDays = recurrenceMask,
            soundUri = soundUriValue,
            vibrate = vibrate
        ) ?: SunAlarm(
            type = type,
            offsetMinutes = offset,
            label = label,
            enabled = enabled,
            recurrenceDays = recurrenceMask,
            soundUri = soundUriValue,
            vibrate = vibrate
        )
    }
}

private fun initialAlarmValues(alarm: SunAlarm?, defaultType: SunEventType): AlarmEditorValues {
    val offset = alarm?.offsetMinutes ?: 0
    return AlarmEditorValues(
        type = alarm?.type ?: defaultType,
        isAfter = offset >= 0,
        hours = abs(offset) / 60,
        minutes = abs(offset) % 60,
        label = alarm?.label ?: "",
        enabled = alarm?.enabled ?: true,
        recurrenceMask = alarm?.recurrenceDays ?: 0,
        soundUriValue = alarm?.soundUri,
        vibrate = alarm?.vibrate ?: true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditorSheet(
    initialAlarm: SunAlarm?,
    defaultType: SunEventType,
    sunriseTime: ZonedDateTime?,
    sunsetTime: ZonedDateTime?,
    now: ZonedDateTime,
    onDismiss: () -> Unit,
    onSave: (SunAlarm) -> Unit,
    onDelete: (SunAlarm) -> Unit,
    onDuplicate: (SunAlarm) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val initialValues = remember(initialAlarm?.id, defaultType) {
        initialAlarmValues(initialAlarm, defaultType)
    }
    var type by rememberSaveable { mutableStateOf(initialValues.type) }
    var isAfter by rememberSaveable { mutableStateOf(initialValues.isAfter) }
    var hours by rememberSaveable { mutableStateOf(initialValues.hours) }
    var minutes by rememberSaveable { mutableStateOf(initialValues.minutes) }
    var label by rememberSaveable { mutableStateOf(initialValues.label) }
    var enabled by rememberSaveable { mutableStateOf(initialValues.enabled) }
    var recurrenceMask by rememberSaveable { mutableStateOf(initialValues.recurrenceMask) }
    var soundUriValue by rememberSaveable { mutableStateOf(initialValues.soundUriValue) }
    var vibrate by rememberSaveable { mutableStateOf(initialValues.vibrate) }
    val context = LocalContext.current
    var liveNow by remember { mutableStateOf(now) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val currentValues = AlarmEditorValues(
        type = type,
        isAfter = isAfter,
        hours = hours,
        minutes = minutes,
        label = label,
        enabled = enabled,
        recurrenceMask = recurrenceMask,
        soundUriValue = soundUriValue,
        vibrate = vibrate
    )
    val hasEdits = currentValues != initialValues
    val isInputValid = true
    val canSave = (initialAlarm == null || hasEdits) && isInputValid
    val offsetMinutes = (hours * 60 + minutes).let { totalMinutes ->
        if (isAfter) totalMinutes else -totalMinutes
    }
    val timingMessage = if (enabled && recurrenceMask == 0) {
        oneShotTimingMessage(
            type = type,
            offsetMinutes = offsetMinutes,
            sunrise = sunriseTime,
            sunset = sunsetTime,
            now = liveNow
        )
    } else {
        null
    }
    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        soundUriValue = when {
            uri == null -> ""
            uri == Settings.System.DEFAULT_ALARM_ALERT_URI -> null
            else -> uri.toString()
        }
    }

    LaunchedEffect(now) {
        liveNow = now
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60 * 1000L)
            liveNow = ZonedDateTime.now(now.zone)
        }
    }

    LaunchedEffect(initialAlarm?.id, defaultType) {
        val updatedInitial = initialAlarmValues(initialAlarm, defaultType)
        type = updatedInitial.type
        isAfter = updatedInitial.isAfter
        hours = updatedInitial.hours
        minutes = updatedInitial.minutes
        label = updatedInitial.label
        enabled = updatedInitial.enabled
        recurrenceMask = updatedInitial.recurrenceMask
        soundUriValue = updatedInitial.soundUriValue
        vibrate = updatedInitial.vibrate
    }

    val handleCancel = {
        if (hasEdits) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    BackHandler { handleCancel() }

    ModalBottomSheet(
        onDismissRequest = handleCancel,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = if (initialAlarm == null) "Add alarm" else "Edit alarm", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "Event")
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.weight(1f)
                    ) {
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
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "Timing")
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.weight(1f)
                    ) {
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
                    maxLines = 3,
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Repeat")
                    val daySize = 40.dp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(
                            DayOfWeek.SUNDAY,
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY,
                            DayOfWeek.SATURDAY
                        ).forEach { day ->
                            val initial = day.name.first().toString()
                            val selected = recurrenceMask.includesDay(day)
                            val dayBit = setOf(day).toBitMask()
                            val backgroundColor = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                            }
                            val contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                            Box(
                                modifier = Modifier
                                    .size(daySize)
                                    .clip(CircleShape)
                                    .background(backgroundColor)
                                    .clickable {
                                        recurrenceMask = if (selected) {
                                            recurrenceMask and dayBit.inv()
                                        } else {
                                            recurrenceMask or dayBit
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initial,
                                    color = contentColor,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (timingMessage != null) {
                        Text(
                            text = timingMessage,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val currentSound = soundUriValue
                            val existingUri = when {
                                currentSound == null -> Settings.System.DEFAULT_ALARM_ALERT_URI
                                currentSound.isBlank() -> null
                                else -> runCatching { Uri.parse(currentSound) }.getOrNull()
                            }
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
                            }
                            ringtoneLauncher.launch(intent)
                    },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "Sound")
                    val currentSound = soundUriValue
                    val soundLabel = when {
                        currentSound == null -> "Default"
                        currentSound.isBlank() -> "Silent"
                        else -> {
                            val uri = runCatching { Uri.parse(currentSound) }.getOrNull()
                            val title = uri?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) }
                            title ?: "Custom"
                        }
                    }
                    Text(text = soundLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Vibrate")
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enabled")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(onClick = handleCancel) {
                                Text("Cancel")
                            }
                            if (initialAlarm != null) {
                                IconButton(
                                    onClick = {
                                        onDelete(initialAlarm)
                                        onDismiss()
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete alarm")
                                }
                                IconButton(
                                    onClick = {
                                        onDuplicate(currentValues.toAlarm(initialAlarm))
                                        onDismiss()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate alarm")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                onSave(currentValues.toAlarm(initialAlarm))
                                onDismiss()
                            },
                            enabled = canSave
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDismiss()
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            }
        )
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
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WheelPickerColumn(
            label = "Hours",
            value = hours,
            range = 0..23,
            onValueChange = onHoursChanged,
            modifier = Modifier.weight(1f)
        )
        WheelPickerColumn(
            label = "Minutes",
            value = minutes,
            range = 0..59,
            onValueChange = onMinutesChanged,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WheelPickerColumn(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    visibleCount: Int = 5,
    modifier: Modifier = Modifier
) {
    val coercedVisible = visibleCount.coerceAtLeast(3).let { if (it % 2 == 0) it + 1 else it }
    val halfCount = coercedVisible / 2
    val itemHeight = 36.dp
    val values = remember(range) { range.toList() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = values.indexOf(value).coerceAtLeast(0))
    val density = LocalDensity.current

    LaunchedEffect(value, values) {
        val targetIndex = values.indexOf(value).takeIf { it >= 0 } ?: return@LaunchedEffect
        if (targetIndex != listState.firstVisibleItemIndex) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    LaunchedEffect(listState, values) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collectLatest {
                val layoutInfo = listState.layoutInfo
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                val centered = layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    kotlin.math.abs((item.offset + item.size / 2f) - viewportCenter)
                } ?: return@collectLatest
                val centeredValue = values.getOrNull(centered.index) ?: return@collectLatest
                if (centeredValue != value) {
                    onValueChange(centeredValue)
                }
            }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(modifier)
            .semantics { contentDescription = "$label picker, selected $value" }
    ) {
        Text(text = label, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .height(itemHeight * coercedVisible)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = itemHeight * halfCount),
                modifier = Modifier
                    .fillMaxSize()
            ) {
                items(values) { entry ->
                    val isSelected = entry == value
                    val alpha = if (isSelected) 1f else 0.4f
                    Box(
                        modifier = Modifier
                            .height(itemHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = entry.toString(),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
            val dividerStroke = with(density) { 1.dp.toPx() }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 16.dp)
                    .drawBehind {
                        val centerY = size.height / 2f
                        val halfHeightPx = with(density) { itemHeight.toPx() / 2f }
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, centerY - halfHeightPx),
                            end = Offset(size.width, centerY - halfHeightPx),
                            strokeWidth = dividerStroke
                        )
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, centerY + halfHeightPx),
                            end = Offset(size.width, centerY + halfHeightPx),
                            strokeWidth = dividerStroke
                        )
                    }
            )
        }
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
    moonLitDirectionRadians: Float?,
    coordinateUsed: Coordinate?,
    sunTimesResolved: Boolean,
    now: ZonedDateTime,
    skyBodySize: SkyBodySize,
    onOpenSettings: () -> Unit,
    skyInfoMetrics: SkyInfoMetrics?,
    onOpenAdvancedInfo: ((SkyInfoMetrics) -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .testTag("sky_top_bar")
    ) {
        SkyAppBarBackground(
            sunrise = sunrise,
            sunset = sunset,
            moonRise = moonRise,
            moonSet = moonSet,
            moonMaxAltDeg = moonMaxAltDeg,
            moonIllumination01 = moonIllumination01,
            moonIsWaxing = moonIsWaxing,
            moonLitDirectionRadians = moonLitDirectionRadians,
            coordinateUsed = coordinateUsed,
            sunTimesResolved = sunTimesResolved,
            now = now,
            skyBodySize = skyBodySize,
            sunAltitudeDeg = skyInfoMetrics?.sunPosition?.altitudeDeg,
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
            ),
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        )
        skyInfoMetrics?.let { metrics ->
            SkyInfoOverlay(
                metrics = metrics,
                modifier = Modifier.align(Alignment.BottomCenter),
                onOpenAdvancedInfo = onOpenAdvancedInfo
            )
        }
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
    moonLitDirectionRadians: Float?,
    coordinateUsed: Coordinate?,
    sunTimesResolved: Boolean,
    now: ZonedDateTime,
    skyBodySize: SkyBodySize,
    modifier: Modifier = Modifier,
    sunAltitudeDeg: Double? = null
) {
    val skyFacingMode = SkyFacingMode.SOUTH_FACING
    val hasSunTimes = sunTimesResolved && sunrise != null && sunset != null
    val moonWindowComplete = coordinateUsed != null && moonRise != null && moonSet != null && moonRise.isBefore(moonSet)
    val moonIsAboveHorizon = moonWindowComplete && !now.isBefore(moonRise) && !now.isAfter(moonSet)
    val moonImage: ImageBitmap = ImageBitmap.imageResource(id = R.drawable.moon_full)
    val sunImage: ImageBitmap = ImageBitmap.imageResource(id = R.drawable.sun)
    val placeholderTop = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
    val placeholderBottom = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp)
    val bodyScale = when (skyBodySize) {
        SkyBodySize.SMALL -> 1.0f
        SkyBodySize.MEDIUM -> 1.25f
        SkyBodySize.LARGE -> 1.5f
    }
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
            horizontalPadding = size.width * 0.1f,
            skyFacingMode = skyFacingMode
        )
        val moonPosition: MoonXY = if (moonWindowComplete) {
            MoonArcPositionCalculator.computeMoonXY(
                now = now,
                rise = moonRise,
                set = moonSet,
                width = size.width,
                horizonY = horizonY,
                arcHeight = moonArcHeight,
                horizontalPadding = size.width * 0.1f,
                skyFacingMode = skyFacingMode
            )
        } else {
            MoonArcPositionCalculator.computeMoonXY(
                now = now,
                rise = null,
                set = null,
                width = size.width,
                horizonY = horizonY,
                arcHeight = moonArcHeight,
                horizontalPadding = size.width * 0.1f,
                skyFacingMode = skyFacingMode
            )
        }
        val resolvedSunAltitude = sunAltitudeDeg ?: coordinateUsed?.let {
            SunTimesCalculator.sunAltAz(now, it.latitude, it.longitude).altitudeDeg
        }
        val skyBackground = SkyBackgroundModel.compute(resolvedSunAltitude, hasSunTimes)
        val gradientStops = if (hasSunTimes) {
            skyBackground.gradientStops.map { stop -> stop.position to Color(stop.color) }.toTypedArray()
        } else {
            arrayOf(0f to placeholderTop, 1f to placeholderBottom)
        }
        drawRect(
            brush = Brush.verticalGradient(*gradientStops),
            size = size
        )

        skyBackground.sunGlow?.let { glow ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(glow.color).copy(alpha = glow.alpha),
                        Color(glow.color).copy(alpha = glow.alpha * 0.32f),
                        Color.Transparent
                    ),
                    center = Offset(sunPosition.x, sunPosition.y),
                    radius = size.minDimension * glow.radiusScale
                ),
                radius = size.minDimension * glow.radiusScale,
                center = Offset(sunPosition.x, sunPosition.y)
            )
        }

        if (hasSunTimes && skyBackground.starAlpha > 0f) {
            drawStars(
                horizonY = horizonY,
                seed = now.toLocalDate().toEpochDay(),
                alphaMultiplier = skyBackground.starAlpha
            )
        }
        drawCloudPatches(horizonY)

        if (sunPosition.isDay && hasSunTimes) {
            val sunBaseDiameter = size.minDimension * 0.12f * bodyScale
            val minSunSize = 24.dp.toPx() * bodyScale
            val maxSunSize = 40.dp.toPx() * bodyScale
            val sunDiameter = sunBaseDiameter.coerceIn(minSunSize, maxSunSize)
            val sunRadius = sunDiameter / 2f
            val topLeft = Offset(sunPosition.x - sunRadius, sunPosition.y - sunRadius)
            drawImage(
                image = sunImage,
                dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                dstSize = IntSize(sunDiameter.roundToInt(), sunDiameter.roundToInt())
            )
        }

        val moonShouldDraw = moonWindowComplete && moonPosition.isUp
        if (moonShouldDraw) {
            val moonBaseDiameter = size.minDimension * 0.10f * bodyScale
            val minMoonSize = 20.dp.toPx() * bodyScale
            val maxMoonSize = 36.dp.toPx() * bodyScale
            val moonDiameter = moonBaseDiameter.coerceIn(minMoonSize, maxMoonSize)
            val moonRadius = moonDiameter / 2f
            val topLeft = Offset(moonPosition.x - moonRadius, moonPosition.y - moonRadius)
            val moonAlpha = 0.65f + 0.30f * skyBackground.starAlpha
            drawMoonPhase(
                moonImage = moonImage,
                topLeft = topLeft,
                diameter = moonDiameter,
                alpha = moonAlpha,
                illumination01 = moonIllumination01.toFloat(),
                isWaxing = moonIsWaxing,
                litDirectionRadians = moonLitDirectionRadians
            )
        }

        drawSkyLandscape(horizonY, skyBackground)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMoonPhase(
    moonImage: ImageBitmap,
    topLeft: Offset,
    diameter: Float,
    alpha: Float,
    illumination01: Float,
    isWaxing: Boolean,
    litDirectionRadians: Float?
) {
    val circleRect = Rect(topLeft, Size(diameter, diameter))
    val circlePath = Path().apply { addOval(circleRect) }
    val clippedIllumination = illumination01.coerceIn(0f, 1f)
    val litPath = buildMoonLitPath(
        bounds = circleRect,
        illumination01 = clippedIllumination,
        isWaxing = isWaxing,
        litDirectionRadians = litDirectionRadians
    )
    val moonBrightnessFilter = ColorFilter.colorMatrix(
        ColorMatrix().apply {
            setToScale(
                redScale = MOON_BRIGHTNESS_SCALE,
                greenScale = MOON_BRIGHTNESS_SCALE,
                blueScale = MOON_BRIGHTNESS_SCALE,
                alphaScale = 1f
            )
        }
    )

    clipPath(circlePath) {
        MoonPhaseMask.softEdgeLayers(clippedIllumination).forEach { layer ->
            val softPath = buildMoonLitPath(
                bounds = circleRect,
                illumination01 = layer.illumination01,
                isWaxing = isWaxing,
                litDirectionRadians = litDirectionRadians
            )
            clipPath(softPath) {
                drawImage(
                    image = moonImage,
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    dstSize = IntSize(diameter.roundToInt(), diameter.roundToInt()),
                    alpha = alpha * layer.alphaMultiplier,
                    colorFilter = moonBrightnessFilter
                )
            }
        }
        clipPath(litPath) {
            drawImage(
                image = moonImage,
                dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                dstSize = IntSize(diameter.roundToInt(), diameter.roundToInt()),
                alpha = alpha,
                colorFilter = moonBrightnessFilter
            )
        }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(circleRect, androidx.compose.ui.graphics.Paint())
            drawPath(
                path = litPath,
                color = Color.White.copy(alpha = 0.18f * alpha)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f * alpha),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.10f * alpha)
                    ),
                    startY = topLeft.y,
                    endY = topLeft.y + diameter
                ),
                topLeft = topLeft,
                size = Size(diameter, diameter),
                blendMode = BlendMode.Modulate
            )
            canvas.restore()
        }
    }
}

private fun buildMoonLitPath(
    bounds: Rect,
    illumination01: Float,
    isWaxing: Boolean,
    litDirectionRadians: Float?
): Path {
    val points = MoonPhaseMask.litDiscPolygon(illumination01, isWaxing, litDirectionRadians)
    return Path().apply {
        points.forEachIndexed { index, point ->
            val x = bounds.left + point.x01 * bounds.width
            val y = bounds.top + point.y01 * bounds.height
            if (index == 0) {
                moveTo(x, y)
            } else {
                lineTo(x, y)
            }
        }
        if (points.isNotEmpty()) {
            close()
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSkyLandscape(
    horizonY: Float,
    skyBackground: com.bfalls.suntimealerts.alarm.domain.service.SkyBackgroundSpec
) {
    drawHillLayer(
        horizonY = horizonY,
        profile = SkyBackgroundModel.farHillProfile(),
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(skyBackground.landscape.farHillColor),
                Color(skyBackground.landscape.farHillColor)
            )
        ),
        verticalShift = size.height * 0.018f
    )
    drawHillLayer(
        horizonY = horizonY,
        profile = SkyBackgroundModel.nearHillProfile(),
        brush = buildGroundGradientBrush(
            horizonY = horizonY,
            color = Color(skyBackground.landscape.groundColor)
        ),
        verticalShift = size.height * 0.045f
    )
    drawHillLayer(
        horizonY = horizonY,
        profile = SkyBackgroundModel.nearHillProfile(),
        brush = buildGroundGradientBrush(
            horizonY = horizonY,
            color = Color(skyBackground.landscape.nearHillColor)
        ),
        verticalShift = 0f
    )
    drawHillShadow(horizonY)
    val hazeAlpha = skyBackground.landscape.hazeAlpha * SKY_HORIZON_HAZE_SCALE
    if (hazeAlpha > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(skyBackground.landscape.hazeColor).copy(alpha = hazeAlpha),
                    Color.Transparent
                ),
                startY = horizonY - size.height * 0.08f,
                endY = horizonY + size.height * 0.10f
            ),
            topLeft = Offset(0f, horizonY - size.height * 0.08f),
            size = Size(size.width, size.height * 0.18f)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHillLayer(
    horizonY: Float,
    profile: List<com.bfalls.suntimealerts.alarm.domain.service.SkyHorizonPoint>,
    brush: Brush,
    verticalShift: Float
) {
    if (profile.isEmpty()) return
    val path = Path()
    val points = profile.map { point ->
        Offset(
            x = point.x01 * size.width,
            y = horizonY + point.yOffset01 * size.height + verticalShift
        )
    }
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) {
        path.lineTo(points.first().x, points.first().y)
    } else {
        for (index in 1 until points.lastIndex) {
            val control = points[index]
            val next = points[index + 1]
            val end = Offset(
                x = (control.x + next.x) / 2f,
                y = (control.y + next.y) / 2f
            )
            path.quadraticTo(control.x, control.y, end.x, end.y)
        }
        val penultimate = points[points.lastIndex - 1]
        val last = points.last()
        path.quadraticTo(penultimate.x, penultimate.y, last.x, last.y)
    }
    path.lineTo(size.width * 1.05f, size.height)
    path.lineTo(size.width * -0.05f, size.height)
    path.close()
    drawPath(path = path, brush = brush)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.buildGroundGradientBrush(
    horizonY: Float,
    color: Color
): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            color.scaleRgb(1f + 0.18f * SKY_GROUND_GRADIENT_AMOUNT),
            color.scaleRgb(1f - 0.28f * SKY_GROUND_GRADIENT_AMOUNT)
        ),
        startY = horizonY,
        endY = size.height
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHillShadow(horizonY: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.13f * SKY_HILL_SHADOW_AMOUNT),
                Color.Black.copy(alpha = 0.06f * SKY_HILL_SHADOW_AMOUNT),
                Color.Transparent
            ),
            startY = horizonY,
            endY = size.height
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(size.width, size.height - horizonY)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloudPatches(horizonY: Float) {
    if (SKY_CLOUD_PATCH_AMOUNT <= 0f) return
    val random = Random(20261003L)
    repeat((18 * SKY_CLOUD_PATCH_AMOUNT).roundToInt().coerceAtLeast(1)) {
        val patchWidth = size.width * (0.14f + random.nextFloat() * 0.18f)
        val patchHeight = horizonY * (0.035f + random.nextFloat() * 0.055f)
        val x = random.nextFloat() * (size.width + patchWidth) - patchWidth * 0.5f
        val y = horizonY * (0.10f + random.nextFloat() * 0.58f)
        val alpha = (0.045f + random.nextFloat() * 0.075f) * SKY_CLOUD_PATCH_AMOUNT
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = alpha),
                    Color(0xFFF5FAFF).copy(alpha = alpha * 0.34f),
                    Color.Transparent
                ),
                center = Offset(x + patchWidth * 0.5f, y + patchHeight * 0.5f),
                radius = patchWidth * 0.55f
            ),
            topLeft = Offset(x, y),
            size = Size(patchWidth, patchHeight)
        )
    }
}

private fun Color.scaleRgb(scale: Float): Color {
    return Color(
        red = (red * scale).coerceIn(0f, 1f),
        green = (green * scale).coerceIn(0f, 1f),
        blue = (blue * scale).coerceIn(0f, 1f),
        alpha = alpha
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStars(
    horizonY: Float,
    seed: Long,
    alphaMultiplier: Float
) {
    val random = Random(seed)
    repeat(60) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * (horizonY * 0.9f)
        val radius = (random.nextDouble(1.0, 3.0)).toFloat()
        drawCircle(
            color = Color.White.copy(alpha = random.nextFloat().coerceIn(0.3f, 0.8f) * alphaMultiplier),
            radius = radius,
            center = Offset(x, y)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmLists(
    state: HomeViewModel.State,
    onToggleAlarmEnabled: (String, Boolean) -> Unit,
    onEditAlarm: (SunAlarm?, SunEventType) -> Unit,
    onDeleteAlarm: (String) -> Unit,
    onRestoreAlarm: (SunAlarm) -> Unit,
    onDuplicateAlarm: (SunAlarm) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        stickyHeader {
            AlarmSectionHeader(title = "Sunrise", time = state.sunriseTime)
        }
        items(state.sunriseAlarms, key = { it.id }) { alarm ->
            AlarmRow(
                alarm = alarm,
                onToggle = { enabled -> onToggleAlarmEnabled(alarm.id, enabled) },
                onEdit = { onEditAlarm(alarm, alarm.type) }
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
            AlarmSectionHeader(title = "Sunset", time = state.sunsetTime)
        }
        items(state.sunsetAlarms, key = { it.id }) { alarm ->
            AlarmRow(
                alarm = alarm,
                onToggle = { enabled -> onToggleAlarmEnabled(alarm.id, enabled) },
                onEdit = { onEditAlarm(alarm, alarm.type) }
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
