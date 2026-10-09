package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM_Service"
        const val CHANNEL_ID = "black_anime_notifications"
        const val CHANNEL_NAME = "إشعارات BLACK ANIME"
        const val PREFS_NAME = "black_anime_fcm_prefs"
        const val KEY_FCM_TOKEN = "fcm_token"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"

        fun isAppNotificationsEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        }

        fun setAppNotificationsEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
            try {
                if (enabled) {
                    FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                    FirebaseMessaging.getInstance().subscribeToTopic("anime_updates")
                } else {
                    FirebaseMessaging.getInstance().unsubscribeFromTopic("all_users")
                    FirebaseMessaging.getInstance().unsubscribeFromTopic("anime_updates")
                    NotificationManagerCompat.from(context).cancelAll()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update FCM topic subscription: ${e.message}")
            }
        }

        fun getSavedToken(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_FCM_TOKEN, "") ?: ""
        }

        fun saveToken(context: Context, token: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (notificationManager != null) {
                    val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                    if (existingChannel == null) {
                        val channel = NotificationChannel(
                            CHANNEL_ID,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "إشعارات الحلقات والأنميات والتحديثات لتطبيق BLACK ANIME"
                            enableLights(true)
                            lightColor = ContextCompat.getColor(context, R.color.notification_accent)
                            enableVibration(true)
                            setShowBadge(true)
                        }
                        notificationManager.createNotificationChannel(channel)
                        Log.d(TAG, "Notification channel created: $CHANNEL_ID")
                    }
                }
            }
        }

        fun showNotification(
            context: Context,
            title: String,
            body: String,
            page: String,
            firebaseId: String,
            episode: String,
            url: String,
            action: String,
            bitmap: Bitmap?,
            contentTitle: String = ""
        ) {
            if (!isAppNotificationsEnabled(context)) {
                Log.d(TAG, "Notifications are disabled in app settings. Skipping notification display.")
                return
            }
            createNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("fcm_page", page)
                putExtra("page", page)
                putExtra("target_page", page)
                putExtra("screen", page)
                putExtra("fcm_firebaseId", firebaseId)
                putExtra("firebaseId", firebaseId)
                putExtra("id", firebaseId)
                putExtra("contentId", firebaseId)
                putExtra("animeId", firebaseId)
                putExtra("movieId", firebaseId)
                putExtra("fcm_episode", episode)
                putExtra("episode", episode)
                putExtra("episodeIndex", episode)
                putExtra("episodeNumber", episode)
                putExtra("ep", episode)
                putExtra("fcm_url", url)
                putExtra("url", url)
                putExtra("link", url)
                putExtra("fcm_action", action)
                putExtra("action", action)
                putExtra("fcm_title", title)
                putExtra("title", title)
                putExtra("contentTitle", contentTitle)
                putExtra("fcm_contentTitle", contentTitle)
                putExtra("animeTitle", contentTitle)
                putExtra("from_notification", true)
            }

            val requestCode = (System.currentTimeMillis() % 100000).toInt()
            val pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val finalTitle = title.ifBlank { NotificationConfig.title.ifBlank { "BLACK ANIME" } }
            val finalBody = body.ifBlank { NotificationConfig.message }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setContentTitle(finalTitle)
                .setContentText(finalBody)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)

            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setSummaryText(finalBody)
                )
            } else if (finalBody.isNotBlank()) {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(finalBody))
            }

            try {
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.notify(requestCode, builder.build())
                Log.d(TAG, "Notification displayed: $finalTitle (target: $firebaseId ep: $episode)")
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing notification permission: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display notification: ${e.message}", e)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token received: $token")
        saveToken(applicationContext, token)

        // Automatically subscribe device to global topics for broadcasting
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Subscribed successfully to topic: all_users")
                    }
                }
            FirebaseMessaging.getInstance().subscribeToTopic("anime_updates")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to subscribe to topics", e)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // 1. Extract Title & Body
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: remoteMessage.data["fcm_title"]
            ?: remoteMessage.data["heading"]
            ?: NotificationConfig.title

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["message"]
            ?: remoteMessage.data["fcm_body"]
            ?: NotificationConfig.message

        // 2. Extract Navigation & Action Data (supports anime, episode, page, URL, action)
        val page = remoteMessage.data["page"]
            ?: remoteMessage.data["fcm_page"]
            ?: remoteMessage.data["target_page"]
            ?: remoteMessage.data["screen"]
            ?: ""

        val firebaseId = remoteMessage.data["firebaseId"]
            ?: remoteMessage.data["id"]
            ?: remoteMessage.data["contentId"]
            ?: remoteMessage.data["animeId"]
            ?: remoteMessage.data["movieId"]
            ?: remoteMessage.data["fcm_firebaseId"]
            ?: ""

        val contentTitle = remoteMessage.data["contentTitle"]
            ?: remoteMessage.data["fcm_contentTitle"]
            ?: remoteMessage.data["animeTitle"]
            ?: remoteMessage.data["movieTitle"]
            ?: remoteMessage.data["name"]
            ?: ""

        val episode = remoteMessage.data["episode"]
            ?: remoteMessage.data["episodeIndex"]
            ?: remoteMessage.data["ep"]
            ?: remoteMessage.data["episodeNumber"]
            ?: remoteMessage.data["fcm_episode"]
            ?: ""

        val url = remoteMessage.data["url"]
            ?: remoteMessage.data["link"]
            ?: remoteMessage.data["fcm_url"]
            ?: ""

        val action = remoteMessage.data["action"]
            ?: remoteMessage.data["fcm_action"]
            ?: (if (episode.isNotBlank()) "play" else "")

        // 3. Extract Optional Image URL
        val imageUrl = remoteMessage.notification?.imageUrl?.toString()
            ?: remoteMessage.data["image"]
            ?: remoteMessage.data["image_url"]
            ?: remoteMessage.data["poster"]
            ?: remoteMessage.data["fcm_image"]

        // 4. Download image if provided
        val bitmap = if (!imageUrl.isNullOrBlank()) {
            downloadBitmap(imageUrl)
        } else {
            null
        }

        // 5. Display the notification
        showNotification(applicationContext, title, body, page, firebaseId, episode, url, action, bitmap, contentTitle)
    }

    private fun downloadBitmap(imageUrl: String): Bitmap? {
        return try {
            val url = URL(imageUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                doInput = true
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                connect()
            }
            val input: InputStream = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            Log.w(TAG, "Could not download notification image: ${e.message}")
            null
        }
    }
}
