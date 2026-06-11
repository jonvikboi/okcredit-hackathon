package com.example.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.flow.firstOrNull

class RateRepository(
    private val rateDao: RateDao,
    private val auditLogDao: AuditLogDao
) {
    val latestRates: Flow<RateSnapshot?> = rateDao.getLatestRatesFlow()
    
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS) // Keep connection alive
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    @Volatile
    private var isConnected = false

    fun startPolling() {
        Log.d("RateFeed", "startPolling called. isConnected=$isConnected")
        if (isConnected) return
        connectWebSocket()
    }

    private fun connectWebSocket() {
        Log.d("RateFeed", "Attempting to connect to WebSocket: ws://ambicaaspot.com:1001/bullion...")
        val request = Request.Builder()
            .url("ws://ambicaaspot.com:1001/bullion?user=ambicaa&auth=1&type=web")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                isConnected = true
                Log.d("RateFeed", "WebSocket Connected Successfully")
                
                // Handshake
                val handshake = "{\"protocol\":\"json\",\"version\":1}\u001E"
                webSocket.send(handshake)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Log.d("RateFeed", "Raw Message: $text")
                
                // SignalR: After the version handshake, the server sends an empty JSON object {} 
                // to indicate it's ready for invocations.
                if (text.contains("{}")) {
                    val subMsg = JSONObject().apply {
                        put("type", 1)
                        put("invocationId", "0")
                        put("target", "client")
                        put("arguments", JSONArray().put("ambicaa"))
                    }.toString() + "\u001E"
                    Log.d("RateFeed", "Sending subscription message: $subMsg")
                    webSocket.send(subMsg)
                }
                
                processMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                Log.d("RateFeed", "WebSocket Closing: $reason")
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                Log.d("RateFeed", "WebSocket Closed: $reason")
                isConnected = false
                retryConnection()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                isConnected = false
                Log.e("RateFeed", "WebSocket Failure: ${t.message}")
                retryConnection()
            }
            
            private fun retryConnection() {
                scope.launch {
                    delay(5000)
                    if (!isConnected) {
                        Log.d("RateFeed", "Retrying connection...")
                        connectWebSocket()
                    }
                }
            }
        })
    }

    private fun processMessage(text: String) {
        try {
            Log.v("RateFeed", "Processing: $text")
            val messages = text.split("\u001E").filter { it.isNotBlank() }
            if (messages.isEmpty()) {
                Log.w("RateFeed", "No messages found after split")
            }
            for (msgStr in messages) {
                val json = JSONObject(msgStr)
                // Log the type of message we get
                val type = json.optInt("type", -1)
                val target = json.optString("target", "")
                Log.d("RateFeed", "Msg Type: $type, Target: $target")

                if (json.has("target") && (json.getString("target") == "workerPublish" || json.getString("target") == "workerPublishCoin" || json.getString("target") == "symbolDetails")) {
                    val arguments = json.optJSONArray("arguments") ?: continue
                    val targetObj = arguments.optJSONObject(0) ?: continue
                    val encoded = targetObj.optString("Data", "")
                    
                    if (encoded.isNotEmpty()) {
                        val decodedBytes = Base64.decode(encoded, Base64.DEFAULT)
                        val gis = GZIPInputStream(ByteArrayInputStream(decodedBytes))
                        val decompressedStr = gis.bufferedReader().use { it.readText() }
                        
                        Log.v("RateFeed", "Decompressed Payload: $decompressedStr")
                        val payloadArray = JSONArray(decompressedStr)
                        updateRatesFromPayload(payloadArray)
                    } else {
                        Log.w("RateFeed", "Data field is empty in message")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RateFeed", "Error processing message: ${e.message}", e)
        }
    }
    
    private fun updateRatesFromPayload(payload: JSONArray) {
        var goldAsk: Double? = null
        var silverAsk: Double? = null
        
        for (i in 0 until payload.length()) {
            val item = payload.optJSONObject(i) ?: continue
            val symbol = item.optString("symbol", "").uppercase()
            val ask = item.optDouble("Ask", -1.0)
            
            if (ask > 0) {
                // Expanded symbol matching for gold and silver
                if (symbol.contains("GOLD") || symbol == "117574919" || symbol == "119445255" || symbol == "GC") {
                    goldAsk = ask 
                } else if (symbol.contains("SILVER") || symbol == "118822407" || symbol == "120761607" || symbol == "SI") {
                    silverAsk = ask
                }
            }
        }
        
        Log.d("RateFeed", "Received update: Gold=$goldAsk, Silver=$silverAsk")

        if (goldAsk != null || silverAsk != null) {
            scope.launch {
                val current = rateDao.getLatestRates()
                
                // Do not override if manual mode is enabled
                if (current?.isOverride == true) {
                    Log.d("RateFeed", "Manual override active, ignoring feed")
                    return@launch
                }
                
                val gold24kPerGram = goldAsk?.let { Math.round(it / 10).toDouble() } ?: current?.gold24kPerGram ?: 0.0
                val gold22kPerGram = if (goldAsk != null) Math.round(gold24kPerGram * (22.0 / 24.0)).toDouble() else current?.gold22kPerGram ?: 0.0
                val gold18kPerGram = if (goldAsk != null) Math.round(gold24kPerGram * (18.0 / 24.0)).toDouble() else current?.gold18kPerGram ?: 0.0
                val silverPerGram = silverAsk?.let { Math.round((it / 1000.0) * 100) / 100.0 } ?: current?.silverPerGram ?: 0.0
                
                val newSnapshot = RateSnapshot(
                    gold24kPerGram = gold24kPerGram,
                    gold22kPerGram = gold22kPerGram,
                    gold18kPerGram = gold18kPerGram,
                    silverPerGram = silverPerGram,
                    source = "ambicaa",
                    timestamp = System.currentTimeMillis(),
                    isStale = false,
                    isOverride = false
                )
                
                Log.d("RateFeed", "Inserting new rates to DB: $newSnapshot")
                rateDao.insertRates(newSnapshot)
                auditLogDao.insertLog(AuditLog(type = "rate.feed", message = "Auto-updated from live feed"))
            }
        }
    }

    suspend fun setManualOverride(gold24K: Double, silver: Double) {
        val gold22K = Math.round(gold24K * (22.0 / 24.0)).toDouble()
        val gold18K = Math.round(gold24K * (18.0 / 24.0)).toDouble()
        
        val newSnapshot = RateSnapshot(
            gold24kPerGram = gold24K,
            gold22kPerGram = gold22K,
            gold18kPerGram = gold18K,
            silverPerGram = silver,
            source = "manual",
            timestamp = System.currentTimeMillis(),
            isStale = false,
            isOverride = true
        )
        rateDao.insertRates(newSnapshot)
        auditLogDao.insertLog(AuditLog(type = "rate.override.enabled", message = "Manual rate override set"))
    }
    
    suspend fun disableOverride() {
        val current = rateDao.getLatestRates()
        if (current != null && current.isOverride) {
            val updated = current.copy(isOverride = false)
            rateDao.insertRates(updated)
            auditLogDao.insertLog(AuditLog(type = "rate.override.disabled", message = "Manual rate override disabled"))
        }
    }

    suspend fun ensureDefaultRates() {
        if (rateDao.getLatestRates() == null) {
            val default = RateSnapshot(
                gold24kPerGram = 7777.0, // Distinct value for tracking
                gold22kPerGram = 7129.0,
                gold18kPerGram = 5833.0,
                silverPerGram = 99.0, // Distinct value
                source = "fallback",
                isStale = true,
                isOverride = false,
                timestamp = System.currentTimeMillis()
            )
            rateDao.insertRates(default)
        }
    }
}
