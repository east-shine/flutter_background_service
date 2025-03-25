package id.flutter.flutter_background_service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    private val TAG = "GeofenceReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called with intent: $intent")
        Log.d(TAG, "Intent action: ${intent.action}")
        Log.d(TAG, "Intent extras: ${intent.extras}")

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "GeofencingEvent error: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        Log.d(TAG, "Geofence transition: $geofenceTransition")

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER || geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            val geofenceTransitionString = when (geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "enter"
                Geofence.GEOFENCE_TRANSITION_EXIT -> "exit"
                else -> "unknown"
            }

            Log.d(TAG, "Geofence transition string: $geofenceTransitionString")

            val triggeringGeofences = geofencingEvent.triggeringGeofences
            val location = geofencingEvent.triggeringLocation
            Log.d(TAG, "Triggering geofences: $triggeringGeofences, location: $location")

            if (triggeringGeofences != null && location != null) {
                for (geofence in triggeringGeofences) {
                    val locationData = mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "altitude" to location.altitude,
                        "horizontalAccuracy" to location.accuracy,
                        "verticalAccuracy" to location.verticalAccuracyMeters,
                        "timestamp" to location.time / 1000.0
                    )
                    Log.d(TAG, "Geofence event for ${geofence.requestId} at location $locationData")
                    GeofencingService.notifyGeofenceEvent(
                        "geofenceEvent",
                        mapOf(
                            "identifier" to geofence.requestId,
                            "eventType" to geofenceTransitionString,
                            "location" to locationData
                        )
                    )
                }
            }
        }
    }
}