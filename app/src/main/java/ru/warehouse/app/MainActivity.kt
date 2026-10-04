package ru.warehouse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.warehouse.app.ui.WarehouseApp
import ru.warehouse.app.ui.WarehouseTheme
import ru.warehouse.app.ui.WarehouseViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application
        setContent {
            val model: WarehouseViewModel = viewModel(factory = WarehouseViewModel.factory(app))
            val state = model.state.collectAsStateForWarehouse()
            WarehouseTheme(darkTheme = state.darkTheme) {
                WarehouseApp(viewModel = model, state = state)
            }
        }
    }
}