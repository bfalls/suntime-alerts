package com.bfalls.suntimealerts.cities.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val PREFS_NAME = "city_prefs"
private const val KEY_CITY_DATA_VERSION = "city_data_version"
private const val CITY_DATA_VERSION = 1
private const val ASSET_FILE_NAME = "cities_offline.dat"

data class CityImportProgress(
    val current: Int,
    val total: Int
)

class CityRepository(
    private val context: Context,
    private val dbHelper: CitiesDatabaseHelper = CitiesDatabaseHelper(context)
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun ensureCitiesLoaded(
        onProgress: ((CityImportProgress) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val storedVersion = prefs.getInt(KEY_CITY_DATA_VERSION, 0)
        if (storedVersion == CITY_DATA_VERSION) {
            // Already imported this version
            return@withContext
        }

        Log.d("CityRepository", "Importing cities from assets...")

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM cities;")

            val json = context.assets.open(ASSET_FILE_NAME).use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }

            val array = JSONArray(json)
            val total = array.length().coerceAtLeast(1)

            val insertSql = """
                INSERT INTO cities (
                    id, name, asciiName, countryCode, admin1Code, lat, lon, timezone, population
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            val stmt = db.compileStatement(insertSql)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                val id = obj.getLong("id")
                val name = obj.getString("name")
                val asciiName = obj.getString("asciiName")
                val countryCode = obj.getString("countryCode")
                val admin1Code = obj.getString("admin1Code")
                val lat = obj.getDouble("lat")
                val lon = obj.getDouble("lon")
                val timezone = obj.getString("timezone")
                val population = obj.getLong("population")

                stmt.clearBindings()
                stmt.bindLong(1, id)
                stmt.bindString(2, name)
                stmt.bindString(3, asciiName)
                stmt.bindString(4, countryCode)
                stmt.bindString(5, admin1Code)
                stmt.bindDouble(6, lat)
                stmt.bindDouble(7, lon)
                stmt.bindString(8, timezone)
                stmt.bindLong(9, population)

                stmt.executeInsert()

                // Report progress every so often (and on the last record)
                if (onProgress != null) {
                    if (i == total - 1 || i % 100 == 0) {
                        onProgress(
                            CityImportProgress(
                                current = i + 1,
                                total = total
                            )
                        )
                    }
                }
            }

            db.setTransactionSuccessful()
            prefs.edit().putInt(KEY_CITY_DATA_VERSION, CITY_DATA_VERSION).apply()
            Log.d("CityRepository", "Imported $total cities.")
        } catch (t: Throwable) {
            Log.e("CityRepository", "Failed to import cities", t)
            throw t
        } finally {
            db.endTransaction()
        }
    }

    suspend fun searchCities(query: String, limit: Int = 20): List<City> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()

            val pattern = "%$trimmed%"
            val db = dbHelper.readableDatabase

            val cities = mutableListOf<City>()
            val cursor = db.query(
                "cities",
                arrayOf(
                    "id",
                    "name",
                    "asciiName",
                    "countryCode",
                    "admin1Code",
                    "lat",
                    "lon",
                    "timezone",
                    "population"
                ),
                "name LIKE ? OR asciiName LIKE ?",
                arrayOf(pattern, pattern),
                null,
                null,
                "population DESC",
                limit.toString()
            )

            cursor.use {
                while (it.moveToNext()) {
                    val city = City(
                        id = it.getLong(0),
                        name = it.getString(1),
                        asciiName = it.getString(2),
                        countryCode = it.getString(3),
                        admin1Code = it.getString(4),
                        lat = it.getDouble(5),
                        lon = it.getDouble(6),
                        timezone = it.getString(7),
                        population = it.getLong(8)
                    )
                    cities.add(city)
                }
            }

            cities
        }
}
