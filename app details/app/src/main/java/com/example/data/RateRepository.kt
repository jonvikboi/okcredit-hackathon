package com.example.data

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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

    // ── HTTP client (used for Vercel polling) ──────────────────────────────
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // ── WebSocket client (used for ambicaaspot real-time feed) ─────────────
    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile private var isWsConnected = false
    @Volatile private var pollingJobStarted = false

    // Build the base URL from BuildConfig (same logic as WebApiRepository)
    private fun vercelBaseUrl(): String {
        val configured = BuildConfig.WEB_API_URL.trim().removeSurrounding("\"").trimEnd('/')
        return if (configured.isNotEmpty() && configured != "https://your-app.vercel.app") {
            configured
        } else {
            // Fallback: hard-code the deployment URL so the app always works
            "https://okcredit-sunrise.vercel.app"
        }
    }

    // ── Entry point ────────────────────────────────────────────────────────
    fun startPolling() {
        Log.d("RateFeed", "startPolling called. wsConnected=$isWsConnected pollingStarted=$pollingJobStarted")

        // 1. Always start the HTTP polling loop (runs every 60 s)
        if (!pollingJobStarted) {
            pollingJobStarted = true
            startVercelPollingLoop()
        }

        // 2. Also try the WebSocket for real-time ticks
        if (!isWsConnected) {
            connectWebSocket()
        }
    }

    // ── Vercel HTTP polling ────────────────────────────────────────────────

    /**
     * Polls GET /api/rates on the Vercel deployment.
     * - First call is immediate (no delay).
     * - Subsequent calls every 60 seconds.
     * - If the WebSocket is alive and has already written fresh data (<5 min old)
     *   we skip the HTTP call to avoid redundant writes.
     */
    private fun startVercelPollingLoop() {
        scope.launch {
            var firstRun = true
            while (true) {
                if (!firstRun) delay(60_000L)
                firstRun = false

                // Skip HTTP fetch if WebSocket already gave us fresh data
                val current = rateDao.getLatestRates()
                val ageMs = System.currentTimeMillis() - (current?.timestamp ?: 0L)
                if (isWsConnected && current != null && !current.isStale && ageMs < 5 * 60_000L) {
                    Log.d("RateFeed", "Skipping HTTP poll — WS data is fresh (${ageMs / 1000}s old)")
                    continue
                }

                fetchRatesFromVercel()
            }
        }
    }

    private fun fetchRatesFromVercel() {
        val url = "${vercelBaseUrl()}/api/rates"
        Log.d("RateFeed", "HTTP polling: GET $url")
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "SunriseJewelsAndroid/1.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                Log.d("RateFeed", "HTTP poll -> HTTP ${response.code}, body=${body?.take(200)}")

                if (!response.isSuccessful || body.isNullOrBlank()) {
                    Log.w("RateFeed", "HTTP poll failed or empty body")
                    return
                }

                val json = JSONObject(body)
                if (!json.optBoolean("success", false)) {
                    Log.w("RateFeed", "API returned success=false")
                    return
                }

                // The Vercel /api/rates response has fields: gold24k, gold22k, gold18k, silver
                // (per gram, in INR)
                val gold24k = json.optDouble("gold24k", -1.0)
                val gold22k = json.optDouble("gold22k", -1.0)
                val gold18k = json.optDouble("gold18k", -1.0)
                val silver  = json.optDouble("silver", -1.0)
                val source  = json.optString("source", "vercel")
                val isStale = json.optBoolean("stale", false)

                if (gold24k <= 0) {
                    Log.w("RateFeed", "No valid gold24k in response: $body")
                    return
                }

                scope.launch {
                    val currentDb = rateDao.getLatestRates()
                    if (currentDb?.isOverride == true) {
                        Log.d("RateFeed", "Manual override active, ignoring HTTP poll result")
                        return@launch
                    }

                    val newSnapshot = RateSnapshot(
                        gold24kPerGram  = gold24k,
                        gold22kPerGram  = if (gold22k > 0) gold22k else Math.round(gold24k * (22.0 / 24.0)).toDouble(),
                        gold18kPerGram  = if (gold18k > 0) gold18k else Math.round(gold24k * (18.0 / 24.0)).toDouble(),
                        silverPerGram   = if (silver > 0) silver else currentDb?.silverPerGram ?: 0.0,
                        source          = "vercel-$source",
                        timestamp       = System.currentTimeMillis(),
                        isStale         = isStale,
                        isOverride      = false
                    )
                    Log.d("RateFeed", "HTTP poll -> saving rates: 24K=₹${gold24k}, 22K=₹${gold22k}, Silver=₹${silver}")
                    rateDao.insertRates(newSnapshot)
                    auditLogDao.insertLog(AuditLog(type = "rate.http", message = "Updated from Vercel API ($source)"))
                }
            }
        } catch (e: Exception) {
            Log.e("RateFeed", "HTTP poll exception: ${e.message}", e)
        }
    }

    // ── WebSocket (real-time ticks from ambicaaspot) ───────────────────────

    private fun connectWebSocket() {
        Log.d("RateFeed", "Attempting WebSocket: ws://ambicaaspot.com:1001/bullion...")
        val request = Request.Builder()
            .url("ws://ambicaaspot.com:1001/bullion?user=ambicaa&auth=1&type=web")
            .build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                isWsConnected = true
                Log.d("RateFeed", "WebSocket Connected")
                val handshake = "{\"protocol\":\"json\",\"version\":1}\u001E"
                webSocket.send(handshake)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Log.v("RateFeed", "WS Raw: $text")
                if (text.contains("{}")) {
                    val subMsg = JSONObject().apply {
                        put("type", 1)
                        put("invocationId", "0")
                        put("target", "client")
                        put("arguments", JSONArray().put("ambicaa"))
                    }.toString() + "\u001E"
                    Log.d("RateFeed", "Sending WS subscription: $subMsg")
                    webSocket.send(subMsg)
                }
                processWsMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                Log.d("RateFeed", "WebSocket Closing: $reason")
                isWsConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                Log.d("RateFeed", "WebSocket Closed: $reason")
                isWsConnected = false
                scheduleWsRetry()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                isWsConnected = false
                Log.e("RateFeed", "WebSocket Failure: ${t.message} — HTTP polling will cover rates")
                scheduleWsRetry()
            }
        })
    }

    private fun scheduleWsRetry() {
        scope.launch {
            delay(30_000L) // retry WS every 30 s (HTTP poll covers the gap)
            if (!isWsConnected) {
                Log.d("RateFeed", "Retrying WebSocket...")
                connectWebSocket()
            }
        }
    }

    private fun processWsMessage(text: String) {
        try {
            val messages = text.split("\u001E").filter { it.isNotBlank() }
            for (msgStr in messages) {
                val json = JSONObject(msgStr)
                val target = json.optString("target", "")
                Log.d("RateFeed", "WS Msg type=${json.optInt("type", -1)} target=$target")

                if (json.has("target") &&
                    (target == "workerPublish" || target == "workerPublishCoin" || target == "symbolDetails")) {
                    val arguments = json.optJSONArray("arguments") ?: continue
                    val targetObj = arguments.optJSONObject(0) ?: continue
                    val encoded = targetObj.optString("Data", "")

                    if (encoded.isNotEmpty()) {
                        val decodedBytes = Base64.decode(encoded, Base64.DEFAULT)
                        val gis = GZIPInputStream(ByteArrayInputStream(decodedBytes))
                        val decompressedStr = gis.bufferedReader().use { it.readText() }
                        Log.v("RateFeed", "WS Decompressed: $decompressedStr")
                        updateRatesFromWsPayload(JSONArray(decompressedStr))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RateFeed", "WS message error: ${e.message}", e)
        }
    }

    private fun updateRatesFromWsPayload(payload: JSONArray) {
        var goldAsk: Double? = null
        var silverAsk: Double? = null

        for (i in 0 until payload.length()) {
            val item = payload.optJSONObject(i) ?: continue
            val symbol = item.optString("symbol", "").uppercase()
            val ask = item.optDouble("Ask", -1.0)

            if (ask > 0) {
                if (symbol.contains("GOLD") || symbol == "117574919" || symbol == "119445255" || symbol == "GC") {
                    goldAsk = ask
                } else if (symbol.contains("SILVER") || symbol == "118822407" || symbol == "120761607" || symbol == "SI") {
                    silverAsk = ask
                }
            }
        }

        Log.d("RateFeed", "WS update: Gold=$goldAsk Silver=$silverAsk")

        if (goldAsk != null || silverAsk != null) {
            scope.launch {
                val current = rateDao.getLatestRates()
                if (current?.isOverride == true) {
                    Log.d("RateFeed", "Manual override active, ignoring WS feed")
                    return@launch
                }

                val gold24kPerGram  = goldAsk?.let { Math.round(it / 10).toDouble() } ?: current?.gold24kPerGram ?: 0.0
                val gold22kPerGram  = if (goldAsk != null) Math.round(gold24kPerGram * (22.0 / 24.0)).toDouble() else current?.gold22kPerGram ?: 0.0
                val gold18kPerGram  = if (goldAsk != null) Math.round(gold24kPerGram * (18.0 / 24.0)).toDouble() else current?.gold18kPerGram ?: 0.0
                val silverPerGram   = silverAsk?.let { Math.round((it / 1000.0) * 100) / 100.0 } ?: current?.silverPerGram ?: 0.0

                val newSnapshot = RateSnapshot(
                    gold24kPerGram = gold24kPerGram,
                    gold22kPerGram = gold22kPerGram,
                    gold18kPerGram = gold18kPerGram,
                    silverPerGram  = silverPerGram,
                    source         = "ambicaa-ws",
                    timestamp      = System.currentTimeMillis(),
                    isStale        = false,
                    isOverride     = false
                )
                Log.d("RateFeed", "WS -> saving rates: $newSnapshot")
                rateDao.insertRates(newSnapshot)
                auditLogDao.insertLog(AuditLog(type = "rate.feed", message = "Auto-updated from live WebSocket feed"))
            }
        }
    }

    // ── Manual override helpers ────────────────────────────────────────────

    suspend fun setManualOverride(gold24K: Double, silver: Double) {
        val gold22K = Math.round(gold24K * (22.0 / 24.0)).toDouble()
        val gold18K = Math.round(gold24K * (18.0 / 24.0)).toDouble()

        val newSnapshot = RateSnapshot(
            gold24kPerGram = gold24K,
            gold22kPerGram = gold22K,
            gold18kPerGram = gold18K,
            silverPerGram  = silver,
            source         = "manual",
            timestamp      = System.currentTimeMillis(),
            isStale        = false,
            isOverride     = true
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
                gold24kPerGram = 7777.0,
                gold22kPerGram = 7129.0,
                gold18kPerGram = 5833.0,
                silverPerGram  = 99.0,
                source         = "fallback",
                isStale        = true,
                isOverride     = false,
                timestamp      = System.currentTimeMillis()
            )
            rateDao.insertRates(default)
        }
    }
}
