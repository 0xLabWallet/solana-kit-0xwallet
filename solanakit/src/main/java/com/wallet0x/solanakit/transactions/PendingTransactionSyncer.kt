package com.wallet0x.solanakit.transactions

import com.solana.api.Api
import com.solana.rxsolana.api.getConfirmedTransaction
import com.wallet0x.solanakit.database.transaction.TransactionStorage
import com.wallet0x.solanakit.models.Transaction
import kotlinx.coroutines.rx2.await
import java.util.logging.Logger

class PendingTransactionSyncer(
    private val rpcClient: Api,
    private val storage: TransactionStorage,
    private val transactionManager: TransactionManager
) {
    private val logger = Logger.getLogger("PendingTransactionSyncer")

    suspend fun sync() {
        val updatedTransactions = mutableListOf<Transaction>()

        storage.pendingTransactions().forEach { pendingTx ->
            try {
                val confirmedTransaction = rpcClient.getConfirmedTransaction(pendingTx.hash).await()
                confirmedTransaction.meta?.let { meta ->
                    updatedTransactions.add(
                        pendingTx.copy(pending = false, error = meta.err?.toString())
                    )
                }
            } catch (error: Throwable) {
                logger.info("getConfirmedTx exception ${error.message ?: error.javaClass.simpleName}")
            }
        }

        if (updatedTransactions.isNotEmpty()) {
            storage.updateTransactions(updatedTransactions)

            val updatedTxHashes = updatedTransactions.map { it.hash }
            val fullTransactions = storage.getFullTransactions(updatedTxHashes)
            transactionManager.handle(fullTransactions, listOf())
        }
    }

}
