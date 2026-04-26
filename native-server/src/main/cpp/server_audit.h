#pragma once

#include <cstdint>
#include <string>

namespace tn::server::audit {

    struct Event {
        long long   timestamp_unix_ms;
        std::string method;
        std::string path;
        int         status;
        long long   duration_ms;
        std::string client_addr_hash;
    };

    void record(const Event& event);
    void clear();

    std::string recent_json(int max_count);
    int         count();

}
