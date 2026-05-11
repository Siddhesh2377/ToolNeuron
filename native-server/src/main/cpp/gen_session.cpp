#include "gen_session.h"

#include <chrono>
#include <utility>

namespace tn::server::gen {

    using std::chrono::milliseconds;

    void Session::push_token(std::string token) {
        {
            std::lock_guard<std::mutex> lock(mu_);
            if (finished_.load() || cancelled_.load()) return;
            tokens_.push_back(std::move(token));
        }
        cv_.notify_all();
    }

    void Session::push_done(std::string finish_reason) {
        {
            std::lock_guard<std::mutex> lock(mu_);
            if (finished_.load()) return;
            finish_reason_ = std::move(finish_reason);
            finished_.store(true);
        }
        cv_.notify_all();
    }

    void Session::push_error(std::string message) {
        {
            std::lock_guard<std::mutex> lock(mu_);
            if (finished_.load()) return;
            error_message_ = std::move(message);
            errored_       = true;
            finished_.store(true);
        }
        cv_.notify_all();
    }

    void Session::cancel() {
        {
            std::lock_guard<std::mutex> lock(mu_);
            if (finished_.load()) return;
            cancelled_.store(true);
            finished_.store(true);
        }
        cv_.notify_all();
    }

    Take Session::take(int timeout_ms) {
        std::unique_lock<std::mutex> lock(mu_);
        cv_.wait_for(lock, milliseconds(timeout_ms), [this] {
            return !tokens_.empty() || finished_.load();
        });

        if (!tokens_.empty()) {
            Take t;
            t.state = TakeState::Token;
            t.token = std::move(tokens_.front());
            tokens_.pop_front();
            return t;
        }

        if (cancelled_.load()) {
            Take t;
            t.state = TakeState::Cancelled;
            return t;
        }

        if (errored_) {
            Take t;
            t.state         = TakeState::Error;
            t.error_message = error_message_;
            return t;
        }

        if (finished_.load()) {
            Take t;
            t.state         = TakeState::Done;
            t.finish_reason = finish_reason_.empty() ? "stop" : finish_reason_;
            return t;
        }

        Take t;
        t.state = TakeState::Token;
        return t;
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
