#include "utf8_utils.h"
#include "logger.h"
#include <cassert>

namespace utf8 {

/* --------------------------------------------------------------------
 *  Thread‑local buffer for incomplete UTF‑8 sequences during streaming
 * -------------------------------------------------------------------- */
    static thread_local std::string t_carry;

    inline std::string& get_carry_buffer() { return t_carry; }
    inline void clear_carry_buffer() { t_carry.clear(); }

/* --------------------------------------------------------------------
 *  Helper: Check if char16_t is a high surrogate
 * -------------------------------------------------------------------- */
    static inline bool is_high_surrogate(char16_t c) {
        return (c >= 0xD800 && c <= 0xDBFF);
    }

/* --------------------------------------------------------------------
 *  Helper: Check if char16_t is a low surrogate
 * -------------------------------------------------------------------- */
    static inline bool is_low_surrogate(char16_t c) {
        return (c >= 0xDC00 && c <= 0xDFFF);
    }

/* --------------------------------------------------------------------
 *  Helper: Combine surrogate pair into codepoint
 * -------------------------------------------------------------------- */
    static inline uint32_t surrogate_to_codepoint(char16_t high, char16_t low) {
        return 0x10000 + ((high - 0xD800) << 10) + (low - 0xDC00);
    }

/* --------------------------------------------------------------------
 *  Helper: Encode a Unicode codepoint as UTF-8
 * -------------------------------------------------------------------- */
    static void encode_utf8(uint32_t codepoint, std::string& out) {
        if (codepoint <= 0x7F) {
            // 1-byte sequence (ASCII)
            out.push_back(static_cast<char>(codepoint));
        }
        else if (codepoint <= 0x7FF) {
            // 2-byte sequence
            out.push_back(static_cast<char>(0xC0 | (codepoint >> 6)));
            out.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        }
        else if (codepoint <= 0xFFFF) {
            // 3-byte sequence
            out.push_back(static_cast<char>(0xE0 | (codepoint >> 12)));
            out.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        }
        else if (codepoint <= 0x10FFFF) {
            // 4-byte sequence (emojis live here!)
            out.push_back(static_cast<char>(0xF0 | (codepoint >> 18)));
            out.push_back(static_cast<char>(0x80 | ((codepoint >> 12) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        }
        else {
            // Invalid codepoint - use replacement character
            out.append("\xEF\xBF\xBD"); // U+FFFD
        }
    }

/* --------------------------------------------------------------------
 *  Convert a Java string (UTF‑16) to a UTF‑8 std::string.
 *  PROPERLY handles surrogate pairs for emoji support.
 * -------------------------------------------------------------------- */
    std::string from_jstring(JNIEnv* env, jstring js) {
        if (!js) return {};

        jsize len = env->GetStringLength(js);
        const jchar* chars = env->GetStringChars(js, nullptr);

        // Convert jchar* to char16_t* for proper UTF-16 handling
        std::u16string u16(reinterpret_cast<const char16_t*>(chars),
                           static_cast<size_t>(len));

        env->ReleaseStringChars(js, chars);

        // Reserve space (worst case: 4 bytes per UTF-16 unit)
        std::string out;
        out.reserve(u16.size() * 4);

        // Process UTF-16 with proper surrogate pair handling
        for (size_t i = 0; i < u16.size(); ++i) {
            char16_t unit = u16[i];

            if (is_high_surrogate(unit)) {
                // High surrogate - must be followed by low surrogate
                if (i + 1 < u16.size() && is_low_surrogate(u16[i + 1])) {
                    uint32_t codepoint = surrogate_to_codepoint(unit, u16[i + 1]);
                    encode_utf8(codepoint, out);
                    ++i; // Skip the low surrogate
                }
                else {
                    // Invalid surrogate pair - use replacement character
                    out.append("\xEF\xBF\xBD"); // U+FFFD
                    LOG_WARN("Invalid high surrogate at position %zu", i);
                }
            }
            else if (is_low_surrogate(unit)) {
                // Unexpected low surrogate - use replacement character
                out.append("\xEF\xBF\xBD"); // U+FFFD
                LOG_WARN("Unexpected low surrogate at position %zu", i);
            }
            else {
                // Regular BMP character (0x0000 - 0xFFFF)
                encode_utf8(static_cast<uint32_t>(unit), out);
            }
        }

        return out;
    }

/* --------------------------------------------------------------------
 *  Convert a UTF‑8 string to a Java string (UTF‑16).
 *  Handles multi-byte UTF-8 sequences properly.
 * -------------------------------------------------------------------- */
    jstring to_jstring(JNIEnv* env,
                       const std::string& utf8,
                       std::string& carry_buffer)
    {
        std::u16string u16;
        u16.reserve(utf8.size()); // Rough estimate

        size_t i = 0;
        while (i < utf8.size()) {
            unsigned char c = static_cast<unsigned char>(utf8[i]);

            if (c <= 0x7F) {
                // 1-byte sequence (ASCII)
                u16.push_back(static_cast<char16_t>(c));
                ++i;
            }
            else if ((c & 0xE0) == 0xC0) {
                // 2-byte sequence
                if (i + 1 < utf8.size()) {
                    uint32_t cp = ((c & 0x1F) << 6) |
                                  (static_cast<unsigned char>(utf8[i + 1]) & 0x3F);
                    u16.push_back(static_cast<char16_t>(cp));
                    i += 2;
                } else {
                    carry_buffer = utf8.substr(i);
                    break;
                }
            }
            else if ((c & 0xF0) == 0xE0) {
                // 3-byte sequence
                if (i + 2 < utf8.size()) {
                    uint32_t cp = ((c & 0x0F) << 12) |
                                  ((static_cast<unsigned char>(utf8[i + 1]) & 0x3F) << 6) |
                                  (static_cast<unsigned char>(utf8[i + 2]) & 0x3F);
                    u16.push_back(static_cast<char16_t>(cp));
                    i += 3;
                } else {
                    carry_buffer = utf8.substr(i);
                    break;
                }
            }
            else if ((c & 0xF8) == 0xF0) {
                // 4-byte sequence (emojis!) - needs surrogate pair
                if (i + 3 < utf8.size()) {
                    uint32_t cp = ((c & 0x07) << 18) |
                                  ((static_cast<unsigned char>(utf8[i + 1]) & 0x3F) << 12) |
                                  ((static_cast<unsigned char>(utf8[i + 2]) & 0x3F) << 6) |
                                  (static_cast<unsigned char>(utf8[i + 3]) & 0x3F);

                    // Convert to UTF-16 surrogate pair
                    if (cp > 0xFFFF) {
                        cp -= 0x10000;
                        u16.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
                        u16.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
                    } else {
                        u16.push_back(static_cast<char16_t>(cp));
                    }
                    i += 4;
                } else {
                    carry_buffer = utf8.substr(i);
                    break;
                }
            }
            else {
                // Invalid UTF-8 byte - skip it
                LOG_WARN("Invalid UTF-8 byte: 0x%02X at position %zu", c, i);
                u16.push_back(0xFFFD); // Replacement character
                ++i;
            }
        }

        return env->NewString(reinterpret_cast<const jchar*>(u16.data()),
                              static_cast<jsize>(u16.size()));
    }

/* --------------------------------------------------------------------
 *  Flush any remaining UTF‑8 bytes from the carry buffer
 * -------------------------------------------------------------------- */
    void flush_carry(JNIEnv* env, jobject cb) {
        if (t_carry.empty()) return;

        // Send replacement character for incomplete sequence
        std::string tmp = "\xEF\xBF\xBD"; // U+FFFD
        t_carry.clear();

        jclass cls = env->GetObjectClass(cb);
        if (!cls) return;
        jmethodID mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
        if (!mid) return;

        jstring js = to_jstring(env, tmp, t_carry);
        env->CallVoidMethod(cb, mid, js);
        env->DeleteLocalRef(js);
    }

} // namespace utf8