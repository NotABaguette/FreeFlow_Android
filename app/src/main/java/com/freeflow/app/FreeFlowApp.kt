package com.freeflow.app

import android.app.Application
import com.freeflow.app.data.AppRepository
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class FreeFlowApp : Application() {

    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Register Bouncy Castle provider for X25519 + ChaCha20-Poly1305
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())

        repository = AppRepository(this)
    }
}
