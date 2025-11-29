package com.bfalls.suntimealerts.alarm.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bfalls.suntimealerts.alarm.presentation.viewmodel.HomeViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(text = "Suntime Alerts")
            Text(text = "Sunrise alarm")
            Switch(checked = state.sunriseEnabled, onCheckedChange = viewModel::toggleSunrise)
            Text(text = "Sunset alarm")
            Switch(checked = state.sunsetEnabled, onCheckedChange = viewModel::toggleSunset)
            Button(onClick = viewModel::reschedule) {
                Text("Recompute & Reschedule")
            }
        }
    }
}
