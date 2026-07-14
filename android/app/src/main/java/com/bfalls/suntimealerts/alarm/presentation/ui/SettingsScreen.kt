package com.bfalls.suntimealerts.alarm.presentation.ui

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.bfalls.suntimealerts.alarm.domain.model.AppThemeMode
import com.bfalls.suntimealerts.alarm.domain.model.SkyBodySize
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.SettingsViewModel
import com.bfalls.suntimealerts.alarm.services.AlarmRepairAction
import com.bfalls.suntimealerts.utils.hasLocationPermission
import com.bfalls.suntimealerts.utils.hasNotificationPermission
import kotlinx.coroutines.launch

private const val FEEDBACK_EMAIL_ADDRESS = "suntimealerts@gmail.com"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLocationUpdated: () -> Unit,
    onSkyBodySizeUpdated: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val alarmManager = remember { context.getSystemService<AlarmManager>() }
    val hasLocationPermission = hasLocationPermission(context)
    val notificationsPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasNotificationPermission(context)
    } else {
        true
    }
    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }
    var feedbackMessage by rememberSaveable { mutableStateOf("") }
    val canScheduleExactAlarms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() == true
    } else {
        true
    }

    LaunchedEffect(hasLocationPermission) {
        viewModel.updateLocationPermission(hasLocationPermission)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshReadiness()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearError()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Feedback", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Send feedback by email while this alpha is being tested by friends and family."
                        )
                        Button(
                            onClick = {
                                feedbackMessage = ""
                                showFeedbackDialog = true
                            }
                        ) {
                            Text("Send feedback")
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Location", fontWeight = FontWeight.SemiBold)
                        LocationPickerPane(
                            state = state.locationState.copy(
                                locationPermissionMissing = !hasLocationPermission
                            ),
                            onLocationModeChanged = viewModel::updateLocationMode,
                            onRequestLocationPermission = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            onOpenPermissionSettings = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            onCityQueryChanged = viewModel::updateCityQuery,
                            onCitySelected = viewModel::selectCity
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    viewModel.saveLocation {
                                        onLocationUpdated()
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Location updated")
                                        }
                                    }
                                },
                                enabled = viewModel.canSaveLocation() && !state.isSavingLocation
                            ) {
                                if (state.isSavingLocation) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(end = 8.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                Text("Save location")
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Permissions", fontWeight = FontWeight.SemiBold)
                        PermissionStatusRow(
                            title = "Location",
                            status = if (hasLocationPermission) "Allowed" else "Not allowed",
                            actionLabel = "Open app settings",
                            onActionClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            showAction = !hasLocationPermission
                        )
                        PermissionStatusRow(
                            title = "Notifications",
                            status = when {
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "Not required on this Android version"
                                notificationsPermissionGranted -> "Allowed"
                                else -> "Not allowed"
                            },
                            actionLabel = "Open app settings",
                            onActionClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            showAction = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsPermissionGranted
                        )
                        PermissionStatusRow(
                            title = "Alarms & reminders",
                            status = when {
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "Not required on this Android version"
                                canScheduleExactAlarms -> "Allowed"
                                else -> "Not allowed"
                            },
                            actionLabel = "Open Alarms & reminders",
                            onActionClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                }
                            },
                            showAction = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Theme", fontWeight = FontWeight.SemiBold)
                        val themeOptions = listOf(
                            AppThemeMode.SYSTEM to "System",
                            AppThemeMode.LIGHT to "Light",
                            AppThemeMode.DARK to "Dark"
                        )
                        themeOptions.forEach { (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        role = Role.RadioButton,
                                        onClick = { viewModel.updateAppThemeMode(mode) }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = state.appThemeMode == mode,
                                    onClick = { viewModel.updateAppThemeMode(mode) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Banner display size", fontWeight = FontWeight.SemiBold)
                        val options = listOf(
                            SkyBodySize.SMALL to "Small",
                            SkyBodySize.MEDIUM to "Medium",
                            SkyBodySize.LARGE to "Large"
                        )
                        options.forEach { (size, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        role = Role.RadioButton,
                                        onClick = {
                                            viewModel.updateSkyBodySize(size) {
                                                onSkyBodySizeUpdated()
                                            }
                                        }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = state.skyBodySize == size,
                                    onClick = {
                                        viewModel.updateSkyBodySize(size) {
                                            onSkyBodySizeUpdated()
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                }

                state.alarmReadiness?.let { readiness ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (readiness.canDeliverReliableAlerts) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Alerts readiness", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (readiness.canDeliverReliableAlerts) {
                                    "Alerts are ready right now."
                                } else {
                                    "Alerts are degraded right now. Repair the missing capabilities below."
                                }
                            )
                            readiness.repairActions.forEach { action ->
                                when (action) {
                                    AlarmRepairAction.REQUEST_LOCATION_PERMISSION -> Button(onClick = {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }) { Text("Open location settings") }
                                    AlarmRepairAction.SELECT_LOCATION -> Button(onClick = {}) {
                                        Text("Review location above")
                                    }
                                    AlarmRepairAction.REQUEST_NOTIFICATION_PERMISSION,
                                    AlarmRepairAction.OPEN_NOTIFICATION_SETTINGS -> Button(onClick = {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        context.startActivity(intent)
                                    }) { Text("Open notification settings") }
                                    AlarmRepairAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS -> Button(onClick = {
                                        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            putExtra(Settings.EXTRA_CHANNEL_ID, readiness.blockedNotificationChannelId)
                                        }
                                        context.startActivity(intent)
                                    }) { Text("Open channel settings") }
                                    AlarmRepairAction.REQUEST_EXACT_ALARM_PERMISSION -> Button(onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                        }
                                    }) { Text("Open Alarms & reminders") }
                                    AlarmRepairAction.OPEN_FULL_SCREEN_INTENT_SETTINGS -> Button(onClick = {
                                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                        } else {
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                        }
                                        context.startActivity(intent)
                                    }) { Text("Open full-screen alarm settings") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("Send feedback") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This opens your email app with a draft to $FEEDBACK_EMAIL_ADDRESS."
                    )
                    OutlinedTextField(
                        value = feedbackMessage,
                        onValueChange = { feedbackMessage = it },
                        label = { Text("Feedback") },
                        placeholder = { Text("What happened? What would you change?") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val body = buildFeedbackBody(
                            message = feedbackMessage.trim(),
                            context = context
                        )
                        val intent = Intent(
                            Intent.ACTION_SENDTO,
                            Uri.parse("mailto:$FEEDBACK_EMAIL_ADDRESS")
                        ).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "Suntime Alerts alpha feedback")
                            putExtra(Intent.EXTRA_TEXT, body)
                        }
                        val canHandle = intent.resolveActivity(context.packageManager) != null
                        if (canHandle) {
                            try {
                                context.startActivity(intent)
                                showFeedbackDialog = false
                            } catch (_: ActivityNotFoundException) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("No email app is available on this device.")
                                }
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("No email app is available on this device.")
                            }
                        }
                    }
                ) {
                    Text("Open email")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    status: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    showAction: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Medium)
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showAction) {
            Button(onClick = onActionClick) {
                Text(actionLabel)
            }
        }
    }
}

private fun buildFeedbackBody(
    message: String,
    context: android.content.Context
): String {
    val packageInfo = context.packageManager.getPackageInfoCompat(context.packageName)
    val versionName = packageInfo?.versionName ?: "unknown"
    val versionCode = packageInfo?.longVersionCode?.toString() ?: "unknown"
    val messageSection = if (message.isBlank()) {
        "(Please describe the issue or suggestion here.)"
    } else {
        message
    }
    return """
$messageSection

---
App version: $versionName ($versionCode)
Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
Device: ${Build.MANUFACTURER} ${Build.MODEL}
    """.trimIndent()
}

private fun PackageManager.getPackageInfoCompat(packageName: String) =
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, 0)
        }
    }.getOrNull()
