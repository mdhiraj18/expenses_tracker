package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import com.example.data.ExpenseDatabase
import com.example.data.ExpenseRepository
import com.example.data.GoogleSheetsSyncManager
import com.example.ui.ExpenseTrackerApp
import com.example.ui.ExpenseViewModel
import com.example.ui.ExpenseViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val syncManager = GoogleSheetsSyncManager.getInstance(applicationContext)
    
    // Intercept Google OAuth deep link code callback
    intent?.data?.let { uri ->
      if (uri.scheme == "expensetracker" && uri.host == "oauth_callback") {
        uri.getQueryParameter("code")?.let { code ->
          lifecycleScope.launch {
            syncManager.exchangeAuthorizationCode(code)
          }
        }
      }
    }

    setContent {
      val database = ExpenseDatabase.getDatabase(applicationContext)
      val repository = ExpenseRepository(database.dao)
      val viewModel: ExpenseViewModel = viewModel(
        factory = ExpenseViewModelFactory(repository, syncManager)
      )
      val darkModeEnabled = viewModel.darkModeEnabled.collectAsState().value
      MyApplicationTheme(darkTheme = darkModeEnabled) {
        ExpenseTrackerApp(viewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    val syncManager = GoogleSheetsSyncManager.getInstance(applicationContext)
    intent.data?.let { uri ->
      if (uri.scheme == "expensetracker" && uri.host == "oauth_callback") {
        uri.getQueryParameter("code")?.let { code ->
          lifecycleScope.launch {
            syncManager.exchangeAuthorizationCode(code)
          }
        }
      }
    }
  }
}
