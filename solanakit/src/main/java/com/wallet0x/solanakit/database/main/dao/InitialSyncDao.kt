package com.wallet0x.solanakit.database.main.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wallet0x.solanakit.models.InitialSyncEntity

@Dao
interface InitialSyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: InitialSyncEntity)

    @Query("SELECT * FROM InitialSyncEntity")
    fun getAllEntities(): List<InitialSyncEntity>
}