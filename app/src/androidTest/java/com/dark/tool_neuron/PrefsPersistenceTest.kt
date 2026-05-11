package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PrefsPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryptor: HxsEncryptor

    @Before
    fun setup() {
        File(context.filesDir, "app_bootstrap").deleteRecursively()
        File(context.filesDir, "app_prefs").deleteRecursively()
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
        HexStorage().close()
        encryptor = HxsEncryptor()
    }

    @After
    fun cleanup() {
        HexStorage().close()
        BootIntegrity.setRelaxedForTesting(false)
        PolicyEngine.resetForTesting()
    }

    @Test
    fun prefsPersistAcrossSimulatedProcessRestart() {
        val keyStoreA = AppKeyStore(context)
        val prefsA = AppPreferences(context, keyStoreA, encryptor)

        prefsA.onboardingComplete = true
        prefsA.securitySetupDone = true
        prefsA.modelSetupDone = true
        prefsA.putString("theme_mode", "DARK_EXPRESSIVE_OBSIDIAN")
        prefsA.putString("theme_palette", "INDIGO_FOREST_CHARCOAL")
        prefsA.putBytes("ml_dsa_seed", ByteArray(64) { (it * 7 + 3).toByte() })

        assertEquals(
            "written theme_mode visible within same process",
            "DARK_EXPRESSIVE_OBSIDIAN",
            prefsA.getString("theme_mode"),
        )

        HexStorage().close()

        val keyStoreB = AppKeyStore(context)
        val prefsB = AppPreferences(context, keyStoreB, encryptor)

        assertTrue(
            "onboardingComplete must persist across simulated process restart",
            prefsB.onboardingComplete,
        )
        assertTrue(
            "securitySetupDone must persist across simulated process restart",
            prefsB.securitySetupDone,
        )
        assertTrue(
            "modelSetupDone must persist across simulated process restart",
            prefsB.modelSetupDone,
        )
        assertEquals(
            "long theme string must persist across simulated process restart",
            "DARK_EXPRESSIVE_OBSIDIAN",
            prefsB.getString("theme_mode"),
        )
        assertEquals(
            "palette string must persist across simulated process restart",
            "INDIGO_FOREST_CHARCOAL",
            prefsB.getString("theme_palette"),
        )
        assertArrayEquals(
            "byte array must persist across simulated process restart",
            ByteArray(64) { (it * 7 + 3).toByte() },
            prefsB.getBytes("ml_dsa_seed"),
        )
    }

    @Test
    fun dekIsStableAcrossSequentialKeyStoreInstances() {
        val firstInstance = AppKeyStore(context)
        val firstDek = firstInstance.unwrapOrCreateDek().copyOf()

        HexStorage().close()

        val secondInstance = AppKeyStore(context)
        val secondDek = secondInstance.unwrapOrCreateDek().copyOf()

        assertArrayEquals(
            "DEK must be identical after wrap/unwrap cycle — otherwise app_prefs decrypts with wrong key",
            firstDek,
            secondDek,
        )
    }

    @Test
    fun concurrentKeyStoreConstructionOnFreshBootstrapCannotBeSimulatedInSingleProcess() {
        // Cross-process DEK race cannot be reproduced in-process: hxs.cpp uses global singletons (g_manifest, g_collections, g_crypto), so two HexStorage instances share them. The fix (TNApplication.isMainProcess()) prevents :inference from opening the vault at all; end-to-end verification is a manual twin-launch logcat check.
        val keyStore = AppKeyStore(context)
        val prefs = AppPreferences(context, keyStore, encryptor)
        prefs.putString("canary", "ok")
        assertEquals("canary", "ok", prefs.getString("canary"))
        assertTrue(
            "app_prefs vault must exist after write",
            File(context.filesDir, "app_prefs/app_prefs.hxs").exists(),
        )
    }
}
