package com.wallet0x.solanakit.database.transaction

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wallet0x.solanakit.database.transaction.dao.MintAccountDao
import com.wallet0x.solanakit.database.transaction.dao.TokenAccountDao
import com.wallet0x.solanakit.database.transaction.dao.TransactionSyncerStateDao
import com.wallet0x.solanakit.database.transaction.dao.TransactionsDao
import com.wallet0x.solanakit.models.LastSyncedTransaction
import com.wallet0x.solanakit.models.MintAccount
import com.wallet0x.solanakit.models.TokenAccount
import com.wallet0x.solanakit.models.TokenTransfer
import com.wallet0x.solanakit.models.Transaction

@Database(
    entities = [
        LastSyncedTransaction::class,
        MintAccount::class,
        TokenTransfer::class,
        Transaction::class,
        TokenAccount::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class TransactionDatabase : RoomDatabase() {

    abstract fun transactionSyncerStateDao(): TransactionSyncerStateDao
    abstract fun transactionsDao(): TransactionsDao
    abstract fun mintAccountDao(): MintAccountDao
    abstract fun tokenAccountsDao(): TokenAccountDao

    companion object {

        fun getInstance(context: Context, databaseName: String): TransactionDatabase {
            return Room.databaseBuilder(context, TransactionDatabase::class.java, databaseName)
//                .setQueryCallback({ sqlQuery, bindArgs ->
//                    println("SQL Query: $sqlQuery SQL Args: $bindArgs")
//                }, Executors.newSingleThreadExecutor())
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
        }

    }

}
