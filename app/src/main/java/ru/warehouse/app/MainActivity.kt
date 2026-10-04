package ru.warehouse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.warehouse.app.ui.ExtraViewModel
import ru.warehouse.app.ui.LoginScreen
import ru.warehouse.app.ui.WarehouseApp
import ru.warehouse.app.ui.WarehouseViewModel
import ru.warehouse.app.ui.theme.WarehouseTheme

class MainActivity : ComponentActivity() {

    private val warehouseViewModel: WarehouseViewModel by viewModels()
    private val extraViewModel: ExtraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WarehouseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var isLoggedIn by remember { mutableStateOf(false) }

                    if (!isLoggedIn) {
                        LoginScreen(
                            onLoginSuccess = { isLoggedIn = true }
                        )
                    } else {
                        WarehouseApp(
                            viewModel = warehouseViewModel,
                            extra = extraViewModel
                        )
                    }
                }
            }
        }
    }
}
