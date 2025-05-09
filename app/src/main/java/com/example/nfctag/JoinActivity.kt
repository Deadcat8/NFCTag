package com.example.nfctag

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcEvent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.nfctag.databinding.ActivityJoinBinding

class JoinActivity : AppCompatActivity(), View.OnClickListener, NfcAdapter.CreateNdefMessageCallback {

    private lateinit var nfcAdapter: NfcAdapter
    lateinit var binding : ActivityJoinBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJoinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnJoinMenuOptionBack.setOnClickListener(this)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.btn_JoinMenuOptionBack ->{
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
        }
    }

    override fun createNdefMessage(event: NfcEvent?): NdefMessage {
        val deviceId = DeviceIdManager.getOrCreateDeviceId(this)
        val ndefRecord = NdefRecord.createMime("application/vnd.com.example.myapp", deviceId.toByteArray())
        return NdefMessage(arrayOf(ndefRecord))
    }
}