#pragma once

#include <cstdint>
#include <string>

namespace tn::server::rl {

    struct Config {
        double capacity    = 30.0;
        double refill_rps  = 1.0;
        int    auth_fail_threshold = 20;
        long long ban_duration_ms  = 60LL * 60LL * 1000LL;
    };

    void configure(const Config& cfg);
    Config current_config();

    bool allow(const std::string& client_addr, long long now_ms);
    void note_auth_fail(const std::string& client_addr, long long now_ms);
    void note_auth_success(const std::string& client_addr);

    bool is_banned(const std::string& client_addr, long long now_ms);
    void unban(const std::string& client_addr);
    void reset_all();

}
