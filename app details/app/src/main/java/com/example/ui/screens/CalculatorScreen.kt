package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(viewModel: MainViewModel) {
    val rates by viewModel.latestRates.collectAsStateWithLifecycle()
    
    var weight by remember { mutableStateOf("") }
    var purity by remember { mutableStateOf("22K") }
    var making by remember { mutableStateOf("10") }
    
    Scaffold(
        topBar = { TopAppBar(title = { Text("Walk-in Calculator") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Estimate jewellery without adding to inventory", style = MaterialTheme.typography.bodyMedium)
            
            OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (g)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = purity, onValueChange = { purity = it }, label = { Text("Purity (24K, 22K, 18K, Silver)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = making, onValueChange = { making = it }, label = { Text("Making Charge %") }, modifier = Modifier.fillMaxWidth())

            if (rates != null) {
                val weightVal = weight.toDoubleOrNull() ?: 0.0
                val makingVal = making.toDoubleOrNull() ?: 0.0
                
                val rateObj = rates!!
                val rate = when (purity) {
                    "24K" -> rateObj.gold24kPerGram
                    "22K" -> rateObj.gold22kPerGram
                    "18K" -> rateObj.gold18kPerGram
                    "Silver" -> rateObj.silverPerGram
                    else -> rateObj.gold24kPerGram
                }
                
                val metal = weightVal * rate
                val makingTotal = metal * (makingVal / 100.0)
                val sub = metal + makingTotal
                val gst = sub * 0.03
                val total = sub + gst
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Rate Used: ₹$rate /g")
                        Text("Metal Value: ₹${metal.toInt()}")
                        Text("Making Charge: ₹${makingTotal.toInt()}")
                        Text("GST (3%): ₹${gst.toInt()}")
                        HorizontalDivider()
                        Text("Estimated Total: ₹${total.toInt()}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Text("Waiting for live rates...")
            }
        }
    }
}
