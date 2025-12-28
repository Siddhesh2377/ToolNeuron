#include "tool_call_state.h"

#include "llama.h"
#include "ggml-backend.h"
#include <sstream>
#include <algorithm>

#include <jni.h>
#include <string>
#include <mutex>

bool ToolCallState::accumulate(const std::string& chunk) {
    for (char c : chunk) {
        if (!collecting) {
            if (c == '{') {
                collecting = true;
                brace_depth = 1;
                buf.clear();
                buf.push_back(c);
                continue;
            }
        } else {
            buf.push_back(c);
            if (c == '{') ++brace_depth;
            else if (c == '}') {
                --brace_depth;
                if (brace_depth == 0) return true;     // complete JSON
            }
        }
    }
    return false;
}

bool ToolCallState::extract_tool_call(std::string& name, std::string& payload) const {
    if (buf.find("\"tool_calls\"") == std::string::npos)
        return false;   // not a tool call

    // Build a simple parser for `name`
    size_t npos = buf.find("\"name\"");
    if (npos != std::string::npos) {
        size_t colon = buf.find(':', npos);
        if (colon != std::string::npos) {
            size_t q1 = buf.find('"', colon + 1);
            size_t q2 = buf.find('"', q1 + 1);
            if (q1 != std::string::npos && q2 != std::string::npos)
                name = buf.substr(q1 + 1, q2 - q1 - 1);
        }
    }
    if (name.empty()) name = "tool";

    payload = buf;
    return true;
}