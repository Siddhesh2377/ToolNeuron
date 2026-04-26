#include "server_webui.h"

#include <mutex>

namespace tn::server::webui {

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
