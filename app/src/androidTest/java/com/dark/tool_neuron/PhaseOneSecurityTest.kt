package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.AuthNative
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.AuthState
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.data.SessionHolder
import com.dark.tool_neuron.data.VerifyResult
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PhaseOneSecurityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryptor: HxsEncryptor
    private lateinit var keyStore: AppKeyStore

    @Before
    fun setup() {
        File(context.filesDir, "app_bootstrap").deleteRecursively()
        File(context.filesDir, "app_prefs").deleteRecursively()
        PolicyEngine.resetForTesting()
        encryptor = HxsEncryptor()
        keyStore = AppKeyStore(context)
    }

    @After
    fun cleanup() {
        PolicyEngine.resetForTesting()
    }

    @Test
    fun policyEngine_blocksGatedFeaturesWithoutSession() {
        PolicyEngine.invalidateSession()
        PolicyEngine.setPassthrough(false)

        assertTrue(
            "APP_LAUNCH must always be allowed",
            PolicyEngine.isAllowed(PolicyEngine.Feature.APP_LAUNCH),
        )
        assertTrue(
            "UI_PASSWORD_SCREEN is unauth-allowed",
            PolicyEngine.isAllowed(PolicyEngine.Feature.UI_PASSWORD_SCREEN),
        )
        assertFalse(
            "UI_SETTINGS must require a session",
            PolicyEngine.isAllowed(PolicyEngine.Feature.UI_SETTINGS),
        )
        assertFalse(
            "PRO features must never open in community build",
            PolicyEngine.isAllowed(PolicyEngine.Feature.PRO_EXPORT_CHATS),
        )
    }

    @Test
    fun policyEngine_passthroughAllowsUnauthenticatedGatedFeatures() {
        PolicyEngine.setPassthrough(true)
        assertTrue(PolicyEngine.isAllowed(PolicyEngine.Feature.UI_SETTINGS))
        assertFalse(
            "PRO features still blocked under passthrough",
            PolicyEngine.isAllowed(PolicyEngine.Feature.PRO_UNLIMITED_CONTEXT),
        )
    }

    @Test
    fun policyEngine_tamperFlipPermanentlyDenies() {
        PolicyEngine.setPassthrough(true)
        assertTrue(PolicyEngine.isAllowed(PolicyEngine.Feature.UI_HOME))

        PolicyEngine.markTampered()
        assertTrue(PolicyEngine.isTampered())
        assertFalse(PolicyEngine.isAllowed(PolicyEngine.Feature.APP_LAUNCH))
        assertFalse(PolicyEngine.isAllowed(PolicyEngine.Feature.UI_HOME))
    }

    @Test
    fun authNative_setupThenVerifyIssuesSessionToken() {
        val pin = "482913".toByteArray()
        val setupResult = AuthNative.setup(pin)
        assertEquals("salt is 16 bytes", 16, setupResult.salt.size)
        assertEquals("hash is 32 bytes", 32, setupResult.hash.size)

        val token = AuthNative.verify(pin, setupResult.salt, setupResult.hash)
        assertNotNull("verify returns token", token)
        assertEquals("token is 32 bytes", 32, token!!.size)
        assertTrue("session registered with PolicyEngine", PolicyEngine.hasSession())

        val wrongPin = "999999".toByteArray()
        val wrongToken = AuthNative.verify(wrongPin, setupResult.salt, setupResult.hash)
        assertNull("wrong PIN returns null token", wrongToken)
    }

    @Test
    fun authNative_sessionTokenGatesFeatures() {
        val pin = "mySecretPin12".toByteArray()
        val r = AuthNative.setup(pin)
        val token = AuthNative.verify(pin, r.salt, r.hash)!!

        assertTrue(PolicyEngine.isAllowed(PolicyEngine.Feature.UI_SETTINGS, token))

        val forged = ByteArray(32) { 0x41 }
        assertFalse(
            "forged token must not unlock gated features",
            PolicyEngine.isAllowed(PolicyEngine.Feature.UI_SETTINGS, forged),
        )
    }

    @Test
    fun appKeyStore_dekIsStableAcrossCalls() {
        val a = keyStore.unwrapOrCreateDek()
        val b = keyStore.unwrapOrCreateDek()
        assertArrayEquals(a, b)
        assertEquals("DEK is 32 bytes", 32, a.size)
    }

    @Test
    fun appKeyStore_dekRegeneratesAfterWipe() {
        val first = keyStore.unwrapOrCreateDek().copyOf()
        keyStore.wipe()
        val second = AppKeyStore(context).unwrapOrCreateDek()
        assertNotEquals(
            "fresh DEK after wipe",
            first.toList(),
            second.toList(),
        )
    }

    @Test
    fun appPreferences_authStateRoundTripsSealed() {
        val prefs = AppPreferences(context, keyStore, encryptor)
        val state = AuthState(
            securityMode = AppPreferences.SECURITY_APP_PASSWORD,
            salt = ByteArray(16) { it.toByte() },
            hash = ByteArray(32) { (it + 10).toByte() },
        )
        prefs.writeAuthState(state)
        val roundtrip = prefs.readAuthState()
        assertEquals(state, roundtrip)
    }

    @Test
    fun appPreferences_vaultOpensEncryptedAndHidesPlaintext() {
        val prefs = AppPreferences(context, keyStore, encryptor)
        prefs.putString("my_secret_key", "HORSE-BATTERY-STAPLE")
        prefs.putBoolean("onboarding_complete", true)

        val vaultDir = File(context.filesDir, "app_prefs")
        val needle = "HORSE-BATTERY-STAPLE".toByteArray()

        var found = false
        vaultDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val bytes = file.readBytes()
            if (containsSubsequence(bytes, needle)) found = true
        }
        assertFalse("plaintext must not appear on disk in encrypted vault", found)
    }

    @Test
    fun securityManager_verifyThenDisableLockClearsLock() {
        val prefs = AppPreferences(context, keyStore, encryptor)
        val session = SessionHolder(encryptor)
        val security = SecurityManager(prefs, session, encryptor, keyStore)

        security.setPassword("1234")
        assertTrue("lock is enabled after setPassword", security.isLockEnabled)

        assertFalse(
            "disableLock refuses without a session",
            security.disableLock(),
        )

        val outcome = security.verifyPassword("1234")
        assertTrue("verify succeeds", outcome === VerifyResult.Success)
        assertTrue(
            "disableLock succeeds after verify",
            security.disableLock(),
        )
        assertFalse("lock disabled", security.isLockEnabled)
    }

    @Test
    fun appBootstrap_resistsTamperedWrappedDek() {
        val first = keyStore.unwrapOrCreateDek().copyOf()

        val bootstrap = HexStorage()
        val dir = File(context.filesDir, "app_bootstrap").absolutePath
        bootstrap.openPlaintext(dir)
        bootstrap.dropCollection("bootstrap")
        bootstrap.flushAll()

        val fresh = AppKeyStore(context).unwrapOrCreateDek()
        assertNotEquals(
            "wiped bootstrap → fresh DEK",
            first.toList(),
            fresh.toList(),
        )
    }

    private fun containsSubsequence(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }
}
