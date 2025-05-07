package com.example.nfctag

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.nfctag.databinding.ActivityJoinBinding

class JoinActivity : AppCompatActivity(), View.OnClickListener {
    lateinit var binding : ActivityJoinBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJoinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnJoinMenuOptionBack.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.btn_JoinMenuOptionBack ->{
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
        }
    }
}