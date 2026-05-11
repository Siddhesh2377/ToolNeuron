package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AccessibilityGuard
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.AuthState
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.data.SessionHolder
import com.dark.tool_neuron.data.VerifyResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PhaseFourDepthTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryptor: HxsEncryptor
    private lateinit var keyStore: AppKeyStore
    private lateinit var prefs: AppPreferences
    private lateinit var session: SessionHolder
    private lateinit var security: SecurityManager

    @Before
    fun setup() {
        File(context.filesDir, "app_bootstrap").deleteRecursively()
        File(context.filesDir, "app_prefs").deleteRecursively()
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
        encryptor = HxsEncryptor()
        keyStore = AppKeyStore(context)
        prefs = AppPreferences(context, keyStore, encryptor)
        session = SessionHolder(encryptor)
        security = SecurityManager(prefs, session, encryptor, keyStore)
    }

    @After
    fun cleanup() {
        BootIntegrity.setRelaxedForTesting(false)
        PolicyEngine.resetForTesting()
    }

    @Test
    fun hookBaselines_verifyPassesOnCleanRuntime() {
        assertTrue(
            "hook baselines must verify on a clean process",
            BootIntegrity.verifyHookBaselines(),
        )
    }

    @Test
    fun a11y_guardReturnsCleanOnEmulator() {
        val guard = AccessibilityGuard(context)
        val scan = guard.scan()
        assertTrue(
            "emulator w/ no a11y services must be Clean or Unknown",
            scan is AccessibilityGuard.Result.Clean || scan is AccessibilityGuard.Result.Unknown,
        )
    }

    @Test
    fun panic_pinTriggersWipeEvenOnFirstEntry() {
        security.setPassword("123456")
        security.verifyPassword("123456")
        assertTrue("panic PIN can be set with active session", security.setPanicPin("000000"))
        session.clear()

        val outcome = security.verifyPassword("000000")
        assertTrue("panic PIN must wipe", outcome === VerifyResult.Wiped)
        assertFalse("lock cleared after panic wipe", security.isLockEnabled)
    }

    @Test
    fun panic_wrongPinDoesNotTriggerPanicPath() {
        security.setPassword("123456")
        security.verifyPassword("123456")
        security.setPanicPin("000000")
        session.clear()

        val outcome = security.verifyPassword("987654")
        assertTrue(
            "random wrong PIN must be WrongPin, not Wiped",
            outcome === VerifyResult.WrongPin,
        )
        assertTrue("lock still enabled", security.isLockEnabled)
    }

    @Test
    fun panic_realPinStillWorksWhenPanicSet() {
        security.setPassword("123456")
        security.verifyPassword("123456")
        security.setPanicPin("000000")
        session.clear()

        val outcome = security.verifyPassword("123456")
        assertTrue("real PIN still succeeds", outcome === VerifyResult.Success)
    }

    @Test
    fun panic_clearPanicPinRemovesPanicPath() {
        security.setPassword("123456")
        security.verifyPassword("123456")
        security.setPanicPin("000000")

        assertTrue(security.clearPanicPin())
        session.clear()

        assertTrue("after clear, lock still enabled", security.isLockEnabled)
        val outcome = security.verifyPassword("000000")
        assertTrue(
            "without panic, the ex-panic-PIN is just another wrong PIN",
            outcome === VerifyResult.WrongPin,
        )
    }

    @Test
    fun authstate_v3EncodesPanicFields() {
        val state = AuthState(
            securityMode = AppPreferences.SECURITY_APP_PASSWORD,
            salt = ByteArray(16) { it.toByte() },
            hash = ByteArray(32) { it.toByte() },
            failedAttempts = 2,
            nextAttemptAtMs = 7L,
            panicSalt = ByteArray(16) { (it + 100).toByte() },
            panicHash = ByteArray(32) { (it + 50).toByte() },
        )
        val roundtrip = AuthState.decode(state.encode())
        assertEquals(state, roundtrip)
        assertTrue(roundtrip.hasPanic)
    }

    @Test
    fun authstate_v3DecodesV2ForwardCompatibly() {
        val v2State = AuthState(
            securityMode = AppPreferences.SECURITY_APP_PASSWORD,
            salt = ByteArray(16) { 1 },
            hash = ByteArray(32) { 2 },
            failedAttempts = 3,
            nextAttemptAtMs = 1234L,
        )
        val encoded = v2State.encode()
        val roundtrip = AuthState.decode(encoded)
        assertEquals(v2State.copy(), roundtrip)
        assertFalse("no panic when not set", roundtrip.hasPanic)
    }
}
