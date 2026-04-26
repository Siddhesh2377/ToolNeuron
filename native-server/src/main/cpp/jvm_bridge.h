#pragma once

#include <cstdint>
#include <jni.h>
#include <string>

namespace tn::server::jvm {

    void attach_bridge(JNIEnv* env, jobject bridge);
    void detach_bridge(JNIEnv* env);
    bool has_bridge();

    bool start_generation(int64_t gen_id,
                          const std::string& messages_json,
                          const std::string& params_json);

    void cancel_generation(int64_t gen_id);

    void emit_request_event(const std::string& event_json);

    void on_vm_load(JavaVM* vm);

}
