package com.wallet0x.solanakit.models

import com.solana.core.PublicKey

data class Address(val publicKey: PublicKey) {

    constructor(pubkeyString: String) : this(PublicKey(pubkeyString))

    override fun toString() = publicKey.toBase58()

}
