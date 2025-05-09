package com.example.nfctag

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.nfctag.databinding.ActivityHostBinding
import android.app.PendingIntent


class HostActivity : AppCompatActivity(), View.OnClickListener {
    lateinit var binding : ActivityHostBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnHostMenuOptionBack.setOnClickListener(this)
        binding.btnHostMenuOptionStart.setOnClickListener(this)

        val intent = Intent(this, NfcReaderService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onResume() {
        super.onResume()
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter != null && nfcAdapter.isEnabled) {
            // Set up foreground dispatch
            val pendingIntent = PendingIntent.getService(
                this,
                0,
                Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val filters = arrayOfNulls<IntentFilter>(1)
            filters[0] = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
            val techList = arrayOf(arrayOf(IsoDep::class.java.name))
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, techList)
        }
    }

    override fun onPause() {
        super.onPause()
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.btn_HostMenuOptionBack ->{
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
            R.id.btn_HostMenuOptionStart ->{
                val intent = Intent(this, HostPairedActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun savePeerDeviceId(id: String) {
        val prefs = getSharedPreferences("known_devices", Context.MODE_PRIVATE)
        if (!prefs.contains(id)) {
            prefs.edit().putBoolean(id, true).apply()
            Log.d("NFC", "New device ID saved: $id")
            R.string.device_list

        } else {
            Log.d("NFC", "Known device ID touched again: $id")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        tag?.let {
            val isoDep = IsoDep.get(it)
            isoDep?.let { iso ->
                try {
                    iso.connect()
                    val response = iso.transceive(
                        byteArrayOf(
                            0x00.toByte(),  // CLA
                            0xA4.toByte(),  // INS
                            0x04.toByte(),  // P1
                            0x00.toByte(),  // P2
                            0x07.toByte(),  // Length
                            0xF0.toByte(),
                            0x01.toByte(),
                            0x02.toByte(),
                            0x03.toByte(),
                            0x04.toByte(),
                            0x05.toByte(),
                            0x06.toByte() // AID
                        )
                    )
                    val receivedId = String(response, Charsets.UTF_8)
                    Log.d("NFC", "Received device ID: $receivedId")
                    savePeerDeviceId(receivedId)
                    iso.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}


