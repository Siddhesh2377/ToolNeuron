#ifndef _AES_H_
#define _AES_H_

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif
#if defined(AES256) && (AES256 == 1)
#define AES_keyExpSize 240
#elif defined(AES192) && (AES192 == 1)
#define AES_keyExpSize 208
#else
#define AES_keyExpSize 176
#endif

// AES context
struct AES_ctx {
    uint8_t RoundKey[AES_keyExpSize];
#if (defined(CBC) && (CBC == 1)) || (defined(CTR) && (CTR == 1))
    uint8_t Iv[AES_BLOCKLEN];
#endif
};

// function declarations
void AES_init_ctx(struct AES_ctx *ctx, const uint8_t *key);
#if (defined(CBC) && (CBC == 1)) || (defined(CTR) && (CTR == 1))
void AES_init_ctx_iv(struct AES_ctx* ctx, const uint8_t* key, const uint8_t* iv);
  void AES_ctx_set_iv(struct AES_ctx* ctx, const uint8_t* iv);
#endif

#if defined(ECB) && (ECB == 1)
void AES_ECB_encrypt(const struct AES_ctx* ctx, uint8_t* buf);
  void AES_ECB_decrypt(const struct AES_ctx* ctx, uint8_t* buf);
#endif

#if defined(CBC) && (CBC == 1)
void AES_CBC_encrypt_buffer(struct AES_ctx* ctx, uint8_t* buf, size_t length);
  void AES_CBC_decrypt_buffer(struct AES_ctx* ctx, uint8_t* buf, size_t length);
#endif

#if defined(CTR) && (CTR == 1)
void AES_CTR_xcrypt_buffer(struct AES_ctx* ctx, uint8_t* buf, size_t length);
#endif

#ifdef __cplusplus
}
#endif

#endif // _AES_H_
