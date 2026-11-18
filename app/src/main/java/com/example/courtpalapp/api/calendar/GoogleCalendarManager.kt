
package com.example.courtpalapp.api.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GoogleCalendarManager {

    suspend fun createEvent(
        accessToken: String,
        title: String,
        location: String,
        description: String,
        startDateTime: String,
        endDateTime: String
    ): String = withContext(Dispatchers.IO) {

        require(accessToken.isNotBlank()) {
            "Google Calendar access token is missing"
        }

        val event = JSONObject().apply {
            put("summary", title)
            put("location", location)
            put("description", description)

            put("start", JSONObject().apply {
                put("dateTime", startDateTime)
            })

            put("end", JSONObject().apply {
                put("dateTime", endDateTime)
            })
        }

        val url = URL(
            "https://www.googleapis.com/calendar/v3/" +
                    "calendars/primary/events"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty(
                "Authorization",
                "Bearer $accessToken"
            )
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
            )
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true

            connection.outputStream.use { output ->
                output.write(
                    event.toString().toByteArray(Charsets.UTF_8)
                )
            }

            val responseCode = connection.responseCode

            val response = (
                    if (responseCode in 200..299)
                        connection.inputStream
                    else
                        connection.errorStream
                    )?.bufferedReader()?.use {
                    it.readText()
                }.orEmpty()

            if (responseCode !in 200..299) {
                throw Exception(
                    "Google Calendar API error " +
                            "$responseCode: $response"
                )
            }

            JSONObject(response).getString("id")

        } finally {
            connection.disconnect()
        }
    }
}
