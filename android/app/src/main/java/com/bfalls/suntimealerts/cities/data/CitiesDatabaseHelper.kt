package com.bfalls.suntimealerts.cities.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CitiesDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE cities (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                asciiName TEXT NOT NULL,
                countryCode TEXT NOT NULL,
                admin1Code TEXT NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                timezone TEXT NOT NULL,
                population INTEGER NOT NULL
            );
            """
        )
        db.execSQL("CREATE INDEX idx_cities_name ON cities(name);")
        db.execSQL("CREATE INDEX idx_cities_ascii_name ON cities(asciiName);")
        db.execSQL("CREATE INDEX idx_cities_population ON cities(population);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // For now, drop and recreate. Schema migrations can be added when needed.
        db.execSQL("DROP TABLE IF EXISTS cities")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "cities.db"
        private const val DATABASE_VERSION = 1
    }
}
