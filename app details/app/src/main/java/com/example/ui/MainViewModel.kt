package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(
    private val inventoryRepository: InventoryRepository,
    private val rateRepository: RateRepository
) : ViewModel() {

    init {
        // Start rate polling immediately in its own scope so it doesn't wait for stock sync
        viewModelScope.launch {
            rateRepository.ensureDefaultRates()
            Log.d("RateFeed", "Default rates ensured, starting polling")
            rateRepository.startPolling()
        }
        
        // Refresh products in another coroutine
        viewModelScope.launch {
            inventoryRepository.refreshProducts()
        }
    }

    val availableProducts: StateFlow<List<Product>> = inventoryRepository.availableProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockSyncStatus: StateFlow<String> = inventoryRepository.stockSyncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "loading")

    val latestRates: StateFlow<RateSnapshot?> = rateRepository.latestRates
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val recentLogs: StateFlow<List<AuditLog>> = inventoryRepository.recentLogs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _invoices = MutableStateFlow<org.json.JSONArray?>(null)
    val invoices: StateFlow<org.json.JSONArray?> = _invoices.asStateFlow()

    fun fetchInvoices() {
        viewModelScope.launch {
            _invoices.value = inventoryRepository.fetchInvoices()
        }
    }

    fun refreshAll() {
        Log.d("RateFeed", "refreshAll triggered")
        viewModelScope.launch {
            inventoryRepository.refreshProducts()
            // Check if websocket is connected, if not start it
            rateRepository.startPolling()
        }
    }

    fun updateManualRates(gold24k: Double, silver: Double) {
        viewModelScope.launch {
            rateRepository.setManualOverride(gold24k, silver)
        }
    }

    fun disableOverride() {
        viewModelScope.launch {
            rateRepository.disableOverride()
        }
    }

    fun addProduct(
        category: String, metal: String, purity: String,
        weight: Double, makingPercent: Double, fixedValue: Double,
        name: String, description: String
    ) {
        viewModelScope.launch {
            val itemCode = inventoryRepository.generateNextItemCode(category)
            val product = Product(
                itemCode = itemCode,
                name = name,
                category = category,
                metal = metal,
                purity = purity,
                weightGrams = weight,
                makingChargePercent = makingPercent,
                fixedValue = fixedValue,
                description = description
            )
            inventoryRepository.saveProduct(product)
        }
    }

    fun scanAndAddToCart(itemCode: String): String? {
        var statusMsg: String? = null
        viewModelScope.launch {
            val product = inventoryRepository.getProductByCode(itemCode)
            if (product == null) {
                statusMsg = "Product not found"
            } else if (product.status != "available") {
                statusMsg = "Product is already ${product.status}"
            } else if (_cartItems.value.any { it.product.itemCode == product.itemCode }) {
                statusMsg = "Already in cart"
            } else {
                val rates = latestRates.value ?: return@launch
                val rate = getRateForProduct(product, rates)
                val calculations = calculateValuation(product, rate)
                
                val cartItem = CartItem(
                    product = product,
                    ratePerGram = rate,
                    metalValue = calculations.metal,
                    makingCharge = calculations.making,
                    subtotal = calculations.subtotal,
                    gst = calculations.gst,
                    total = calculations.total
                )
                _cartItems.update { it + cartItem }
            }
        }
        return statusMsg
    }

    fun addToCart(product: Product) {
        val rates = latestRates.value ?: return
        if (_cartItems.value.any { it.product.itemCode == product.itemCode }) return
        val rate = getRateForProduct(product, rates)
        val calc = calculateValuation(product, rate)
        _cartItems.update {
            it + CartItem(product, rate, calc.metal, calc.making, calc.subtotal, calc.gst, calc.total)
        }
    }

    fun removeFromCart(itemCode: String) {
        _cartItems.update { list -> list.filter { it.product.itemCode != itemCode } }
    }

    fun checkout(customerName: String, customerPhone: String, paymentMethod: String) {
        val cart = _cartItems.value
        if (cart.isEmpty()) return
        
        val subtotal = cart.sumOf { it.subtotal }
        val gst = cart.sumOf { it.gst }
        val total = cart.sumOf { it.total }

        viewModelScope.launch {
            val invoiceNo = "SRF-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}-${System.currentTimeMillis() % 100000}"
            val serialized = Json.encodeToString(cart)

            val sale = Sale(
                invoiceNo = invoiceNo,
                customerName = customerName,
                customerPhone = customerPhone,
                serializedItems = serialized,
                subtotal = subtotal,
                gst = gst,
                total = total,
                paymentMethod = paymentMethod
            )

            val totalWeight = cart.sumOf { it.product.weightGrams }
            inventoryRepository.checkout(sale, cart.map { it.product.itemCode }, totalWeight)
            _cartItems.value = emptyList() // clear cart
        }
    }

    data class CalculationResult(val metal: Double, val making: Double, val subtotal: Double, val gst: Double, val total: Double)

    companion object {
        fun getRateForProduct(product: Product, rates: RateSnapshot): Double {
            if (product.metal.equals("Silver", ignoreCase = true) || product.purity.equals("Silver", ignoreCase = true)) {
                return rates.silverPerGram
            }
            return when (product.purity) {
                "24K" -> rates.gold24kPerGram
                "22K" -> rates.gold22kPerGram
                "18K" -> rates.gold18kPerGram
                else -> rates.gold24kPerGram
            }
        }

        fun calculateValuation(product: Product, ratePerGram: Double): CalculationResult {
            val metal = product.weightGrams * ratePerGram
            val making = metal * (product.makingChargePercent / 100.0)
            val sub = metal + making + product.fixedValue
            val gst = sub * 0.03
            val tot = sub + gst
            return CalculationResult(metal, making, sub, gst, tot)
        }
    }
}
