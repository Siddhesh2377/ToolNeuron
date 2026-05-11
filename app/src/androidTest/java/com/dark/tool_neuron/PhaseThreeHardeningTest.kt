package com.dark.tool_neuron

import android.content.ClipDescription
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.AuthState
import com.dark.tool_neuron.data.LockoutPolicy
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.data.SessionHolder
import com.dark.tool_neuron.data.VerifyResult
import com.dark.tool_neuron.util.SecureClipboard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PhaseThreeHardeningTest {

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
    fun lockout_counterIncrementsOnWrongPin() {
        security.setPassword("4242")

        val bad = security.verifyPassword("9999")
        assertTrue(bad === VerifyResult.WrongPin)

        val state = prefs.readAuthState()
        assertEquals(1, state.failedAttempts)
        assertEquals(0L, state.nextAttemptAtMs)
    }

    @Test
    fun lockout_successResetsCounter() {
        security.setPassword("4242")

        security.verifyPassword("9999")
        security.verifyPassword("9998")
        assertEquals(2, prefs.readAuthState().failedAttempts)

        val ok = security.verifyPassword("4242")
        assertTrue(ok === VerifyResult.Success)
        assertEquals(0, prefs.readAuthState().failedAttempts)
    }

    @Test
    fun lockout_exponentialBackoffTriggersAt4thFail() {
        security.setPassword("4242")
        val start = 1_000_000L

        security.verifyPassword("a", nowMs = start)
        security.verifyPassword("b", nowMs = start)
        security.verifyPassword("c", nowMs = start)
        val fourth = security.verifyPassword("d", nowMs = start)

        assertTrue(fourth is VerifyResult.LockedOut)
        val expected = start + LockoutPolicy.backoffMillis(4)
        assertEquals(expected, (fourth as VerifyResult.LockedOut).retryAtMs)
    }

    @Test
    fun lockout_rejectsAttemptsInsideBackoffWindow() {
        security.setPassword("4242")
        val t0 = 1_000_000L

        repeat(4) { security.verifyPassword("x", nowMs = t0) }

        val t1 = t0 + 1_000L
        val result = security.verifyPassword("4242", nowMs = t1)
        assertTrue("backoff window blocks even correct PIN", result is VerifyResult.LockedOut)
    }

    @Test
    fun lockout_allowsAfterBackoffExpires() {
        security.setPassword("4242")
        val t0 = 1_000_000L

        repeat(4) { security.verifyPassword("x", nowMs = t0) }

        val after = t0 + LockoutPolicy.backoffMillis(4) + 1_000L
        val result = security.verifyPassword("4242", nowMs = after)
        assertTrue("correct PIN after backoff succeeds", result === VerifyResult.Success)
    }

    @Test
    fun lockout_wipeAfterNAttempts() {
        security.setPassword("4242")

        var lastResult: VerifyResult = VerifyResult.WrongPin
        var t = 1L
        for (i in 0 until LockoutPolicy.WIPE_THRESHOLD) {
            t += LockoutPolicy.backoffMillis(i + 1) + 1_000L
            lastResult = security.verifyPassword("wrong_$i", nowMs = t)
        }
        assertTrue("final attempt wipes the vault", lastResult === VerifyResult.Wiped)
        assertFalse("lock must be disabled after wipe", security.isLockEnabled)
    }

    @Test
    fun session_autoLockClearsOnClear() {
        security.setPassword("1234")
        security.verifyPassword("1234")
        assertTrue(session.active.value)

        session.clear()
        assertFalse("session cleared by auto-lock", session.active.value)
        assertFalse(
            "gated feature blocked after clear",
            PolicyEngine.isAllowed(PolicyEngine.Feature.UI_SETTINGS, session.get()),
        )
    }

    @Test
    fun clipboard_sensitiveFlagSetOnTiramisuAndUp() {
        assumeTrue("IS_SENSITIVE requires API 33+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val clip = SecureClipboard.buildClipData("test", "secret-payload")
        val extras = clip.description.extras
        assertNotNull("clip extras set by SecureClipboard", extras)
        assertTrue(
            "IS_SENSITIVE extra set on built clip",
            extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false),
        )
    }

    @Test
    fun integrity_detectionStringsAreObfuscatedInBinary() {
        val libPath = File(context.applicationInfo.nativeLibraryDir, "libhxs_encryptor.so")
        assumeTrue("libhxs_encryptor.so must ship with the APK", libPath.exists())

        val raw = libPath.readBytes()
        val needles = listOf(
            "frida".toByteArray(),
            "linjector".toByteArray(),
            "XposedBridge".toByteArray(),
            "LSPosed".toByteArray(),
            "TracerPid:".toByteArray(),
        )
        for (needle in needles) {
            assertFalse(
                "plaintext ${String(needle)} must not appear in shipping .so",
                containsSubsequence(raw, needle),
            )
        }
    }

    @Test
    fun authstate_v2EncodesAndDecodesBackoffFields() {
        val state = AuthState(
            securityMode = AppPreferences.SECURITY_APP_PASSWORD,
            salt = ByteArray(16) { it.toByte() },
            hash = ByteArray(32) { (it + 3).toByte() },
            failedAttempts = 5,
            nextAttemptAtMs = 1_700_000_000_000L,
        )
        val roundtrip = AuthState.decode(state.encode())
        assertEquals(state, roundtrip)
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
