#pragma once

#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

namespace httplib {
    class Server;
}

namespace tn::server {

    class ServerCore {
    public:
        ServerCore();
        ~ServerCore();

        ServerCore(const ServerCore&) = delete;
        ServerCore& operator=(const ServerCore&) = delete;

        bool start(const std::string& host, int port);
        void stop();

        bool isRunning() const { return running_.load(); }
        int  boundPort() const { return bound_port_.load(); }

    private:
        void registerRoutes();

        std::unique_ptr<httplib::Server> server_;
        std::thread                      listen_thread_;
        std::mutex                       lifecycle_mu_;
        std::atomic<bool>                running_{false};
        std::atomic<int>                 bound_port_{-1};
    };

    ServerCore& core();

}
