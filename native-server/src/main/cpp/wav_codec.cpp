#include "wav_codec.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <fstream>

namespace tn::server::wav {

    namespace {

        struct RiffHeader {
            char     riff[4];
            uint32_t file_size_minus_8;
            char     wave[4];
        };

        struct FmtChunk {
            char     id[4];
            uint32_t size;
            uint16_t audio_format;
            uint16_t channels;
            uint32_t sample_rate;
            uint32_t byte_rate;
            uint16_t block_align;
            uint16_t bits_per_sample;
        };

        uint32_t read_u32_le(const uint8_t* p) {
            return static_cast<uint32_t>(p[0]) |
                   (static_cast<uint32_t>(p[1]) << 8) |
                   (static_cast<uint32_t>(p[2]) << 16) |
                   (static_cast<uint32_t>(p[3]) << 24);
        }

        uint16_t read_u16_le(const uint8_t* p) {
            return static_cast<uint16_t>(p[0]) |
                   (static_cast<uint16_t>(p[1]) << 8);
        }

    }

    void encode_pcm16_to_file(const std::string& path,
                              const float* samples,
                              size_t count,
                              int sample_rate,
                              int channels) {
        const uint32_t data_size  = static_cast<uint32_t>(count) * sizeof(int16_t);
        const uint32_t fmt_size   = 16;
        const uint32_t riff_size  = 4 + (8 + fmt_size) + (8 + data_size);

        std::ofstream f(path, std::ios::binary | std::ios::trunc);
        if (!f) return;

        auto put_u32 = [&](uint32_t v) {
            uint8_t b[4] = {
                static_cast<uint8_t>(v & 0xFF),
                static_cast<uint8_t>((v >> 8) & 0xFF),
                static_cast<uint8_t>((v >> 16) & 0xFF),
                static_cast<uint8_t>((v >> 24) & 0xFF),
            };
            f.write(reinterpret_cast<const char*>(b), 4);
        };
        auto put_u16 = [&](uint16_t v) {
            uint8_t b[2] = {
                static_cast<uint8_t>(v & 0xFF),
                static_cast<uint8_t>((v >> 8) & 0xFF),
            };
            f.write(reinterpret_cast<const char*>(b), 2);
        };

        f.write("RIFF", 4);
        put_u32(riff_size);
        f.write("WAVE", 4);

        f.write("fmt ", 4);
        put_u32(fmt_size);
        put_u16(1);
        put_u16(static_cast<uint16_t>(channels));
        put_u32(static_cast<uint32_t>(sample_rate));
        put_u32(static_cast<uint32_t>(sample_rate * channels * sizeof(int16_t)));
        put_u16(static_cast<uint16_t>(channels * sizeof(int16_t)));
        put_u16(16);

        f.write("data", 4);
        put_u32(data_size);

        std::vector<int16_t> pcm(count);
        for (size_t i = 0; i < count; ++i) {
            float s = std::max(-1.0f, std::min(1.0f, samples[i]));
            pcm[i]  = static_cast<int16_t>(std::lrintf(s * 32767.0f));
        }
        f.write(reinterpret_cast<const char*>(pcm.data()),
                static_cast<std::streamsize>(pcm.size() * sizeof(int16_t)));
        f.flush();
    }

    bool decode_pcm_from_bytes(const uint8_t* data, size_t len, PcmAudio& out) {
        if (len < 44) return false;
        if (std::memcmp(data, "RIFF", 4) != 0) return false;
        if (std::memcmp(data + 8, "WAVE", 4) != 0) return false;

        size_t pos = 12;
        uint16_t audio_format    = 0;
        uint16_t channels        = 0;
        uint32_t sample_rate     = 0;
        uint16_t bits_per_sample = 0;
        const uint8_t* data_ptr  = nullptr;
        uint32_t data_bytes      = 0;

        while (pos + 8 <= len) {
            const uint8_t* hdr = data + pos;
            uint32_t chunk_size = read_u32_le(hdr + 4);
            if (pos + 8 + chunk_size > len) return false;

            if (std::memcmp(hdr, "fmt ", 4) == 0 && chunk_size >= 16) {
                audio_format    = read_u16_le(hdr + 8);
                channels        = read_u16_le(hdr + 10);
                sample_rate     = read_u32_le(hdr + 12);
                bits_per_sample = read_u16_le(hdr + 22);
            } else if (std::memcmp(hdr, "data", 4) == 0) {
                data_ptr   = hdr + 8;
                data_bytes = chunk_size;
                break;
            }
            pos += 8 + chunk_size;
            if (chunk_size & 1) pos += 1;
        }

        if (!data_ptr || data_bytes == 0) return false;
        if (channels == 0 || sample_rate == 0) return false;

        out.sample_rate = static_cast<int>(sample_rate);
        out.channels    = static_cast<int>(channels);

        if (audio_format == 1 && bits_per_sample == 16) {
            size_t total = data_bytes / 2;
            out.samples.assign(total, 0.0f);
            const int16_t* pcm = reinterpret_cast<const int16_t*>(data_ptr);
            for (size_t i = 0; i < total; ++i) {
                out.samples[i] = static_cast<float>(pcm[i]) / 32768.0f;
            }
            if (channels > 1) {
                size_t frames = total / channels;
                std::vector<float> mono(frames, 0.0f);
                for (size_t i = 0; i < frames; ++i) {
                    float acc = 0.0f;
                    for (int c = 0; c < channels; ++c) acc += out.samples[i * channels + c];
                    mono[i] = acc / static_cast<float>(channels);
                }
                out.samples = std::move(mono);
                out.channels = 1;
            }
            return true;
        }
        if (audio_format == 3 && bits_per_sample == 32) {
            size_t total = data_bytes / 4;
            out.samples.assign(total, 0.0f);
            const float* fp = reinterpret_cast<const float*>(data_ptr);
            std::memcpy(out.samples.data(), fp, total * sizeof(float));
            if (channels > 1) {
                size_t frames = total / channels;
                std::vector<float> mono(frames, 0.0f);
                for (size_t i = 0; i < frames; ++i) {
                    float acc = 0.0f;
                    for (int c = 0; c < channels; ++c) acc += out.samples[i * channels + c];
                    mono[i] = acc / static_cast<float>(channels);
                }
                out.samples = std::move(mono);
                out.channels = 1;
            }
            return true;
        }
        return false;
    }

    bool decode_pcm_from_file(const std::string& path, PcmAudio& out) {
        std::ifstream f(path, std::ios::binary | std::ios::ate);
        if (!f) return false;
        std::streamsize size = f.tellg();
        if (size <= 0) return false;
        f.seekg(0, std::ios::beg);
        std::vector<uint8_t> buf(static_cast<size_t>(size));
        f.read(reinterpret_cast<char*>(buf.data()), size);
        return decode_pcm_from_bytes(buf.data(), buf.size(), out);
    }

}
