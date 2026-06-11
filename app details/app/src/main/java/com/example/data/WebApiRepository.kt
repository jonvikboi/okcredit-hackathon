package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Loads stock from the Vercel-hosted website backend.
 * MongoDB is server-side only — Android cannot resolve Atlas shard hostnames.
 */
class WebApiRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    var lastError: String? = null
        private set

    private fun candidateBaseUrls(): List<String> {
        val configured = BuildConfig.WEB_API_URL.trim().removeSurrounding("\"").trimEnd('/')
        if (configured.isNotEmpty() && configured != "https://your-app.vercel.app") {
            return listOf(configured)
        }
        return if (BuildConfig.DEBUG) listOf("http://10.0.2.2:5173") else emptyList()
    }

    suspend fun getAvailableProducts(): List<Product>? = withContext(Dispatchers.IO) {
        lastError = null
        val bases = candidateBaseUrls()
        if (bases.isEmpty()) {
            lastError = "WEB_API_URL is not set. Add your Vercel URL to app details/.env and rebuild the app."
            return@withContext null
        }

        for (baseUrl in bases) {
            fetchViaGet("$baseUrl/api/products", baseUrl)?.let { return@withContext it }
            fetchViaGet("$baseUrl/api/stock", baseUrl)?.let { return@withContext it }
        }

        if (lastError == null) {
            lastError = "Live sync failed (HTTP 405). Redeploy okcreditproject-two.vercel.app from the latest code."
        }
        Log.w("WebApiRepo", lastError!!)
        null
    }

    suspend fun saveProduct(product: Product): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("id", product.itemCode)
            put("itemCode", product.itemCode)
            put("name", product.name)
            put("category", product.category)
            put("metal", product.metal)
            put("purity", product.purity)
            put("weight", product.weightGrams)
            put("weightGrams", product.weightGrams)
            put("makingCharge", product.makingChargePercent / 100.0)
            put("makingChargePercent", product.makingChargePercent)
            put("fixedValue", product.fixedValue)
            put("description", product.description)
            put("status", product.status)
            put("createdAt", product.createdAt)
        }

        for (baseUrl in candidateBaseUrls()) {
            try {
                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url("$baseUrl/api/products").post(body).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext true
                    lastError = "$baseUrl returned HTTP ${response.code}"
                }
            } catch (e: Exception) {
                lastError = "$baseUrl: ${e.message ?: "connection failed"}"
                Log.w("WebApiRepo", "POST to $baseUrl failed", e)
            }
        }
        false
    }

    suspend fun deleteProducts(itemCodes: List<String>): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("ids", JSONArray(itemCodes))
        for (baseUrl in candidateBaseUrls()) {
            try {
                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url("$baseUrl/api/products").delete(body).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext true
                    lastError = "$baseUrl returned HTTP ${response.code}"
                }
            } catch (e: Exception) {
                lastError = "$baseUrl: ${e.message ?: "connection failed"}"
                Log.w("WebApiRepo", "DELETE to $baseUrl failed", e)
            }
        }
        false
    }

    private fun fetchViaGet(url: String, baseUrl: String): List<Product>? {
        Log.d("WebApiRepo", "GET $url")
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SunriseJewelsAndroid/1.0")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                Log.d("WebApiRepo", "GET $url -> ${response.code}")
                if (!response.isSuccessful) {
                    lastError = "API error ($url): HTTP ${response.code}"
                    return null
                }
                val products = parseProductsResponse(body, baseUrl) ?: return null
                Log.d("WebApiRepo", "Loaded ${products.size} products from $url")
                lastError = null
                products
            }
        } catch (e: Exception) {
            lastError = "$baseUrl: ${e.message ?: "connection failed"}"
            null
        }
    }

    private fun parseProductsResponse(body: String?, baseUrl: String): List<Product>? {
        if (body.isNullOrBlank()) {
            lastError = "$baseUrl returned empty response"
            return null
        }

        val json = JSONObject(body)
        if (!json.optBoolean("success", false)) {
            lastError = json.optString("error", "$baseUrl returned success=false")
            return null
        }

        val productsArray = json.optJSONArray("products") ?: JSONArray()
        val products = mutableListOf<Product>()
        for (i in 0 until productsArray.length()) {
            val item = productsArray.getJSONObject(i)
            val itemCode = item.optString("id", item.optString("itemCode", ""))
            if (itemCode.isBlank()) continue

            products.add(
                Product(
                    itemCode = itemCode,
                    name = item.optString("name", itemCode),
                    category = item.optString("category", "Custom"),
                    metal = item.optString("metal", "Gold"),
                    purity = item.optString("purity", "22K"),
                    weightGrams = item.optDouble("weight", item.optDouble("weightGrams", 0.0)),
                    makingChargePercent = normalizeMakingCharge(
                        item.optDouble("makingChargePercent", Double.NaN).takeIf { !it.isNaN() }
                            ?: item.optDouble("makingCharge", 0.0)
                    ),
                    fixedValue = item.optDouble("fixedValue", 0.0),
                    description = item.optString("description", ""),
                    status = item.optString("status", "available"),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return products.filter { it.status == "available" }
    }

    private fun normalizeMakingCharge(value: Double): Double {
        return if (value > 0 && value <= 1) value * 100 else value
    }
}
