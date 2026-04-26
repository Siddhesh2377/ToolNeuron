#include "server_rate_limit.h"

#include <mutex>
#include <unordered_map>

namespace tn::server::rl {

    namespace {
        struct Bucket {
            double    tokens;
            long long last_refill_ms;
        };

        struct BanInfo {
            long long until_ms;
        };

        struct State {
            std::mutex                               mu;
            Config                                   cfg;
            std::unordered_map<std::string, Bucket>  buckets;
            std::unordered_map<std::string, int>     auth_fails;
            std::unordered_map<std::string, BanInfo> banned;
        };

        State& state() {
            static State s;
            return s;
        }

        Bucket& bucket_ref(State& s, const std::string& addr, long long now_ms) {
            auto it = s.buckets.find(addr);
            if (it == s.buckets.end()) {
                Bucket b{ s.cfg.capacity, now_ms };
                it = s.buckets.emplace(addr, b).first;
            } else {
                double elapsed_s = static_cast<double>(now_ms - it->second.last_refill_ms) / 1000.0;
                if (elapsed_s > 0) {
                    it->second.tokens = std::min(
                        s.cfg.capacity,
                        it->second.tokens + elapsed_s * s.cfg.refill_rps);
                    it->second.last_refill_ms = now_ms;
                }
            }
            return it->second;
        }
    }

    void configure(const Config& cfg) {
        auto& s = state();
        std::lock_guard<std::mutex> lock(s.mu);
        s.cfg = cfg;
    }

    Config current_config() {
        auto& s = state();
        std::lock_guard<std::mutex> lock(s.mu);
        return s.cfg;
    }

    bool allow(const std::string& addr, long long now_ms) {
        auto& s = state();
        std::lock_guard<std::mutex> lock(s.mu);

        auto banIt = s.banned.find(addr);
        if (banIt != s.banned.end()) {
            if (now_ms < banIt->second.until_ms) return false;
            s.banned.erase(banIt);
        }

        Bucket& b = bucket_ref(s, addr, now_ms);
        if (b.tokens >= 1.0) {
            b.tokens -= 1.0;
            return true;
        }
        return false;
    }

    void note_auth_fail(const std::string& addr, long long now_ms) {
        auto& s = state();
        std::lock_guard<std::mutex> lock(s.mu);
        int& fails = s.auth_fails[addr];
        fails += 1;
        if (fails >= s.cfg.auth_fail_threshold) {
            s.banned[addr] = BanInfo{ now_ms + s.cfg.ban_duration_ms };
            fails = 0;
        }
    }

    void note_auth_success(const std::string& addr) {
        auto& s = state();
        std::lock_guard<std::mutex> lock(s.mu);
        s.auth_fails.erase(addr);
    }

    bool is_banned(const std::string& addr, long long now_ms) {
        auto& s = state();
        std::lock_guard<std::mutex> lock(s.mu);
        auto it = s.banned.find(addr);
        if (it == s.banned.end()) return false;
        if (now_ms >= it->second.until_ms) {
            s.banned.erase(it);
            return false;
        }
        return true;
    }

    void unban(const std::string& addr) {
        auto& s = state();
        std::lock_guard<std::mutex> lock(s.mu);
        s.banned.erase(addr);
        s.auth_fails.erase(addr);
    }

    void reset_all() {
        auto& s = state();
        std::lock_guard<std::mutex> lock(s.mu);
        s.buckets.clear();
        s.auth_fails.clear();
        s.banned.clear();
    }

}
