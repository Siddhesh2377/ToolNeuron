#pragma once

#include <string>
#include <unordered_set>
#include <vector>

namespace tn::server::models {

    enum class Kind {
        Unknown,
        ChatGguf,
        Vlm,
        Embedding,
        Tts,
        Stt,
        ImageGen,
        ImageUpscaler,
    };

    struct ModelRef {
        std::string id;
        std::string display_name;
        std::string path;
        std::string mmproj_path;
        std::string config_json;
        Kind        kind         = Kind::Unknown;
        long long   created_unix = 0;
        bool        default_model = false;
    };

    Kind parse_kind(const std::string& s);
    const char* kind_owner(Kind k);

    void set_catalog_json(const std::string& data_array_json);
    void clear_catalog();

    std::vector<ModelRef> snapshot();
    bool                  has_id(const std::string& id);
    bool                  has_id_of_kind(const std::string& id, Kind k);
    ModelRef              get(const std::string& id);
    ModelRef              first_of_kind(Kind k);
    ModelRef              default_of_kind(Kind k);
    size_t                count_of_kind(Kind k);
    bool                  has_any_of_kind(Kind k);

    std::string build_list_response();

}
