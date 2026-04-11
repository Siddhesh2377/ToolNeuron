/*
 * Argon2id reference implementation (RFC 9106)
 * Single-file, self-contained. No threads — sequential lane processing.
 * Based on the reference C by Daniel Dinu & Dmitry Khovratovich (CC0/Apache-2.0).
 *
 * BLAKE2b-512: Inline reference implementation (RFC 7693, CC0/public domain).
 * We do NOT use EVP_blake2b512 because it is not available in all BoringSSL builds.
 * OPENSSL_cleanse is still used from openssl/mem.h for secure erasure.
 */
#include "argon2.h"

#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <openssl/mem.h>   // OPENSSL_cleanse only

// ── Inline BLAKE2b-512 (RFC 7693) ────────────────────────────────────────────

namespace {

static const uint64_t B2B_IV[8] = {
    0x6a09e667f3bcc908ULL, 0xbb67ae8584caa73bULL,
    0x3c6ef372fe94f82bULL, 0xa54ff53a5f1d36f1ULL,
    0x510e527fade682d1ULL, 0x9b05688c2b3e6c1fULL,
    0x1f83d9abfb41bd6bULL, 0x5be0cd19137e2179ULL
};

static const uint8_t B2B_SIGMA[12][16] = {
    { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,15},
    {14,10, 4, 8, 9,15,13, 6, 1,12, 0, 2,11, 7, 5, 3},
    {11, 8,12, 0, 5, 2,15,13,10,14, 3, 6, 7, 1, 9, 4},
    { 7, 9, 3, 1,13,12,11,14, 2, 6, 5,10, 4, 0,15, 8},
    { 9, 0, 5, 7, 2, 4,10,15,14, 1,11,12, 6, 8, 3,13},
    { 2,12, 6,10, 0,11, 8, 3, 4,13, 7, 5,15,14, 1, 9},
    {12, 5, 1,15,14,13, 4,10, 0, 7, 6, 3, 9, 2, 8,11},
    {13,11, 7,14,12, 1, 3, 9, 5, 0,15, 4, 8, 6, 2,10},
    { 6,15,14, 9,11, 3, 0, 8,12, 2,13, 7, 1, 4,10, 5},
    {10, 2, 8, 4, 7, 6, 1, 5,15,11, 9,14, 3,12,13, 0},
    { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,15},
    {14,10, 4, 8, 9,15,13, 6, 1,12, 0, 2,11, 7, 5, 3},
};

struct B2BState {
    uint64_t h[8];
    uint64_t t[2];
    uint64_t f[2];
    uint8_t  buf[128];
    size_t   buflen;
    size_t   outlen;
};

static inline uint64_t b2b_rotr64(uint64_t x, int n) {
    return (x >> n) | (x << (64 - n));
}

#define B2B_G(v,a,b,c,d,x,y)                     \
    v[a] += v[b] + (x);  v[d] ^= v[a]; v[d] = b2b_rotr64(v[d], 32); \
    v[c] += v[d];         v[b] ^= v[c]; v[b] = b2b_rotr64(v[b], 24); \
    v[a] += v[b] + (y);  v[d] ^= v[a]; v[d] = b2b_rotr64(v[d], 16); \
    v[c] += v[d];         v[b] ^= v[c]; v[b] = b2b_rotr64(v[b], 63);

static void b2b_compress(B2BState* S, const uint8_t blk[128]) {
    uint64_t m[16], v[16];
    for (int i = 0; i < 16; i++) {
        m[i]  = (uint64_t)blk[i*8+0]        | ((uint64_t)blk[i*8+1] << 8)
              | ((uint64_t)blk[i*8+2] << 16) | ((uint64_t)blk[i*8+3] << 24)
              | ((uint64_t)blk[i*8+4] << 32) | ((uint64_t)blk[i*8+5] << 40)
              | ((uint64_t)blk[i*8+6] << 48) | ((uint64_t)blk[i*8+7] << 56);
    }
    for (int i = 0; i < 8; i++) v[i] = S->h[i];
    v[8]  = B2B_IV[0]; v[9]  = B2B_IV[1];
    v[10] = B2B_IV[2]; v[11] = B2B_IV[3];
    v[12] = B2B_IV[4] ^ S->t[0];
    v[13] = B2B_IV[5] ^ S->t[1];
    v[14] = B2B_IV[6] ^ S->f[0];
    v[15] = B2B_IV[7] ^ S->f[1];
    for (int r = 0; r < 12; r++) {
        const uint8_t* s = B2B_SIGMA[r];
        B2B_G(v, 0, 4,  8, 12, m[s[0]],  m[s[1]]);
        B2B_G(v, 1, 5,  9, 13, m[s[2]],  m[s[3]]);
        B2B_G(v, 2, 6, 10, 14, m[s[4]],  m[s[5]]);
        B2B_G(v, 3, 7, 11, 15, m[s[6]],  m[s[7]]);
        B2B_G(v, 0, 5, 10, 15, m[s[8]],  m[s[9]]);
        B2B_G(v, 1, 6, 11, 12, m[s[10]], m[s[11]]);
        B2B_G(v, 2, 7,  8, 13, m[s[12]], m[s[13]]);
        B2B_G(v, 3, 4,  9, 14, m[s[14]], m[s[15]]);
    }
    for (int i = 0; i < 8; i++) S->h[i] ^= v[i] ^ v[i+8];
}

// outlen: 1–64 bytes (controls digest size via parameter block)
static void b2b_init(B2BState* S, size_t outlen) {
    memset(S, 0, sizeof(*S));
    S->outlen = outlen;
    // Parameter block p0: digest length | fanout(1) | max depth(1)
    uint64_t p0 = (uint64_t)outlen | (1ULL << 8) | (1ULL << 16);
    S->h[0] = B2B_IV[0] ^ p0;
    for (int i = 1; i < 8; i++) S->h[i] = B2B_IV[i];
}

static void b2b_update(B2BState* S, const void* in, size_t inlen) {
    const uint8_t* p = (const uint8_t*)in;
    while (inlen > 0) {
        size_t left = S->buflen;
        size_t fill = 128 - left;
        if (inlen > fill) {
            memcpy(S->buf + left, p, fill);
            S->buflen = 128;
            S->t[0] += 128; if (S->t[0] < 128) S->t[1]++;
            b2b_compress(S, S->buf);
            S->buflen = 0;
            p += fill; inlen -= fill;
        } else {
            memcpy(S->buf + left, p, inlen);
            S->buflen += inlen;
            break;
        }
    }
}

static void b2b_final(B2BState* S, uint8_t* out) {
    S->t[0] += (uint64_t)S->buflen;
    if (S->t[0] < (uint64_t)S->buflen) S->t[1]++;
    S->f[0] = 0xFFFFFFFFFFFFFFFFULL;
    memset(S->buf + S->buflen, 0, 128 - S->buflen);
    b2b_compress(S, S->buf);
    // Write output (little-endian)
    uint8_t tmp[64];
    for (int i = 0; i < 8; i++) {
        tmp[i*8+0] = (uint8_t)(S->h[i]);
        tmp[i*8+1] = (uint8_t)(S->h[i] >>  8);
        tmp[i*8+2] = (uint8_t)(S->h[i] >> 16);
        tmp[i*8+3] = (uint8_t)(S->h[i] >> 24);
        tmp[i*8+4] = (uint8_t)(S->h[i] >> 32);
        tmp[i*8+5] = (uint8_t)(S->h[i] >> 40);
        tmp[i*8+6] = (uint8_t)(S->h[i] >> 48);
        tmp[i*8+7] = (uint8_t)(S->h[i] >> 56);
    }
    memcpy(out, tmp, S->outlen);
    OPENSSL_cleanse(tmp, sizeof(tmp));
    OPENSSL_cleanse(S, sizeof(*S));
}

// ── Argon2 internal types ─────────────────────────────────────────────────────

struct Block {
    uint64_t v[ARGON2_QWORDS_IN_BLOCK]; // 128 × uint64 = 1024 bytes
};

static inline uint64_t rotr64(uint64_t x, unsigned n) {
    return (x >> n) | (x << (64 - n));
}

static inline uint64_t fBlaMka(uint64_t x, uint64_t y) {
    uint64_t m  = 0xFFFFFFFF;
    uint64_t xy = (x & m) * (y & m);
    return x + y + 2 * xy;
}

#define G(a, b, c, d)                                               \
    do {                                                            \
        a = fBlaMka(a, b); d ^= a; d = rotr64(d, 32);             \
        c = fBlaMka(c, d); b ^= c; b = rotr64(b, 24);             \
        a = fBlaMka(a, b); d ^= a; d = rotr64(d, 16);             \
        c = fBlaMka(c, d); b ^= c; b = rotr64(b, 63);             \
    } while (0)

// ── blake2b_long: Argon2's variable-length hash H' (RFC 9106 §3.2) ───────────

static void blake2b_long(uint8_t* out, size_t outlen,
                         const uint8_t* in, size_t inlen) {
    uint8_t outlen_le[4] = {
        (uint8_t)outlen,
        (uint8_t)(outlen >> 8),
        (uint8_t)(outlen >> 16),
        (uint8_t)(outlen >> 24)
    };

    if (outlen <= 64) {
        B2BState S;
        b2b_init(&S, outlen);
        b2b_update(&S, outlen_le, 4);
        b2b_update(&S, in, inlen);
        b2b_final(&S, out);
    } else {
        // Produce ceil(outlen/32) – 2 full 64-byte blocks + one final partial
        uint8_t buf[64];
        size_t r   = (outlen + 31) / 32 - 2;

        // V_1 = BLAKE2b-512(outlen_le || in)
        {
            B2BState S;
            b2b_init(&S, 64);
            b2b_update(&S, outlen_le, 4);
            b2b_update(&S, in, inlen);
            b2b_final(&S, buf);
        }
        memcpy(out, buf, 32);
        size_t pos = 32;

        for (size_t i = 1; i < r; i++) {
            B2BState S;
            b2b_init(&S, 64);
            b2b_update(&S, buf, 64);
            b2b_final(&S, buf);
            memcpy(out + pos, buf, 32);
            pos += 32;
        }

        // Final block — may be less than 32 bytes
        size_t remaining = outlen - pos;
        B2BState S;
        b2b_init(&S, remaining <= 64 ? remaining : 64);
        b2b_update(&S, buf, 64);
        b2b_final(&S, buf);
        memcpy(out + pos, buf, remaining);

        OPENSSL_cleanse(buf, sizeof(buf));
    }
}

// ── fill_block ────────────────────────────────────────────────────────────────

static void fill_block(const Block& prev, const Block& ref, Block& next, bool xor_mode) {
    Block blockR, tmp;
    for (int i = 0; i < ARGON2_QWORDS_IN_BLOCK; i++) {
        blockR.v[i] = prev.v[i] ^ ref.v[i];
    }
    tmp = blockR;

    // Row permutations (8 rows of 16 qwords)
    for (int i = 0; i < 8; i++) {
        int b = i * 16;
        G(blockR.v[b+0],  blockR.v[b+4],  blockR.v[b+8],  blockR.v[b+12]);
        G(blockR.v[b+1],  blockR.v[b+5],  blockR.v[b+9],  blockR.v[b+13]);
        G(blockR.v[b+2],  blockR.v[b+6],  blockR.v[b+10], blockR.v[b+14]);
        G(blockR.v[b+3],  blockR.v[b+7],  blockR.v[b+11], blockR.v[b+15]);
        G(blockR.v[b+0],  blockR.v[b+5],  blockR.v[b+10], blockR.v[b+15]);
        G(blockR.v[b+1],  blockR.v[b+6],  blockR.v[b+11], blockR.v[b+12]);
        G(blockR.v[b+2],  blockR.v[b+7],  blockR.v[b+8],  blockR.v[b+13]);
        G(blockR.v[b+3],  blockR.v[b+4],  blockR.v[b+9],  blockR.v[b+14]);
    }

    // Column permutations: 8×8 matrix of 128-bit (2 × uint64) values.
    // Column i picks one 128-bit element from each of the 8 rows (stride 16 qwords).
    for (int i = 0; i < 8; i++) {
        int c = 2 * i;
        G(blockR.v[c],    blockR.v[c+32], blockR.v[c+64], blockR.v[c+96]);
        G(blockR.v[c+1],  blockR.v[c+33], blockR.v[c+65], blockR.v[c+97]);
        G(blockR.v[c+16], blockR.v[c+48], blockR.v[c+80], blockR.v[c+112]);
        G(blockR.v[c+17], blockR.v[c+49], blockR.v[c+81], blockR.v[c+113]);
        G(blockR.v[c],    blockR.v[c+33], blockR.v[c+80], blockR.v[c+113]);
        G(blockR.v[c+1],  blockR.v[c+32], blockR.v[c+81], blockR.v[c+112]);
        G(blockR.v[c+16], blockR.v[c+49], blockR.v[c+64], blockR.v[c+97]);
        G(blockR.v[c+17], blockR.v[c+48], blockR.v[c+65], blockR.v[c+96]);
    }

    if (xor_mode) {
        for (int i = 0; i < ARGON2_QWORDS_IN_BLOCK; i++) {
            next.v[i] ^= blockR.v[i] ^ tmp.v[i];
        }
    } else {
        for (int i = 0; i < ARGON2_QWORDS_IN_BLOCK; i++) {
            next.v[i] = blockR.v[i] ^ tmp.v[i];
        }
    }
}

// ── index_alpha ───────────────────────────────────────────────────────────────

static uint32_t index_alpha(uint32_t pass, uint32_t lane, uint32_t slice,
                             uint32_t index, uint32_t lane_length,
                             uint32_t segment_length, uint32_t lanes,
                             uint64_t pseudo_rand) {
    uint32_t ref_area_size;
    uint32_t ref_lane = static_cast<uint32_t>((pseudo_rand >> 32) % lanes);

    if (pass == 0) {
        if (slice == 0) {
            ref_lane      = lane;
            ref_area_size = (index > 0) ? index - 1 : 0;
        } else {
            ref_area_size = (ref_lane == lane)
                ? slice * segment_length + index - 1
                : slice * segment_length - (index == 0 ? 1 : 0);
        }
    } else {
        ref_area_size = (ref_lane == lane)
            ? lane_length - segment_length + index - 1
            : lane_length - segment_length - (index == 0 ? 1 : 0);
    }

    if (ref_area_size == 0) ref_area_size = 1;

    uint64_t rel = pseudo_rand & 0xFFFFFFFFULL;
    rel = (rel * rel) >> 32;
    rel = ref_area_size - 1 - ((uint64_t)ref_area_size * rel >> 32);

    uint32_t start = 0;
    if (pass != 0) {
        start = (slice == ARGON2_SYNC_POINTS - 1) ? 0 : (slice + 1) * segment_length;
    }

    return (start + static_cast<uint32_t>(rel)) % lane_length;
}

} // anonymous namespace

// ── argon2id_hash_raw ─────────────────────────────────────────────────────────

extern "C" int argon2id_hash_raw(
    uint32_t t_cost, uint32_t m_cost, uint32_t parallelism,
    const void* pwd,  size_t pwdlen,
    const void* salt, size_t saltlen,
    void* hash, size_t hashlen
) {
    if (!hash)            return ARGON2_OUTPUT_PTR_NULL;
    if (hashlen  < ARGON2_MIN_OUTLEN)  return ARGON2_OUTPUT_TOO_SHORT;
    if (saltlen  < ARGON2_MIN_SALT)    return ARGON2_SALT_TOO_SHORT;
    if (t_cost   < ARGON2_MIN_TIME)    return ARGON2_TIME_TOO_SMALL;
    if (m_cost   < ARGON2_MIN_MEMORY)  return ARGON2_MEMORY_TOO_LITTLE;
    if (parallelism < ARGON2_MIN_LANES) return ARGON2_LANES_TOO_FEW;

    uint32_t lanes          = parallelism;
    uint32_t segment_length = m_cost / (lanes * ARGON2_SYNC_POINTS);
    if (segment_length < 2) segment_length = 2;
    uint32_t lane_length   = segment_length * ARGON2_SYNC_POINTS;
    uint32_t memory_blocks = lane_length * lanes;

    Block* memory = static_cast<Block*>(calloc(memory_blocks, sizeof(Block)));
    if (!memory) return ARGON2_MEMORY_ALLOCATION_ERROR;

    // ── H_0: initial hash (RFC 9106 §3.4) ────────────────────────────────────
    // H_0 = BLAKE2b-512(p || T || m || t || v || y || |P||P | |S||S | 0||0)
    const uint32_t type    = 2;              // Argon2id
    const uint32_t version = ARGON2_VERSION_13;

    auto store32 = [](uint8_t* dst, uint32_t val) {
        dst[0] = (uint8_t)(val);
        dst[1] = (uint8_t)(val >>  8);
        dst[2] = (uint8_t)(val >> 16);
        dst[3] = (uint8_t)(val >> 24);
    };

    uint8_t h0[64], tmp4[4];
    {
        B2BState S;
        b2b_init(&S, 64);
        store32(tmp4, parallelism);                   b2b_update(&S, tmp4, 4);
        store32(tmp4, (uint32_t)hashlen);             b2b_update(&S, tmp4, 4);
        store32(tmp4, m_cost);                        b2b_update(&S, tmp4, 4);
        store32(tmp4, t_cost);                        b2b_update(&S, tmp4, 4);
        store32(tmp4, version);                       b2b_update(&S, tmp4, 4);
        store32(tmp4, type);                          b2b_update(&S, tmp4, 4);
        store32(tmp4, (uint32_t)pwdlen);              b2b_update(&S, tmp4, 4);
        if (pwdlen > 0)  b2b_update(&S, pwd,  pwdlen);
        store32(tmp4, (uint32_t)saltlen);             b2b_update(&S, tmp4, 4);
        if (saltlen > 0) b2b_update(&S, salt, saltlen);
        store32(tmp4, 0); b2b_update(&S, tmp4, 4);   // secret len = 0
        store32(tmp4, 0); b2b_update(&S, tmp4, 4);   // AD len = 0
        b2b_final(&S, h0);
    }

    // ── Initialise first two blocks of each lane ──────────────────────────────
    for (uint32_t l = 0; l < lanes; l++) {
        uint8_t seed[72];
        memcpy(seed, h0, 64);
        store32(seed + 64, 0); store32(seed + 68, l);
        blake2b_long(reinterpret_cast<uint8_t*>(memory[l * lane_length + 0].v),
                     ARGON2_BLOCK_SIZE, seed, 72);
        store32(seed + 64, 1);
        blake2b_long(reinterpret_cast<uint8_t*>(memory[l * lane_length + 1].v),
                     ARGON2_BLOCK_SIZE, seed, 72);
        OPENSSL_cleanse(seed, sizeof(seed));
    }
    OPENSSL_cleanse(h0, 64);

    // ── Fill memory ───────────────────────────────────────────────────────────
    for (uint32_t pass = 0; pass < t_cost; pass++) {
        for (uint32_t slice = 0; slice < ARGON2_SYNC_POINTS; slice++) {
            for (uint32_t lane = 0; lane < lanes; lane++) {
                uint32_t start = (pass == 0 && slice == 0) ? 2 : 0;
                for (uint32_t idx = start; idx < segment_length; idx++) {
                    uint32_t curr = lane * lane_length + slice * segment_length + idx;
                    uint32_t prev = (curr == lane * lane_length)
                        ? lane * lane_length + lane_length - 1
                        : curr - 1;

                    uint64_t pseudo_rand = memory[prev].v[0];
                    uint32_t ref_lane_idx = static_cast<uint32_t>(
                        (pseudo_rand >> 32) % lanes);
                    if (pass == 0 && slice == 0) ref_lane_idx = lane;
                    uint32_t ref = ref_lane_idx * lane_length +
                        index_alpha(pass, lane, slice, idx,
                                    lane_length, segment_length, lanes, pseudo_rand);

                    fill_block(memory[prev], memory[ref], memory[curr], pass > 0);
                }
            }
        }
    }

    // ── Finalize: XOR last blocks of all lanes ────────────────────────────────
    Block final_block = memory[(lanes - 1) * lane_length + lane_length - 1];
    for (uint32_t l = 0; l < lanes - 1; l++) {
        for (int i = 0; i < ARGON2_QWORDS_IN_BLOCK; i++) {
            final_block.v[i] ^= memory[l * lane_length + lane_length - 1].v[i];
        }
    }

    blake2b_long(static_cast<uint8_t*>(hash), hashlen,
                 reinterpret_cast<uint8_t*>(final_block.v), ARGON2_BLOCK_SIZE);

    OPENSSL_cleanse(&final_block, sizeof(Block));
    OPENSSL_cleanse(memory, (size_t)memory_blocks * sizeof(Block));
    free(memory);
    return ARGON2_OK;
}
