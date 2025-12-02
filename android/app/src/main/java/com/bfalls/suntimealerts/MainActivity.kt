package com.bfalls.suntimealerts

import android.Manifest
import android.os.Bundle
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bfalls.suntimealerts.alarm.data.LocationService
import com.bfalls.suntimealerts.alarm.data.SettingsStore
import com.bfalls.suntimealerts.alarm.data.SunScheduleService
import com.bfalls.suntimealerts.alarm.domain.model.LocationMode
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.presentation.ui.HomeScreen
import com.bfalls.suntimealerts.alarm.presentation.ui.OnboardingScreen
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.HomeViewModel
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingViewModel
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingViewModelFactory
import com.bfalls.suntimealerts.alarm.services.NotificationScheduler
import com.bfalls.suntimealerts.cities.data.CityRepository
import com.bfalls.suntimealerts.cities.presentation.CityImportViewModel
import com.bfalls.suntimealerts.cities.presentation.CityImportViewModelFactory
import com.bfalls.suntimealerts.ui.theme.SuntimeAlertsTheme
import com.bfalls.suntimealerts.utils.hasLocationPermission
import androidx.compose.runtime.LaunchedEffect
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingStep


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settingsStore = remember { SettingsStore(applicationContext) }
            val locationService = remember { LocationService(application) }
            val notificationScheduler = remember { NotificationScheduler(applicationContext) }
            val scheduleService = remember { SunScheduleService(SunTimesCalculator(), settingsStore, notificationScheduler) }
            val cityRepository = remember { CityRepository(applicationContext) }
            val homeViewModel = remember { HomeViewModel(locationService, settingsStore, scheduleService) }
            val onboardingViewModel: OnboardingViewModel = viewModel(
                factory = OnboardingViewModelFactory(settingsStore, cityRepository, locationService)
            )
            val onboardingState by onboardingViewModel.state.collectAsState()
            val cityImportViewModel: CityImportViewModel = viewModel(
                factory = CityImportViewModelFactory(cityRepository)
            )
            val cityImportState by cityImportViewModel.state.collectAsState()
            val locationPermissionLauncher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val granted =
                        permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

                    Log.d(
                        "MainActivity",
                        "Location permission result: granted=$granted perms=$permissions"
                    )

                    if (granted) {
                        // Now it’s safe to use device location
                        onboardingViewModel.updateLocationMode(LocationMode.DEVICE)
                    } else {
                        // Optionally: show a message or keep them on Manual mode
                        // e.g. onboardingViewModel.updateLocationMode(LocationMode.FIXED)
                    }
                }

            LaunchedEffect(
                onboardingState.isLoaded,
                onboardingState.step,
                onboardingState.locationMode,
                onboardingState.deviceNearestCityLabel
            ) {
                // If onboarding is showing the LOCATION step and is in DEVICE mode,
                // and we don't yet have a nearest-city label, run the permission flow.
                if (
                    onboardingState.isLoaded &&
                    !onboardingState.onboardingComplete &&
                    onboardingState.step == OnboardingStep.LOCATION &&
                    onboardingState.locationMode == LocationMode.DEVICE &&
                    onboardingState.deviceNearestCityLabel == null
                ) {
                    if (hasLocationPermission(context)) {
                        // Permission already granted: trigger device-mode behavior
                        onboardingViewModel.updateLocationMode(LocationMode.DEVICE)
                    } else {
                        // Ask the system for permission
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }
            }


            SuntimeAlertsTheme {
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
                                    if (hasLocationPermission(context)) {
                                        // Already granted → just switch to device mode
                                        onboardingViewModel.updateLocationMode(LocationMode.DEVICE)
                                    } else {
                                        // Trigger system permission dialog
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                                LocationMode.FIXED -> {
                                    onboardingViewModel.updateLocationMode(LocationMode.FIXED)
                                }
                            }
                        },
                        onNotificationsChanged = onboardingViewModel::updateNotifications,
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