package com.example.mj_player_tv.database

import com.example.mj_player_tv.database.entity.EpgSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.objectbox.converter.PropertyConverter

class EpgSourceListConverter : PropertyConverter<MutableList<EpgSource>?, String?> {
    private val gson = Gson()
    private val listType = object : TypeToken<ArrayList<EpgSource>?>() {}.type

    override fun convertToEntityProperty(databaseValue: String?): MutableList<EpgSource>? {
        return gson.fromJson(databaseValue, listType)
    }

    override fun convertToDatabaseValue(entityProperty: MutableList<EpgSource>?): String? {
        return gson.toJson(entityProperty)
    }
}
