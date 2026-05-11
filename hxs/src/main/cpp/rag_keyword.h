#pragma once

#include "collection.h"

#include <cstdint>
#include <mutex>
#include <shared_mutex>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace hxs {

struct RagHit {
    std::string doc_id;
    std::string chat_id;
    std::string source_id;
    int32_t chunk_index = 0;
    std::string text;
    double score = 0.0;
};

class RagKeywordIndex {
public:
    static constexpr uint16_t TAG_DOC_ID      = 1;
    static constexpr uint16_t TAG_CHAT_ID     = 2;
    static constexpr uint16_t TAG_SOURCE_ID   = 3;
    static constexpr uint16_t TAG_CHUNK_INDEX = 4;
    static constexpr uint16_t TAG_TEXT        = 5;

    explicit RagKeywordIndex(Collection* coll);

    int32_t ingest(const std::string& doc_id,
                   const std::string& chat_id,
                   const std::string& source_id,
                   int32_t chunk_index,
                   const std::string& text);

    int32_t remove_document(const std::string& doc_id);

    void clear_all();

    int32_t doc_count(const std::string& doc_id) const;

    std::vector<RagHit> query(const std::string& query_text,
                              const std::string& chat_id,
                              int32_t top_k) const;

    void rebuild();

private:
    struct Posting {
        uint32_t record_id;
        uint16_t term_freq;
    };

    Collection* coll_;
    mutable std::shared_mutex mtx_;

    std::unordered_map<std::string, std::vector<Posting>> postings_;
    std::unordered_map<uint32_t, uint16_t> doc_lengths_;
    double avg_doc_len_ = 0.0;
    bool dirty_ = true;

    void index_record(uint32_t record_id, const std::string& text);
    void unindex_record(uint32_t record_id);
    void recompute_average_length();
};

} // namespace hxs
