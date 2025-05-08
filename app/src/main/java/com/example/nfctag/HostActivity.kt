package com.example.nfctag

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.nfctag.databinding.ActivityHostBinding


class HostActivity : AppCompatActivity(), View.OnClickListener {
    lateinit var binding : ActivityHostBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnHostMenuOptionBack.setOnClickListener(this)
        binding.btnHostMenuOptionStart.setOnClickListener(this)
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
}

