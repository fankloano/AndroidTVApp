package com.example.mj_player_tv.database

import io.objectbox.converter.PropertyConverter

class IntListConverter : PropertyConverter<List<Int>, String> {
    override fun convertToDatabaseValue(entityProperty: List<Int>?): String {
        return entityProperty?.joinToString(separator = ",") ?: ""
    }

    override fun convertToEntityProperty(databaseValue: String?): List<Int> {
        return databaseValue?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
    }
}