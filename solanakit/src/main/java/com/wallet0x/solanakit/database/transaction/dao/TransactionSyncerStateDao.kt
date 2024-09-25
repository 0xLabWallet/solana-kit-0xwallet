package com.wallet0x.solanakit.database.transaction.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wallet0x.solanakit.models.LastSyncedTransaction

@Dao
interface TransactionSyncerStateDao {

    @Query("SELECT * FROM LastSyncedTransaction WHERE syncSourceName = :syncSourceName LIMIT 1")
    fun get(syncSourceName: String) : LastSyncedTransaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(transactionSyncerState: LastSyncedTransaction)

}
