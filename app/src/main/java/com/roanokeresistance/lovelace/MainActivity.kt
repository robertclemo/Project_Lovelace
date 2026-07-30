package com.roanokeresistance.lovelace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.roanokeresistance.lovelace.ui.theme.LovelaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LovelaceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LovelaceApp()
                }
            }
        }
    }
}
