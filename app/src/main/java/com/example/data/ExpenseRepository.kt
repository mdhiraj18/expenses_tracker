package com.example.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class ExpenseRepository(private val dao: ExpenseDao) {

    val allExpenses: Flow<List<Expense>> = dao.getAllExpenses()
    val allBudgetLimits: Flow<List<BudgetLimit>> = dao.getAllBudgetLimits()
    val allBankConnections: Flow<List<BankConnection>> = dao.getAllBankConnections()
    val allLoans: Flow<List<Loan>> = dao.getAllLoans()
    val allSubscriptions: Flow<List<Subscription>> = dao.getAllSubscriptions()

    fun getExpensesForPeriod(startMilli: Long, endMilli: Long): Flow<List<Expense>> {
        return dao.getExpensesForPeriod(startMilli, endMilli)
    }

    suspend fun saveLoan(loan: Loan) {
        dao.insertLoan(loan)
    }

    suspend fun removeLoan(loan: Loan) {
        dao.deleteLoan(loan)
    }

    suspend fun saveSubscription(sub: Subscription) {
        dao.insertSubscription(sub)
    }

    suspend fun removeSubscription(sub: Subscription) {
        dao.deleteSubscription(sub)
    }

    suspend fun addExpense(expense: Expense) {
        dao.insertExpense(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        dao.deleteExpense(expense)
    }

    suspend fun clearAllExpenses() {
        dao.clearAllExpenses()
    }

    suspend fun saveBudgetLimit(limit: BudgetLimit) {
        dao.insertBudgetLimit(limit)
    }

    suspend fun removeBudgetLimit(limit: BudgetLimit) {
        dao.deleteBudgetLimit(limit)
    }

    suspend fun saveBankConnection(connection: BankConnection) {
        dao.insertBankConnection(connection)
    }

    suspend fun disconnectBank(connection: BankConnection) {
        dao.deleteBankConnection(connection)
    }

    suspend fun claimOrphanedRecords(userEmail: String) {
        val trimmedEmail = userEmail.trim().lowercase()
        if (trimmedEmail.isNotEmpty()) {
            dao.claimOrphanedExpenses(trimmedEmail)
            dao.claimOrphanedBudgetLimits(trimmedEmail)
            dao.claimOrphanedBankConnections(trimmedEmail)
            dao.claimOrphanedLoans(trimmedEmail)
            dao.claimOrphanedSubscriptions(trimmedEmail)
        }
    }

    // Prepopulate some budget limits if they are empty
    suspend fun prepopulateDefaultLimits(userEmail: String = "") {
        val trimmedEmail = userEmail.trim().lowercase()
        listOf(
            BudgetLimit("Food & Dining", 600.0, trimmedEmail),
            BudgetLimit("Shopping & Lifestyle", 300.0, trimmedEmail),
            BudgetLimit("Bills & Utilities", 500.0, trimmedEmail),
            BudgetLimit("Transport & Auto", 200.0, trimmedEmail),
            BudgetLimit("Entertainment", 250.0, trimmedEmail),
            BudgetLimit("Others", 150.0, trimmedEmail)
        ).forEach {
            dao.insertBudgetLimit(it)
        }
    }

    // Prepopulate some connected banks for simulation
    suspend fun prepopulateDefaultBanks(userEmail: String = "") {
        val trimmedEmail = userEmail.trim().lowercase()
        listOf(
            BankConnection("Chase Platinum", "Checking ...4920", 5432.18, true, System.currentTimeMillis(), trimmedEmail),
            BankConnection("Bank of America", "Savings ...9182", 12450.50, false, 0L, trimmedEmail),
            BankConnection("Wells Fargo Core", "Credit ...3311", -841.20, false, 0L, trimmedEmail)
        ).forEach {
            dao.insertBankConnection(it)
        }
    }

    // Auto-categorize based on common merchant description
    fun autoCategorize(description: String): String {
        val lower = description.lowercase()
        return when {
            lower.contains("starbucks") || lower.contains("mcdonald") || lower.contains("burger") || 
            lower.contains("restaurant") || lower.contains("food") || lower.contains("grocery") || 
            lower.contains("market") || lower.contains("trader joes") || lower.contains("whole foods") ||
            lower.contains("deli") || lower.contains("pizza") || lower.contains("cafe") -> "Food & Dining"

            lower.contains("netflix") || lower.contains("spotify") || lower.contains("disney") || 
            lower.contains("cinema") || lower.contains("theatre") || lower.contains("ticket") || 
            lower.contains("game") || lower.contains("arcade") || lower.contains("entertainment") -> "Entertainment"

            lower.contains("uber") || lower.contains("lyft") || lower.contains("gas") || 
            lower.contains("shell") || lower.contains("chevron") || lower.contains("transit") || 
            lower.contains("parking") || lower.contains("subway") || lower.contains("train") -> "Transport & Auto"

            lower.contains("comcast") || lower.contains("at&t") || lower.contains("verizon") || 
            lower.contains("electric") || lower.contains("pge") || lower.contains("water") || 
            lower.contains("utility") || lower.contains("insurance") || lower.contains("rent") || 
            lower.contains("mobile") -> "Bills & Utilities"

            lower.contains("amazon") || lower.contains("target") || lower.contains("walmart") || 
            lower.contains("nike") || lower.contains("clothing") || lower.contains("mall") || 
            lower.contains("shoes") || lower.contains("lifestyle") || lower.contains("apple") -> "Shopping & Lifestyle"

            lower.contains("salary") || lower.contains("payroll") || lower.contains("dividend") || 
            lower.contains("deposit") || lower.contains("bonus") || lower.contains("cashin") -> "Income"

            else -> "Others"
        }
    }

    // Simulate standard transactions for a bank connection
    suspend fun simulateBankSync(bankName: String, userEmail: String = ""): List<Expense> {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) // 0-based

        // Generate 6 sample transactions spread out over the current month
        val simulatedList = mutableListOf<Expense>()
        
        val merchants = listOf(
            Triple("Whole Foods Grocery", -86.50, "Food & Dining"),
            Triple("Uber Ride Airport", -32.00, "Transport & Auto"),
            Triple("Netflix Monthly Subscription", -15.49, "Entertainment"),
            Triple("Chevron Fueling Station", -45.00, "Transport & Auto"),
            Triple("Target Department Store", -112.30, "Shopping & Lifestyle"),
            Triple("PG&E Utility Invoice", -135.00, "Bills & Utilities"),
            Triple("Enterprise Monthly Interest", 2.10, "Income"),
            Triple("Acme Corp Payroll", 2850.00, "Income")
        )

        merchants.forEachIndexed { index, (descr, amt, category) ->
            // Distribute across days of current month
            val customCal = Calendar.getInstance()
            customCal.set(Calendar.YEAR, currentYear)
            customCal.set(Calendar.MONTH, currentMonth)
            customCal.set(Calendar.DAY_OF_MONTH, (index * 3) + 2) // days 2, 5, 8, etc.
            customCal.set(Calendar.HOUR_OF_DAY, 11)
            customCal.set(Calendar.MINUTE, 30)

            simulatedList.add(
                Expense(
                    title = descr,
                    amount = if (amt < 0) -amt else amt,
                    category = if (amt < 0) category else "Income",
                    timestamp = customCal.timeInMillis,
                    isBankSynced = true,
                    bankName = bankName,
                    note = "Auto-synchronized from $bankName",
                    userEmail = userEmail
                )
            )
        }

        // Save generated transactions to DB
        dao.insertExpenses(simulatedList)
        return simulatedList
    }
}
