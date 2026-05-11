#pragma once

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

namespace tn::server::reply {

    enum class State {
        Pending,
        Text,
        Binary,
        Error,
        Cancelled,
    };

    struct Result {
        State        state = State::Pending;
        std::string  text;
        std::string  binary_path;
        std::string  binary_mime;
        std::string  error_message;
    };

    class Session {
    public:
        explicit Session(int64_t id) : id_(id) {}

        int64_t id() const { return id_; }

        void push_text(std::string body, std::string mime = "application/json");
        void push_binary_path(std::string path, std::string mime);
        void push_error(std::string message);
        void cancel();

        Result wait(int timeout_ms);

        bool is_finished() const { return finished_.load(); }

    private:
        int64_t                 id_;
        std::mutex              mu_;
        std::condition_variable cv_;
        std::atomic<bool>       finished_{false};
        Result                  pending_;
    };

    class Registry {
    public:
        std::shared_ptr<Session> create();
        std::shared_ptr<Session> get(int64_t id);
        void                     erase(int64_t id);

    private:
        std::mutex                                            mu_;
        std::unordered_map<int64_t, std::shared_ptr<Session>> by_id_;
        std::atomic<int64_t>                                  next_id_{1};
    };

    Registry& registry();

}
