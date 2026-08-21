package com.example.epubnoveltranslator.ui.screens.conversation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.epubnoveltranslator.MainActivity
import com.example.epubnoveltranslator.R

class TranslationForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val chapterTitle = intent?.getStringExtra(EXTRA_CHAPTER_TITLE) ?: "chapter"
        val novelTitle = intent?.getStringExtra(EXTRA_NOVEL_TITLE) ?: ""

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Translating: $chapterTitle"
        val body = if (novelTitle.isNotEmpty()) novelTitle else "Translation in progress..."

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_novel_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Translation Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while a chapter is being translated in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_STOP = "com.example.epubnoveltranslator.TRANSLATION_STOP"
        const val EXTRA_CHAPTER_TITLE = "extra_chapter_title"
        const val EXTRA_NOVEL_TITLE = "extra_novel_title"
        private const val CHANNEL_ID = "translation_channel"
        private const val NOTIFICATION_ID = 1001

        fun buildStartIntent(context: Context, novelTitle: String, chapterTitle: String): Intent =
            Intent(context, TranslationForegroundService::class.java).apply {
                putExtra(EXTRA_NOVEL_TITLE, novelTitle)
                putExtra(EXTRA_CHAPTER_TITLE, chapterTitle)
            }

        fun buildStopIntent(context: Context): Intent =
            Intent(context, TranslationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
    }
}