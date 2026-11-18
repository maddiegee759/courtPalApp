
package com.example.courtpalapp.api.calendar

import android.app.Activity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

class CalendarAuthManager(private val activity: Activity) {

    private val client = Identity.getAuthorizationClient(activity)

    fun requestAuthorization(
        onSuccess: (AuthorizationResult) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(
                        "https://www.googleapis.com/auth/calendar.events.owned"
                    )
                )
            )
            .build()

        client.authorize(request)
            .addOnSuccessListener(onSuccess)
            .addOnFailureListener(onError)
    }

    fun getResultFromIntent(
        data: android.content.Intent
    ): AuthorizationResult {
        return client.getAuthorizationResultFromIntent(data)
    }
}
