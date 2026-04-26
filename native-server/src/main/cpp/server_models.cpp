#include "server_models.h"

#include <nlohmann/json.hpp>

#include <mutex>

namespace tn::server::models {

    using json = nlohmann::json;

    namespace {
        std::mutex               g_mu;
        std::vector<ModelRef>    g_models;
        std::unordered_set<std::string> g_ids;
    }

    void set_catalog_json(const std::string& data_array_json) {
        std::vector<ModelRef>           parsed;
        std::unordered_set<std::string> ids;

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
                    auto cIt = entry.find("created");
                    m.created_unix = (cIt != entry.end() && cIt->is_number_integer())
                        ? cIt->get<long long>() : 0;
                    ids.insert(m.id);
                    parsed.push_back(std::move(m));
                }
            }
        } catch (...) {
            return;
        }

        std::lock_guard<std::mutex> lock(g_mu);
        g_models = std::move(parsed);
        g_ids    = std::move(ids);
    }

    void clear_catalog() {
        std::lock_guard<std::mutex> lock(g_mu);
        g_models.clear();
        g_ids.clear();
    }

    std::vector<ModelRef> snapshot() {
        std::lock_guard<std::mutex> lock(g_mu);
        return g_models;
    }

    bool has_id(const std::string& id) {
        std::lock_guard<std::mutex> lock(g_mu);
        return g_ids.find(id) != g_ids.end();
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
            e["owned_by"] = "tool-neuron";
            data.push_back(std::move(e));
        }
        root["data"] = std::move(data);
        return root.dump();
    }

}
