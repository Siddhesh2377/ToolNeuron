#include "server_webui.h"

#include <mutex>

namespace tn::server::webui {

    namespace {
        std::mutex  g_mu;
        std::string g_html;
        std::string g_css;
    }

    void set_html(const std::string& html) {
        std::lock_guard<std::mutex> lock(g_mu);
        g_html = html;
    }

    void set_css(const std::string& css) {
        std::lock_guard<std::mutex> lock(g_mu);
        g_css = css;
    }

    void clear_html() {
        std::lock_guard<std::mutex> lock(g_mu);
        g_html.clear();
    }

    void clear_css() {
        std::lock_guard<std::mutex> lock(g_mu);
        g_css.clear();
    }

    std::string get_html() {
        std::lock_guard<std::mutex> lock(g_mu);
        return g_html;
    }

    std::string get_css() {
        std::lock_guard<std::mutex> lock(g_mu);
        return g_css;
    }

    bool has_html() {
        std::lock_guard<std::mutex> lock(g_mu);
        return !g_html.empty();
    }

    bool has_css() {
        std::lock_guard<std::mutex> lock(g_mu);
        return !g_css.empty();
    }

}
