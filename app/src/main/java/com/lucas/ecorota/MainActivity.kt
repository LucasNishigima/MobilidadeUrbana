package com.lucas.ecorota

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.lucas.ecorota.ui.comparacao.ComparacaoScreen
import com.lucas.ecorota.ui.theme.EcoRotaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EcoRotaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ComparacaoScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
