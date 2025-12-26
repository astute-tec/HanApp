package com.hanapp.ui.components

import android.content.Context
import com.google.gson.Gson

data class HanziData(
    val strokes: List<String>,
    val medians: List<List<List<Int>>>
)

object HanziDataHelper {
    fun loadHanziXmlData(context: Context, char: String): HanziData? {
        return try {
            val inputStream = context.assets.open("hanzi-data/$char.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(jsonString, HanziData::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
