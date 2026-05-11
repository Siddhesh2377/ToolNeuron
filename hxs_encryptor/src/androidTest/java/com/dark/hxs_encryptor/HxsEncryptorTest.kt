package com.dark.hxs_encryptor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HxsEncryptorTest {

    private lateinit var enc: HxsEncryptor

    @Before
    fun setup() {
        enc = HxsEncryptor()
    }


    @Test
    fun randomBytes_returnsCorrectSize() {
        val bytes = enc.randomBytes(32)
        assertEquals(32, bytes.size)
    }

    @Test
    fun randomBytes_differentEachCall() {
        val a = enc.randomBytes(32)
        val b = enc.randomBytes(32)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun randomBytes_variousSizes() {
        assertEquals(1, enc.randomBytes(1).size)
        assertEquals(64, enc.randomBytes(64).size)
        assertEquals(256, enc.randomBytes(256).size)
    }


    @Test
    fun sha256_returnsCorrectLength() {
        val hash = enc.sha256("hello".toByteArray())
        assertEquals(32, hash.size)
    }

    @Test
    fun sha256_deterministic() {
        val a = enc.sha256("test".toByteArray())
        val b = enc.sha256("test".toByteArray())
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun sha256_differentInputsDifferentOutput() {
        val a = enc.sha256("hello".toByteArray())
        val b = enc.sha256("world".toByteArray())
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun sha256_emptyInput() {
        val hash = enc.sha256(byteArrayOf())
        assertEquals(32, hash.size)
    }


    @Test
    fun encryptDecrypt_roundTrip() {
        val key = enc.randomBytes(32)
        val plaintext = "secret message".toByteArray()
        val sealed = enc.encrypt(plaintext, key)
        val decrypted = enc.decrypt(sealed, key)
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun encryptDecrypt_withAad() {
        val key = enc.randomBytes(32)
        val plaintext = "data".toByteArray()
        val aad = "metadata".toByteArray()
        val sealed = enc.encrypt(plaintext, key, aad)
        val decrypted = enc.decrypt(sealed, key, aad)
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun decrypt_wrongKeyFails() {
        val key1 = enc.randomBytes(32)
        val key2 = enc.randomBytes(32)
        val sealed = enc.encrypt("data".toByteArray(), key1)
        try {
            enc.decrypt(sealed, key2)
            fail("Should have thrown SecurityException")
        } catch (e: SecurityException) {
            // expected
        }
    }

    @Test
    fun decrypt_wrongAadFails() {
        val key = enc.randomBytes(32)
        val sealed = enc.encrypt("data".toByteArray(), key, "aad1".toByteArray())
        try {
            enc.decrypt(sealed, key, "aad2".toByteArray())
            fail("Should have thrown SecurityException")
        } catch (e: SecurityException) {
            // expected
        }
    }

    @Test
    fun decrypt_tamperedDataFails() {
        val key = enc.randomBytes(32)
        val sealed = enc.encrypt("data".toByteArray(), key)
        sealed[sealed.size / 2] = (sealed[sealed.size / 2].toInt() xor 0xFF).toByte()
        try {
            enc.decrypt(sealed, key)
            fail("Should have thrown SecurityException")
        } catch (e: SecurityException) {
            // expected
        }
    }

    @Test
    fun encryptDecrypt_emptyPlaintext() {
        val key = enc.randomBytes(32)
        val sealed = enc.encrypt(byteArrayOf(), key)
        val decrypted = enc.decrypt(sealed, key)
        assertEquals(0, decrypted.size)
    }

    @Test
    fun encryptDecrypt_largePayload() {
        val key = enc.randomBytes(32)
        val plaintext = enc.randomBytes(1024 * 100) // 100 KB
        val sealed = enc.encrypt(plaintext, key)
        val decrypted = enc.decrypt(sealed, key)
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun encrypt_ciphertextDiffersFromPlaintext() {
        val key = enc.randomBytes(32)
        val plaintext = "hello world".toByteArray()
        val sealed = enc.encrypt(plaintext, key)
        assertFalse(plaintext.contentEquals(sealed))
        assertTrue(sealed.size > plaintext.size) // includes nonce + tag
    }


    @Test
    fun deriveKey_returnsCorrectLength() {
        val ikm = enc.randomBytes(32)
        val key = enc.deriveKey(ikm, info = "test")
        assertEquals(32, key.size)
    }

    @Test
    fun deriveKey_deterministic() {
        val ikm = enc.randomBytes(32)
        val salt = enc.randomBytes(16)
        val a = enc.deriveKey(ikm, salt, "test")
        val b = enc.deriveKey(ikm, salt, "test")
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun deriveKey_differentInfoDifferentOutput() {
        val ikm = enc.randomBytes(32)
        val a = enc.deriveKey(ikm, info = "purpose1")
        val b = enc.deriveKey(ikm, info = "purpose2")
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun deriveKey_withoutSalt() {
        val ikm = enc.randomBytes(32)
        val key = enc.deriveKey(ikm, salt = null, info = "no-salt")
        assertEquals(32, key.size)
    }


    @Test
    fun argon2id_returnsCorrectLength() {
        val pwd = "password".toByteArray()
        val salt = enc.randomBytes(16)
        val hash = enc.argon2id(pwd, salt, tCost = 1, mCost = 1024, parallelism = 1, hashLen = 32)
        assertEquals(32, hash.size)
    }

    @Test
    fun argon2id_deterministic() {
        val pwd = "password".toByteArray()
        val salt = enc.randomBytes(16)
        val a = enc.argon2id(pwd, salt, tCost = 1, mCost = 1024, parallelism = 1, hashLen = 32)
        val b = enc.argon2id(pwd, salt, tCost = 1, mCost = 1024, parallelism = 1, hashLen = 32)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun argon2id_differentPasswordDifferentHash() {
        val salt = enc.randomBytes(16)
        val a = enc.argon2id("pass1".toByteArray(), salt, tCost = 1, mCost = 1024, parallelism = 1)
        val b = enc.argon2id("pass2".toByteArray(), salt, tCost = 1, mCost = 1024, parallelism = 1)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun argon2id_differentSaltDifferentHash() {
        val pwd = "password".toByteArray()
        val a = enc.argon2id(pwd, enc.randomBytes(16), tCost = 1, mCost = 1024, parallelism = 1)
        val b = enc.argon2id(pwd, enc.randomBytes(16), tCost = 1, mCost = 1024, parallelism = 1)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun argon2id_customHashLength() {
        val pwd = "password".toByteArray()
        val salt = enc.randomBytes(16)
        val hash64 = enc.argon2id(pwd, salt, tCost = 1, mCost = 1024, parallelism = 1, hashLen = 64)
        assertEquals(64, hash64.size)
    }

    @Test
    fun argon2id_higherParams() {
        val pwd = "password".toByteArray()
        val salt = enc.randomBytes(16)
        val hash = enc.argon2id(pwd, salt, tCost = 2, mCost = 16384, parallelism = 2, hashLen = 32)
        assertEquals(32, hash.size)
    }


    @Test
    fun pbkdf2_returnsCorrectLength() {
        val pwd = "password".toByteArray()
        val salt = enc.randomBytes(16)
        val key = enc.pbkdf2(pwd, salt, iterations = 1000, keyLength = 32)
        assertEquals(32, key.size)
    }

    @Test
    fun pbkdf2_deterministic() {
        val pwd = "password".toByteArray()
        val salt = enc.randomBytes(16)
        val a = enc.pbkdf2(pwd, salt, iterations = 1000, keyLength = 32)
        val b = enc.pbkdf2(pwd, salt, iterations = 1000, keyLength = 32)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun pbkdf2_differentPasswordDifferentKey() {
        val salt = enc.randomBytes(16)
        val a = enc.pbkdf2("pass1".toByteArray(), salt, iterations = 1000)
        val b = enc.pbkdf2("pass2".toByteArray(), salt, iterations = 1000)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun pbkdf2_customKeyLength() {
        val key = enc.pbkdf2("pwd".toByteArray(), enc.randomBytes(16), iterations = 1000, keyLength = 64)
        assertEquals(64, key.size)
    }


    @Test
    fun secureWipe_zerosOutBuffer() {
        val data = enc.randomBytes(32)
        assertTrue(data.any { it != 0.toByte() })
        enc.secureWipe(data)
        assertTrue(data.all { it == 0.toByte() })
    }


    @Test
    fun detectOptimalCipher_returnsValidValue() {
        val cipher = enc.detectOptimalCipher()
        assertTrue(cipher == 0 || cipher == 1) // 0=AES-GCM, 1=ChaCha20
    }


    @Test
    fun isDebuggerAttached_returnsBoolean() {
        // Just verify it doesn't crash; value depends on test runner
        enc.isDebuggerAttached()
    }

    @Test
    fun isFridaPresent_returnsFalse() {
        assertFalse(enc.isFridaPresent())
    }

    @Test
    fun isXposedPresent_returnsFalse() {
        assertFalse(enc.isXposedPresent())
    }

    @Test
    fun verifyApkSignature_matchingHashesPass() {
        val hash = enc.sha256("apk-cert".toByteArray())
        assertTrue(enc.verifyApkSignature(hash, hash))
    }

    @Test
    fun verifyApkSignature_mismatchFails() {
        val a = enc.sha256("cert-a".toByteArray())
        val b = enc.sha256("cert-b".toByteArray())
        assertFalse(enc.verifyApkSignature(a, b))
    }


    @Test
    fun hashFile_existingFile() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val file = ctx.filesDir.resolve("test_hash.txt")
        file.writeText("hash me")
        val hash = enc.hashFile(file.absolutePath)
        assertNotNull(hash)
        assertEquals(32, hash!!.size)
        file.delete()
    }

    @Test
    fun hashFile_nonExistentReturnsNull() {
        val hash = enc.hashFile("/nonexistent/path/file.bin")
        assertNull(hash)
    }


    @Test
    fun hybridKem_keygenProducesKeys() {
        val kp = enc.hybridKemKeygen()
        assertTrue(kp.publicKey.isNotEmpty())
        assertTrue(kp.secretKey.isNotEmpty())
    }

    @Test
    fun hybridKem_encapsDecapsRoundTrip() {
        val kp = enc.hybridKemKeygen()
        val encaps = enc.hybridKemEncaps(kp.publicKey)
        val recovered = enc.hybridKemDecaps(encaps.ciphertext, kp.secretKey)
        assertTrue(encaps.sharedSecret.contentEquals(recovered))
    }

    @Test
    fun hybridKem_differentKeyPairsDifferentSecrets() {
        val kp1 = enc.hybridKemKeygen()
        val kp2 = enc.hybridKemKeygen()
        val e1 = enc.hybridKemEncaps(kp1.publicKey)
        val e2 = enc.hybridKemEncaps(kp2.publicKey)
        assertFalse(e1.sharedSecret.contentEquals(e2.sharedSecret))
    }

    @Test
    fun hybridKem_wrongSecretKeyFails() {
        val kp1 = enc.hybridKemKeygen()
        val kp2 = enc.hybridKemKeygen()
        val encaps = enc.hybridKemEncaps(kp1.publicKey)
        try {
            enc.hybridKemDecaps(encaps.ciphertext, kp2.secretKey)
            // If it doesn't throw, the shared secret should differ
            val recovered = enc.hybridKemDecaps(encaps.ciphertext, kp2.secretKey)
            assertFalse(encaps.sharedSecret.contentEquals(recovered))
        } catch (e: SecurityException) {
            // Also acceptable
        }
    }


    @Test
    fun hybridSign_keygenProducesKeys() {
        val kp = enc.hybridSignKeygen()
        assertTrue(kp.publicKey.isNotEmpty())
        assertTrue(kp.secretKey.isNotEmpty())
    }

    @Test
    fun hybridSign_signAndVerify() {
        val kp = enc.hybridSignKeygen()
        val message = "sign this".toByteArray()
        val signature = enc.hybridSign(message, kp.secretKey)
        assertTrue(signature.isNotEmpty())
        assertTrue(enc.hybridVerify(message, signature, kp.publicKey))
    }

    @Test
    fun hybridSign_wrongMessageFails() {
        val kp = enc.hybridSignKeygen()
        val signature = enc.hybridSign("original".toByteArray(), kp.secretKey)
        assertFalse(enc.hybridVerify("tampered".toByteArray(), signature, kp.publicKey))
    }

    @Test
    fun hybridSign_wrongKeyFails() {
        val kp1 = enc.hybridSignKeygen()
        val kp2 = enc.hybridSignKeygen()
        val message = "msg".toByteArray()
        val signature = enc.hybridSign(message, kp1.secretKey)
        assertFalse(enc.hybridVerify(message, signature, kp2.publicKey))
    }

    @Test
    fun hybridSign_tamperedSignatureFails() {
        val kp = enc.hybridSignKeygen()
        val message = "msg".toByteArray()
        val signature = enc.hybridSign(message, kp.secretKey)
        signature[0] = (signature[0].toInt() xor 0xFF).toByte()
        assertFalse(enc.hybridVerify(message, signature, kp.publicKey))
    }

    @Test
    fun hybridSign_emptyMessage() {
        val kp = enc.hybridSignKeygen()
        val signature = enc.hybridSign(byteArrayOf(), kp.secretKey)
        assertTrue(enc.hybridVerify(byteArrayOf(), signature, kp.publicKey))
    }
}
