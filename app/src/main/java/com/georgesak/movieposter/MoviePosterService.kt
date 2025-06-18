package com.georgesak.movieposter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MoviePosterService : Service() {

    companion object {
        private const val CHANNEL_ID = "MoviePosterServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createPersistentNotification()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service is already running in foreground, nothing to do here
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Movie Poster Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createPersistentNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Movie Poster Slideshow Running")
        .setContentText("Tap to open settings")
        .setSmallIcon(R.mipmap.ic_launcher) // Use the app icon
        .setContentIntent(createSettingsPendingIntent())
        .setOngoing(true) // Makes the notification persistent
        .build()

    private fun createSettingsPendingIntent(): PendingIntent {
        val settingsIntent = Intent(this, SettingsActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}