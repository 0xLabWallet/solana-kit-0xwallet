package com.wallet0x.solanakit

import android.app.Application
import android.content.Context
import com.metaplex.lib.programs.token_metadata.accounts.MetadataAccountJsonAdapterFactory
import com.metaplex.lib.programs.token_metadata.accounts.MetadataAccountRule
import com.solana.actions.Action
import com.solana.api.Api
import com.solana.networking.Network
import com.solana.networking.NetworkingRouterConfig
import com.solana.networking.OkHttpNetworkingRouter
import com.wallet0x.solanakit.core.BalanceManager
import com.wallet0x.solanakit.core.ISyncListener
import com.wallet0x.solanakit.core.SolanaDatabaseManager
import com.wallet0x.solanakit.core.SyncManager
import com.wallet0x.solanakit.core.TokenAccountManager
import com.wallet0x.solanakit.database.main.MainStorage
import com.wallet0x.solanakit.database.transaction.TransactionStorage
import com.wallet0x.solanakit.models.Address
import com.wallet0x.solanakit.models.FullTokenAccount
import com.wallet0x.solanakit.models.FullTransaction
import com.wallet0x.solanakit.models.RpcSource
import com.wallet0x.solanakit.network.ConnectionManager
import com.wallet0x.solanakit.noderpc.ApiSyncer
import com.wallet0x.solanakit.noderpc.NftClient
import com.wallet0x.solanakit.transactions.PendingTransactionSyncer
import com.wallet0x.solanakit.transactions.SolanaFmService
import com.wallet0x.solanakit.transactions.SolscanClient
import com.wallet0x.solanakit.transactions.TransactionManager
import com.wallet0x.solanakit.transactions.TransactionSyncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.math.BigDecimal
import java.util.Objects

class SolanaKit(
    private val apiSyncer: ApiSyncer,
    private val balanceManager: BalanceManager,
    private val tokenAccountManager: TokenAccountManager,
    private val transactionManager: TransactionManager,
    private val syncManager: SyncManager,
    rpcSource: RpcSource,
    private val address: Address,
) : ISyncListener {

    private var scope: CoroutineScope? = null

    private val _balanceSyncStateFlow = MutableStateFlow(syncState)
    private val _tokenBalanceSyncStateFlow = MutableStateFlow(tokenBalanceSyncState)
    private val _transactionsSyncStateFlow = MutableStateFlow(transactionsSyncState)

    private val _lastBlockHeightFlow = MutableStateFlow(lastBlockHeight)
    private val _balanceFlow = MutableStateFlow(balance)

    val isMainnet: Boolean = rpcSource.endpoint.network == Network.mainnetBeta
    val receiveAddress = address.publicKey.toBase58()

    val lastBlockHeight: Long?
        get() = apiSyncer.lastBlockHeight
    val lastBlockHeightFlow: StateFlow<Long?> = _lastBlockHeightFlow

    // Balance API
    val syncState: com.wallet0x.solanakit.SolanaKit.SyncState
        get() = syncManager.balanceSyncState
    val balanceSyncStateFlow: StateFlow<com.wallet0x.solanakit.SolanaKit.SyncState> = _balanceSyncStateFlow
    val balance: Long?
        get() = balanceManager.balance
    val balanceFlow: StateFlow<Long?> = _balanceFlow

    // Token accounts API
    val tokenBalanceSyncState: com.wallet0x.solanakit.SolanaKit.SyncState
        get() = syncManager.tokenBalanceSyncState
    val tokenBalanceSyncStateFlow: StateFlow<com.wallet0x.solanakit.SolanaKit.SyncState> = _tokenBalanceSyncStateFlow
    val fungibleTokenAccountsFlow: Flow<List<FullTokenAccount>> = tokenAccountManager.newTokenAccountsFlow.map { tokenAccounts ->
        tokenAccounts.filter { !it.mintAccount.isNft }
    }
    val nonFungibleTokenAccountsFlow: Flow<List<FullTokenAccount>> = tokenAccountManager.tokenAccountsFlow.map { tokenAccounts ->
        tokenAccounts.filter { it.mintAccount.isNft }
    }

    fun tokenAccount(mintAddress: String): FullTokenAccount? =
        tokenAccountManager.fullTokenAccount(mintAddress)

    fun tokenAccountFlow(mintAddress: String): Flow<FullTokenAccount> = tokenAccountManager.tokenBalanceFlow(mintAddress)

    // Transactions API
    val transactionsSyncState: com.wallet0x.solanakit.SolanaKit.SyncState
        get() = syncManager.transactionsSyncState
    val transactionsSyncStateFlow: StateFlow<com.wallet0x.solanakit.SolanaKit.SyncState> = _transactionsSyncStateFlow

    fun allTransactionsFlow(incoming: Boolean?): Flow<List<FullTransaction>> = transactionManager.allTransactionsFlow(incoming)
    fun solTransactionsFlow(incoming: Boolean?): Flow<List<FullTransaction>> = transactionManager.solTransactionsFlow(incoming)
    fun splTransactionsFlow(mintAddress: String, incoming: Boolean?): Flow<List<FullTransaction>> = transactionManager.splTransactionsFlow(mintAddress, incoming)

    fun start() {
        scope = CoroutineScope(Dispatchers.IO)
        scope?.launch {
            syncManager.start(this)
        }
    }

    fun stop() {
        syncManager.stop()
        scope?.cancel()
    }

    fun refresh() {
        if (scope?.isActive != true) return

        scope?.launch {
            syncManager.refresh(this)
        }
    }

    fun debugInfo(): String {
        val lines = mutableListOf<String>()
        lines.add("ADDRESS: $address")
        return lines.joinToString { "\n" }
    }

    fun statusInfo(): Map<String, Any> {
        val statusInfo = LinkedHashMap<String, Any>()

        statusInfo["Last Block Height"] = lastBlockHeight ?: 0L
        statusInfo["Sync State"] = syncState

        return statusInfo
    }

    fun addTokenAccount(mintAddress: String, decimals: Int) {
        tokenAccountManager.addTokenAccount(receiveAddress, mintAddress, decimals)

        refresh()
    }

    override fun onUpdateLastBlockHeight(lastBlockHeight: Long) {
        _lastBlockHeightFlow.tryEmit(lastBlockHeight)
    }

    override fun onUpdateBalanceSyncState(syncState: com.wallet0x.solanakit.SolanaKit.SyncState) {
        _balanceSyncStateFlow.tryEmit(syncState)
    }

    override fun onUpdateBalance(balance: Long) {
        _balanceFlow.tryEmit(balance)
    }

    override fun onUpdateTokenSyncState(syncState: com.wallet0x.solanakit.SolanaKit.SyncState) {
        _tokenBalanceSyncStateFlow.tryEmit(syncState)
    }

    override fun onUpdateTransactionSyncState(syncState: com.wallet0x.solanakit.SolanaKit.SyncState) {
        _transactionsSyncStateFlow.tryEmit(syncState)
    }

    suspend fun getAllTransactions(incoming: Boolean? = null, fromHash: String? = null, limit: Int? = null): List<FullTransaction> =
        transactionManager.getAllTransaction(incoming, fromHash, limit)

    suspend fun getSolTransactions(incoming: Boolean? = null, fromHash: String? = null, limit: Int? = null): List<FullTransaction> =
        transactionManager.getSolTransaction(incoming, fromHash, limit)

    suspend fun getSplTransactions(
        mintAddress: String,
        incoming: Boolean? = null,
        fromHash: String? = null,
        limit: Int? = null
    ): List<FullTransaction> =
        transactionManager.getSplTransaction(mintAddress, incoming, fromHash, limit)

    suspend fun sendSol(toAddress: Address, amount: Long, signer: com.wallet0x.solanakit.Signer): FullTransaction =
        transactionManager.sendSol(toAddress, amount, signer.account)

    suspend fun sendSpl(mintAddress: Address, toAddress: Address, amount: Long, signer: com.wallet0x.solanakit.Signer): FullTransaction =
        transactionManager.sendSpl(mintAddress, toAddress, amount, signer.account)

    fun fungibleTokenAccounts(): List<FullTokenAccount> =
        tokenAccountManager.tokenAccounts().filter { !it.mintAccount.isNft }

    fun nonFungibleTokenAccounts(): List<FullTokenAccount> =
        tokenAccountManager.tokenAccounts().filter { it.mintAccount.isNft }

    sealed class SyncState {
        class Synced : com.wallet0x.solanakit.SolanaKit.SyncState()
        class NotSynced(val error: Throwable) : com.wallet0x.solanakit.SolanaKit.SyncState()
        class Syncing(val progress: Double? = null) : com.wallet0x.solanakit.SolanaKit.SyncState()

        override fun toString(): String = when (this) {
            is com.wallet0x.solanakit.SolanaKit.SyncState.Syncing -> "Syncing ${progress?.let { "${it * 100}" } ?: ""}"
            is com.wallet0x.solanakit.SolanaKit.SyncState.NotSynced -> "NotSynced ${error.javaClass.simpleName} - message: ${error.message}"
            else -> this.javaClass.simpleName
        }

        override fun equals(other: Any?): Boolean {
            if (other !is com.wallet0x.solanakit.SolanaKit.SyncState)
                return false

            if (other.javaClass != this.javaClass)
                return false

            if (other is com.wallet0x.solanakit.SolanaKit.SyncState.Syncing && this is com.wallet0x.solanakit.SolanaKit.SyncState.Syncing) {
                return other.progress == this.progress
            }

            return true
        }

        override fun hashCode(): Int {
            if (this is com.wallet0x.solanakit.SolanaKit.SyncState.Syncing) {
                return Objects.hashCode(this.progress)
            }
            return Objects.hashCode(this.javaClass.name)
        }
    }

    open class SyncError : Exception() {
        class NotStarted : com.wallet0x.solanakit.SolanaKit.SyncError()
        class NoNetworkConnection : com.wallet0x.solanakit.SolanaKit.SyncError()
    }

    companion object {

        val fee = BigDecimal(0.000005)
        // Solana network will not store a SOL account with less than ~0.001 SOL.
        // Which means you can't have a SOL account with 0 SOL stored on the network.
        val accountRentAmount = BigDecimal(0.001)


        fun getInstance(
            application: Application,
            addressString: String,
            rpcSource: RpcSource,
            walletId: String,
            solscanApiKey: String,
            debug: Boolean = false
        ): com.wallet0x.solanakit.SolanaKit {
            val httpClient = com.wallet0x.solanakit.SolanaKit.Companion.httpClient(debug)
            val config = NetworkingRouterConfig(listOf(MetadataAccountRule()), listOf(MetadataAccountJsonAdapterFactory()))
            val router = OkHttpNetworkingRouter(rpcSource.endpoint, httpClient, config)
            val connectionManager = ConnectionManager(application)

            val mainDatabase = SolanaDatabaseManager.getMainDatabase(application, walletId)
            val mainStorage = MainStorage(mainDatabase)

            val rpcApiClient = Api(router)
            val nftClient = NftClient(rpcApiClient)
            val rpcAction = Action(rpcApiClient, listOf())
            val apiSyncer = ApiSyncer(rpcApiClient, rpcSource.syncInterval, connectionManager, mainStorage)
            val address = Address(addressString)

            val balanceManager = BalanceManager(address.publicKey, rpcApiClient, mainStorage)

            val transactionDatabase = SolanaDatabaseManager.getTransactionDatabase(application, walletId)
            val transactionStorage = TransactionStorage(transactionDatabase, addressString)
            val solscanClient = SolscanClient(solscanApiKey, debug)
            val tokenAccountManager = TokenAccountManager(addressString, rpcApiClient, transactionStorage, mainStorage, SolanaFmService())
            val transactionManager = TransactionManager(address, transactionStorage, rpcAction, tokenAccountManager)
            val pendingTransactionSyncer = PendingTransactionSyncer(rpcApiClient, transactionStorage, transactionManager)
            val transactionSyncer = TransactionSyncer(
                address.publicKey,
                rpcApiClient,
                solscanClient,
                nftClient,
                transactionStorage,
                transactionManager,
                pendingTransactionSyncer
            )

            val syncManager = SyncManager(apiSyncer, balanceManager, tokenAccountManager, transactionSyncer, transactionManager)

            val kit = com.wallet0x.solanakit.SolanaKit(
                apiSyncer,
                balanceManager,
                tokenAccountManager,
                transactionManager,
                syncManager,
                rpcSource,
                address
            )
            syncManager.listener = kit

            return kit
        }

        fun clear(context: Context, walletId: String) {
            SolanaDatabaseManager.clear(context, walletId)
        }

        private fun httpClient(debug: Boolean): OkHttpClient {
            val headersInterceptor = Interceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                chain.proceed(requestBuilder.build())
            }

            val client = OkHttpClient.Builder()
            client.addInterceptor(headersInterceptor)

            if (debug) {
                val logging = HttpLoggingInterceptor()
                logging.level = HttpLoggingInterceptor.Level.BODY
                client.addInterceptor(logging)
            }

            return client.build()
        }

    }

}
