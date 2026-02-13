package com.oogley.billbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.oogley.billbot.ui.navigation.BillBotNavHost
import com.oogley.billbot.ui.theme.BillBotTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BillBotTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BillBotNavHost()
                }
            }
        }
    }
}
