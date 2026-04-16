package com.example.universitymapproj

import android.content.Context
import android.util.Log
import com.example.universitymapproj.models.Landmark

object MapConfig {
    const val COLS = 162
    const val ROWS = 183
    const val IMG_W = 1170f
    const val IMG_H = 1414f

    fun readFromAssets(context: Context, fileName: String): String {
        return try {
            val inputStream = context.assets.open(fileName)
            java.io.InputStreamReader(inputStream).buffered().use { it.readText() }
        } catch (e: Exception) {
            Log.e("MAP_ERROR", "Ошибка при чтении файла")
            ""
        }
    }

    fun logLongString(tag: String, text: String) {
        val chunkSize = 4000
        var i = 0
        while (i < text.length) {
            val end = (i + chunkSize).coerceAtMost(text.length)
            Log.d(tag, text.substring(i, end))
            i += chunkSize
        }
    }

    var SAVED_GRID = ""
    var SAVED_FOOD_PLACES = ""
    const val DEFAULT_FOOD = "80,61,Новый общепит,Описание,08:00-18:00,Кафе,Борщ|Чай;" +
            "84,63,Новый общепит 1,Описание,08:00-18:00,Кафе,Пицца|Кофе;" +
            "76,71,Новый общепит 2,Описание,08:00-18:00,Кафе,Салат|Борщ;" +
            "66,71,Новый общепит 3,Описание,08:00-18:00,Кафе,Суши;" +
            "68,83,Новый общепит 4,Описание,08:00-18:00,Кафе,Пицца|Суши;" +
            "83,85,Новый общепит 5,Описание,08:00-18:00,Кафе,Чай|Десерт"
}

object LandmarkConfig {
    val DEFAULT_LANDMARKS = listOf(
        Landmark(1, 78, 60, "Главный корпус", "Центральное здание университета"),
        Landmark(2, 83, 85, "Библиотека", "Университетская библиотека"),
        Landmark(3, 68, 83, "Студенческий центр", "Место встреч и мероприятий"),
        Landmark(4, 66, 71, "Спортивный корпус", "Спортивные секции"),
        Landmark(5, 76, 71, "Лекторий", "Учебные аудитории"),
        Landmark(6, 84, 63, "Памятник", "Известная точка кампуса")
    )
}