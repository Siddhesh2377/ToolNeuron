/*=============================================================
 *   utils/chat_template.h
 *=============================================================
 *
 *  Routines that handle chat‑template formatting, JSON escaping,
 *  tool‑preamble generation, and GBNF grammar for tool calls.
 *
 *  The implementation is massively simplified: it either uses
 *  the default template from `llama_model_chat_template()` or a
 *  user‑supplied Jinja‑2 string.
 *  All string manipulation is pure C++; no external JSON parser.
 *============================================================*/

#pragma once

#include <string>
#include <vector>

namespace chat {
    // Escape a string for inclusion in JSON literals
    std::string json_escape(const std::string& s);

    // Return the full prompt to be tokenised
    std::string apply_template(const struct llama_model* model,
                               const std::string& system_prompt,
                               const std::string& user_message,
                               const std::string& custom_template = "",
                               bool add_assistant = true);

    // Helper that prepends tool‑calling instructions to the system prompt
    std::string build_tool_preamble(const std::string& tools_json);

    // Extract a vector of tool names from a JSON‑style tool array
    std::vector<std::string> extract_tool_names(const std::string& tools_json);

    // Generates GBNF grammar string for the minimum tool‑call JSON pattern
    std::string build_tool_grammar(const std::string& tools_json);
}