package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.SessionManager
import com.example.data.WebApiRepository
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.screens.AppNavigation
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val db = AppDatabase.getDatabase(this)
        val factory = MainViewModelFactory(db)
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = viewModel(factory = factory)
                    val sessionManager = remember { SessionManager(applicationContext) }
                    val webApiRepository = remember { WebApiRepository() }
                    
                    val isLoggedInState by sessionManager.isLoggedInFlow.collectAsState(initial = null)
                    
                    when (isLoggedInState) {
                        null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "SUNRISE JEWELS",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 3.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                        true -> {
                            val coroutineScope = rememberCoroutineScope()
                            AppNavigation(
                                viewModel = viewModel,
                                onLogout = {
                                    coroutineScope.launch {
                                        sessionManager.clearSession()
                                    }
                                }
                            )
                        }
                        false -> {
                            val coroutineScope = rememberCoroutineScope()
                            LoginScreen(
                                onLogin = { username, password ->
                                    webApiRepository.login(username, password)
                                },
                                onLoginSuccess = { username, role ->
                                    coroutineScope.launch {
                                        sessionManager.saveSession(username, role)
                                    }
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
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

