#pragma once

#include <string>

namespace tn::server::webui {
    void set_html(const std::string& html);
    void clear_html();
    std::string get_html();
    bool has_html();
}
