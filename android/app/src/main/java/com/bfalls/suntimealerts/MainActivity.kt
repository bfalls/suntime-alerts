package com.bfalls.suntimealerts

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.bfalls.suntimealerts.alarm.domain.model.AppThemeMode
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
import com.bfalls.suntimealerts.alarm.services.AlarmReadinessService
import com.bfalls.suntimealerts.alarm.services.AlarmRepairAction
import com.bfalls.suntimealerts.alarm.services.NotificationScheduler
import com.bfalls.suntimealerts.cities.data.CityRepository
import com.bfalls.suntimealerts.cities.presentation.CityImportViewModel
import com.bfalls.suntimealerts.cities.presentation.CityImportViewModelFactory
import com.bfalls.suntimealerts.ui.theme.SuntimeAlertsTheme
import kotlinx.coroutines.runBlocking


class MainActivity : ComponentActivity() {
    private val settingsStore: SettingsStore by lazy { SettingsStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        runBlocking {
            applyAppThemeMode(settingsStore.load().appThemeMode)
        }
        enableEdgeToEdge()
        setContent {
            val settingsStore = remember { this@MainActivity.settingsStore }
            val locationService = remember { LocationService(application) }
            val notificationScheduler = remember { NotificationScheduler(applicationContext) }
            val sunTimesCalculator = remember { SunTimesCalculator() }
            val scheduleService = remember { SunScheduleService(sunTimesCalculator, settingsStore, notificationScheduler) }
            val cityRepository = remember { CityRepository(applicationContext) }
            val alarmReadinessService = remember {
                AlarmReadinessService(
                    context = applicationContext,
                    settingsStore = settingsStore,
                    usesFullScreenAlarmUi = true
                )
            }
            val homeViewModel = remember {
                HomeViewModel(
                    locationService,
                    settingsStore,
                    scheduleService,
                    sunTimesCalculator,
                    alarmReadinessService
                )
            }
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(
                    settingsStore,
                    cityRepository,
                    locationService,
                    applicationContext,
                    alarmReadinessService
                )
            )
            val onboardingViewModel: OnboardingViewModel = viewModel(
                factory = OnboardingViewModelFactory(
                    settingsStore,
                    cityRepository,
                    locationService,
                    alarmReadinessService
                )
            )
            val settingsState by settingsViewModel.state.collectAsState()
            val onboardingState by onboardingViewModel.state.collectAsState()
            val cityImportViewModel: CityImportViewModel = viewModel(
                factory = CityImportViewModelFactory(cityRepository)
            )
            val cityImportState by cityImportViewModel.state.collectAsState()
            var permissionRequestOrigin by remember { mutableStateOf<PermissionRequestOrigin?>(null) }
            var autoLocationPermissionRequested by rememberSaveable { mutableStateOf(false) }
            var pendingExactAlarmPermissionRequest by rememberSaveable { mutableStateOf(false) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            val openAppSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            val openAppNotificationSettings = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
            val openChannelNotificationSettings: (String?) -> Unit = { channelId ->
                if (channelId.isNullOrBlank()) {
                    openAppNotificationSettings()
                } else {
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                    }
                    startActivity(intent)
                }
            }
            val openExactAlarmSettings = {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
            val openFullScreenIntentSettings = {
                val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                }
                startActivity(intent)
            }
            val notificationPermissionLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    Log.d("MainActivity", "Notification permission result: granted=$granted")
                    onboardingViewModel.handleNotificationPermissionResult()
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

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        onboardingViewModel.handleResume()
                        if (pendingExactAlarmPermissionRequest) {
                            onboardingViewModel.handleExactAlarmSettingsResult()
                            homeViewModel.handleExactAlarmSettingsResult()
                            pendingExactAlarmPermissionRequest = false
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
                onboardingState.alarmReadiness,
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
                    if (onboardingState.alarmReadiness?.locationReady == true) {
                        onboardingViewModel.clearLocationPermissionDenial()
                        onboardingViewModel.updateLocationMode(LocationMode.DEVICE)
                    } else {
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
                onboardingState.alarmReadiness,
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
                    onboardingState.alarmReadiness?.locationReady != true
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

            val appThemeMode = settingsState.appThemeMode
            val darkTheme = when (appThemeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            LaunchedEffect(appThemeMode) {
                applyAppThemeMode(appThemeMode)
            }

            SuntimeAlertsTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        cityImportState.isImporting -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Preparing Suntime Alerts...",
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                CircularProgressIndicator(
                                    progress = { cityImportState.progress }
                                )
                                if (cityImportState.total > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${cityImportState.current} / ${cityImportState.total}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

                                            if (onboardingState.alarmReadiness?.locationReady == true) {
                                                onboardingViewModel.clearLocationPermissionDenial()
                                                return@OnboardingScreen
                                            }

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
                                onOpenPermissionSettings = openAppSettings,
                                onOpenNotificationSettings = openAppNotificationSettings,
                                onOpenNotificationChannelSettings = {
                                    openChannelNotificationSettings(
                                        onboardingState.alarmReadiness?.blockedNotificationChannelId
                                    )
                                },
                                onCityQueryChanged = onboardingViewModel::updateCityQuery,
                                onCitySelected = onboardingViewModel::selectCity,
                                notificationsPermissionRequired = onboardingState.alarmReadiness?.notificationsReady == false,
                                exactAlarmPermissionRequired = onboardingState.alarmReadiness?.exactAlarmReady == false,
                                onNotificationsContinue = {
                                    val readiness = onboardingState.alarmReadiness
                                    if (readiness?.notificationsReady == true) {
                                        onboardingViewModel.nextStep()
                                        return@OnboardingScreen
                                    }
                                    if (
                                        readiness?.repairActions?.contains(
                                            AlarmRepairAction.REQUEST_NOTIFICATION_PERMISSION
                                        ) == true
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else if (
                                        readiness?.repairActions?.contains(
                                            AlarmRepairAction.OPEN_NOTIFICATION_SETTINGS
                                        ) == true
                                    ) {
                                        openAppNotificationSettings()
                                    } else if (
                                        readiness?.repairActions?.contains(
                                            AlarmRepairAction.OPEN_NOTIFICATION_CHANNEL_SETTINGS
                                        ) == true
                                    ) {
                                        openChannelNotificationSettings(
                                            readiness.blockedNotificationChannelId
                                        )
                                    }
                                },
                                onNotificationsSkip = onboardingViewModel::nextStep,
                                onExactAlarmsContinue = {
                                    val readiness = onboardingState.alarmReadiness
                                    if (readiness?.exactAlarmReady == true) {
                                        onboardingViewModel.nextStep()
                                        return@OnboardingScreen
                                    }
                                    if (pendingExactAlarmPermissionRequest) {
                                        return@OnboardingScreen
                                    }
                                    if (
                                        readiness?.repairActions?.contains(
                                            AlarmRepairAction.REQUEST_EXACT_ALARM_PERMISSION
                                        ) == true
                                    ) {
                                        pendingExactAlarmPermissionRequest = true
                                        openExactAlarmSettings()
                                    }
                                },
                                onExactAlarmsSkip = {
                                    pendingExactAlarmPermissionRequest = false
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
                            onOpenSettings = { showSettings = true },
                            onOpenNotificationSettings = openAppNotificationSettings,
                            onOpenNotificationChannelSettings = { channelId ->
                                openChannelNotificationSettings(channelId)
                            },
                            onOpenExactAlarmSettings = openExactAlarmSettings,
                            onOpenFullScreenIntentSettings = openFullScreenIntentSettings
                        )
                    }
                }
            }
        }
    }

    private fun applyAppThemeMode(mode: AppThemeMode) {
        val nightMode = when (mode) {
            AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
