package com.example.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import android.util.Log

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateRequest
    ): GeminiGenerateResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

object FinancialContextBuilder {
    fun buildFinancialContextPrompt(
        email: String,
        expenses: List<Expense>,
        limits: List<BudgetLimit>,
        banks: List<BankConnection>,
        loans: List<Loan>,
        subs: List<Subscription>
    ): String {
        val filterEmail = email.trim().lowercase()
        val userExpenses = expenses.filter { it.userEmail.trim().lowercase() == filterEmail }
        val userLimits = limits.filter { it.userEmail.trim().lowercase() == filterEmail }
        val userBanks = banks.filter { it.userEmail.trim().lowercase() == filterEmail }
        val userLoans = loans.filter { it.userEmail.trim().lowercase() == filterEmail }
        val userSubs = subs.filter { it.userEmail.trim().lowercase() == filterEmail }

        val totalExpensesSum = userExpenses.sumOf { it.amount }
        val expenseBreakdown = userExpenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }

        val totalBalance = userBanks.sumOf { it.balance }

        val budgetStatus = userLimits.joinToString("\n") { limit ->
            val spentObj = expenseBreakdown[limit.category] ?: 0.0
            "- Category '${limit.category}': Spent $${"%.2f".format(spentObj)} out of monthly budget $${"%.2f".format(limit.monthlyLimit)}"
        }

        val debtStatus = userLoans.joinToString("\n") { loan ->
            "- Loan: '${loan.title}' | Total: $${"%.2f".format(loan.totalAmount)} | Monthly EMI: $${"%.2f".format(loan.emiAmount)} | Due Day: ${loan.dueDateDay}"
        }

        val subStatus = userSubs.joinToString("\n") { sub ->
            "- Subscription: '${sub.title}' | Monthly: $${"%.2f".format(sub.amount)} | Due Day: ${sub.dueDay}"
        }

        val bankStatus = userBanks.joinToString("\n") { bank ->
            "- Bank: '${bank.bankName}' (${bank.accountName}) | Balance: $${"%.2f".format(bank.balance)} | Connected: ${bank.isConnected}"
        }

        val recentTxStr = userExpenses.take(15).joinToString("\n") { tx ->
            val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            "- [${df.format(java.util.Date(tx.timestamp))}] Category: ${tx.category} | ${tx.title} | Amount: $${"%.2f".format(tx.amount)} ${if (tx.note.isNullOrBlank()) "" else "(${tx.note})"}"
        }

        return """
            User Email Session Profile: $filterEmail
            Active Logged-In User Financial State Checklist:
            - Combined Tracker Expenditure Sum: $${"%.2f".format(totalExpensesSum)} across ${userExpenses.size} items.
            - Combined Active Bank Connection Balance: $${"%.2f".format(totalBalance)} across ${userBanks.size} sources.
            
            Detailed Breakdowns:
            1. Current Spending by Category:
            ${expenseBreakdown.map { (cat, sum) -> "- $cat: $${"%.2f".format(sum)}" }.joinToString("\n").ifEmpty { "No expenses recorded yet." }}
            
            2. Monthly Budget Enforcements vs Actual Spent:
            ${budgetStatus.ifEmpty { "No active monthly category budget limits declared." }}
            
            3. Linked Banks & Balances:
            ${bankStatus.ifEmpty { "No local bank institutions connected yet." }}
            
            4. Loans & Debts (Subject to active Auto-Debts):
            ${debtStatus.ifEmpty { "No active loans/EMIs stored." }}
            
            5. Configured Over-the-Top Subscription Services:
            ${subStatus.ifEmpty { "No recurring subscriptions registered." }}
            
            6. Last 15 Transaction History:
            ${recentTxStr.ifEmpty { "No recent transactions found." }}
        """.trimIndent()
    }
}
