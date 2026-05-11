package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.NativeIntegrity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PhaseTwoIntegrityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryptor: HxsEncryptor
    private lateinit var keyStore: AppKeyStore
    private lateinit var prefs: AppPreferences
    private lateinit var integrity: NativeIntegrity

    @Before
    fun setup() {
        File(context.filesDir, "app_bootstrap").deleteRecursively()
        File(context.filesDir, "app_prefs").deleteRecursively()
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
        encryptor = HxsEncryptor()
        keyStore = AppKeyStore(context)
        prefs = AppPreferences(context, keyStore, encryptor)
        integrity = NativeIntegrity(context, prefs)
    }

    @After
    fun cleanup() {
        BootIntegrity.setRelaxedForTesting(false)
        PolicyEngine.resetForTesting()
    }

    companion object {
        private const val MANIFEST_KEY = "native_lib_manifest_v1"
        private const val SIGNER_KEY = "apk_signer_hash_v1"
    }

    @Test
    fun boot_firstRunTofuStoresManifestAndSigner() {
        prefs.deleteKey(MANIFEST_KEY)
        prefs.deleteKey(SIGNER_KEY)

        val outcome = integrity.bootVerify()
        assertTrue("first-run verify succeeds", outcome.ok)
        assertTrue("first-run flag set", outcome.firstInstall)
        assertEquals(BootIntegrity.FAIL_NONE, outcome.reasons)

        val signerHash = integrity.storedApkSignerHash()
        assertNotNull("APK signer hash must be recorded on first run", signerHash)
        assertEquals("signer SHA-256 is 32 bytes", 32, signerHash!!.size)
    }

    @Test
    fun boot_secondRunReusesManifestAndPasses() {
        prefs.deleteKey(MANIFEST_KEY)

        val first = integrity.bootVerify()
        assertTrue(first.ok)
        assertTrue(first.firstInstall)

        val second = integrity.bootVerify()
        assertTrue("second run passes", second.ok)
        assertFalse("second run is not first-install", second.firstInstall)
    }

    @Test
    fun boot_tamperedLibDetectedAsHashMismatch() {
        prefs.deleteKey(MANIFEST_KEY)
        integrity.bootVerify()

        val genuine = prefs.getBytes(MANIFEST_KEY)!!.copyOf()
        val forged = genuine.copyOf()
        var flipped = false
        for (i in forged.indices.reversed()) {
            val b = forged[i].toInt() and 0xFF
            if (b != 0) {
                forged[i] = (b xor 0x55).toByte()
                flipped = true
                break
            }
        }
        assertTrue(flipped)
        prefs.putBytes(MANIFEST_KEY, forged)

        val outcome = integrity.bootVerify()
        assertFalse("forged manifest must fail verify", outcome.ok)
        assertTrue(
            "reason must include lib hash bit",
            (outcome.reasons and BootIntegrity.FAIL_LIB_HASH) != 0,
        )
    }

    @Test
    fun boot_hardFailMarksPolicyTamperedUnderRelaxedMode() {
        assertFalse(PolicyEngine.isTampered())
        BootIntegrity.hardFail(BootIntegrity.FAIL_LIB_HASH)
        assertTrue("hardFail flips policy to tampered", PolicyEngine.isTampered())
    }

    @Test
    fun boot_scanEnvironmentReturnsCleanOnEmulator() {
        val reasons = integrity.scanProcessEnvironment()
        assertEquals(
            "emulator without Frida/Xposed must be clean",
            BootIntegrity.FAIL_NONE,
            reasons,
        )
    }

    @Test
    fun boot_verifyApiRejectsMismatchedArrayLengths() {
        val result = BootIntegrity.verify(
            arrayOf("/tmp/does-not-matter.so"),
            arrayOf(ByteArray(32), ByteArray(32)),
        )
        assertTrue(
            "mismatched arrays must return bad-input bit",
            (result and BootIntegrity.FAIL_BAD_INPUT) != 0,
        )
    }

    @Test
    fun boot_verifyApiRejectsNon32ByteHash() {
        val result = BootIntegrity.verify(
            arrayOf("/tmp/does-not-matter.so"),
            arrayOf(ByteArray(16)),
        )
        assertTrue(
            "short hash must return bad-input bit",
            (result and BootIntegrity.FAIL_BAD_INPUT) != 0,
        )
    }
}
