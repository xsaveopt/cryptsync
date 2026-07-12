package io.github.xsaveopt.cryptsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.xsaveopt.cryptsync.ui.CryptSyncApp
import io.github.xsaveopt.cryptsync.ui.theme.CryptSyncTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CryptSyncTheme {
                CryptSyncApp()
            }
        }
    }
}
