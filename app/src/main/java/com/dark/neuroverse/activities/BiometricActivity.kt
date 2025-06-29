package com.dark.neuroverse.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dark.neuroverse.utils.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class BiometricActivity : FragmentActivity() {

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var executor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = this

        Log.d("BiometricActivity", "Started biometric authentication")

//        startActivity(Intent(this@BiometricActivity, MainActivity::class.java))
//        finish()

        CoroutineScope(Dispatchers.Main).launch {
            UserPrefs.isTermsAccepted(context).collect {
                when (it) {
                    true -> {
                        auth()
                    }

                    false -> {
                        startActivity(Intent(this@BiometricActivity, SetUpActivity::class.java))
                        finish()
                    }
                }
            }
        }
    }

    private fun auth() {
        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(
            this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d("BiometricActivity", "Authentication succeeded")
                    startActivity(Intent(this@BiometricActivity, MainActivity::class.java))
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.d("BiometricActivity", "Authentication error: $errString")
                    Toast.makeText(
                        this@BiometricActivity,
                        "Authentication error: $errString",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.d("BiometricActivity", "Authentication failed")
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock NeuroVerse")
            .setSubtitle("Authenticate with fingerprint or device PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPrompt.authenticate(promptInfo)
        } else {
            Toast.makeText(this, "Biometric not Enabled, Any One Can See Your Data ...", Toast.LENGTH_LONG).show()
            Log.d("BiometricActivity", "Biometric not available or not enrolled")
            startActivity(Intent(this@BiometricActivity, MainActivity::class.java))
            finish()
        }
    }
}


