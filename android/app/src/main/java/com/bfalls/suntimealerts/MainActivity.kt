package com.bfalls.suntimealerts

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bfalls.suntimealerts.alarm.data.LocationService
import com.bfalls.suntimealerts.alarm.data.SettingsStore
import com.bfalls.suntimealerts.alarm.data.SunScheduleService
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.presentation.ui.HomeScreen
import com.bfalls.suntimealerts.alarm.presentation.ui.OnboardingScreen
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.HomeViewModel
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingStep
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingViewModel
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingViewModelFactory
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.PermissionRequestOrigin
import com.bfalls.suntimealerts.alarm.services.NotificationScheduler
import com.bfalls.suntimealerts.cities.data.CityRepository
import com.bfalls.suntimealerts.cities.presentation.CityImportViewModel
import com.bfalls.suntimealerts.cities.presentation.CityImportViewModelFactory
import com.bfalls.suntimealerts.ui.theme.SuntimeAlertsTheme
import com.bfalls.suntimealerts.utils.ExactAlarmPermissionTracker
import com.bfalls.suntimealerts.utils.hasLocationPermission
import com.bfalls.suntimealerts.utils.hasNotificationPermission
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsStore = remember { SettingsStore(applicationContext) }
            val locationService = remember { LocationService(application) }
            val notificationScheduler = remember { NotificationScheduler(applicationContext) }
            val sunTimesCalculator = remember { SunTimesCalculator() }
            val scheduleService = remember { SunScheduleService(sunTimesCalculator, settingsStore, notificationScheduler) }
            val cityRepository = remember { CityRepository(applicationContext) }
            val homeViewModel = remember { HomeViewModel(locationService, settingsStore, scheduleService, sunTimesCalculator) }
            val onboardingViewModel: OnboardingViewModel = viewModel(
                factory = OnboardingViewModelFactory(settingsStore, cityRepository, locationService)
            )
            val onboardingState by onboardingViewModel.state.collectAsState()
            val cityImportViewModel: CityImportViewModel = viewModel(
                factory = CityImportViewModelFactory(cityRepository)
            )
            val cityImportState by cityImportViewModel.state.collectAsState()
            var permissionRequestOrigin by remember { mutableStateOf<PermissionRequestOrigin?>(null) }
            var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
            var hasEnabledAlarms by remember { mutableStateOf(false) }
            var pendingExactAlarmPermissionRequest by rememberSaveable { mutableStateOf(false) }
            var exactAlarmPermissionDialogReason by rememberSaveable { mutableStateOf<String?>(null) }
            val alarmManager = remember { getSystemService(ALARM_SERVICE) as AlarmManager }
            val exactAlarmPermissionTracker = remember { ExactAlarmPermissionTracker(applicationContext) }
            val coroutineScope = rememberCoroutineScope()
            val notificationPermissionLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    Log.d("MainActivity", "Notification permission result: granted=$granted")
                }
            val lifecycleOwner = LocalLifecycleOwner.current
            val locationPermissionLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val granted =
                        permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

                    val shouldShowRationale =
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) || ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )

                    val permanentlyDenied = !granted && !shouldShowRationale

                    val origin = permissionRequestOrigin ?: PermissionRequestOrigin.AUTOMATIC
                    permissionRequestOrigin = null

                    Log.d(
                        "MainActivity",
                        "Location permission result: granted=$granted perms=$permissions"
                    )

                    onboardingViewModel.handleLocationPermissionResult(
                        granted = granted,
                        permanentlyDenied = permanentlyDenied,
                        origin = origin
                    )
                }

            fun requestExactAlarmPermission(reason: String) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
                if (!hasEnabledAlarms) return
                if (alarmManager.canScheduleExactAlarms()) {
                    exactAlarmPermissionTracker.reset()
                    return
                }
                if (!exactAlarmPermissionTracker.canRequestExactAlarmPermission()) return
                if (pendingExactAlarmPermissionRequest) return
                if (exactAlarmPermissionDialogReason != null) return

                Log.i("MainActivity", "Requesting exact alarm permission ($reason)")
                exactAlarmPermissionDialogReason = reason
            }

            DisposableEffect(
                lifecycleOwner,
                onboardingState.locationPermissionPermanentlyDenied
            ) {
                val observer = LifecycleEventObserver { _, event ->
                    if (
                        event == Lifecycle.Event.ON_RESUME &&
                        onboardingState.locationPermissionPermanentlyDenied &&
                        hasLocationPermission(context)
                    ) {
                        onboardingViewModel.clearLocationPermissionDenial()
                    }
                }

                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        coroutineScope.launch {
                            val alarms = settingsStore.loadAlarms()
                            hasEnabledAlarms = alarms.any { it.enabled }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val canSchedule = alarmManager.canScheduleExactAlarms()
                            if (canSchedule) {
                                exactAlarmPermissionTracker.reset()
                            }
                            if (pendingExactAlarmPermissionRequest) {
                                if (!canSchedule) {
                                    exactAlarmPermissionTracker.recordDenial()
                                }
                                pendingExactAlarmPermissionRequest = false
                            }
                        } else {
                            pendingExactAlarmPermissionRequest = false
                        }
                    }
                }

                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(Unit) {
                hasEnabledAlarms = settingsStore.loadAlarms().any { it.enabled }
            }

            LaunchedEffect(
                onboardingState.isLoaded,
                onboardingState.step,
                onboardingState.locationMode,
                onboardingState.deviceNearestCityLabel,
                onboardingState.locationPermissionPermanentlyDenied,
                permissionRequestOrigin
            ) {
                // If onboarding is showing the LOCATION step and is in DEVICE mode,
                // and we don't yet have a nearest-city label, run the permission flow.
                if (
                    onboardingState.isLoaded &&
                    !onboardingState.onboardingComplete &&
                    onboardingState.step == OnboardingStep.LOCATION &&
                    onboardingState.locationMode == LocationMode.DEVICE &&
                    onboardingState.deviceNearestCityLabel == null &&
                    permissionRequestOrigin == null &&
                    !onboardingState.locationPermissionPermanentlyDenied
                ) {
                    if (hasLocationPermission(context)) {
                        // Permission already granted: trigger device-mode behavior
                        onboardingViewModel.clearLocationPermissionDenial()
                        onboardingViewModel.updateLocationMode(LocationMode.DEVICE)
                    } else {
                        // Ask the system for permission
                        permissionRequestOrigin = PermissionRequestOrigin.AUTOMATIC
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            }

            LaunchedEffect(
                onboardingState.isLoaded,
                onboardingState.onboardingComplete,
                onboardingState.notificationsEnabled
            ) {
                if (
                    onboardingState.isLoaded &&
                    onboardingState.onboardingComplete &&
                    onboardingState.notificationsEnabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !hasNotificationPermission(context) &&
                    !notificationPermissionRequested
                ) {
                    notificationPermissionRequested = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LaunchedEffect(
                onboardingState.isLoaded,
                onboardingState.onboardingComplete,
                hasEnabledAlarms
            ) {
                if (onboardingState.isLoaded && onboardingState.onboardingComplete) {
                    requestExactAlarmPermission("active-alerts")
                }
            }

            SuntimeAlertsTheme {
                if (exactAlarmPermissionDialogReason != null) {
                    AlertDialog(
                        onDismissRequest = { exactAlarmPermissionDialogReason = null },
                        title = { Text("Allow alarms & reminders") },
                        text = {
                            Text(
                                "Suntime Alerts needs permission to schedule alarms. " +
                                    "We'll open the Alarms & reminders settings so you can enable this."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    exactAlarmPermissionDialogReason = null
                                    pendingExactAlarmPermissionRequest = true
                                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                }
                            ) {
                                Text("Continue")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { exactAlarmPermissionDialogReason = null }) {
                                Text("Not now")
                            }
                        }
                    )
                }

                when {
                    cityImportState.isImporting -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Preparing Suntime Alerts…")
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(
                                progress = cityImportState.progress
                            )
                            if (cityImportState.total > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${cityImportState.current} / ${cityImportState.total}"
                                )
                            }
                        }
                    }
                    !onboardingState.isLoaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    onboardingState.onboardingComplete -> HomeScreen(viewModel = homeViewModel)
                    else -> OnboardingScreen(
                        state = onboardingState,
                        onLocationModeChanged = { mode ->
                            when (mode) {
                                LocationMode.DEVICE -> {
                                    onboardingViewModel.updateLocationMode(LocationMode.DEVICE)

                                    if (hasLocationPermission(context)) {
                                        // Already granted → just switch to device mode
                                        onboardingViewModel.clearLocationPermissionDenial()
                                        return@OnboardingScreen
                                    }

                                    // Trigger system permission dialog after the UI switches to device mode
                                    permissionRequestOrigin = PermissionRequestOrigin.USER
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                                LocationMode.FIXED -> {
                                    onboardingViewModel.updateLocationMode(LocationMode.FIXED)
                                }
                            }
                        },
                        onOpenPermissionSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        },
                        onNotificationsChanged = { enabled ->
                            onboardingViewModel.updateNotifications(enabled)
                            hasEnabledAlarms = enabled
                            if (
                                enabled &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !hasNotificationPermission(context)
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            requestExactAlarmPermission("notifications-toggle")
                        },
                        onSunriseEnabledChanged = onboardingViewModel::updateSunriseEnabled,
                        onSunsetEnabledChanged = onboardingViewModel::updateSunsetEnabled,
                        onSunriseOffsetChanged = onboardingViewModel::updateSunriseOffset,
                        onSunsetOffsetChanged = onboardingViewModel::updateSunsetOffset,
                        onCityQueryChanged = onboardingViewModel::updateCityQuery,
                        onCitySelected = onboardingViewModel::selectCity,
                        onNext = onboardingViewModel::nextStep,
                        onBack = onboardingViewModel::previousStep,
                        onComplete = { onboardingViewModel.complete { } },
                        canAdvance = onboardingViewModel.canAdvance()
                    )
                }
            }
        }
    }
}
