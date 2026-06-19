package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(viewModel: MainViewModel) {
    val invoicesArray by viewModel.invoices.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    val expandedInvoices = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        viewModel.fetchInvoices()
    }

    // Parse JSONArray to Kotlin List for reactive filtering
    val invoicesList = remember(invoicesArray) {
        if (invoicesArray == null) emptyList()
        else {
            val list = mutableListOf<JSONObject>()
            for (i in 0 until invoicesArray!!.length()) {
                list.add(invoicesArray!!.getJSONObject(i))
            }
            // Sort by date/createdAt descending
            list.sortedByDescending { it.optLong("createdAt", 0L) }
        }
    }

    val filteredInvoices = remember(invoicesList, searchQuery) {
        invoicesList.filter { invoice ->
            invoice.optString("invoiceId", "").contains(searchQuery, ignoreCase = true) ||
            invoice.optString("customerName", "").contains(searchQuery, ignoreCase = true) ||
            invoice.optString("customerPhone", "").contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "INVOICE HISTORY",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            if (invoicesArray == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                // Search Field at the top
                PaddingValues(horizontal = 16.dp, vertical = 8.dp).let {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(it),
                        placeholder = { Text("Search by customer, phone, or invoice...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                if (filteredInvoices.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "No invoices found.",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredInvoices, key = { it.optString("invoiceId", "") }) { invoice ->
                            val invoiceId = invoice.optString("invoiceId", "Unknown")
                            val isExpanded = expandedInvoices.contains(invoiceId)
                            
                            InvoiceCard(
                                invoice = invoice,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    if (isExpanded) expandedInvoices.remove(invoiceId)
                                    else expandedInvoices.add(invoiceId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceCard(
    invoice: JSONObject,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val customerName = invoice.optString("customerName", "Guest Customer")
    val customerPhone = invoice.optString("customerPhone", "N/A")
    val invoiceId = invoice.optString("invoiceId", "Unknown")
    val total = invoice.optDouble("total", 0.0)
    val totalWeight = invoice.optDouble("totalWeight", 0.0)
    val date = invoice.optString("date", "")
    val paymentMethod = invoice.optString("paymentMethod", "Cash")
    val itemsArray = invoice.optJSONArray("items") ?: JSONArray()
    val itemCount = itemsArray.length()

    // Generate initials for avatar
    val initials = remember(customerName) {
        val parts = customerName.trim().split("\\s+".toRegex())
        when {
            parts.isEmpty() || parts[0].isBlank() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase(Locale.getDefault())
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase(Locale.getDefault())
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row (Avatar, Customer Details & Total Pill)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Customer Avatar Badge
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.sp
                            )
                        )
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = customerName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Phone: $customerPhone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Total Price Pill
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "₹${total.toInt()}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            Spacer(Modifier.height(12.dp))

            // Metadata: Date, Invoice ID & Payment Method
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Invoice ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(invoiceId, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(date, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Payment", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(paymentMethod, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Weight", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$totalWeight g ($itemCount items)", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Expandable items sheet
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "INVOICE ITEMS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (i in 0 until itemsArray.length()) {
                                val item = itemsArray.getJSONObject(i)
                                val productObj = item.optJSONObject("product")
                                val productName = productObj?.optString("name") ?: "Unknown Product"
                                val productCode = productObj?.optString("itemCode") ?: ""
                                val itemWeight = productObj?.optDouble("weightGrams", 0.0) ?: 0.0
                                val itemTotal = item.optDouble("total", 0.0)

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(productName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        Text(productCode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = "${itemWeight}g | ₹${itemTotal.toInt()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (i < itemsArray.length() - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    val context = LocalContext.current
                    OutlinedButton(
                        onClick = { shareOldInvoice(context, invoice) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share Invoice Summary", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // View Items Toggle Button
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (isExpanded) "Hide transaction items" else "View transaction items",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun shareOldInvoice(context: android.content.Context, invoice: JSONObject) {
    val customerName = invoice.optString("customerName", "Guest Customer")
    val customerPhone = invoice.optString("customerPhone", "N/A")
    val invoiceId = invoice.optString("invoiceId", "Unknown")
    val date = invoice.optString("date", "")
    val total = invoice.optDouble("total", 0.0)
    val totalWeight = invoice.optDouble("totalWeight", 0.0)
    val itemsArray = invoice.optJSONArray("items") ?: JSONArray()

    val shareText = StringBuilder().apply {
        appendLine("🌟 SUNRISE JEWELS 🌟")
        appendLine("Invoice No: $invoiceId")
        appendLine("Date: $date")
        appendLine("--------------------------------")
        appendLine("Customer: $customerName")
        appendLine("Phone: $customerPhone")
        appendLine("--------------------------------")
        for (i in 0 until itemsArray.length()) {
            val item = itemsArray.getJSONObject(i)
            val productObj = item.optJSONObject("product")
            val name = productObj?.optString("name") ?: "Unknown"
            val code = productObj?.optString("itemCode") ?: ""
            val itemWeight = productObj?.optDouble("weightGrams", 0.0) ?: 0.0
            val purity = productObj?.optString("purity") ?: ""
            val itemTotal = item.optDouble("total", 0.0)
            appendLine("${i + 1}. $name ($code)")
            appendLine("   Weight: ${itemWeight}g | Purity: $purity")
            appendLine("   Price: ₹${itemTotal.toInt()}")
            appendLine("--------------------------------")
        }
        appendLine("Total Weight: $totalWeight g")
        appendLine("GRAND TOTAL: ₹${total.toInt()}")
        appendLine("Payment: Cash")
        appendLine("--------------------------------")
        appendLine("Thank you for shopping!")
    }.toString()

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "Sunrise Jewels Invoice $invoiceId")
        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share Invoice via"))
}
