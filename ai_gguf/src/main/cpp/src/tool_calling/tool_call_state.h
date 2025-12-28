#pragma once

#include <string>

class ToolCallState {
public:
    // Called for every generated piece; returns true when
    // a complete JSON object has been accumulated.
    bool accumulate(const std::string& chunk);

    // Extract name and full payload from the accumulated JSON.
    // Returns false if parsing fails or too short.
    bool extract_tool_call(std::string& name, std::string& payload) const;

    // Whether we are currently collecting JSON.
    bool is_collecting() const { return collecting; }

    // Reset helpers.
    void reset() { collecting = false; brace_depth = 0; buf.clear(); }

private:
    std::string buf;
    int brace_depth = 0;
    bool collecting = false;
};