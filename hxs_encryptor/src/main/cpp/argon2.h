/*
 * Argon2id reference implementation (RFC 9106)
 * Vendored and minimal — only argon2id_hash_raw exposed.
 * Based on the reference C implementation by Daniel Dinu and Dmitry Khovratovich.
 * License: CC0 / Apache 2.0 dual license (original project).
 */
#pragma once

#include <cstddef>
#include <cstdint>

#define ARGON2_OK              0
#define ARGON2_OUTPUT_PTR_NULL -1
#define ARGON2_OUTPUT_TOO_SHORT -2
#define ARGON2_OUTPUT_TOO_LONG -3
#define ARGON2_PWD_TOO_LONG   -4
#define ARGON2_SALT_TOO_SHORT -5
#define ARGON2_SALT_TOO_LONG  -6
#define ARGON2_TIME_TOO_SMALL -7
#define ARGON2_MEMORY_TOO_LITTLE -8
#define ARGON2_LANES_TOO_FEW  -9
#define ARGON2_MEMORY_ALLOCATION_ERROR -10

#define ARGON2_BLOCK_SIZE 1024
#define ARGON2_QWORDS_IN_BLOCK (ARGON2_BLOCK_SIZE / 8)
#define ARGON2_SYNC_POINTS 4
#define ARGON2_VERSION_13 0x13
#define ARGON2_MIN_OUTLEN 4
#define ARGON2_MAX_OUTLEN 0xFFFFFFFFu
#define ARGON2_MIN_SALT 8
#define ARGON2_MAX_SALT 0xFFFFFFFFu
#define ARGON2_MIN_TIME 1
#define ARGON2_MIN_MEMORY 8
#define ARGON2_MIN_LANES 1

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Argon2id key derivation.
 * @param t_cost   Number of iterations (time cost)
 * @param m_cost   Memory usage in KiB
 * @param parallelism  Number of threads/lanes
 * @param pwd      Password bytes
 * @param pwdlen   Password length
 * @param salt     Salt bytes (min 8)
 * @param saltlen  Salt length
 * @param hash     Output buffer
 * @param hashlen  Desired hash length (min 4)
 * @return ARGON2_OK on success, negative on error
 */
int argon2id_hash_raw(
    uint32_t t_cost, uint32_t m_cost, uint32_t parallelism,
    const void* pwd, size_t pwdlen,
    const void* salt, size_t saltlen,
    void* hash, size_t hashlen
);

#ifdef __cplusplus
}
#endif
