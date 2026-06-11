package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController) {
    val rates by viewModel.latestRates.collectAsStateWithLifecycle()
    val logs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val cart by viewModel.cartItems.collectAsStateWithLifecycle()
    var showOverrideDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sunrise Jewells") },
                actions = {
                    TextButton(onClick = { viewModel.refreshAll() }) {
                        Text("Force Log Pull", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    BadgedBox(
                        badge = { if (cart.isNotEmpty()) Badge { Text(cart.size.toString()) } }
                    ) {
                        IconButton(onClick = { navController.navigate("cart") }) {
                            Icon(Icons.Default.ShoppingCart, "Cart")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Live Bullion Rates", style = MaterialTheme.typography.titleLarge)
            }
            if (rates != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Source: ${rates!!.source}", style = MaterialTheme.typography.labelMedium)
                            if (rates!!.isOverride) {
                                Text("MANUAL OVERRIDE ACTIVE", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { viewModel.disableOverride() }) { Text("Disable Override") }
                            } else {
                                TextButton(onClick = { showOverrideDialog = true }) { Text("Set Override Rates") }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("24K Gold", style = MaterialTheme.typography.titleMedium)
                                    Text("₹${rates!!.gold24kPerGram}/g")
                                }
                                Column {
                                    Text("22K Gold", style = MaterialTheme.typography.titleMedium)
                                    Text("₹${rates!!.gold22kPerGram}/g")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("18K Gold", style = MaterialTheme.typography.titleMedium)
                                    Text("₹${rates!!.gold18kPerGram}/g")
                                }
                                Column {
                                    Text("Silver", style = MaterialTheme.typography.titleMedium)
                                    Text("₹${rates!!.silverPerGram}/g")
                                }
                            }
                        }
                    }
                }
            } else {
                item { Text("Waiting for rates...") }
            }

            item {
                Text("Recent Activity Logs", style = MaterialTheme.typography.titleLarge)
            }
            
            items(logs) { log ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(log.type, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(log.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showOverrideDialog && rates != null) {
        var manual24k by remember { mutableStateOf(rates!!.gold24kPerGram.toString()) }
        var manualSilver by remember { mutableStateOf(rates!!.silverPerGram.toString()) }

        AlertDialog(
            onDismissRequest = { showOverrideDialog = false },
            title = { Text("Set Override Rates") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = manual24k, onValueChange = { manual24k = it }, label = { Text("24K Gold Price/g") })
                    OutlinedTextField(value = manualSilver, onValueChange = { manualSilver = it }, label = { Text("Silver Price/g") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateManualRates(manual24k.toDoubleOrNull() ?: 0.0, manualSilver.toDoubleOrNull() ?: 0.0)
                    showOverrideDialog = false
                }) { Text("Override") }
            },
            dismissButton = {
                TextButton(onClick = { showOverrideDialog = false }) { Text("Cancel") }
            }
        )
    }
}
