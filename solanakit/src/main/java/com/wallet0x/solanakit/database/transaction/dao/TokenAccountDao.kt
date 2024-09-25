package com.wallet0x.solanakit.database.transaction.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import com.wallet0x.solanakit.models.FullTokenAccount
import com.wallet0x.solanakit.models.MintAccount
import com.wallet0x.solanakit.models.TokenAccount

@Dao
interface TokenAccountDao {

    @Query("SELECT * FROM TokenAccount WHERE mintAddress=:address LIMIT 1")
    fun getByMintAddress(address: String): TokenAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(tokenAccount: TokenAccount)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(balance: List<TokenAccount>)

    @Query("SELECT * FROM TokenAccount WHERE mintAddress=:mintAddress LIMIT 1")
    fun get(mintAddress: String): TokenAccountWrapper?

    @Query("SELECT * FROM TokenAccount WHERE mintAddress IN (:mintAddresses)")
    fun get(mintAddresses: List<String>): List<TokenAccount>

    @Query("SELECT * FROM TokenAccount")
    fun getAll(): List<TokenAccount>

    @Query("SELECT * FROM TokenAccount")
    fun getAllFullAccounts(): List<TokenAccountWrapper>

    data class TokenAccountWrapper(
        @Embedded
        val tokenAccount: TokenAccount,

        @Relation(
            parentColumn = "mintAddress",
            entityColumn = "address"
        )
        val mintAccount: MintAccount
    ) {

        val fullTokenAccount: FullTokenAccount
            get() = FullTokenAccount(tokenAccount, mintAccount)

    }

}
