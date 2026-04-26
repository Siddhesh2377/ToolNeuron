#include "server_audit.h"

#include <nlohmann/json.hpp>

#include <deque>
#include <mutex>

namespace tn::server::audit {

    using json = nlohmann::json;

    namespace {
        constexpr size_t     kCapacity = 128;
        std::mutex           g_mu;
        std::deque<Event>    g_events;
    }

    void record(const Event& event) {
        std::lock_guard<std::mutex> lock(g_mu);
        if (g_events.size() >= kCapacity) g_events.pop_front();
        g_events.push_back(event);
    }

    void clear() {
        std::lock_guard<std::mutex> lock(g_mu);
        g_events.clear();
    }

    std::string recent_json(int max_count) {
        std::deque<Event> snap;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            snap = g_events;
        }

        json arr = json::array();
        int limit = max_count > 0 ? max_count : static_cast<int>(snap.size());
        int start = static_cast<int>(snap.size()) - limit;
        if (start < 0) start = 0;

        for (int i = start; i < static_cast<int>(snap.size()); ++i) {
            const auto& e = snap[i];
            json o;
            o["ts_ms"]       = e.timestamp_unix_ms;
            o["method"]      = e.method;
            o["path"]        = e.path;
            o["status"]      = e.status;
            o["duration_ms"] = e.duration_ms;
            o["client"]      = e.client_addr_hash;
            arr.push_back(std::move(o));
        }
        return arr.dump();
    }

    int count() {
        std::lock_guard<std::mutex> lock(g_mu);
        return static_cast<int>(g_events.size());
    }

}
