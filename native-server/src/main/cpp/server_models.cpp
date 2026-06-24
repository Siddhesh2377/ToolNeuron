#include "server_models.h"

#include <nlohmann/json.hpp>

#include <mutex>
#include <unordered_map>

namespace tn::server::models {

    using json = nlohmann::json;

    namespace {
        std::mutex                          g_mu;
        std::vector<ModelRef>               g_models;
        std::unordered_set<std::string>     g_ids;
        std::unordered_map<std::string, size_t> g_by_id;
    }

    Kind parse_kind(const std::string& s) {
        if (s == "gguf"           || s == "GGUF")           return Kind::ChatGguf;
        if (s == "vlm"            || s == "VLM")            return Kind::Vlm;
        if (s == "embedding"      || s == "EMBEDDING")      return Kind::Embedding;
        if (s == "tts"            || s == "TTS")            return Kind::Tts;
        if (s == "stt"            || s == "STT")            return Kind::Stt;
        if (s == "image_gen"      || s == "IMAGE_GEN")      return Kind::ImageGen;
        if (s == "image_upscaler" || s == "IMAGE_UPSCALER") return Kind::ImageUpscaler;
        return Kind::Unknown;
    }

    const char* kind_owner(Kind k) {
        switch (k) {
            case Kind::ChatGguf:       return "tool-neuron-chat";
            case Kind::Vlm:            return "tool-neuron-vlm";
            case Kind::Embedding:      return "tool-neuron-embedding";
            case Kind::Tts:            return "tool-neuron-tts";
            case Kind::Stt:            return "tool-neuron-stt";
            case Kind::ImageGen:       return "tool-neuron-image";
            case Kind::ImageUpscaler:  return "tool-neuron-upscaler";
            default:                   return "tool-neuron";
        }
    }

    static const char* kind_token(Kind k) {
        switch (k) {
            case Kind::ChatGguf:       return "gguf";
            case Kind::Vlm:            return "vlm";
            case Kind::Embedding:      return "embedding";
            case Kind::Tts:            return "tts";
            case Kind::Stt:            return "stt";
            case Kind::ImageGen:       return "image_gen";
            case Kind::ImageUpscaler:  return "image_upscaler";
            default:                   return "unknown";
        }
    }

    void set_catalog_json(const std::string& data_array_json) {
        std::vector<ModelRef>                   parsed;
        std::unordered_set<std::string>         ids;
        std::unordered_map<std::string, size_t> by_id;

        try {
            json arr = json::parse(data_array_json);
            if (arr.is_array()) {
                parsed.reserve(arr.size());
                for (const auto& entry : arr) {
                    if (!entry.is_object()) continue;
                    auto idIt = entry.find("id");
                    if (idIt == entry.end() || !idIt->is_string()) continue;
                    ModelRef m;
                    m.id = idIt->get<std::string>();

                    auto nameIt = entry.find("name");
                    if (nameIt != entry.end() && nameIt->is_string()) m.display_name = nameIt->get<std::string>();
                    if (m.display_name.empty()) m.display_name = m.id;

                    auto pathIt = entry.find("path");
                    if (pathIt != entry.end() && pathIt->is_string()) m.path = pathIt->get<std::string>();

                    auto mmIt = entry.find("mmproj_path");
                    if (mmIt != entry.end() && mmIt->is_string()) m.mmproj_path = mmIt->get<std::string>();

                    auto cfgIt = entry.find("config_json");
                    if (cfgIt != entry.end() && cfgIt->is_string()) m.config_json = cfgIt->get<std::string>();
                    if (m.config_json.empty()) m.config_json = "{}";

                    auto typeIt = entry.find("type");
                    if (typeIt != entry.end() && typeIt->is_string()) m.kind = parse_kind(typeIt->get<std::string>());

                    auto cIt = entry.find("created");
                    m.created_unix = (cIt != entry.end() && cIt->is_number_integer())
                        ? cIt->get<long long>() : 0;

                    auto dIt = entry.find("default");
                    m.default_model = (dIt != entry.end() && dIt->is_boolean() && dIt->get<bool>());

                    ids.insert(m.id);
                    by_id.emplace(m.id, parsed.size());
                    parsed.push_back(std::move(m));
                }
            }
        } catch (...) {
            return;
        }

        std::lock_guard<std::mutex> lock(g_mu);
        g_models = std::move(parsed);
        g_ids    = std::move(ids);
        g_by_id  = std::move(by_id);
    }

    void clear_catalog() {
        std::lock_guard<std::mutex> lock(g_mu);
        g_models.clear();
        g_ids.clear();
        g_by_id.clear();
    }

    std::vector<ModelRef> snapshot() {
        std::lock_guard<std::mutex> lock(g_mu);
        return g_models;
    }

    bool has_id(const std::string& id) {
        std::lock_guard<std::mutex> lock(g_mu);
        return g_ids.find(id) != g_ids.end();
    }

    bool has_id_of_kind(const std::string& id, Kind k) {
        std::lock_guard<std::mutex> lock(g_mu);
        auto it = g_by_id.find(id);
        if (it == g_by_id.end()) return false;
        return g_models[it->second].kind == k;
    }

    ModelRef get(const std::string& id) {
        std::lock_guard<std::mutex> lock(g_mu);
        auto it = g_by_id.find(id);
        if (it == g_by_id.end()) return {};
        return g_models[it->second];
    }

    ModelRef first_of_kind(Kind k) {
        std::lock_guard<std::mutex> lock(g_mu);
        for (const auto& m : g_models) {
            if (m.kind == k) return m;
        }
        return {};
    }

    ModelRef default_of_kind(Kind k) {
        std::lock_guard<std::mutex> lock(g_mu);
        for (const auto& m : g_models) {
            if (m.kind == k && m.default_model) return m;
        }
        return {};
    }

    size_t count_of_kind(Kind k) {
        std::lock_guard<std::mutex> lock(g_mu);
        size_t count = 0;
        for (const auto& m : g_models) {
            if (m.kind == k) ++count;
        }
        return count;
    }

    bool has_any_of_kind(Kind k) {
        std::lock_guard<std::mutex> lock(g_mu);
        for (const auto& m : g_models) {
            if (m.kind == k) return true;
        }
        return false;
    }

    std::string build_list_response() {
        std::vector<ModelRef> snap;
        {
            std::lock_guard<std::mutex> lock(g_mu);
            snap = g_models;
        }

        json root;
        root["object"] = "list";
        json data = json::array();
        for (const auto& m : snap) {
            json e;
            e["id"]       = m.id;
            e["object"]   = "model";
            e["created"]  = m.created_unix;
            e["owned_by"] = kind_owner(m.kind);
            e["type"]     = kind_token(m.kind);
            if (m.default_model) e["default"] = true;
            data.push_back(std::move(e));
        }
        root["data"] = std::move(data);
        return root.dump();
    }

}
