#include "server_docs.h"

#include <mutex>

namespace tn::server::docs {

    namespace {
        std::mutex  g_mu;
        std::string g_html;
    }

    void set_html(const std::string& html) {
        std::lock_guard<std::mutex> lock(g_mu);
        g_html = html;
    }

    void clear_html() {
        std::lock_guard<std::mutex> lock(g_mu);
        g_html.clear();
    }

    std::string get_html() {
        std::lock_guard<std::mutex> lock(g_mu);
        return g_html;
    }

    bool has_html() {
        std::lock_guard<std::mutex> lock(g_mu);
        return !g_html.empty();
    }

}
