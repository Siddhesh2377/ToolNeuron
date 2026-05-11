package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.LockoutPolicy
import com.dark.tool_neuron.data.RootGuard
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.data.SessionHolder
import com.dark.tool_neuron.data.VerifyResult
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ResilienceTest {

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
    fun clockRollback_extendsBackoffEvenWithCorrectPin() {
        security.setPassword("482913")

        val realNow = 10_000_000_000L
        security.verifyPassword("bad1", nowMs = realNow)
        security.verifyPassword("bad2", nowMs = realNow)

        val past = realNow - (24 * 60 * 60_000L)
        val result = security.verifyPassword("482913", nowMs = past)
        assertTrue(
            "rolling clock backward must NOT simply reset backoff to success",
            result !is VerifyResult.Success,
        )
        val state = prefs.readAuthState()
        assertTrue(
            "clock rollback must bump failedAttempts to penalize",
            state.failedAttempts >= 3,
        )
    }

    @Test
    fun clockRollback_insideGraceWindowIsTolerated() {
        security.setPassword("482913")

        val now = 10_000_000_000L
        security.verifyPassword("482913", nowMs = now)
        session.clear()

        val slightSkew = now - 30_000L
        val ok = security.verifyPassword("482913", nowMs = slightSkew)
        assertTrue(
            "small NTP-style skew (~30s) inside grace window still allows correct PIN",
            ok === VerifyResult.Success,
        )
    }

    @Test
    fun root_guardReturnsCleanOnStockEmulator() {
        val guard = RootGuard(context)
        val scan = guard.scan()
        assertTrue(
            "stock emulator must not trigger root detection on allowlisted su paths",
            scan is RootGuard.Result.Clean,
        )
    }

    @Test
    fun lockout_lastSeenIsPersistedAcrossReads() {
        security.setPassword("482913")
        val before = System.currentTimeMillis()
        security.verifyPassword("482913", nowMs = before)
        val state = prefs.readAuthState()
        assertTrue("lastSeenNowMs recorded after success", state.lastSeenNowMs >= before)
    }

    @Test
    fun lockout_backoffBaseUsesMonotonicMax() {
        security.setPassword("482913")
        val big = 50_000_000_000L

        repeat(4) { security.verifyPassword("x", nowMs = big) }
        val state = prefs.readAuthState()
        val expected = big + LockoutPolicy.backoffMillis(4)
        assertTrue("nextAttemptAtMs pushed forward from big", state.nextAttemptAtMs >= expected)
    }
}
