#include "rag_keyword.h"
#include "wire_format.h"

#include <algorithm>
#include <cmath>
#include <unordered_set>

#include <android/log.h>
#define TAG "HXS-RAG"

namespace hxs {

namespace {

constexpr double K1 = 1.2;
constexpr double B  = 0.75;

constexpr size_t MIN_TOKEN_LEN = 2;
constexpr size_t MAX_TOKEN_LEN = 64;

bool is_token_char(uint8_t c) {
    return (c >= '0' && c <= '9') ||
           (c >= 'a' && c <= 'z') ||
           (c >= 'A' && c <= 'Z') ||
           c == '_' ||
           c >= 0x80;
}

uint8_t to_lower_ascii(uint8_t c) {
    return (c >= 'A' && c <= 'Z') ? static_cast<uint8_t>(c + 32) : c;
}

void tokenize(const std::string& text, std::vector<std::string>& out) {
    out.clear();
    const auto* p = reinterpret_cast<const uint8_t*>(text.data());
    const auto* end = p + text.size();
    std::string buf;
    buf.reserve(32);
    while (p < end) {
        if (is_token_char(*p)) {
            buf.push_back(static_cast<char>(to_lower_ascii(*p)));
            ++p;
        } else {
            if (buf.size() >= MIN_TOKEN_LEN && buf.size() <= MAX_TOKEN_LEN) {
                out.push_back(std::move(buf));
                buf = {};
            } else {
                buf.clear();
            }
            ++p;
        }
    }
    if (buf.size() >= MIN_TOKEN_LEN && buf.size() <= MAX_TOKEN_LEN) {
        out.push_back(std::move(buf));
    }
}

} // namespace

RagKeywordIndex::RagKeywordIndex(Collection* coll) : coll_(coll) {
    rebuild();
}

void RagKeywordIndex::rebuild() {
    std::unique_lock lock(mtx_);
    postings_.clear();
    doc_lengths_.clear();
    avg_doc_len_ = 0.0;
    if (!coll_) return;
    coll_->for_each([this](const Record& rec) {
        const auto* text_bytes = rec.get_bytes(TAG_TEXT);
        if (!text_bytes || text_bytes->empty()) return;
        std::string text(reinterpret_cast<const char*>(text_bytes->data()), text_bytes->size());
        index_record(rec.id(), text);
    });
    recompute_average_length();
    dirty_ = false;
    __android_log_print(ANDROID_LOG_INFO, TAG,
        "rebuild: %zu records, %zu unique terms, avg_len=%.1f",
        doc_lengths_.size(), postings_.size(), avg_doc_len_);
}

void RagKeywordIndex::index_record(uint32_t record_id, const std::string& text) {
    std::vector<std::string> tokens;
    tokenize(text, tokens);
    if (tokens.empty()) return;

    std::unordered_map<std::string, uint16_t> term_freq;
    term_freq.reserve(tokens.size());
    for (const auto& t : tokens) {
        auto& f = term_freq[t];
        if (f < UINT16_MAX) ++f;
    }
    for (const auto& [term, tf] : term_freq) {
        postings_[term].push_back({record_id, tf});
    }
    uint16_t doc_len = tokens.size() > UINT16_MAX
        ? static_cast<uint16_t>(UINT16_MAX)
        : static_cast<uint16_t>(tokens.size());
    doc_lengths_[record_id] = doc_len;
}

void RagKeywordIndex::unindex_record(uint32_t record_id) {
    doc_lengths_.erase(record_id);
    for (auto it = postings_.begin(); it != postings_.end();) {
        auto& vec = it->second;
        vec.erase(std::remove_if(vec.begin(), vec.end(),
            [record_id](const Posting& p) { return p.record_id == record_id; }),
            vec.end());
        if (vec.empty()) it = postings_.erase(it);
        else ++it;
    }
}

void RagKeywordIndex::recompute_average_length() {
    if (doc_lengths_.empty()) {
        avg_doc_len_ = 0.0;
        return;
    }
    uint64_t total = 0;
    for (const auto& [_, len] : doc_lengths_) total += len;
    avg_doc_len_ = static_cast<double>(total) / static_cast<double>(doc_lengths_.size());
}

int32_t RagKeywordIndex::ingest(const std::string& doc_id,
                                const std::string& chat_id,
                                const std::string& source_id,
                                int32_t chunk_index,
                                const std::string& text) {
    if (!coll_) return -1;
    if (text.empty()) return 0;

    Record rec;
    rec.put_string(TAG_DOC_ID, doc_id);
    rec.put_string(TAG_CHAT_ID, chat_id);
    rec.put_string(TAG_SOURCE_ID, source_id);
    rec.put_varint(TAG_CHUNK_INDEX, chunk_index);
    rec.put_string(TAG_TEXT, text);

    uint32_t record_id = coll_->put(rec);
    if (record_id == 0) return -1;

    {
        std::unique_lock lock(mtx_);
        index_record(record_id, text);
        recompute_average_length();
    }
    return 1;
}

int32_t RagKeywordIndex::remove_document(const std::string& doc_id) {
    if (!coll_) return 0;
    auto records = coll_->query_string(TAG_DOC_ID, doc_id);
    int32_t removed = 0;
    for (const auto& rec : records) {
        if (coll_->remove(rec.id())) {
            std::unique_lock lock(mtx_);
            unindex_record(rec.id());
            ++removed;
        }
    }
    if (removed > 0) {
        std::unique_lock lock(mtx_);
        recompute_average_length();
    }
    return removed;
}

void RagKeywordIndex::clear_all() {
    if (!coll_) return;
    auto all = coll_->get_all();
    for (const auto& rec : all) coll_->remove(rec.id());
    std::unique_lock lock(mtx_);
    postings_.clear();
    doc_lengths_.clear();
    avg_doc_len_ = 0.0;
}

int32_t RagKeywordIndex::doc_count(const std::string& doc_id) const {
    if (!coll_) return 0;
    return static_cast<int32_t>(coll_->query_string(TAG_DOC_ID, doc_id).size());
}

std::vector<RagHit> RagKeywordIndex::query(const std::string& query_text,
                                            const std::string& chat_id,
                                            int32_t top_k) const {
    std::vector<RagHit> out;
    if (!coll_ || top_k <= 0 || query_text.empty()) return out;

    std::vector<std::string> q_tokens;
    tokenize(query_text, q_tokens);
    if (q_tokens.empty()) return out;

    std::shared_lock lock(mtx_);
    if (doc_lengths_.empty() || avg_doc_len_ <= 0.0) return out;

    std::unordered_set<std::string> unique_terms(q_tokens.begin(), q_tokens.end());
    const double n_docs = static_cast<double>(doc_lengths_.size());

    std::unordered_map<uint32_t, double> scores;
    scores.reserve(64);

    for (const auto& term : unique_terms) {
        auto pit = postings_.find(term);
        if (pit == postings_.end()) continue;
        const auto& posting_list = pit->second;
        const double df = static_cast<double>(posting_list.size());
        const double idf = std::log(1.0 + (n_docs - df + 0.5) / (df + 0.5));
        for (const auto& p : posting_list) {
            auto dl_it = doc_lengths_.find(p.record_id);
            if (dl_it == doc_lengths_.end()) continue;
            const double dl = static_cast<double>(dl_it->second);
            const double tf = static_cast<double>(p.term_freq);
            const double norm = tf + K1 * (1.0 - B + B * dl / avg_doc_len_);
            const double term_score = idf * (tf * (K1 + 1.0)) / norm;
            scores[p.record_id] += term_score;
        }
    }

    if (scores.empty()) return out;

    std::vector<std::pair<uint32_t, double>> ranked(scores.begin(), scores.end());
    std::sort(ranked.begin(), ranked.end(),
        [](const auto& a, const auto& b) { return a.second > b.second; });

    out.reserve(static_cast<size_t>(top_k));
    for (const auto& [rid, score] : ranked) {
        if (static_cast<int32_t>(out.size()) >= top_k) break;
        Record rec = coll_->get(rid);
        if (rec.id() == 0) continue;
        std::string rec_chat = rec.get_string(TAG_CHAT_ID, "");
        if (!chat_id.empty() && rec_chat != chat_id) continue;
        const auto* text_bytes = rec.get_bytes(TAG_TEXT);
        std::string text;
        if (text_bytes) text.assign(reinterpret_cast<const char*>(text_bytes->data()), text_bytes->size());
        RagHit hit;
        hit.doc_id    = rec.get_string(TAG_DOC_ID, "");
        hit.chat_id   = rec_chat;
        hit.source_id = rec.get_string(TAG_SOURCE_ID, "");
        hit.chunk_index = static_cast<int32_t>(rec.get_varint(TAG_CHUNK_INDEX, 0));
        hit.text = std::move(text);
        hit.score = score;
        out.push_back(std::move(hit));
    }
    return out;
}

} // namespace hxs
