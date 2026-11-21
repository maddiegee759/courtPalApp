package com.example.courtpalapp.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.courtpalapp.domain.match.MatchSettings
import com.example.courtpalapp.domain.match.TennisScore

object ActiveMatchJson {

    private val gson = Gson()

    fun settingsToJson(settings: MatchSettings): String {
        return gson.toJson(settings)
    }

    fun settingsFromJson(json: String): MatchSettings {
        return gson.fromJson(json, MatchSettings::class.java)
    }

    fun scoreToJson(score: TennisScore): String {
        return gson.toJson(score)
    }

    fun scoreFromJson(json: String): TennisScore {
        return gson.fromJson(json, TennisScore::class.java)
    }

    fun historyToJson(history: List<TennisScore>): String {
        return gson.toJson(history)
    }

    fun historyFromJson(json: String): List<TennisScore> {
        val type = object : TypeToken<List<TennisScore>>() {}.type
        return gson.fromJson(json, type)
    }
}
