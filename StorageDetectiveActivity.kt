package com.yourapp.storageapp.storage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourapp.storageapp.ui.theme.StorageDetectiveTheme

class StorageDetectiveActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StorageDetectiveTheme {
                val vm: StorageViewModel = viewModel()
                StorageDetectiveScreen(vm)
            }
        }
    }
}
