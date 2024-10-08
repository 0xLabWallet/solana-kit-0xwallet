package com.wallet0x.solanakit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity
data class Transaction(
    @PrimaryKey
    val hash: String,
    val timestamp: Long,
    val fee: BigDecimal? = null,
    val from: String? = null,
    val to: String? = null,
    val amount: BigDecimal? = null,
    val error: String? = null,
    val pending: Boolean = false
)
