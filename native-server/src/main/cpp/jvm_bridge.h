#pragma once

#include <cstdint>
#include <jni.h>
#include <string>
#include <vector>

namespace tn::server::jvm {

    void attach_bridge(JNIEnv* env, jobject bridge);
    void detach_bridge(JNIEnv* env);
    bool has_bridge();

    bool start_generation(int64_t gen_id,
                          const std::string& model_id,
                          const std::string& messages_json,
                          const std::string& params_json,
                          const std::vector<std::string>& image_paths);

    void cancel_generation(int64_t gen_id);

    bool start_embedding(int64_t reply_id,
                         const std::string& model_id,
                         const std::string& inputs_json);

    bool start_tts(int64_t reply_id,
                   const std::string& model_id,
                   const std::string& text,
                   int speaker_id,
                   float speed,
                   const std::string& out_path);

    bool start_stt(int64_t reply_id,
                   const std::string& model_id,
                   const std::string& wav_path);

    bool start_image_gen(int64_t reply_id,
                        const std::string& model_id,
                        const std::string& params_json,
                        const std::string& input_image_path,
                        const std::string& mask_path,
                        const std::string& out_path);

    bool start_image_upscale(int64_t reply_id,
                            const std::string& model_id,
                            const std::string& image_path,
                            const std::string& out_path);

    void emit_request_event(const std::string& event_json);

    void on_vm_load(JavaVM* vm);

}
