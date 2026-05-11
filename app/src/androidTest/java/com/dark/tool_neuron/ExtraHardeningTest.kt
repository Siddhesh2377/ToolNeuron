package com.dark.tool_neuron

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.PinStrength
import com.dark.tool_neuron.util.SecureClipboard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ExtraHardeningTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryptor: HxsEncryptor
    private lateinit var keyStore: AppKeyStore

    @Before
    fun setup() {
        File(context.filesDir, "app_bootstrap").deleteRecursively()
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
        encryptor = HxsEncryptor()
        keyStore = AppKeyStore(context)
    }

    @After
    fun cleanup() {
        BootIntegrity.setRelaxedForTesting(false)
        PolicyEngine.resetForTesting()
    }

    @Test
    fun keystore_backingIsHardwareOrExplicitlyKnown() {
        keyStore.unwrapOrCreateDek()
        val backing = keyStore.backing()
        assertTrue(
            "backing must be one of the known categories",
            backing == AppKeyStore.Backing.STRONGBOX ||
                backing == AppKeyStore.Backing.TEE ||
                backing == AppKeyStore.Backing.SOFTWARE_FALLBACK,
        )
    }

    @Test
    fun clipboard_autoClearEventuallyWipesSecret() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("baseline", "baseline-text"))
        val payload = "temporary-secret-${System.currentTimeMillis()}"
        SecureClipboard.copy(context, "test", payload, autoClearMs = 500L)

        val deadline = System.currentTimeMillis() + 5_000L
        var cleared = false
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(100L)
            val current = cm.primaryClip?.getItemAt(0)?.text?.toString()
            if (current != payload) {
                cleared = true
                break
            }
        }
        assertTrue("clipboard must be cleared within timeout", cleared)
        val afterClear = cm.primaryClip?.getItemAt(0)?.text?.toString()
        assertNotEquals(payload, afterClear)
    }

    @Test
    fun pinStrength_rejectsTooShort() {
        assertEquals(
            PinStrength.Result.TooShort(PinStrength.MIN_LENGTH),
            PinStrength.evaluate("123"),
        )
    }

    @Test
    fun pinStrength_rejectsRepeatedDigits() {
        assertEquals(PinStrength.Result.AllSameDigit, PinStrength.evaluate("000000"))
        assertEquals(PinStrength.Result.AllSameDigit, PinStrength.evaluate("7777777"))
    }

    @Test
    fun pinStrength_rejectsSequential() {
        assertEquals(PinStrength.Result.Sequential, PinStrength.evaluate("123456"))
        assertEquals(PinStrength.Result.Sequential, PinStrength.evaluate("654321"))
        assertEquals(PinStrength.Result.Sequential, PinStrength.evaluate("234567"))
    }

    @Test
    fun pinStrength_rejectsCommonlyUsed() {
        assertEquals(PinStrength.Result.CommonlyUsed, PinStrength.evaluate("121212"))
        assertEquals(PinStrength.Result.CommonlyUsed, PinStrength.evaluate("112233"))
    }

    @Test
    fun pinStrength_acceptsStrongPin() {
        assertEquals(PinStrength.Result.Ok, PinStrength.evaluate("284013"))
        assertEquals(PinStrength.Result.Ok, PinStrength.evaluate("7391824"))
    }
}
