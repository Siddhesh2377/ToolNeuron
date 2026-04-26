#pragma once

#include <string>
#include <unordered_set>
#include <vector>

namespace tn::server::models {

    struct ModelRef {
        std::string id;
        long long   created_unix;
    };

    void set_catalog_json(const std::string& data_array_json);
    void clear_catalog();

    std::vector<ModelRef> snapshot();
    bool                  has_id(const std::string& id);

    std::string build_list_response();

}
