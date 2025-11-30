package com.bfalls.suntimealerts.cities.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfalls.suntimealerts.cities.data.CityImportProgress
import com.bfalls.suntimealerts.cities.data.CityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CityImportUiState(
    val isImporting: Boolean = false,
    val progress: Float = 0f,      // 0.0f .. 1.0f
    val current: Int = 0,
    val total: Int = 0
)

class CityImportViewModel(
    private val cityRepository: CityRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CityImportUiState())
    val state: StateFlow<CityImportUiState> = _state

    init {
        viewModelScope.launch {
            // Show overlay while we decide whether import is needed.
            _state.value = _state.value.copy(isImporting = true)

            try {
                cityRepository.ensureCitiesLoaded { progress: CityImportProgress ->
                    val fraction = if (progress.total > 0) {
                        progress.current.toFloat() / progress.total.toFloat()
                    } else {
                        0f
                    }

                    _state.value = CityImportUiState(
                        isImporting = true,
                        progress = fraction.coerceIn(0f, 1f),
                        current = progress.current,
                        total = progress.total
                    )
                }
            } finally {
                // Import is done or was skipped because the version already matched
                _state.value = _state.value.copy(isImporting = false)
            }
        }
    }
}
