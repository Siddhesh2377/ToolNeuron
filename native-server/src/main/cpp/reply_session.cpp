#include "reply_session.h"

#include <chrono>
#include <utility>

namespace tn::server::reply {

    using std::chrono::milliseconds;

    void Session::push_text(std::string body, std::string mime) {
        {
            std::lock_guard<std::mutex> lock(mu_);
            if (finished_.load()) return;
            pending_.state       = State::Text;
            pending_.text        = std::move(body);
            pending_.binary_mime = std::move(mime);
            finished_.store(true);
        }
        cv_.notify_all();
    }

    void Session::push_binary_path(std::string path, std::string mime) {
        {
            std::lock_guard<std::mutex> lock(mu_);
            if (finished_.load()) return;
            pending_.state       = State::Binary;
            pending_.binary_path = std::move(path);
            pending_.binary_mime = std::move(mime);
            finished_.store(true);
        }
        cv_.notify_all();
    }

    void Session::push_error(std::string message) {
        {
            std::lock_guard<std::mutex> lock(mu_);
            if (finished_.load()) return;
            pending_.state         = State::Error;
            pending_.error_message = std::move(message);
            finished_.store(true);
        }
        cv_.notify_all();
    }

    void Session::cancel() {
        {
            std::lock_guard<std::mutex> lock(mu_);
            if (finished_.load()) return;
            pending_.state = State::Cancelled;
            finished_.store(true);
        }
        cv_.notify_all();
    }

    Result Session::wait(int timeout_ms) {
        std::unique_lock<std::mutex> lock(mu_);
        if (!finished_.load()) {
            if (timeout_ms <= 0) {
                cv_.wait(lock, [this] { return finished_.load(); });
            } else {
                cv_.wait_for(lock, milliseconds(timeout_ms),
                             [this] { return finished_.load(); });
            }
        }
        return pending_;
    }

    std::shared_ptr<Session> Registry::create() {
        int64_t id = next_id_.fetch_add(1);
        auto session = std::make_shared<Session>(id);
        std::lock_guard<std::mutex> lock(mu_);
        by_id_.emplace(id, session);
        return session;
    }

    std::shared_ptr<Session> Registry::get(int64_t id) {
        std::lock_guard<std::mutex> lock(mu_);
        auto it = by_id_.find(id);
        return it == by_id_.end() ? nullptr : it->second;
    }

    void Registry::erase(int64_t id) {
        std::lock_guard<std::mutex> lock(mu_);
        by_id_.erase(id);
    }

    Registry& registry() {
        static Registry instance;
        return instance;
    }

}
