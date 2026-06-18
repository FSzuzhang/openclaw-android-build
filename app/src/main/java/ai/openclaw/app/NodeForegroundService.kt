package ai.openclaw.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Foreground service that keeps the Android node connection visible to the OS. */
class NodeForegroundService : Service() {
  override fun onCreate() {
    super.onCreate()
    ensureChannel()
    startForeground(NOTIFICATION_ID, buildNotification(title = "OpenClaw Node", text = "Connected"))
  }

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        (application as NodeApp).peekRuntime()?.disconnect()
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_SET_VOICE_CAPTURE_MODE -> {
        startForeground(NOTIFICATION_ID, buildNotification(title = "OpenClaw Node", text = "Voice mode active"))
      }
    }
    return START_STICKY
  }

  override fun onBind(intent: Intent?) = null

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = getSystemService(NotificationManager::class.java)
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        "Connection",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "OpenClaw node connection status"
        setShowBadge(false)
      }
    manager.createNotificationChannel(channel)
  }

  private fun buildNotification(
    title: String,
    text: String,
  ): Notification {
    val immutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    val launchIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val launchPending =
      PendingIntent.getActivity(this, 1, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag)

    val stopIntent = Intent(this, NodeForegroundService::class.java).setAction(ACTION_STOP)
    val stopPending =
      PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag)

    return NotificationCompat
      .Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(text)
      .setContentIntent(launchPending)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .addAction(0, "Disconnect", stopPending)
      .build()
  }

  companion object {
    private const val CHANNEL_ID = "connection"
    private const val NOTIFICATION_ID = 1

    private const val ACTION_STOP = "ai.openclaw.app.action.STOP"
    private const val ACTION_SET_VOICE_CAPTURE_MODE = "ai.openclaw.app.action.SET_VOICE_CAPTURE_MODE"
    private const val EXTRA_VOICE_CAPTURE_MODE = "ai.openclaw.app.extra.VOICE_CAPTURE_MODE"

    fun start(context: Context) {
      val intent = Intent(context, NodeForegroundService::class.java)
      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
      val intent = Intent(context, NodeForegroundService::class.java).setAction(ACTION_STOP)
      context.startService(intent)
    }

    fun setVoiceCaptureMode(
      context: Context,
      mode: VoiceCaptureMode,
    ) {
      val intent =
        Intent(context, NodeForegroundService::class.java)
          .setAction(ACTION_SET_VOICE_CAPTURE_MODE)
          .putExtra(EXTRA_VOICE_CAPTURE_MODE, mode.name)
      ContextCompat.startForegroundService(context, intent)
    }
  }
}

internal fun voiceNotificationSuffix(
  mode: VoiceCaptureMode,
  manualMicEnabled: Boolean,
  manualMicListening: Boolean,
  talkListening: Boolean,
  talkSpeaking: Boolean,
): String =
  when (mode) {
    VoiceCaptureMode.TalkMode ->
      when {
        talkSpeaking -> " - Talk: Speaking"
        talkListening -> " - Talk: Listening"
        else -> " - Talk: On"
      }
    VoiceCaptureMode.ManualMic ->
      if (manualMicEnabled) {
        if (manualMicListening) " - Mic: Listening" else " - Mic: Pending"
      } else {
        ""
      }
    VoiceCaptureMode.Off -> ""
  }