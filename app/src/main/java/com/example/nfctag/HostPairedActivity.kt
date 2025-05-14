package com.example.nfctag

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.example.nfctag.databinding.ActivityHostPairedBinding


class HostPairedActivity : AppCompatActivity(), View.OnClickListener {
    private val showDialog = mutableStateOf(false)

    lateinit var binding: ActivityHostPairedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHostPairedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnHostPairedMenuOptionCancel.setOnClickListener(this)
        val composeView = findViewById<ComposeView>(R.id.Host_compose_view)
        composeView.setContent {
            AlertContent(showDialog)
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btn_HostPairedMenuOptionCancel -> {
                showDialog.value = true
            }
        }
    }

    private fun cancelGame(){
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    @Composable
    fun AlertContent(showDialog: MutableState<Boolean>) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (showDialog.value) {
                AlertDialog(
                    onDismissRequest = { showDialog.value = false },
                    title = { Text("Warning") },
                    text = { Text("Are you sure you want to continue? This will cancel the game and lose all current player data.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDialog.value = false
                            cancelGame()
                        }) {
                            Text("Yes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialog.value = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}



