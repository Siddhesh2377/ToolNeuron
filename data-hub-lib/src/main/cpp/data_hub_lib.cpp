#include <jni.h>
#include <string>
#include <fstream>
#include <vector>
#include "libs/json.hpp"
#include <sodium.h>

using json = nlohmann::json;

// keep JSON tree in memory
static json g_root;
static std::vector<unsigned char> g_key;

// Function to encrypt and authenticate a message using libsodium's crypto_secretbox
// The output buffer will contain the nonce followed by the ciphertext.
bool sodium_encrypt(const std::string &plaintext,
                    const std::vector<unsigned char> &key,
                    std::vector<unsigned char> &out) {
    if (key.size() != crypto_secretbox_KEYBYTES) {
        return false;
    }

    // Generate a secure, random nonce
    unsigned char nonce[crypto_secretbox_NONCEBYTES];
    randombytes_buf(nonce, sizeof nonce);

    // Encrypt the message. libsodium handles padding and authentication automatically.
    std::vector<unsigned char> cipher(plaintext.size() + crypto_secretbox_MACBYTES);
    if (crypto_secretbox_easy(
            cipher.data(),
            (const unsigned char *) plaintext.c_str(),
            plaintext.size(),
            nonce,
            key.data()) != 0) {
        return false;
    }

    // Prepend the nonce to the ciphertext
    out.clear();
    out.insert(out.end(), nonce, nonce + sizeof(nonce));
    out.insert(out.end(), cipher.begin(), cipher.end());
    return true;
}

// Function to decrypt an authenticated message using libsodium's crypto_secretbox
bool sodium_decrypt(const std::vector<unsigned char> &in,
                    const std::vector<unsigned char> &key,
                    std::string &plaintext) {
    if (key.size() != crypto_secretbox_KEYBYTES ||
        in.size() < crypto_secretbox_NONCEBYTES + crypto_secretbox_MACBYTES) {
        return false;
    }

    // The nonce is prepended to the ciphertext
    const unsigned char *nonce = in.data();
    const unsigned char *cipher = in.data() + crypto_secretbox_NONCEBYTES;
    size_t cipher_len = in.size() - crypto_secretbox_NONCEBYTES;

    // Use a temporary buffer for the decrypted plaintext
    std::vector<unsigned char> decrypted(cipher_len);

    // Decrypt and authenticate the message.
    if (crypto_secretbox_open_easy(
            decrypted.data(),
            cipher,
            cipher_len,
            nonce,
            key.data()) != 0) {
        // Authentication failed, the message might be tampered with or corrupt
        return false;
    }

    plaintext.assign(decrypted.begin(), decrypted.end() - crypto_secretbox_MACBYTES);
    return true;
}

// JNI helpers
extern "C" jboolean
Java_com_mp_data_1hub_1lib_DataNativeLib_loadVecx(
        JNIEnv *env, jobject thiz,
        jstring jpath, jstring jkey) {

    // Initialize libsodium if it hasn't been already
    if (sodium_init() == -1) {
        // Handle initialization error
        return JNI_FALSE;
    }

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    const char *kstr = env->GetStringUTFChars(jkey, nullptr);

    // IMPORTANT: In a real app, use crypto_pwhash_str to create a secure, salted password hash
    // and store it securely. For this demo, we'll use a direct hash of the input string.
    static const unsigned char salt[crypto_pwhash_SALTBYTES] = {0}; // Static salt for simplicity
    g_key.resize(crypto_secretbox_KEYBYTES);
    if (crypto_pwhash(
            g_key.data(),
            g_key.size(),
            kstr,
            strlen(kstr),
            salt,
            crypto_pwhash_OPSLIMIT_MODERATE,
            crypto_pwhash_MEMLIMIT_MODERATE,
            crypto_pwhash_ALG_DEFAULT) != 0) {
        return JNI_FALSE;
    }

    std::ifstream ifs(path, std::ios::binary);
    if (!ifs) return JNI_FALSE;
    std::vector<unsigned char> buf((std::istreambuf_iterator<char>(ifs)), {});
    ifs.close();

    std::string plain;
    if (!sodium_decrypt(buf, g_key, plain)) return JNI_FALSE;

    try { g_root = json::parse(plain); }
    catch (...) { return JNI_FALSE; }

    env->ReleaseStringUTFChars(jpath, path);
    env->ReleaseStringUTFChars(jkey, kstr);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_data_1hub_1lib_DataNativeLib_updateEntity(
        JNIEnv *env, jobject thiz,
        jstring jentity, jstring jkey, jstring jvalue) {

    const char *entity = env->GetStringUTFChars(jentity, 0);
    const char *ekey = env->GetStringUTFChars(jkey, 0);
    const char *val = env->GetStringUTFChars(jvalue, 0);

    try {
        if (!g_root.contains(entity)) {
            env->ReleaseStringUTFChars(jentity, entity);
            env->ReleaseStringUTFChars(jkey, ekey);
            env->ReleaseStringUTFChars(jvalue, val);
            return JNI_FALSE;
        }

        if (strcmp(entity, "l") == 0) {
            g_root["l"][ekey] = val;
        } else if (strcmp(entity, "m") == 0 || strcmp(entity, "D") == 0) {
            // find obj with id == ekey
            for (auto &item: g_root[entity]) {
                if (item["id"] == ekey) {
                    item = json::parse(val); // replace whole obj
                }
            }
        }
    } catch (...) {
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(jentity, entity);
    env->ReleaseStringUTFChars(jkey, ekey);
    env->ReleaseStringUTFChars(jvalue, val);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mp_data_1hub_1lib_DataNativeLib_getEntity(
        JNIEnv *env, jobject thiz, jstring jentity) {

    const char *entity = env->GetStringUTFChars(jentity, 0);
    std::string out;
    try {
        out = g_root[entity].dump();
    } catch (...) {
        out = "{}";
    }
    env->ReleaseStringUTFChars(jentity, entity);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mp_data_1hub_1lib_DataNativeLib_saveVecx(
        JNIEnv *env, jobject thiz, jstring jpath) {

    const char *path = env->GetStringUTFChars(jpath, 0);
    std::string plain = g_root.dump();

    std::vector<unsigned char> enc;
    if (!sodium_encrypt(plain, g_key, enc)) return JNI_FALSE;

    std::ofstream ofs(path, std::ios::binary);
    ofs.write((char *) enc.data(), enc.size());
    ofs.close();

    env->ReleaseStringUTFChars(jpath, path);
    return JNI_TRUE;
}
