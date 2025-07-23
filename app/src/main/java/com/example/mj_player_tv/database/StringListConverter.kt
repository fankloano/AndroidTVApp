package com.example.mj_player_tv.database

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.objectbox.converter.PropertyConverter


class StringListConverter : PropertyConverter<MutableList<String>, String> {

    private val gson = Gson()

    override fun convertToDatabaseValue(entityProperty: MutableList<String>?): String {
        return if (entityProperty == null) {
            ""
        } else {
            gson.toJson(entityProperty)
        }
    }

    override fun convertToEntityProperty(databaseValue: String?): MutableList<String> {
        return if (databaseValue == null || databaseValue.isEmpty()) {
            mutableListOf()
        } else {
            val listType = object : TypeToken<MutableList<String>>() {}.type
            gson.fromJson(databaseValue, listType)
        }
    }
}