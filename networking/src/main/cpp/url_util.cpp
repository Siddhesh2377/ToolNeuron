#include "url_util.h"

#include <cctype>
#include <sstream>
#include <iomanip>

namespace net::url {

namespace {

int hex_value(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return 10 + (c - 'a');
    if (c >= 'A' && c <= 'F') return 10 + (c - 'A');
    return -1;
}

}

std::string encode(const std::string& in) {
    std::ostringstream out;
    out.fill('0');
    out << std::hex;
    for (unsigned char c : in) {
        if (std::isalnum(c) || c == '-' || c == '_' || c == '.' || c == '~') {
            out << static_cast<char>(c);
        } else {
            out << '%' << std::setw(2) << static_cast<int>(c);
        }
    }
    return out.str();
}

std::string decode(const std::string& in) {
    std::string out;
    out.reserve(in.size());
    for (size_t i = 0; i < in.size(); ++i) {
        char c = in[i];
        if (c == '+') {
            out.push_back(' ');
        } else if (c == '%' && i + 2 < in.size()) {
            int hi = hex_value(in[i + 1]);
            int lo = hex_value(in[i + 2]);
            if (hi >= 0 && lo >= 0) {
                out.push_back(static_cast<char>((hi << 4) | lo));
                i += 2;
            } else {
                out.push_back(c);
            }
        } else {
            out.push_back(c);
        }
    }
    return out;
}

std::string unwrap_ddg_redirect(const std::string& href) {
    const std::string marker = "uddg=";
    auto pos = href.find(marker);
    if (pos == std::string::npos) return href;
    pos += marker.size();
    auto end = href.find('&', pos);
    std::string encoded = (end == std::string::npos)
        ? href.substr(pos)
        : href.substr(pos, end - pos);
    return decode(encoded);
}

}
