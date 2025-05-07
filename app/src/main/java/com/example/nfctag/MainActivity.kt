package com.example.nfctag

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.nfctag.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() , View.OnClickListener {
    // Binds Activity to XML
    lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()

        binding.btnMenuOptionHost.setOnClickListener(this)
        binding.btnMenuOptionJoin.setOnClickListener(this)
        binding.btnMenuOptionAboutUs.setOnClickListener(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onClick(v: View?) {

        when(v?.id){
            R.id.btn_MenuOptionHost ->{
                val intent = Intent(this, HostActivity::class.java)
                startActivity(intent)
            }
            R.id.btn_MenuOptionJoin ->{
                val intent = Intent(this, JoinActivity::class.java)
                startActivity(intent)
            }
            R.id.btn_MenuOptionAboutUs ->{
                val intent = Intent(this, AboutusActivity::class.java)
                startActivity(intent)
            }
        }
    }
}