package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InventoryRepository(
    private val productDao: ProductDao,
    private val saleDao: SaleDao,
    private val auditLogDao: AuditLogDao,
    private val webApiRepository: WebApiRepository = WebApiRepository()
) {
    private val _availableProducts = MutableStateFlow<List<Product>>(emptyList())
    val availableProducts: Flow<List<Product>> = _availableProducts.asStateFlow()

    private val _stockSyncStatus = MutableStateFlow("loading")
    val stockSyncStatus: Flow<String> = _stockSyncStatus.asStateFlow()

    val recentLogs: Flow<List<AuditLog>> = auditLogDao.getRecentLogs()

    init {
        // We let the ViewModel trigger the first refresh to keep control of initialization order.
    }

    suspend fun refreshProducts() {
        _stockSyncStatus.value = "loading"

        // 1. First, try to sync any queued offline products
        try {
            val unsyncedProducts = productDao.getUnsyncedProducts()
            var productsSynced = 0
            for (product in unsyncedProducts) {
                val success = webApiRepository.saveProduct(product)
                if (success) {
                    productDao.insertProduct(product.copy(isSynced = true))
                    productsSynced++
                }
            }

            // 2. Next, try to sync any queued offline sales
            val unsyncedSales = saleDao.getUnsyncedSales()
            var salesSynced = 0
            for (sale in unsyncedSales) {
                val cartArray = org.json.JSONArray(sale.serializedItems)
                val itemCodes = mutableListOf<String>()
                var totalWeight = 0.0
                for (i in 0 until cartArray.length()) {
                    val cartItem = cartArray.getJSONObject(i)
                    val productObj = cartItem.getJSONObject("product")
                    itemCodes.add(productObj.getString("itemCode"))
                    totalWeight += productObj.getDouble("weightGrams")
                }
                val deleteSuccess = webApiRepository.deleteProducts(itemCodes)
                val invoiceSuccess = webApiRepository.saveInvoice(sale, totalWeight)
                if (deleteSuccess && invoiceSuccess) {
                    saleDao.insertSale(sale.copy(isSynced = true))
                    salesSynced++
                }
            }

            if (productsSynced > 0 || salesSynced > 0) {
                auditLogDao.insertLog(
                    AuditLog(
                        type = "stock.sync_queue",
                        message = "Auto-synced offline queue: $productsSynced products, $salesSynced sales"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("InventoryRepo", "Error syncing offline queue", e)
        }

        // Website API is the only reliable path on Android.
        // Direct MongoDB fails on phones with "Unable to resolve host" (DNS).
        try {
            Log.d("InventoryRepo", "Refreshing products...")
            val apiProducts = webApiRepository.getAvailableProducts()
            if (apiProducts != null) {
                Log.d("InventoryRepo", "Loaded ${apiProducts.size} products from API")
                syncLocalProducts(apiProducts)
                _availableProducts.value = apiProducts
                _stockSyncStatus.value = "api:${apiProducts.size}"
                auditLogDao.insertLog(
                    AuditLog(type = "stock.sync", message = "Loaded ${apiProducts.size} items from Vercel API")
                )
                return
            } else {
                Log.w("InventoryRepo", "API returned null products. Error: ${webApiRepository.lastError}")
            }
        } catch (e: Exception) {
            Log.e("InventoryRepo", "Web API fetch failed with exception", e)
        }

        // Offline cache — only after all live API attempts fail
        val local = productDao.getAvailableProducts().firstOrNull()
        if (!local.isNullOrEmpty()) {
            _availableProducts.value = local
            val reason = webApiRepository.lastError ?: "unknown error"
            _stockSyncStatus.value = "cache:${local.size}|$reason"
            return
        }

        _availableProducts.value = emptyList()
        _stockSyncStatus.value = "error:${webApiRepository.lastError ?: "Set WEB_API_URL in app details/.env to your Vercel deployment URL, then rebuild the app."}"
    }

    private suspend fun syncLocalProducts(products: List<Product>) {
        if (products.isNotEmpty()) {
            productDao.insertProducts(products)
        }
    }

    suspend fun getProductByCode(code: String): Product? {
        productDao.getProduct(code)?.let { return it }
        return _availableProducts.value.find { it.itemCode == code }
    }

    suspend fun saveProduct(product: Product) {
        var saved = false
        try {
            saved = webApiRepository.saveProduct(product)
        } catch (e: Exception) {
            Log.e("InventoryRepo", "saveProduct API failed", e)
        }
        val productToSave = product.copy(isSynced = saved)
        productDao.insertProduct(productToSave)
        auditLogDao.insertLog(
            AuditLog(
                type = "product.created",
                message = if (saved) "Created product ${product.itemCode} via API" else "Created product ${product.itemCode} locally (offline queue)"
            )
        )
        refreshProducts()
    }

    suspend fun checkout(sale: Sale, itemCodes: List<String>, totalWeight: Double) {
        var synced = false
        try {
            val deleteSuccess = webApiRepository.deleteProducts(itemCodes)
            val invoiceSuccess = webApiRepository.saveInvoice(sale, totalWeight)
            synced = deleteSuccess && invoiceSuccess
        } catch (e: Exception) {
            Log.e("InventoryRepo", "checkout API failed", e)
        }
        val saleToSave = sale.copy(isSynced = synced)
        saleDao.insertSale(saleToSave)
        productDao.markProductsAsSold(itemCodes)
        auditLogDao.insertLog(
            AuditLog(
                type = "sale.completed",
                message = if (synced) "Completed sale ${sale.invoiceNo} with ${itemCodes.size} items" else "Completed sale ${sale.invoiceNo} locally (offline queue)"
            )
        )
        refreshProducts()
    }

    suspend fun fetchInvoices(): org.json.JSONArray? {
        return webApiRepository.getInvoices()
    }

    suspend fun generateNextItemCode(category: String): String {
        val prefixMap = mapOf(
            "Ring" to "RNG",
            "Necklace" to "NKL",
            "Bracelet" to "BRC",
            "Earrings" to "ERR",
            "Watch" to "WCH",
            "Pendant" to "PND",
            "Chain" to "CHN",
            "Bangle" to "BNG",
            "Coin" to "CON",
            "Silver" to "SLV",
            "Custom" to "GEN"
        )
        val prefix = prefixMap[category] ?: "GEN"

        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val dateString = dateFormat.format(Date())

        val todayStart = SimpleDateFormat("yyyyMMdd", Locale.US).let { f ->
            f.parse(f.format(Date()))?.time ?: 0L
        }

        val count = productDao.getCountForCategoryToday(category, todayStart)
        val sequence = String.format(Locale.US, "%03d", count + 1)

        return "$prefix-$dateString-$sequence"
    }
}
