package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "products")
@Serializable
data class Product(
    @PrimaryKey
    val itemCode: String,
    val name: String,
    val category: String,
    val metal: String,
    val purity: String,
    val weightGrams: Double,
    val makingChargePercent: Double,
    val fixedValue: Double,
    val description: String,
    val status: String = "available", // available, sold, archived
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "rates")
data class RateSnapshot(
    @PrimaryKey val key: String = "latest_rates",
    val gold24kPerGram: Double,
    val gold22kPerGram: Double,
    val gold18kPerGram: Double,
    val silverPerGram: Double,
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStale: Boolean = false,
    val isOverride: Boolean = false
)

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey
    val invoiceNo: String,
    val customerName: String,
    val customerPhone: String,
    val serializedItems: String, // JSON serialization of cart items
    val subtotal: Double,
    val gst: Double,
    val total: Double,
    val paymentMethod: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class CartItem(
    val product: Product,
    val ratePerGram: Double,
    val metalValue: Double,
    val makingCharge: Double,
    val subtotal: Double,
    val gst: Double,
    val total: Double
)
