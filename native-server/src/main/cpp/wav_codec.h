#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace tn::server::wav {

    struct PcmAudio {
        int                 sample_rate = 0;
        int                 channels    = 1;
        std::vector<float>  samples;
    };

    void encode_pcm16_to_file(const std::string& path,
                              const float* samples,
                              size_t count,
                              int sample_rate,
                              int channels);

    bool decode_pcm_from_file(const std::string& path, PcmAudio& out);

    bool decode_pcm_from_bytes(const uint8_t* data, size_t len, PcmAudio& out);

}
