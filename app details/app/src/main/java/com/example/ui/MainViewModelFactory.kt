package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.InventoryRepository
import com.example.data.RateRepository

class MainViewModelFactory(
    private val appDatabase: AppDatabase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val inventoryRepository = InventoryRepository(appDatabase.productDao(), appDatabase.saleDao(), appDatabase.auditLogDao())
            val rateRepository = RateRepository(appDatabase.rateDao(), appDatabase.auditLogDao())
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(inventoryRepository, rateRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
