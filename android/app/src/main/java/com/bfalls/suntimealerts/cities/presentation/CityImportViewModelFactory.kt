package com.bfalls.suntimealerts.cities.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bfalls.suntimealerts.cities.data.CityRepository

class CityImportViewModelFactory(
    private val cityRepository: CityRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CityImportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CityImportViewModel(cityRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
