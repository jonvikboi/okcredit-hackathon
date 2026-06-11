package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: MainViewModel) {
    val products by viewModel.availableProducts.collectAsStateWithLifecycle()
    val rates by viewModel.latestRates.collectAsStateWithLifecycle()
    val syncStatus by viewModel.stockSyncStatus.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshAll()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock (${products.size})") },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, "Refresh stock")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add Stock")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Stock Catalog", style = MaterialTheme.typography.titleLarge)
                when {
                    syncStatus == "loading" -> Text(
                        "Syncing from Vercel API...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    syncStatus.startsWith("api:") -> Text(
                        "Live from Vercel (${syncStatus.removePrefix("api:")} items)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    syncStatus.startsWith("cache:") -> {
                        val payload = syncStatus.removePrefix("cache:")
                        val parts = payload.split("|", limit = 2)
                        val count = parts[0]
                        val reason = parts.getOrNull(1)
                        Text(
                            if (reason.isNullOrBlank()) {
                                "Showing cached stock ($count items, offline)"
                            } else {
                                "Cached stock ($count items). $reason"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    syncStatus.startsWith("error:") -> Text(
                        syncStatus.removePrefix("error:"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            items(products) { product ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(product.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("${product.category} • ${product.itemCode}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        product.purity,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("${product.weightGrams} g", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        
                        if (rates != null) {
                            val rate = MainViewModel.getRateForProduct(product, rates!!)
                            val calc = MainViewModel.calculateValuation(product, rate)
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Metal Value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("₹${calc.metal.toInt()}", style = MaterialTheme.typography.bodyMedium)
                                }
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    Text("Making (${product.makingChargePercent}%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("₹${calc.making.toInt()}", style = MaterialTheme.typography.bodyMedium)
                                }
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                    Text("GST (3%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("₹${calc.gst.toInt()}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column {
                                    Text("Owner Valuation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("₹${calc.total.toInt()}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Button(onClick = { viewModel.addToCart(product) }, shape = MaterialTheme.shapes.small) {
                                    Icon(Icons.Default.ShoppingCart, "Add to Cart", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add")
                                }
                            }
                        } else {
                            Text("Waiting for live rates...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (products.isEmpty() && syncStatus != "loading") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "No stock loaded.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Stock syncs from your Vercel website.\n" +
                                "1. Redeploy okcreditproject-two.vercel.app (Dashboard → Deployments → Redeploy)\n" +
                                "2. Rebuild the Android app\n" +
                                "3. Tap refresh above",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Ring") }
        var metal by remember { mutableStateOf("Gold") }
        var purity by remember { mutableStateOf("22K") }
        var weight by remember { mutableStateOf("") }
        var making by remember { mutableStateOf("12") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add New Stock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") })
                    OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (g)") })
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category (e.g. Ring)") })
                    OutlinedTextField(value = purity, onValueChange = { purity = it }, label = { Text("Purity (e.g. 22K)") })
                    OutlinedTextField(value = making, onValueChange = { making = it }, label = { Text("Making Charge %") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    showAddDialog = false
                    viewModel.addProduct(
                        category = category, metal = metal, purity = purity,
                        weight = weight.toDoubleOrNull() ?: 0.0,
                        makingPercent = making.toDoubleOrNull() ?: 0.0,
                        fixedValue = 0.0, name = name, description = ""
                    )
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
