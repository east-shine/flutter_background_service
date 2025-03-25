package id.flutter.flutter_background_service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import io.flutter.plugin.common.MethodChannel

class GeofencingService(private val context: Context) {
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        intent.action = "com.google.android.gms.location.Geofence"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
    private var methodChannel: MethodChannel? = null
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("Geofences", Context.MODE_PRIVATE)

    fun setMethodChannel(channel: MethodChannel) {
        this.methodChannel = channel
    }

    fun registerGeofence(latitude: Double, longitude: Double, radius: Double, identifier: String) {
        val geofence = Geofence.Builder()
            .setRequestId(identifier)
            .setCircularRegion(latitude, longitude, radius.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        try {
            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
                addOnSuccessListener {
                    Log.d("GeofencingService", "Geofence added successfully: $identifier")
                    saveGeofence(identifier, latitude, longitude, radius)
                }
                addOnFailureListener { e ->
                    Log.e("GeofencingService", "Failed to add geofence: $identifier", e)
                }
            }
        } catch (e: SecurityException) {
            Log.e("GeofencingService", "SecurityException while adding geofence: $identifier", e)
        }
    }

    fun removeGeofence(identifier: String) {
        geofencingClient.removeGeofences(listOf(identifier)).run {
            addOnSuccessListener {
                Log.d("GeofencingService", "Geofence removed successfully: $identifier")
                removeGeofenceFromPreferences(identifier)
            }
            addOnFailureListener { e ->
                Log.e("GeofencingService", "Failed to remove geofence: $identifier", e)
            }
        }
    }

    fun getRegisteredGeofences(): List<Map<String, Any>> {
        val geofences = mutableListOf<Map<String, Any>>()
        val allGeofences = sharedPreferences.all
        for ((key, value) in allGeofences) {
            val geofenceData = value as String
            val parts = geofenceData.split(",")
            val latitude = parts[0].toDouble()
            val longitude = parts[1].toDouble()
            val radius = parts[2].toDouble()
            geofences.add(
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "radius" to radius,
                    "identifier" to key
                )
            )
        }
        return geofences
    }

    fun notifyGeofenceEvent(event: String, data: Map<String, Any>) {
        methodChannel?.invokeMethod(event, data)
    }

    private fun saveGeofence(
        identifier: String,
        latitude: Double,
        longitude: Double,
        radius: Double
    ) {
        with(sharedPreferences.edit()) {
            putString(identifier, "$latitude,$longitude,$radius")
            apply()
        }
    }

    private fun removeGeofenceFromPreferences(identifier: String) {
        with(sharedPreferences.edit()) {
            remove(identifier)
            apply()
        }
    }
}