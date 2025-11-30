package com.bfalls.suntimealerts.cities.data

data class City(
    val id: Long,
    val name: String,
    val asciiName: String,
    val countryCode: String,
    val admin1Code: String,
    val lat: Double,
    val lon: Double,
    val timezone: String,
    val population: Long
)
