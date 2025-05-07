package com.example.nfctag

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.nfctag.databinding.ActivityAboutusBinding

class AboutusActivity : AppCompatActivity(), View.OnClickListener {
    lateinit var binding : ActivityAboutusBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAboutUsMenuOptionBack.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.btn_AboutUsMenuOptionBack ->{
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
        }
    }
}