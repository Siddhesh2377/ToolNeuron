#pragma once

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

namespace tn::server::gen {

    enum class TakeState {
        Token,
        Done,
        Error,
        Cancelled,
    };

    struct Take {
        TakeState   state;
        std::string token;
        std::string finish_reason;
        std::string error_message;
    };

    class Session {
    public:
        explicit Session(int64_t id) : id_(id) {}

        int64_t id() const { return id_; }

        void push_token(std::string token);
        void push_done(std::string finish_reason);
        void push_error(std::string message);
        void cancel();

        Take take(int timeout_ms);

        bool is_finished() const { return finished_.load(); }

    private:
        int64_t                 id_;
        std::mutex              mu_;
        std::condition_variable cv_;
        std::deque<std::string> tokens_;
        std::string             finish_reason_;
        std::string             error_message_;
        std::atomic<bool>       finished_{false};
        std::atomic<bool>       cancelled_{false};
        bool                    errored_{false};
    };

    class Registry {
    public:
        std::shared_ptr<Session> create();
        std::shared_ptr<Session> get(int64_t id);
        void                     erase(int64_t id);

    private:
        std::mutex                                               mu_;
        std::unordered_map<int64_t, std::shared_ptr<Session>>    by_id_;
        std::atomic<int64_t>                                     next_id_{1};
    };

    Registry& registry();

}
