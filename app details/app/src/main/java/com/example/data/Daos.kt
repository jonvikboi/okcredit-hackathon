package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE status = 'available' ORDER BY createdAt DESC")
    fun getAvailableProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE itemCode = :itemCode LIMIT 1")
    suspend fun getProduct(itemCode: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Update
    suspend fun updateProduct(product: Product)
    
    @Query("UPDATE products SET status = 'sold' WHERE itemCode IN (:itemCodes)")
    suspend fun markProductsAsSold(itemCodes: List<String>)
    
    @Query("SELECT COUNT(*) FROM products WHERE category = :category AND createdAt > :todayStart")
    suspend fun getCountForCategoryToday(category: String, todayStart: Long): Int
}

@Dao
interface RateDao {
    @Query("SELECT * FROM rates WHERE key = 'latest_rates' LIMIT 1")
    fun getLatestRatesFlow(): Flow<RateSnapshot?>
    
    @Query("SELECT * FROM rates WHERE key = 'latest_rates' LIMIT 1")
    suspend fun getLatestRates(): RateSnapshot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRates(rateSnapshot: RateSnapshot)
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    fun getSales(): Flow<List<Sale>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: Sale)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<AuditLog>>

    @Insert
    suspend fun insertLog(log: AuditLog)
}
