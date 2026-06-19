package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.ui.MainViewModel
import com.example.data.Product
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, navController: NavController, onLogout: () -> Unit) {
    val rates by viewModel.latestRates.collectAsStateWithLifecycle()
    val products by viewModel.availableProducts.collectAsStateWithLifecycle()
    val cart by viewModel.cartItems.collectAsStateWithLifecycle()
    var showOverrideDialog by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    // Pulsating animation for live rates indicator
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SUNRISE JEWELS",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync Rates",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Bullion Rates Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Live Bullion Rates",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    // Pulsating Live Badge
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.secondary.copy(
                                            alpha = pulseAlpha
                                        )
                                    )
                            )
                            Text(
                                text = "LIVE FEED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }
                }
            }

            if (rates != null) {
                val currentRates = rates!!
                item {
                    // Bullion Rates Source and Override options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Source: ${currentRates.source}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (currentRates.isOverride) {
                            TextButton(
                                onClick = { viewModel.disableOverride() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Restore Live Rates", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            TextButton(onClick = { showOverrideDialog = true }) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Override Rates", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // Grid cards for Rates
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            // 24K Gold Card
                            RateCard(
                                title = "Gold 24K",
                                price = "₹${currentRates.gold24kPerGram}",
                                modifier = Modifier.weight(1f),
                                isGold = true,
                                isDark = isDark
                            )
                            // 22K Gold Card
                            RateCard(
                                title = "Gold 22K",
                                price = "₹${currentRates.gold22kPerGram}",
                                modifier = Modifier.weight(1f),
                                isGold = true,
                                isDark = isDark,
                                isSecondaryGold = true
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            // 18K Gold Card
                            RateCard(
                                title = "Gold 18K",
                                price = "₹${currentRates.gold18kPerGram}",
                                modifier = Modifier.weight(1f),
                                isGold = true,
                                isDark = isDark,
                                isTertiaryGold = true
                            )
                            // Silver Card
                            RateCard(
                                title = "Silver 999",
                                price = "₹${currentRates.silverPerGram}",
                                modifier = Modifier.weight(1f),
                                isGold = false,
                                isDark = isDark
                            )
                        }
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Waiting for bullion rates...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Total Inventory Summary Section Header
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Total Inventory Summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Total Inventory Summary Card
            item {
                val totalGoldWeight = remember(products) {
                    products.filter { product ->
                        !(product.metal.equals("Silver", ignoreCase = true) || product.purity.equals("Silver", ignoreCase = true))
                    }.sumOf { it.weightGrams }
                }

                val totalSilverWeight = remember(products) {
                    products.filter { product ->
                        (product.metal.equals("Silver", ignoreCase = true) || product.purity.equals("Silver", ignoreCase = true))
                    }.sumOf { it.weightGrams }
                }

                val currentValuation = remember(products, rates) {
                    if (rates != null) {
                        products.sumOf { product ->
                            val rate = MainViewModel.getRateForProduct(product, rates!!)
                            val calc = MainViewModel.calculateValuation(product, rate)
                            calc.total
                        }
                    } else {
                        0.0
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
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Total Gold Weight Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL GOLD WEIGHT",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(Locale.US, "%.2f g", totalGoldWeight),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(Locale.US, "%.3f kg", totalGoldWeight / 1000.0),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                        // 2. Total Silver Weight Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL SILVER WEIGHT",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(Locale.US, "%.2f g", totalSilverWeight),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(Locale.US, "%.3f kg", totalSilverWeight / 1000.0),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                        // 3. Current Valuation Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "CURRENT VALUATION",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(Locale.US, "₹%,d", currentValuation.toInt()),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = "Full inventory sum",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
            title = {
                Text(
                    "Set Override Rates",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Set custom prices per gram. These will override the live feed until cleared.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manual24k,
                        onValueChange = { manual24k = it },
                        label = { Text("24K Gold Price/g (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualSilver,
                        onValueChange = { manualSilver = it },
                        label = { Text("Silver Price/g (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateManualRates(
                            manual24k.toDoubleOrNull() ?: 0.0,
                            manualSilver.toDoubleOrNull() ?: 0.0
                        )
                        showOverrideDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Apply Override")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverrideDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun RateCard(
    title: String,
    price: String,
    modifier: Modifier = Modifier,
    isGold: Boolean,
    isDark: Boolean,
    isSecondaryGold: Boolean = false,
    isTertiaryGold: Boolean = false
) {
    // Elegant metallic gradients depending on type and theme mode
    val gradientBrush = remember(isGold, isDark, isSecondaryGold, isTertiaryGold) {
        if (isGold) {
            when {
                isDark -> {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF382F1D), // Dark bronze-gold
                            Color(0xFF241E12)
                        )
                    )
                }
                isSecondaryGold -> {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFFFBF0), // Very light champagne
                            Color(0xFFF7ECD5)
                        )
                    )
                }
                isTertiaryGold -> {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFAF6EE),
                            Color(0xFFF3E7D0)
                        )
                    )
                }
                else -> {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFBF1D5), // Golden sand
                            Color(0xFFE5CE8F)
                        )
                    )
                }
            }
        } else {
            if (isDark) {
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF232529), // Dark steel
                        Color(0xFF16181B)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF0F2F5), // Light cool silver
                        Color(0xFFD2D7DF)
                    )
                )
            }
        }
    }

    val borderColor = if (isDark) {
        if (isGold) Color(0xFF6B582E) else Color(0xFF3E434B)
    } else {
        if (isGold) Color(0xFFDFBA6B) else Color(0xFFBCC2CD)
    }

    val titleColor = if (isDark) {
        if (isGold) Color(0xFFDFBA6B) else Color(0xFFB0BEC5)
    } else {
        if (isGold) Color(0xFF705822) else Color(0xFF455A64)
    }

    val priceColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier
            .height(95.dp)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = titleColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = price,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = priceColor
                    )
                    Text(
                        text = "/g",
                        style = MaterialTheme.typography.labelSmall,
                        color = titleColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
