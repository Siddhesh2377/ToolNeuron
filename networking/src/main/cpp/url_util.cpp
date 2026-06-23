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

namespace {

int b64_value(char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+' || c == '-') return 62;
    if (c == '/' || c == '_') return 63;
    return -1;
}

std::string base64url_decode(const std::string& in) {
    std::string out;
    out.reserve(in.size() * 3 / 4 + 1);
    int buf = 0;
    int bits = 0;
    for (char c : in) {
        if (c == '=' || c == '\0') break;
        int v = b64_value(c);
        if (v < 0) continue;
        buf = (buf << 6) | v;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out.push_back(static_cast<char>((buf >> bits) & 0xFF));
        }
    }
    return out;
}

}

std::string unwrap_bing_redirect(const std::string& href) {
    if (href.find("/ck/a") == std::string::npos) return href;
    const std::string marker = "u=a1";
    auto pos = href.find(marker);
    if (pos == std::string::npos) return href;
    pos += marker.size();
    auto end = href.find('&', pos);
    std::string payload = (end == std::string::npos)
        ? href.substr(pos)
        : href.substr(pos, end - pos);
    std::string decoded = base64url_decode(payload);
    if (decoded.rfind("http", 0) == 0) return decoded;
    return href;
}

}
