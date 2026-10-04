package ru.warehouse.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ru.warehouse.app.data.WarehouseState

@Composable
fun WarehouseViewModel.collectAsStateForWarehouse(): WarehouseState {
    val current by state.collectAsState()
    return current
}