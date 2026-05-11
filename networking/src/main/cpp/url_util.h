#pragma once

#include <string>

namespace net::url {

std::string encode(const std::string& in);

std::string decode(const std::string& in);

std::string unwrap_ddg_redirect(const std::string& href);

}
