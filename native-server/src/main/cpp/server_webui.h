#pragma once

#include <string>

namespace tn::server::webui {
    void set_html(const std::string& html);
    void set_css(const std::string& css);
    void clear_html();
    void clear_css();
    std::string get_html();
    std::string get_css();
    bool has_html();
    bool has_css();
}
