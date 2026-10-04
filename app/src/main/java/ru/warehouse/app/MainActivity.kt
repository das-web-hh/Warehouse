package ru.warehouse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import ru.warehouse.app.ui.WarehouseApp
import ru.warehouse.app.ui.WarehouseTheme
import ru.warehouse.app.ui.WarehouseViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: WarehouseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WarehouseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WarehouseApp(viewModel = viewModel)
                }
            }
        }
    }
}
