package com.bfalls.suntimealerts

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.bfalls.suntimealerts.ui.theme.SuntimeAlertsTheme
import com.bfalls.suntimealerts.alarm.data.LocationService
import com.bfalls.suntimealerts.alarm.data.SettingsStore
import com.bfalls.suntimealerts.alarm.data.SunScheduleService
import com.bfalls.suntimealerts.alarm.domain.service.SunTimesCalculator
import com.bfalls.suntimealerts.alarm.presentation.ui.HomeScreen
import com.bfalls.suntimealerts.alarm.presentation.ui.OnboardingScreen
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.HomeViewModel
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingViewModel
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.OnboardingViewModelFactory
import com.bfalls.suntimealerts.alarm.services.NotificationScheduler
import com.bfalls.suntimealerts.cities.data.CityRepository
import com.bfalls.suntimealerts.cities.data.CitiesDatabaseHelper
import com.bfalls.suntimealerts.cities.presentation.CityImportViewModel
import com.bfalls.suntimealerts.cities.presentation.CityImportViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsStore = remember { SettingsStore(applicationContext) }
            val locationService = remember { LocationService(application) }
            val notificationScheduler = remember { NotificationScheduler(applicationContext) }
            val scheduleService = remember { SunScheduleService(SunTimesCalculator(), settingsStore, notificationScheduler) }
            val homeViewModel = remember { HomeViewModel(locationService, settingsStore, scheduleService) }
            val onboardingViewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModelFactory(settingsStore))
            val onboardingState by onboardingViewModel.state.collectAsState()
            val cityRepository = remember { CityRepository(applicationContext) }
            val cityImportViewModel: CityImportViewModel = viewModel(
                factory = CityImportViewModelFactory(cityRepository)
            )
            val cityImportState by cityImportViewModel.state.collectAsState()

            SuntimeAlertsTheme {
                when {
                    !onboardingState.isLoaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    onboardingState.onboardingComplete -> HomeScreen(viewModel = homeViewModel)
                    else -> OnboardingScreen(
                        state = onboardingState,
                        onLocationModeChanged = onboardingViewModel::updateLocationMode,
                        onNotificationsChanged = onboardingViewModel::updateNotifications,
                        onSunriseEnabledChanged = onboardingViewModel::updateSunriseEnabled,
                        onSunsetEnabledChanged = onboardingViewModel::updateSunsetEnabled,
                        onSunriseOffsetChanged = onboardingViewModel::updateSunriseOffset,
                        onSunsetOffsetChanged = onboardingViewModel::updateSunsetOffset,
                        onFixedLatitudeChanged = onboardingViewModel::updateFixedLatitude,
                        onFixedLongitudeChanged = onboardingViewModel::updateFixedLongitude,
                        onNext = onboardingViewModel::nextStep,
                        onBack = onboardingViewModel::previousStep,
                        onComplete = { onboardingViewModel.complete { } },
                        canAdvance = onboardingViewModel.canAdvance()
                    )
                }

                if (cityImportState.isImporting) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Preparing offline city data…")
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
                }
            }
        }
    }
}