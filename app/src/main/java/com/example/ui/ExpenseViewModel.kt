package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.receiver.ReminderScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ExpenseViewModel(
    private val repository: ExpenseRepository,
    val syncManager: GoogleSheetsSyncManager
) : ViewModel() {

    // Current selected month: "YYYY-MM", defaults to current local time (June 2026)
    private val _selectedMonth = MutableStateFlow(getTodayMonthYearString())
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    // Authentication State
    private val _isUserAuthenticated = MutableStateFlow(false)
    val isUserAuthenticated: StateFlow<Boolean> = _isUserAuthenticated.asStateFlow()

    private val _authUserEmail = MutableStateFlow<String?>(null)
    val authUserEmail: StateFlow<String?> = _authUserEmail.asStateFlow()

    private val _authUserName = MutableStateFlow<String?>(null)
    val authUserName: StateFlow<String?> = _authUserName.asStateFlow()

    private val _authUserPhotoUrl = MutableStateFlow<String?>(null)
    val authUserPhotoUrl: StateFlow<String?> = _authUserPhotoUrl.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    // Global Dark Mode enabled state
    private val _darkModeEnabled = MutableStateFlow(false)
    val darkModeEnabled: StateFlow<Boolean> = _darkModeEnabled.asStateFlow()

    // Bank sync state
    private val _isSyncingBank = MutableStateFlow(false)
    val isSyncingBank: StateFlow<Boolean> = _isSyncingBank.asStateFlow()

    private val _syncingBankName = MutableStateFlow<String?>(null)
    val syncingBankName: StateFlow<String?> = _syncingBankName.asStateFlow()

    // Alarm Reminder enabled state
    private val _reminderEnabled = MutableStateFlow(false)
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    private val _reminderTimes = MutableStateFlow<List<String>>(listOf("20:00"))
    val reminderTimes: StateFlow<List<String>> = _reminderTimes.asStateFlow()

    // Optional Bank Tracker Active switch state
    private val _bankTrackerEnabled = MutableStateFlow(true)
    val bankTrackerEnabled: StateFlow<Boolean> = _bankTrackerEnabled.asStateFlow()

    // Search query for transactions
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // All expenses from base DB
    val allExpenses: StateFlow<List<Expense>> = combine(
        repository.allExpenses,
        _authUserEmail
    ) { expenses, email ->
        val userSpec = email?.trim()?.lowercase() ?: ""
        expenses.filter { it.userEmail.lowercase() == userSpec }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current month filtered expenses
    val filteredExpenses: StateFlow<List<Expense>> = combine(
        allExpenses,
        _selectedMonth,
        _searchQuery
    ) { expenses, selected, query ->
        val (start, end) = getStartEndMilliOfMonth(selected)
        expenses.filter { expense ->
            val matchesTime = expense.timestamp in start..end
            val matchesQuery = if (query.isEmpty()) true else {
                expense.title.contains(query, ignoreCase = true) || 
                expense.category.contains(query, ignoreCase = true) || 
                (expense.bankName != null && expense.bankName.contains(query, ignoreCase = true))
            }
            matchesTime && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Budget Limits
    val budgetLimits: StateFlow<List<BudgetLimit>> = combine(
        repository.allBudgetLimits,
        _authUserEmail
    ) { limits, email ->
        val userSpec = email?.trim()?.lowercase() ?: ""
        limits.filter { it.userEmail.lowercase() == userSpec }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Bank Connections
    val bankConnections: StateFlow<List<BankConnection>> = combine(
        repository.allBankConnections,
        _authUserEmail
    ) { connections, email ->
        val userSpec = email?.trim()?.lowercase() ?: ""
        connections.filter { it.userEmail.lowercase() == userSpec }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val loans: StateFlow<List<Loan>> = combine(
        repository.allLoans,
        _authUserEmail
    ) { list, email ->
        val userSpec = email?.trim()?.lowercase() ?: ""
        list.filter { it.userEmail.lowercase() == userSpec }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val subscriptions: StateFlow<List<Subscription>> = combine(
        repository.allSubscriptions,
        _authUserEmail
    ) { list, email ->
        val userSpec = email?.trim()?.lowercase() ?: ""
        list.filter { it.userEmail.lowercase() == userSpec }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Dynamic list of categories containing defaults + custom categories loaded from limits / transactions
    val categories: StateFlow<List<String>> = combine(
        budgetLimits,
        allExpenses
    ) { limits, expenses ->
        val defaultCats = listOf(
            "Food & Dining", "Shopping & Lifestyle", "Bills & Utilities",
            "Transport & Auto", "Entertainment", "Others", "Income"
        )
        val list = (defaultCats + limits.map { it.category } + expenses.map { it.category })
            .distinct()
            .filter { it.isNotBlank() }
        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("Food & Dining", "Shopping & Lifestyle", "Bills & Utilities", "Transport & Auto", "Entertainment", "Others", "Income")
    )

    init {
        viewModelScope.launch {
            // First run prepopulate
            repository.allBudgetLimits.first().let { limits ->
                if (limits.isEmpty()) {
                    repository.prepopulateDefaultLimits()
                }
            }
            repository.allBankConnections.first().let { banks ->
                if (banks.isEmpty()) {
                    repository.prepopulateDefaultBanks()
                }
            }
            // Auto-check and process active loans and subscriptions
            checkAndProcessRecurringDebits()
        }
    }

    fun loadBankTrackerSetting(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        _bankTrackerEnabled.value = prefs.getBoolean("bank_tracker_enabled", true)
    }

    fun setBankTrackerEnabled(context: Context, enabled: Boolean) {
        _bankTrackerEnabled.value = enabled
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("bank_tracker_enabled", enabled).apply()
    }

    fun addCategory(categoryName: String) {
        viewModelScope.launch {
            val name = categoryName.trim()
            if (name.isNotEmpty() && !categories.value.contains(name)) {
                // Prepopulate limit of 1000 INR for newly created custom category
                repository.saveBudgetLimit(BudgetLimit(name, 1000.0, _authUserEmail.value?.lowercase() ?: ""))
            }
        }
    }

    fun selectMonth(month: String) {
        _selectedMonth.value = month
    }

    fun searchExpenses(query: String) {
        _searchQuery.value = query
    }

    fun addExpense(title: String, amount: Double, category: String, timestamp: Long, note: String?) {
        viewModelScope.launch {
            repository.addExpense(
                Expense(
                    title = title,
                    amount = amount,
                    category = category,
                    timestamp = timestamp,
                    isBankSynced = false,
                    note = note,
                    userEmail = _authUserEmail.value?.lowercase() ?: ""
                )
            )
            try {
                if (syncManager.isConnected.value) {
                    syncManager.syncDataToGoogleSheet(allExpenses.value)
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Automatic Google Sheets sync failure", e)
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            try {
                if (syncManager.isConnected.value) {
                    syncManager.syncDataToGoogleSheet(allExpenses.value)
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Automatic Google Sheets sync failure", e)
            }
        }
    }

    fun saveBudgetLimit(category: String, limit: Double) {
        viewModelScope.launch {
            repository.saveBudgetLimit(BudgetLimit(category, limit, _authUserEmail.value?.lowercase() ?: ""))
        }
    }

    private fun syncRemindersWithSystem(context: Context, times: List<String>) {
        // Cancel all existing scheduled alarms in standard slot range (0..19)
        for (i in 0..19) {
            ReminderScheduler.cancelDailyReminder(context, i)
        }
        // Reschedule active hours
        times.forEachIndexed { index, timeStr ->
            val parts = timeStr.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toIntOrNull() ?: 20
                val min = parts[1].toIntOrNull() ?: 0
                ReminderScheduler.scheduleDailyReminder(context, index, hour, min)
            }
        }
    }

    fun loadReminderSettings(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        _reminderEnabled.value = prefs.getBoolean("reminder_enabled", false)
        val timesStr = prefs.getString("reminder_times", "20:00") ?: "20:00"
        _reminderTimes.value = timesStr.split(",").filter { it.isNotBlank() }

        if (_reminderEnabled.value) {
            syncRemindersWithSystem(context, _reminderTimes.value)
        }
    }

    fun loadDarkModeSetting(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("dark_mode_enabled", false)
        _darkModeEnabled.value = enabled
        com.example.ui.theme.isDarkModeGlobal = enabled
    }

    fun toggleDarkMode(context: Context, enabled: Boolean) {
        _darkModeEnabled.value = enabled
        com.example.ui.theme.isDarkModeGlobal = enabled
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("dark_mode_enabled", enabled).apply()
    }

    private var isFirebaseAvailable: Boolean = false

    fun initFirebase(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                isFirebaseAvailable = true
            } else {
                FirebaseApp.initializeApp(context)
                isFirebaseAvailable = true
            }
        } catch (e: Exception) {
            Log.e("ExpenseViewModel", "Firebase initialization deferred. Sandbox fallback: ${e.message}")
            isFirebaseAvailable = false
        }
    }

    private fun saveAuthPrefs(context: Context, email: String, name: String, photoUrl: String?) {
        val trimmedEmail = email.trim().lowercase()
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("auth_authenticated", true)
            putString("auth_email", trimmedEmail)
            putString("auth_name", name)
            putString("auth_photo_url", photoUrl ?: "")
        }.apply()
        
        _isUserAuthenticated.value = true
        _authUserEmail.value = trimmedEmail
        _authUserName.value = name
        _authUserPhotoUrl.value = photoUrl

        viewModelScope.launch {
            repository.claimOrphanedRecords(trimmedEmail)
            ensureUserDataPrepopulated(trimmedEmail)
        }
    }

    private fun ensureUserDataPrepopulated(email: String) {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty()) return
        
        viewModelScope.launch {
            try {
                // Check if this specific user has any budget limits
                val limits = repository.allBudgetLimits.first().filter { it.userEmail.lowercase() == trimmedEmail }
                if (limits.isEmpty()) {
                    repository.prepopulateDefaultLimits(trimmedEmail)
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Error prepopulating budget limits: ${e.message}")
            }
            try {
                // Check if this specific user has any bank connections
                val banks = repository.allBankConnections.first().filter { it.userEmail.lowercase() == trimmedEmail }
                if (banks.isEmpty()) {
                    repository.prepopulateDefaultBanks(trimmedEmail)
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Error prepopulating bank connections: ${e.message}")
            }
        }
    }

    fun loadAuthState(context: Context) {
        initFirebase(context)
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val authenticated = prefs.getBoolean("auth_authenticated", false)
        val email = prefs.getString("auth_email", null)?.trim()?.lowercase()
        _isUserAuthenticated.value = authenticated
        _authUserEmail.value = email
        _authUserName.value = prefs.getString("auth_name", null)
        _authUserPhotoUrl.value = prefs.getString("auth_photo_url", null)
        
        if (authenticated && !email.isNullOrEmpty()) {
            viewModelScope.launch {
                repository.claimOrphanedRecords(email)
                ensureUserDataPrepopulated(email)
            }
        }

        // Synchronize with Firebase session state if available
        if (isFirebaseAvailable) {
            try {
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    saveAuthPrefs(
                        context,
                        firebaseUser.email ?: "",
                        firebaseUser.displayName ?: "User",
                        firebaseUser.photoUrl?.toString()
                    )
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Error fetching current Firebase user: ${e.message}")
            }
        }
    }

    fun loginWithEmail(context: Context, email: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            initFirebase(context)
            if (isFirebaseAvailable) {
                try {
                    val auth = FirebaseAuth.getInstance()
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnCompleteListener { task ->
                            _isAuthLoading.value = false
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                val finalName = user?.displayName ?: email.substringBefore("@")
                                val finalEmail = user?.email ?: email
                                saveAuthPrefs(context, finalEmail, finalName, null)
                                onResult(true, "Authenticated via Live Firebase! Welcome back, $finalName!")
                            } else {
                                onResult(false, "Firebase Auth failed: ${task.exception?.localizedMessage ?: "Unknown error"}")
                            }
                        }
                    return@launch
                } catch (e: Exception) {
                    Log.w("ExpenseViewModel", "Real Firebase login failed, executing Sandbox fallback: ${e.message}")
                }
            }
            
            // Local Secure Sandbox fallback logic:
            delay(1000)
            _isAuthLoading.value = false
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val registeredPass = prefs.getString("reg_pwd_${email.trim().lowercase()}", null)
            val registeredName = prefs.getString("reg_name_${email.trim().lowercase()}", "User")
            
            if ((email.trim().lowercase() == "dhiraj.bitu18@gmail.com" && password == "password") || (registeredPass != null && registeredPass == password)) {
                val finalName = if (email.trim().lowercase() == "dhiraj.bitu18@gmail.com") "Dhiraj Bitu" else registeredName ?: "User"
                saveAuthPrefs(context, email.trim().lowercase(), finalName, "")
                onResult(true, "Welcome back, $finalName! (Authenticated via Secure Sandbox)")
            } else {
                onResult(false, if (registeredPass != null) "Incorrect password" else "User not found. Use 'dhiraj.bitu18@gmail.com' and 'password' or Sign Up.")
            }
        }
    }

    fun registerWithEmail(context: Context, email: String, name: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            initFirebase(context)
            if (isFirebaseAvailable) {
                try {
                    val auth = FirebaseAuth.getInstance()
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(name.trim())
                                    .build()
                                user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                    _isAuthLoading.value = false
                                    saveAuthPrefs(context, email.trim().lowercase(), name.trim(), null)
                                    onResult(true, "Firebase Account Created! Welcome, ${name.trim()}.")
                                }
                            } else {
                                _isAuthLoading.value = false
                                onResult(false, "Firebase Registration failed: ${task.exception?.localizedMessage ?: "Unknown error"}")
                            }
                        }
                    return@launch
                } catch (e: Exception) {
                    Log.w("ExpenseViewModel", "Real Firebase registration failed, executing Sandbox fallback: ${e.message}")
                }
            }
            
            // Local Secure Sandbox fallback logic:
            delay(1200)
            _isAuthLoading.value = false
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("reg_pwd_${email.trim().lowercase()}", password)
                putString("reg_name_${email.trim().lowercase()}", name.trim())
            }.apply()
            
            saveAuthPrefs(context, email.trim().lowercase(), name.trim(), "")
            onResult(true, "Account created! Welcome, $name. (Registered via Secure Sandbox)")
        }
    }

    fun loginWithGoogle(context: Context, email: String, name: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            delay(1200)
            _isAuthLoading.value = false
            
            saveAuthPrefs(context, email, name, "google_simulated")
            
            // Link Google Sheets
            syncManager.linkAccount(email)
            
            onResult(true, "Successfully Authenticated via Google Sign-In Simulator (Sheet integration linked!)")
        }
    }

    fun loginWithFirebaseGoogleCredentials(context: Context, idToken: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            initFirebase(context)
            if (isFirebaseAvailable) {
                try {
                    val auth = FirebaseAuth.getInstance()
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { task ->
                            _isAuthLoading.value = false
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                val finalName = user?.displayName ?: "Google User"
                                val finalEmail = user?.email ?: ""
                                val photoUrl = user?.photoUrl?.toString() ?: "google_simulated"
                                saveAuthPrefs(context, finalEmail, finalName, photoUrl)
                                syncManager.linkAccount(finalEmail)
                                onResult(true, "Google Sign-In Success! Authenticated via Live Firebase and Google Sheets.")
                            } else {
                                onResult(false, "Firebase credential validation failed: ${task.exception?.localizedMessage}")
                            }
                        }
                    return@launch
                } catch (e: Exception) {
                    Log.e("ExpenseViewModel", "Firebase Google Auth error: ${e.message}")
                }
            }
            _isAuthLoading.value = false
            onResult(false, "Firebase initialization outstanding. Try Simulated Google Sign In or place your google-services.json.")
        }
    }

    fun logout(context: Context) {
        initFirebase(context)
        if (isFirebaseAvailable) {
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Error logging out from Firebase auth: ${e.message}")
            }
        }

        // Sign out from Google Sign-In client to clear the cached active account
        try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            val webClientId = if (resId != 0) context.getString(resId) else "123456789012-abcdefghijklmnopqrstuvwxyz.apps.googleusercontent.com"
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            )
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
            googleSignInClient.signOut()
        } catch (e: Exception) {
            Log.e("ExpenseViewModel", "Error signing out from GoogleSignInClient: ${e.message}")
        }
        
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("auth_authenticated", false)
            putString("auth_email", null)
            putString("auth_name", null)
            putString("auth_photo_url", null)
        }.apply()
        
        _isUserAuthenticated.value = false
        _authUserEmail.value = null
        _authUserName.value = null
        _authUserPhotoUrl.value = null
        
        // Disconnect Sheets linkage
        syncManager.disconnect()
    }

    fun exportBackupData(context: Context): Intent? {
        val expenses = allExpenses.value
        val limits = budgetLimits.value
        val banks = bankConnections.value
        
        val json = StringBuilder()
        json.append("{\n")
        
        json.append("  \"expenses\": [\n")
        expenses.forEachIndexed { i, e ->
            json.append("    {\n")
            json.append("      \"title\": \"${escapeJson(e.title)}\",\n")
            json.append("      \"amount\": ${e.amount},\n")
            json.append("      \"category\": \"${escapeJson(e.category)}\",\n")
            json.append("      \"timestamp\": ${e.timestamp},\n")
            json.append("      \"isBankSynced\": ${e.isBankSynced},\n")
            json.append("      \"bankName\": ${if (e.bankName == null) "null" else "\"${escapeJson(e.bankName)}\""},\n")
            json.append("      \"note\": ${if (e.note == null) "null" else "\"${escapeJson(e.note)}\""}\n")
            json.append("    }${if (i < expenses.size - 1) "," else ""}\n")
        }
        json.append("  ],\n")
        
        json.append("  \"budgetLimits\": [\n")
        limits.forEachIndexed { i, l ->
            json.append("    {\n")
            json.append("      \"category\": \"${escapeJson(l.category)}\",\n")
            json.append("      \"monthlyLimit\": ${l.monthlyLimit}\n")
            json.append("    }${if (i < limits.size - 1) "," else ""}\n")
        }
        json.append("  ],\n")
        
        json.append("  \"bankConnections\": [\n")
        banks.forEachIndexed { i, b ->
            json.append("    {\n")
            json.append("      \"bankName\": \"${escapeJson(b.bankName)}\",\n")
            json.append("      \"accountName\": \"${escapeJson(b.accountName)}\",\n")
            json.append("      \"balance\": ${b.balance},\n")
            json.append("      \"isConnected\": ${b.isConnected},\n")
            json.append("      \"lastSynced\": ${b.lastSynced}\n")
            json.append("    }${if (i < banks.size - 1) "," else ""}\n")
        }
        json.append("  ]\n")
        json.append("}")
        
        try {
            val fileName = "smart_ledger_backup.json"
            val file = File(context.cacheDir, fileName)
            file.writeText(json.toString())
            
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Smart Expense Tracker Backup File")
                putExtra(Intent.EXTRA_TEXT, "Import this backup file in the Settings segment of your new device to fully restore your custom statements.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return Intent.createChooser(shareIntent, "Share Ledger Backup...")
        } catch (e: Exception) {
            Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    fun restoreBackupData(context: Context, jsonString: String): Boolean {
        try {
            val root = org.json.JSONObject(jsonString)
            
            viewModelScope.launch {
                // Import expenses
                if (root.has("expenses")) {
                    val arr = root.getJSONArray("expenses")
                    val list = mutableListOf<Expense>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            Expense(
                                title = obj.getString("title"),
                                amount = obj.getDouble("amount"),
                                category = obj.getString("category"),
                                timestamp = obj.getLong("timestamp"),
                                isBankSynced = obj.optBoolean("isBankSynced", false),
                                bankName = if (obj.isNull("bankName")) null else obj.getString("bankName"),
                                note = if (obj.isNull("note")) null else obj.getString("note")
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        repository.clearAllExpenses()
                        list.forEach { repository.addExpense(it) }
                    }
                }
                
                // Import limits
                if (root.has("budgetLimits")) {
                    val arr = root.getJSONArray("budgetLimits")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.saveBudgetLimit(
                            BudgetLimit(
                                category = obj.getString("category"),
                                monthlyLimit = obj.getDouble("monthlyLimit")
                            )
                        )
                    }
                }
                
                // Import banks
                if (root.has("bankConnections")) {
                    val arr = root.getJSONArray("bankConnections")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        repository.saveBankConnection(
                            BankConnection(
                                bankName = obj.getString("bankName"),
                                accountName = obj.getString("accountName"),
                                balance = obj.getDouble("balance"),
                                isConnected = obj.getBoolean("isConnected"),
                                lastSynced = obj.getLong("lastSynced")
                            )
                        )
                    }
                }
            }
            Toast.makeText(context, "Ledger fully restored from backup!", Toast.LENGTH_SHORT).show()
            return true
        } catch (e: Exception) {
            Toast.makeText(context, "Error reading backup: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun toggleReminder(context: Context, enabled: Boolean) {
        _reminderEnabled.value = enabled
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("reminder_enabled", enabled).apply()

        if (enabled) {
            syncRemindersWithSystem(context, _reminderTimes.value)
            val timesFormatted = _reminderTimes.value.joinToString(", ")
            Toast.makeText(context, "Ledger Reminders activated for: $timesFormatted", Toast.LENGTH_SHORT).show()
        } else {
            for (i in 0..19) {
                ReminderScheduler.cancelDailyReminder(context, i)
            }
            Toast.makeText(context, "Ledger Alerts disabled completely.", Toast.LENGTH_SHORT).show()
        }
    }

    fun addReminderTime(context: Context, timeStr: String) {
        val current = _reminderTimes.value.toMutableList()
        val formattedTime = normalizeTimeStr(timeStr)
        if (!current.contains(formattedTime) && formattedTime.isNotBlank()) {
            current.add(formattedTime)
            current.sort()
            _reminderTimes.value = current
            
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("reminder_times", current.joinToString(",")).apply()
            
            if (_reminderEnabled.value) {
                syncRemindersWithSystem(context, current)
            }
            Toast.makeText(context, "Added reminder at $formattedTime", Toast.LENGTH_SHORT).show()
        } else if (current.contains(formattedTime)) {
            Toast.makeText(context, "Reminder at $formattedTime already exists", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeReminderTime(context: Context, timeStr: String) {
        val current = _reminderTimes.value.toMutableList()
        if (current.size <= 1) {
            Toast.makeText(context, "At least one reminder time is required.", Toast.LENGTH_SHORT).show()
            return
        }
        if (current.remove(timeStr)) {
            _reminderTimes.value = current
            
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("reminder_times", current.joinToString(",")).apply()
            
            if (_reminderEnabled.value) {
                syncRemindersWithSystem(context, current)
            }
            Toast.makeText(context, "Removed reminder at $timeStr", Toast.LENGTH_SHORT).show()
        }
    }

    private fun normalizeTimeStr(timeStr: String): String {
        val parts = timeStr.trim().split(":")
        if (parts.size == 2) {
            val h = parts[0].toIntOrNull() ?: 0
            val m = parts[1].toIntOrNull() ?: 0
            return String.format(Locale.US, "%02d:%02d", h, m)
        }
        return timeStr
    }

    fun resetAllData(context: Context) {
        viewModelScope.launch {
            repository.clearAllExpenses()

            val currentLimits = budgetLimits.value
            currentLimits.forEach { repository.removeBudgetLimit(it) }
            repository.prepopulateDefaultLimits()

            val currentBanks = bankConnections.value
            currentBanks.forEach { repository.disconnectBank(it) }
            repository.prepopulateDefaultBanks()
            
            _searchQuery.value = ""
            _reminderTimes.value = listOf("20:00")
            
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("reminder_times", "20:00").apply()
            
            syncRemindersWithSystem(context, listOf("20:00"))
            
            Toast.makeText(context, "Full reset complete! Data restored to clean defaults.", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerInstantTestReminder(context: Context) {
        ReminderScheduler.triggerInstantNotification(context)
    }

    fun syncBank(context: Context, connection: BankConnection) {
        if (_isSyncingBank.value) return
        viewModelScope.launch {
            _isSyncingBank.value = true
            _syncingBankName.value = connection.bankName
            
            delay(2000)

            val addedCount = repository.simulateBankSync(connection.bankName, _authUserEmail.value?.lowercase() ?: "").size
            val updatedConnection = connection.copy(
                isConnected = true,
                lastSynced = System.currentTimeMillis()
            )
            repository.saveBankConnection(updatedConnection)
            
            _isSyncingBank.value = false
            _syncingBankName.value = null
            
            Toast.makeText(context, "Sync complete! Imported $addedCount bank transactions.", Toast.LENGTH_LONG).show()

            try {
                if (syncManager.isConnected.value) {
                    syncManager.syncDataToGoogleSheet(allExpenses.value)
                }
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Automatic Google Sheets sync failure after bank sync", e)
            }
        }
    }

    fun disconnectBank(connection: BankConnection) {
        viewModelScope.launch {
            val updated = connection.copy(isConnected = false, lastSynced = 0L)
            repository.saveBankConnection(updated)
        }
    }

    fun exportExpensesToCsv(context: Context, periodMonths: Int): Intent? {
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val expenses = when (periodMonths) {
            1 -> filteredExpenses.value
            2 -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -2)
                allExpenses.value.filter { it.timestamp >= cal.timeInMillis }
            }
            3 -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -3)
                allExpenses.value.filter { it.timestamp >= cal.timeInMillis }
            }
            6 -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -6)
                allExpenses.value.filter { it.timestamp >= cal.timeInMillis }
            }
            12 -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -12)
                allExpenses.value.filter { it.timestamp >= cal.timeInMillis }
            }
            else -> allExpenses.value
        }

        if (expenses.isEmpty()) {
            Toast.makeText(context, "No financial transactions found to export for the selected range.", Toast.LENGTH_SHORT).show()
            return null
        }

        try {
            val label = when (periodMonths) {
                1 -> "Month_${_selectedMonth.value}"
                2 -> "Last_2_Months"
                3 -> "Last_3_Months_Quarter"
                6 -> "Last_6_Months_Halfyearly"
                12 -> "Last_12_Months_Annually"
                else -> "All_Time_Ledger"
            }
            val fileName = "expenses_report_$label.csv"
            val file = File(context.cacheDir, fileName)
            file.printWriter().use { out ->
                out.println("=========================================")
                out.println("SMART EXPENSE TRACKER - FINANCIAL REPORT")
                out.println("=========================================")
                out.println("Export Scope: ${label.replace("_", " ")}")
                out.println("Generated on: ${sdf.format(Date(now))}")
                out.println()
                
                val grandIn = expenses.filter { it.category == "Income" }.sumOf { it.amount }
                val grandOut = expenses.filter { it.category != "Income" }.sumOf { it.amount }
                val grandNet = grandIn - grandOut
                
                out.println("--- GRAND PERIOD SUMMARY ---")
                out.println("Grand Total Income     : INR ${String.format(Locale.US, "%,.2f", grandIn)}")
                out.println("Grand Total Expenditure: INR ${String.format(Locale.US, "%,.2f", grandOut)}")
                out.println("Grand Net Savings      : INR ${String.format(Locale.US, "%,.2f", grandNet)}")
                out.println("=========================================")
                out.println()
                
                val monthGroupSdf = SimpleDateFormat("yyyy-MM", Locale.US)
                val monthDisplaySdf = SimpleDateFormat("MMMM yyyy", Locale.US)
                val itemSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                
                val grouped = expenses.groupBy { monthGroupSdf.format(Date(it.timestamp)) }
                    .toSortedMap(compareByDescending { it })
                    
                grouped.forEach { (monthStr, monthExpenses) ->
                    val monthIn = monthExpenses.filter { it.category == "Income" }.sumOf { it.amount }
                    val monthOut = monthExpenses.filter { it.category != "Income" }.sumOf { it.amount }
                    val monthNet = monthIn - monthOut
                    
                    var displayTitle = monthStr
                    try {
                        val dateObj = SimpleDateFormat("yyyy-MM", Locale.US).parse(monthStr)
                        if (dateObj != null) {
                            displayTitle = monthDisplaySdf.format(dateObj)
                        }
                    } catch(e: Exception) {}
                    
                    out.println("=========================================")
                    out.println("STATEMENT FOR: ${displayTitle.uppercase()}")
                    out.println("=========================================")
                    out.println("Month Income: INR ${String.format(Locale.US, "%,.2f", monthIn)}")
                    out.println("Month Spend : INR ${String.format(Locale.US, "%,.2f", monthOut)}")
                    out.println("Month Net   : INR ${String.format(Locale.US, "%,.2f", monthNet)}")
                    out.println("-----------------------------------------")
                    out.println("ID,Title,Amount,Category,Date,Type,Bank,Notes")
                    
                    monthExpenses.forEach { expense ->
                        val cleanTitle = expense.title.replace(",", " ")
                        val cleanNote = (expense.note ?: "").replace(",", " ")
                        val dateStr = itemSdf.format(Date(expense.timestamp))
                        val isSynced = if (expense.isBankSynced) "Synced" else "Manual"
                        val bank = expense.bankName ?: "N/A"
                        out.println("${expense.id},\"$cleanTitle\",${expense.amount},\"${expense.category}\",\"$dateStr\",\"$isSynced\",\"$bank\",\"$cleanNote\"")
                    }
                    out.println()
                }
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Smart Expense Tracker Report: ${label.replace("_", " ")}")
                putExtra(Intent.EXTRA_TEXT, "Hello, here is your customized financial spreadsheet report for ${label.replace("_", " ")}.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return Intent.createChooser(shareIntent, "Export Report & Share...")
        } catch (e: Exception) {
            Toast.makeText(context, "Error compiling report: ${e.message}", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    // Gmail cloud backup
    fun composeGmailBackup(context: Context, recipientEmail: String = "dhiraj.bitu18@gmail.com") {
        val expenses = allExpenses.value
        if (expenses.isEmpty()) {
            Toast.makeText(context, "No expenses logged to back up yet.", Toast.LENGTH_SHORT).show()
            return
        }

        val backupData = StringBuilder()
        backupData.append("----- SMART EXPENSE TRACKER - BACKUP RECORD -----\n")
        backupData.append("Export Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        backupData.append("User Email Target: $recipientEmail\n")
        backupData.append("Total Records: ${expenses.size}\n")
        backupData.append("-------------------------------------------\n\n")

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        expenses.forEach {
            val syncedMark = if (it.isBankSynced) "[Bank: ${it.bankName}]" else "[Manual]"
            backupData.append("${sdf.format(Date(it.timestamp))} | ${it.category.padEnd(20)} | \u20B9${String.format(Locale.US, "%.2f", it.amount).padEnd(10)} | ${it.title} $syncedMark | Note: ${it.note ?: ""}\n")
        }

        val subject = "Smart Expense Tracker Cloud Backup - ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}"
        val bodyText = "Hello,\n\nHere is your full database backup containing all recorded expenses, automatic bank syncs, and manual entries.\n\n$backupData\n\nSincerely,\nYour Wallet Guardian"

        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, bodyText)
        }

        try {
            context.startActivity(Intent.createChooser(emailIntent, "Send Backup to Gmail..."))
        } catch (e: Exception) {
            Toast.makeText(context, "No email client application found on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    // Get Months list for selection: last 6 months + next 2
    fun getAvailableMonthsList(): List<String> {
        val months = mutableListOf<String>()
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        
        // Go 5 months back, current month, and 1 month ahead
        cal.add(Calendar.MONTH, -5)
        for (i in 0..7) {
            months.add(sdf.format(cal.time))
            cal.add(Calendar.MONTH, 1)
        }
        return months
    }

    // ------------ LOAN & SUBSCRIPTION RECURRING DEBITS & CRUD ------------
 
    fun checkAndProcessRecurringDebits() {
        viewModelScope.launch {
            val today = Calendar.getInstance()
            val todayDay = today.get(Calendar.DAY_OF_MONTH)
            val currentMonthStr = getTodayMonthYearString() // "yyyy-MM"
            val currentUserEmail = _authUserEmail.value?.lowercase() ?: ""
 
            // Process Loans
            repository.allLoans.first()
                .filter { it.userEmail.lowercase() == currentUserEmail }
                .forEach { loan ->
                    // If today is on or past the due day, and has not been processed for this month yet
                    if (todayDay >= loan.dueDateDay && loan.lastProcessedMonth != currentMonthStr) {
                        val emiExpense = Expense(
                            title = "Loan EMI Auto-Debit: ${loan.title}",
                            amount = loan.emiAmount,
                            category = "Bills & Utilities",
                            timestamp = System.currentTimeMillis(),
                            isBankSynced = false,
                            note = "Automated EMI repayment for ${loan.title} (INR ${loan.emiAmount})",
                            userEmail = currentUserEmail
                        )
                        repository.addExpense(emiExpense)
                        repository.saveLoan(loan.copy(lastProcessedMonth = currentMonthStr))
                    }
                }
 
            // Process Subscriptions
            repository.allSubscriptions.first()
                .filter { it.userEmail.lowercase() == currentUserEmail }
                .forEach { sub ->
                    // If today is on or past the sub due day, and has not been processed for this month yet
                    if (todayDay >= sub.dueDay && sub.lastProcessedMonth != currentMonthStr) {
                        val subExpense = Expense(
                            title = "Sub Renewal Auto-Debit: ${sub.title}",
                            amount = sub.amount,
                            category = "Bills & Utilities",
                            timestamp = System.currentTimeMillis(),
                            isBankSynced = false,
                            note = "Automated subscription charge for ${sub.title} (INR ${sub.amount})",
                            userEmail = currentUserEmail
                        )
                        repository.addExpense(subExpense)
                        repository.saveSubscription(sub.copy(lastProcessedMonth = currentMonthStr))
                    }
                }
        }
    }
 
    fun addLoan(title: String, totalAmount: Double, tenureMonths: Int, emiAmount: Double, dueDateDay: Int) {
        viewModelScope.launch {
            val loan = Loan(
                title = title,
                totalAmount = totalAmount,
                tenureMonths = tenureMonths,
                emiAmount = emiAmount,
                dueDateDay = dueDateDay,
                lastProcessedMonth = null,
                userEmail = _authUserEmail.value?.lowercase() ?: ""
            )
            repository.saveLoan(loan)
            delay(100)
            checkAndProcessRecurringDebits()
        }
    }
 
    fun updateLoan(loan: Loan) {
        viewModelScope.launch {
            repository.saveLoan(loan)
            delay(100)
            checkAndProcessRecurringDebits()
        }
    }
 
    fun deleteLoan(loan: Loan) {
        viewModelScope.launch {
            repository.removeLoan(loan)
        }
    }
 
    fun addSubscription(title: String, amount: Double, dueDay: Int) {
        viewModelScope.launch {
            val sub = Subscription(
                title = title,
                amount = amount,
                dueDay = dueDay,
                lastProcessedMonth = null,
                userEmail = _authUserEmail.value?.lowercase() ?: ""
            )
            repository.saveSubscription(sub)
            delay(100)
            checkAndProcessRecurringDebits()
        }
    }

    fun updateSubscription(sub: Subscription) {
        viewModelScope.launch {
            repository.saveSubscription(sub)
            delay(100)
            checkAndProcessRecurringDebits()
        }
    }

    fun deleteSubscription(sub: Subscription) {
        viewModelScope.launch {
            repository.removeSubscription(sub)
        }
    }

    // --- Gemini Wallet Guardian Support States & Functions ---

    private val _aiAnalysisState = MutableStateFlow<AiAnalysisState>(AiAnalysisState.Idle)
    val aiAnalysisState: StateFlow<AiAnalysisState> = _aiAnalysisState.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun generateAnalysis() {
        val email = _authUserEmail.value?.lowercase() ?: ""
        if (email.isEmpty()) {
            _aiAnalysisState.value = AiAnalysisState.Error("Please log in to study your expenses.")
            return
        }

        _aiAnalysisState.value = AiAnalysisState.Loading

        viewModelScope.launch {
            try {
                val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    _aiAnalysisState.value = AiAnalysisState.Error("Gemini API key is not configured. Please add GEMINI_API_KEY in the Secrets panel.")
                    return@launch
                }

                // Gather all logged-in user details:
                val contextDataStr = FinancialContextBuilder.buildFinancialContextPrompt(
                    email = email,
                    expenses = allExpenses.value,
                    limits = budgetLimits.value,
                    banks = bankConnections.value,
                    loans = loans.value,
                    subs = subscriptions.value
                )

                val prompt = "Based on my active financial data below, perform a holistic, multi-dimensional analysis. Identify overspending risks, budget efficiency, debt/auto-debits drain ratios, and offer 3 highly actionable steps written in bulleted format. Ground your assessment purely in these numbers."

                val systemInstructionText = """
                    You are "Wallet Guardian AI", a friendly, empathetic, and expert personal finance assistant built into the app.
                    Your job is to study the active logged-in user's authentic expenses, budgets, bank balances, auto-debits, and recurring subscriptions to provide bespoke financial intelligence and support.
                    Always keep advice extremely actionable, positive, and professional. Focus on visual clarity and brevity using bullet points and bolding of numbers.
                """.trimIndent()

                val request = GeminiGenerateRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = "Financial Context:\n$contextDataStr\n\nUser Request: $prompt")
                            )
                        )
                    ),
                    systemInstruction = GeminiContent(
                        parts = listOf(GeminiPart(text = systemInstructionText))
                    )
                )

                val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Could not generate analysis. Please try again."

                _aiAnalysisState.value = AiAnalysisState.Success(textResponse)
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Error in Gemini Analysis: ${e.message}", e)
                _aiAnalysisState.value = AiAnalysisState.Error("Error connecting to Gemini: ${e.localizedMessage ?: "Unknown Error"}")
            }
        }
    }

    fun sendChatMessage(text: String) {
        val email = _authUserEmail.value?.lowercase() ?: ""
        if (email.isEmpty()) {
            _chatHistory.value = _chatHistory.value + ChatMessage("Chat unavailable: Please sign in.", false)
            return
        }

        val userMsg = ChatMessage(text, true)
        _chatHistory.value = _chatHistory.value + userMsg
        _isChatLoading.value = true

        viewModelScope.launch {
            try {
                val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    _chatHistory.value = _chatHistory.value + ChatMessage(
                        "Error: Gemini API key is not configured. Please add GEMINI_API_KEY in the Secrets panel.", 
                        false
                    )
                    _isChatLoading.value = false
                    return@launch
                }

                // Compile all logged-in user details
                val contextDataStr = FinancialContextBuilder.buildFinancialContextPrompt(
                    email = email,
                    expenses = allExpenses.value,
                    limits = budgetLimits.value,
                    banks = bankConnections.value,
                    loans = loans.value,
                    subs = subscriptions.value
                )

                val systemInstructionText = """
                    You are "Wallet Guardian AI", a friendly, empathetic, and expert personal finance assistant.
                    Your job is to study the active logged-in user's authentic expenses, budgets, bank balances, auto-debits, and recurring subscriptions to provide bespoke financial intelligence and support.
                    Always keep advice extremely actionable, positive, and professional. Focus on visual clarity and brevity using bullet points and bolding of numbers.
                    Ground all advice strictly in the provided User Financial Context, keeping answers concise and easy to read.
                """.trimIndent()

                // Formulate the conversation contents list, including the background financial profile.
                val contentsList = mutableListOf<GeminiContent>()
                
                // Add the user context profile first to orient Gemini
                contentsList.add(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = "User Financial Profile Context:\n$contextDataStr")),
                        role = "user"
                    )
                )
                contentsList.add(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = "Understood. I will ground my answers strictly in this transaction data and limits.")),
                        role = "model"
                    )
                )

                // Map the conversation history up to the last 10 messages to avoid context overflow:
                val historyToUse = _chatHistory.value.takeLast(10)
                historyToUse.forEach { msg ->
                    contentsList.add(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = msg.text)),
                            role = if (msg.isUser) "user" else "model"
                        )
                    )
                }

                val request = GeminiGenerateRequest(
                    contents = contentsList,
                    systemInstruction = GeminiContent(
                        parts = listOf(GeminiPart(text = systemInstructionText))
                    )
                )

                val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "I'm having trouble analyzing that. Let me know if you would like me to try again!"

                _chatHistory.value = _chatHistory.value + ChatMessage(textResponse, false)
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Error in Gemini Chat: ${e.message}", e)
                _chatHistory.value = _chatHistory.value + ChatMessage(
                    "Error executing query: ${e.localizedMessage ?: "Unknown Error"}. Please verify your network connection.", 
                    false
                )
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun clearChatHistory() {
        _chatHistory.value = emptyList()
    }

    companion object {
        fun getTodayMonthYearString(): String {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            return sdf.format(Date()) // Local device time. On 2026-06-07 it returns "2026-06"
        }

        fun getStartEndMilliOfMonth(monthYearStr: String): Pair<Long, Long> {
            try {
                val parts = monthYearStr.split("-")
                val year = parts[0].toInt()
                val month = parts[1].toInt() - 1 // 0-based for calendar
                val cal = Calendar.getInstance()
                cal.clear()
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val startMilli = cal.timeInMillis

                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val endMilli = cal.timeInMillis

                return Pair(startMilli, endMilli)
            } catch (e: Exception) {
                // Fallback to current month limits
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val startMilli = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val endMilli = cal.timeInMillis
                return Pair(startMilli, endMilli)
            }
        }
    }
}

class ExpenseViewModelFactory(
    private val repository: ExpenseRepository,
    private val syncManager: GoogleSheetsSyncManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(repository, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface AiAnalysisState {
    object Idle : AiAnalysisState
    object Loading : AiAnalysisState
    data class Success(val analysis: String) : AiAnalysisState
    data class Error(val message: String) : AiAnalysisState
}
