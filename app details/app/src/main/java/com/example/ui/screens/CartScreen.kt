package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
fun CartScreen(viewModel: MainViewModel, navController: NavController) {
    val cart by viewModel.cartItems.collectAsStateWithLifecycle()
    var showCheckout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Cart / Checkout") })
        },
        bottomBar = {
            if (cart.isNotEmpty()) {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total (inc. GST)", fontWeight = FontWeight.Bold)
                            Text("₹${cart.sumOf { it.total }.toInt()}")
                        }
                        Button(onClick = { showCheckout = true }) {
                            Text("Checkout")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cart) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(item.product.name, fontWeight = FontWeight.Bold)
                                Text(item.product.itemCode, style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { viewModel.removeFromCart(item.product.itemCode) }) {
                                Icon(Icons.Default.Delete, "Remove")
                            }
                        }
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Metal: ₹${item.metalValue.toInt()}")
                            Text("Making: ₹${item.makingCharge.toInt()}")
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("GST (3%): ₹${item.gst.toInt()}")
                            Text("SubTotal: ₹${item.total.toInt()}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (cart.isEmpty()) {
                item { Text("Cart is empty.") }
            }
        }
    }

    if (showCheckout) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var showInvoice by remember { mutableStateOf(false) }

        if (!showInvoice) {
            AlertDialog(
                onDismissRequest = { showCheckout = false },
                title = { Text("Complete Sale") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Customer Name") })
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") })
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.checkout(name, phone, "Cash")
                        showInvoice = true
                    }) { Text("Confirm Checkout") }
                },
                dismissButton = {
                    TextButton(onClick = { showCheckout = false }) { Text("Cancel") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = {
                    showCheckout = false
                    navController.popBackStack()
                },
                title = { Text("Invoice Generated") },
                text = { Text("Sale completed successfully! Invoice saved to database.") },
                confirmButton = {
                    Button(onClick = {
                        showCheckout = false
                        navController.popBackStack()
                    }) { Text("Done") }
                }
            )
        }
    }
}
