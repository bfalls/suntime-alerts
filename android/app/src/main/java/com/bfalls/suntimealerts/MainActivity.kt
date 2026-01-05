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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.bfalls.suntimealerts.alarm.presentation.ui.SettingsScreen
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.HomeViewModel
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingStep
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingViewModel
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingViewModelFactory
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.PermissionRequestOrigin
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.SettingsViewModel
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.SettingsViewModelFactory
import com.bfalls.suntimealerts.alarm.services.NotificationScheduler
import com.bfalls.suntimealerts.cities.data.CityRepository
import com.bfalls.suntimealerts.cities.presentation.CityImportViewModel
import com.bfalls.suntimealerts.cities.presentation.CityImportViewModelFactory
import com.bfalls.suntimealerts.ui.theme.SplashBackground
import com.bfalls.suntimealerts.ui.theme.SuntimeAlertsTheme
import com.bfalls.suntimealerts.ui.theme.TextPrimary
import com.bfalls.suntimealerts.ui.theme.TextSecondary
import com.bfalls.suntimealerts.utils.ExactAlarmPermissionTracker
import com.bfalls.suntimealerts.utils.hasLocationPermission
import com.bfalls.suntimealerts.utils.hasNotificationPermission


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
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(settingsStore, cityRepository, locationService, applicationContext)
            )
            val onboardingViewModel: OnboardingViewModel = viewModel(
                factory = OnboardingViewModelFactory(settingsStore, cityRepository, locationService)
            )
            val onboardingState by onboardingViewModel.state.collectAsState()
            val cityImportViewModel: CityImportViewModel = viewModel(
                factory = CityImportViewModelFactory(cityRepository)
            )
            val cityImportState by cityImportViewModel.state.collectAsState()
            var permissionRequestOrigin by remember { mutableStateOf<PermissionRequestOrigin?>(null) }
            var autoLocationPermissionRequested by rememberSaveable { mutableStateOf(false) }
            var pendingExactAlarmPermissionRequest by rememberSaveable { mutableStateOf(false) }
            var awaitingExactAlarmOnboardingResult by rememberSaveable { mutableStateOf(false) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            val alarmManager = remember { getSystemService(ALARM_SERVICE) as AlarmManager }
            val exactAlarmPermissionTracker = remember { ExactAlarmPermissionTracker(applicationContext) }
            val notificationPermissionLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    Log.d("MainActivity", "Notification permission result: granted=$granted")
                    if (onboardingViewModel.state.value.step == OnboardingStep.NOTIFICATIONS) {
                        onboardingViewModel.nextStep()
                    }
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
                            if (awaitingExactAlarmOnboardingResult) {
                                if (canSchedule) {
                                    onboardingViewModel.nextStep()
                                }
                                awaitingExactAlarmOnboardingResult = false
                            }
                        } else {
                            pendingExactAlarmPermissionRequest = false
                            awaitingExactAlarmOnboardingResult = false
                        }
                    }
                }

                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
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
                        autoLocationPermissionRequested = true
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
                onboardingState.locationMode,
                onboardingState.locationPermissionPermanentlyDenied,
                autoLocationPermissionRequested,
                permissionRequestOrigin
            ) {
                if (
                    onboardingState.isLoaded &&
                    onboardingState.onboardingComplete &&
                    onboardingState.locationMode == LocationMode.DEVICE &&
                    !onboardingState.locationPermissionPermanentlyDenied &&
                    permissionRequestOrigin == null &&
                    !autoLocationPermissionRequested &&
                    !hasLocationPermission(context)
                ) {
                    autoLocationPermissionRequested = true
                    permissionRequestOrigin = PermissionRequestOrigin.AUTOMATIC
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            LaunchedEffect(
                onboardingState.isLoaded,
                onboardingState.onboardingComplete,
            ) {
                if (onboardingState.isLoaded && onboardingState.onboardingComplete) {
                    // No automatic notification or alarm permission prompts here;
                    // these are handled during onboarding.
                }
            }

            LaunchedEffect(onboardingState.onboardingComplete) {
                if (!onboardingState.onboardingComplete) {
                    showSettings = false
                }
            }

            SuntimeAlertsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        cityImportState.isImporting -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(SplashBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Preparing Suntime Alerts...", color = TextPrimary)
                                Spacer(modifier = Modifier.height(16.dp))
                                CircularProgressIndicator(
                                    progress = { cityImportState.progress }
                                )
                                if (cityImportState.total > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${cityImportState.current} / ${cityImportState.total}",
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                        !onboardingState.isLoaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        !onboardingState.onboardingComplete -> {
                            OnboardingScreen(
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
                                onCityQueryChanged = onboardingViewModel::updateCityQuery,
                                onCitySelected = onboardingViewModel::selectCity,
                                notificationsPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !hasNotificationPermission(context),
                                exactAlarmPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    !alarmManager.canScheduleExactAlarms(),
                                onNotificationsContinue = {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                        onboardingViewModel.nextStep()
                                        return@OnboardingScreen
                                    }
                                    if (hasNotificationPermission(context)) {
                                        onboardingViewModel.nextStep()
                                    } else {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onNotificationsSkip = onboardingViewModel::nextStep,
                                onExactAlarmsContinue = {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                        onboardingViewModel.nextStep()
                                        return@OnboardingScreen
                                    }
                                    if (alarmManager.canScheduleExactAlarms()) {
                                        exactAlarmPermissionTracker.reset()
                                        onboardingViewModel.nextStep()
                                        return@OnboardingScreen
                                    }
                                    if (pendingExactAlarmPermissionRequest) {
                                        return@OnboardingScreen
                                    }
                                    pendingExactAlarmPermissionRequest = true
                                    if (
                                        onboardingState.step == OnboardingStep.EXACT_ALARMS &&
                                        !onboardingState.onboardingComplete
                                    ) {
                                        awaitingExactAlarmOnboardingResult = true
                                    }
                                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                },
                                onExactAlarmsSkip = {
                                    pendingExactAlarmPermissionRequest = false
                                    awaitingExactAlarmOnboardingResult = false
                                    onboardingViewModel.nextStep()
                                },
                                onNext = onboardingViewModel::nextStep,
                                onBack = onboardingViewModel::previousStep,
                                onComplete = { onboardingViewModel.complete { } },
                                canAdvance = onboardingViewModel.canAdvance()
                            )
                        }
                        showSettings -> SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { showSettings = false },
                            onLocationUpdated = {
                                homeViewModel.refresh()
                            },
                            onSkyBodySizeUpdated = {
                                homeViewModel.refresh()
                            }
                        )
                        else -> HomeScreen(
                            viewModel = homeViewModel,
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}
