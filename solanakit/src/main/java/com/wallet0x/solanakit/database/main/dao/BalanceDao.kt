package com.wallet0x.solanakit.database.main.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wallet0x.solanakit.models.BalanceEntity

@Dao
interface BalanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balance: BalanceEntity)

    @Query("SELECT * FROM BalanceEntity")
    fun getBalance(): BalanceEntity?

}
