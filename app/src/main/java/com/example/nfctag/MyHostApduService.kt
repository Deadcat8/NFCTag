package com.example.nfctag

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat

class MyHostApduService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        triggerNotification("NFC Scan Detected", "You were scanned by a device")
        val deviceId = DeviceIdManager.getOrCreateDeviceId(this)
        return deviceId.toByteArray(Charsets.UTF_8)
    }

    override fun onDeactivated(reason: Int) {
        TODO("Not yet implemented")
    }

    private fun triggerNotification(title: String, message: String) {
        val channelId = "nfc_detected_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "NFC Events", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(1001, notification)
    }
}
