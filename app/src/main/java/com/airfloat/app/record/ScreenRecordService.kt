package com.airfloat.app.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Surface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.airfloat.app.R
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenRecordService : Service() {

    companion object {
        private const val CHANNEL_ID = "airfloat_recording"
        private const val CHANNEL_NAME = "AirFloat Recording"

        private const val ACTION_START = "com.airfloat.app.record.START"
        private const val ACTION_STOP = "com.airfloat.app.record.STOP"

        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_WIDTH = "width"
        private const val EXTRA_HEIGHT = "height"
        private const val EXTRA_DPI = "dpi"
        private const val EXTRA_ROTATION = "rotation"

        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
            width: Int,
            height: Int,
            dpi: Int,
            rotation: Int
        ) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
                putExtra(EXTRA_WIDTH, width)
                putExtra(EXTRA_HEIGHT, height)
                putExtra(EXTRA_DPI, dpi)
                putExtra(EXTRA_ROTATION, rotation)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recordingFd: ParcelFileDescriptor? = null
    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (isRecording) return START_NOT_STICKY
                startForeground(1, buildNotification())
                startRecording(intent)
            }
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            mgr?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AirFloat")
            .setContentText("Recording session")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun startRecording(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, ActivityResultCodes.INVALID)
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
        val width = intent.getIntExtra(EXTRA_WIDTH, 0)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 0)
        val dpi = intent.getIntExtra(EXTRA_DPI, 0)
        val rotation = intent.getIntExtra(EXTRA_ROTATION, Surface.ROTATION_0)

        if (resultCode == ActivityResultCodes.INVALID || data == null || width <= 0 || height <= 0) {
            Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val mgr = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = mgr?.getMediaProjection(resultCode, data)
        if (mediaProjection == null) {
            Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        if (!prepareMediaRecorder(width, height, rotation)) {
            Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        try {
            val vd = mediaProjection?.createVirtualDisplay(
                "AirFloatRecord",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                null
            )
            if (vd == null) {
                stopRecording()
                Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
                stopSelf()
                return
            }
            virtualDisplay = vd
            mediaRecorder?.start()
            isRecording = true
        } catch (e: RuntimeException) {
            stopRecording()
            Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun prepareMediaRecorder(width: Int, height: Int, rotation: Int): Boolean {
        val recorder = MediaRecorder()
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        } catch (_: RuntimeException) {
            recorder.release()
            return false
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            .format(Date())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "AirFloat_$timestamp.mp4")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AirFloat")
            }
            val uri = contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return false
            val pfd = contentResolver.openFileDescriptor(uri, "w") ?: return false
            recordingFd = pfd
            recorder.setOutputFile(pfd.fileDescriptor)
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES
                ),
                "AirFloat"
            )
            if (!dir.exists() && !dir.mkdirs()) return false
            val file = File(dir, "AirFloat_$timestamp.mp4")
            recorder.setOutputFile(file.absolutePath)
        }

        val orientationHint = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        recorder.setOrientationHint(orientationHint)

        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(128_000)
        recorder.setAudioSamplingRate(44_100)
        recorder.setAudioChannels(1)
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        recorder.setVideoEncodingBitRate(6_000_000)
        recorder.setVideoFrameRate(30)
        recorder.setVideoSize(width, height)

        return try {
            recorder.prepare()
            mediaRecorder = recorder
            true
        } catch (e: IOException) {
            recordingFd?.close()
            recordingFd = null
            false
        }
    }

    private fun stopRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
            } catch (_: RuntimeException) {
                // ignore short/failed recordings
            }
        }
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        recordingFd?.close()
        recordingFd = null
        isRecording = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}

private object ActivityResultCodes {
    const val INVALID = Int.MIN_VALUE
}
