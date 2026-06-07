package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Double,
    val category: String, // e.g. "Food", "Bills", "Shopping", "Transport", "Entertainment", "Income", "Others"
    val timestamp: Long, // Date-time of transaction
    val isBankSynced: Boolean = false,
    val bankName: String? = null,
    val note: String? = null,
    val userEmail: String = ""
)

@Entity(tableName = "budget_limits", primaryKeys = ["category", "userEmail"])
data class BudgetLimit(
    val category: String, // String ID as category name
    val monthlyLimit: Double,
    val userEmail: String = ""
)

@Entity(tableName = "bank_connections", primaryKeys = ["bankName", "userEmail"])
data class BankConnection(
    val bankName: String, // Chase, Bank of America, Wells Fargo, Citibank
    val accountName: String,
    val balance: Double,
    val isConnected: Boolean,
    val lastSynced: Long,
    val userEmail: String = ""
)

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val totalAmount: Double,
    val tenureMonths: Int,
    val emiAmount: Double,
    val dueDateDay: Int, // 1 to 31
    val lastProcessedMonth: String? = null, // YYYY-MM
    val userEmail: String = ""
)

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Double,
    val dueDay: Int, // 1 to 31
    val lastProcessedMonth: String? = null, // YYYY-MM
    val userEmail: String = ""
)

@Dao
interface ExpenseDao {
    // Expense Queries
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startOfMilli AND timestamp <= :endOfMilli ORDER BY timestamp DESC")
    fun getExpensesForPeriod(startOfMilli: Long, endOfMilli: Long): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<Expense>)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses")
    suspend fun clearAllExpenses()

    // Budget Limit Queries
    @Query("SELECT * FROM budget_limits")
    fun getAllBudgetLimits(): Flow<List<BudgetLimit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetLimit(limit: BudgetLimit)

    @Delete
    suspend fun deleteBudgetLimit(limit: BudgetLimit)

    // Bank Connection Queries
    @Query("SELECT * FROM bank_connections")
    fun getAllBankConnections(): Flow<List<BankConnection>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankConnection(connection: BankConnection)

    @Delete
    suspend fun deleteBankConnection(connection: BankConnection)

    // Loan Queries
    @Query("SELECT * FROM loans ORDER BY id DESC")
    fun getAllLoans(): Flow<List<Loan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: Loan)

    @Delete
    suspend fun deleteLoan(loan: Loan)

    // Subscription Queries
    @Query("SELECT * FROM subscriptions ORDER BY id DESC")
    fun getAllSubscriptions(): Flow<List<Subscription>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(sub: Subscription)

    @Delete
    suspend fun deleteSubscription(sub: Subscription)

    @Query("UPDATE expenses SET userEmail = :userEmail WHERE userEmail = '' OR userEmail IS NULL")
    suspend fun claimOrphanedExpenses(userEmail: String)

    @Query("UPDATE budget_limits SET userEmail = :userEmail WHERE userEmail = '' OR userEmail IS NULL")
    suspend fun claimOrphanedBudgetLimits(userEmail: String)

    @Query("UPDATE bank_connections SET userEmail = :userEmail WHERE userEmail = '' OR userEmail IS NULL")
    suspend fun claimOrphanedBankConnections(userEmail: String)

    @Query("UPDATE loans SET userEmail = :userEmail WHERE userEmail = '' OR userEmail IS NULL")
    suspend fun claimOrphanedLoans(userEmail: String)

    @Query("UPDATE subscriptions SET userEmail = :userEmail WHERE userEmail = '' OR userEmail IS NULL")
    suspend fun claimOrphanedSubscriptions(userEmail: String)
}

@Database(
    entities = [Expense::class, BudgetLimit::class, BankConnection::class, Loan::class, Subscription::class],
    version = 3,
    exportSchema = false
)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract val dao: ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        fun getDatabase(context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "expense_tracker_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
