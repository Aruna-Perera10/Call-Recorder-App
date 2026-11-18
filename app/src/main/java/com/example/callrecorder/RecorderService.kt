package com.example.callrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service: app eken start karama call state eka balanawa.
 * Call eka connect (OFFHOOK) unama record start, call eka iwara unama stop.
 */
class RecorderService : Service() {

    private lateinit var tm: TelephonyManager
    private var recorder: MediaRecorder? = null
    private var legacyListener: PhoneStateListener? = null
    private var modernCallback: Any? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        startAsForeground()
        registerCallListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        unregisterCallListener()
        stopRecording()
        super.onDestroy()
    }

    // ---------- Foreground notification ----------
    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Call recorder", NotificationManager.IMPORTANCE_LOW)
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call recorder on")
            .setContentText("Calls watch karanawa")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
    }

    // ---------- Call state listening ----------
    private fun registerCallListener() {
        if (Build.VERSION.SDK_INT >= 31) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleState(state)
            }
            modernCallback = cb
            tm.registerTelephonyCallback(mainExecutor, cb)
        } else {
            @Suppress("DEPRECATION")
            val l = object : PhoneStateListener() {
                @Deprecated("old api")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = handleState(state)
            }
            legacyListener = l
            @Suppress("DEPRECATION")
            tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun unregisterCallListener() {
        if (Build.VERSION.SDK_INT >= 31) {
            (modernCallback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
        } else {
            @Suppress("DEPRECATION")
            legacyListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
    }

    private fun handleState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> startRecording()
            TelephonyManager.CALL_STATE_IDLE -> stopRecording()
        }
    }

    // ---------- Recording ----------
    private fun startRecording() {
        if (recorder != null) return
        val dir = recordingsDir(this).apply { mkdirs() }
        val name = "call_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".m4a"
        val file = File(dir, name)

        try {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
        } catch (e: Exception) {
            e.printStackTrace()
            recorder = null
            file.delete()
        }
    }

    private fun stopRecording() {
        recorder?.let {
            try { it.stop() } catch (_: Exception) { /* too short */ }
            it.release()
        }
        recorder = null
    }

    companion object {
        private const val CHANNEL_ID = "call_recorder"
        fun recordingsDir(ctx: Context) =
            File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "CallRecordings")
    }
}
