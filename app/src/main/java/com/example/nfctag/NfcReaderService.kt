package com.example.nfctag

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.START_STICKY
import androidx.core.app.ServiceCompat.startForeground
import androidx.core.content.ContextCompat.getSystemService
import java.security.Provider

class NfcReaderService : Service() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFilter: Array<IntentFilter>
    private lateinit var techList: Array<Array<String>>


    override fun onCreate() {
        super.onCreate()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        val intent = Intent(this, HostActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        pendingIntent = PendingIntent.getService(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        intentFilter = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        techList = arrayOf(arrayOf(IsoDep::class.java.name))
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        Log.d("NfcReaderService", "Service started")

        if (intent?.action == NfcAdapter.ACTION_TAG_DISCOVERED) {
            handleNfcIntent(intent)
        }

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "nfc_reader_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "NFC Reader", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("NFC Reader Active")
            .setContentText("Waiting for nearby devices...")
            .build()
    }

    private fun handleNfcIntent(intent: Intent) {
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        val isoDep = IsoDep.get(tag) ?: return

        try {
            isoDep.connect()
            val response = isoDep.transceive(byteArrayOf(
                0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
                0x07.toByte(), 0xF0.toByte(), 0x01.toByte(), 0x02.toByte(),
                0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte()
            ))
            val deviceId = String(response, Charsets.UTF_8)
            Log.d("NFC_SERVICE", "Received Device ID: $deviceId")
            savePeerDeviceId(deviceId)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isoDep.close()
        }
    }

    private fun savePeerDeviceId(id: String) {
        val prefs = getSharedPreferences("known_devices", Context.MODE_PRIVATE)
        if (!prefs.contains(id)) {
            prefs.edit().putBoolean(id, true).apply()
            Log.d("NFC", "New device detected and stored: $id")
        } else {
            Log.d("NFC", "Known device reconnected: $id")
            //Caught!
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
