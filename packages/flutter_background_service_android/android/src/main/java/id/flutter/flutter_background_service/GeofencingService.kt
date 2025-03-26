package id.flutter.flutter_background_service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import org.json.JSONObject

object GeofencingService {


    fun registerGeofence(
        context: Context,
        latitude: Double,
        longitude: Double,
        radius: Double,
        identifier: String
    ) {
        val geofencingClient = LocationServices.getGeofencingClient(context)

        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = "com.google.android.gms.location.Geofence"
        }
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val geofence = Geofence.Builder()
            .setRequestId(identifier)
            .setCircularRegion(latitude, longitude, radius.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        try {
            geofencingClient.addGeofences(geofencingRequest, pendingIntent).run {
                addOnSuccessListener {
                    Log.d("GeofencingService", "Geofence added successfully: $identifier")
                    saveGeofence(context, identifier, latitude, longitude, radius)
                }
                addOnFailureListener { e ->
                    Log.e("GeofencingService", "Failed to add geofence: $identifier", e)
                }
            }
        } catch (e: SecurityException) {
            Log.e("GeofencingService", "SecurityException while adding geofence: $identifier", e)
        }
    }

    fun removeGeofence(context: Context, identifier: String) {
        val geofencingClient = LocationServices.getGeofencingClient(context)
        geofencingClient.removeGeofences(listOf(identifier)).run {
            addOnSuccessListener {
                Log.d("GeofencingService", "Geofence removed: $identifier")
                removeGeofenceFromPreferences(context, identifier)
            }
            addOnFailureListener {
                Log.e("GeofencingService", "Failed to remove geofence: $identifier", it)
            }
        }
    }

    fun getRegisteredGeofences(context: Context): List<Map<String, Any>> {
        val prefs = context.getSharedPreferences("Geofences", Context.MODE_PRIVATE)
        val allGeofences = prefs.all
        return allGeofences.mapNotNull { (key, value) ->
            val parts = (value as? String)?.split(",") ?: return@mapNotNull null
            if (parts.size != 3) return@mapNotNull null
            val lat = parts[0].toDoubleOrNull()
            val lng = parts[1].toDoubleOrNull()
            val rad = parts[2].toDoubleOrNull()
            if (lat == null || lng == null || rad == null) return@mapNotNull null
            mapOf(
                "latitude" to lat,
                "longitude" to lng,
                "radius" to rad,
                "identifier" to key
            )
        }
    }

    fun notifyGeofenceEvent(method: String, args: Map<String, Any>?) {
        val data: MutableMap<String, Any?> = mutableMapOf("method" to method)
        args?.let { data["args"] = it }

        @Suppress("UNCHECKED_CAST")
        synchronized(FlutterBackgroundServicePlugin.servicePipe) {
            if (FlutterBackgroundServicePlugin.servicePipe.hasListener()) {
                FlutterBackgroundServicePlugin.servicePipe.invoke(JSONObject(data as Map<Any?, Any?>))
            }
        }
    }

    private fun saveGeofence(
        context: Context,
        identifier: String,
        latitude: Double,
        longitude: Double,
        radius: Double
    ) {
        val prefs = context.getSharedPreferences("Geofences", Context.MODE_PRIVATE)
        prefs.edit { putString(identifier, "$latitude,$longitude,$radius") }
    }

    private fun removeGeofenceFromPreferences(context: Context, identifier: String) {
        val prefs = context.getSharedPreferences("Geofences", Context.MODE_PRIVATE)
        prefs.edit { remove(identifier) }
    }
}
