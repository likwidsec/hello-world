package com.example.locationsharing

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth

/**
 * A foreground service that reads this phone's location on a steady interval and
 * publishes each position to Firebase, so the other person's map stays current
 * even while this app is in the background or the screen is off.
 *
 * Start it with [startSharing] and stop it with [stopSharing].
 */
class LocationSharingService : LifecycleService() {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var pairSettings: PairSettings
    private var locationRepository: LocationRepository? = null

    private val locationUpdateCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val latestLocation = locationResult.lastLocation ?: return
            publishLocation(latestLocation.latitude,
                latestLocation.longitude,
                latestLocation.accuracy)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        pairSettings = PairSettings(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundWithNotification()
        beginLocationUpdates()
        // Restart the service if Android kills it while sharing is meant to be on.
        return START_STICKY
    }

    private fun beginLocationUpdates() {
        val hasForegroundLocationPermission = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasForegroundLocationPermission) {
            stopSelf()
            return
        }

        // Make sure we have a Firebase identity before writing anything.
        FirebaseAuth.getInstance().signInAnonymously().addOnSuccessListener {
            locationRepository = LocationRepository(pairSettings.pairCode)
            requestLocationUpdatesFromDevice()
        }
    }

    private fun requestLocationUpdatesFromDevice() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LOCATION_UPDATE_INTERVAL_MILLIS
        )
            .setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_INTERVAL_MILLIS)
            .build()

        try {
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationUpdateCallback,
                mainLooper
            )
        } catch (securityException: SecurityException) {
            stopSelf()
        }
    }

    private fun publishLocation(latitude: Double, longitude: Double, accuracyInMeters: Float) {
        val signedInUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val point = LocationPoint(
            latitude = latitude,
            longitude = longitude,
            accuracyInMeters = accuracyInMeters,
            recordedAtEpochMillis = System.currentTimeMillis()
        )
        locationRepository?.publishLiveLocation(signedInUserId, point)
    }

    private fun startForegroundWithNotification() {
        createNotificationChannelIfNeeded()

        val openAppIntent = Intent(this, MapActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.sharing_notification_title))
            .setContentText(getString(R.string.sharing_notification_text))
            .setSmallIcon(R.drawable.app_icon)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.sharing_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationUpdateCallback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "location_sharing_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LOCATION_UPDATE_INTERVAL_MILLIS = 15_000L
        private const val FASTEST_LOCATION_UPDATE_INTERVAL_MILLIS = 5_000L

        /** Turn location sharing on. */
        fun startSharing(context: Context) {
            val serviceIntent = Intent(context, LocationSharingService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        }

        /** Turn location sharing off. */
        fun stopSharing(context: Context) {
            context.stopService(Intent(context, LocationSharingService::class.java))
        }
    }
}
