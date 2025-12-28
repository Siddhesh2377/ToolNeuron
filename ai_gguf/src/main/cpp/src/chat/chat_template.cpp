#include "chat_template.h"
#include "llama.h"

#include <iomanip>
#include <sstream>
#include <algorithm>

namespace chat {

/* --------------------------------------------------------------------
 *  JSON escape helpers
 * -------------------------------------------------------------------- */
    std::string json_escape(const std::string &s) {
        std::ostringstream oss;
        for (auto c: s) {
            switch (c) {
                case '\\':
                    oss << "\\\\";
                    break;
                case '\"':
                    oss << "\\\"";
                    break;
                case '\n':
                    oss << "\\n";
                    break;
                case '\r':
                    oss << "\\r";
                    break;
                case '\t':
                    oss << "\\t";
                    break;
                default:
                    if (static_cast<unsigned char>(c) < 0x20) {
                        oss << "\\u" << std::hex << std::setw(4) << std::setfill('0')
                            << static_cast<int>(c);
                    } else {
                        oss << c;
                    }
            }
        }
        return oss.str();
    }

/* --------------------------------------------------------------------
 *  Build tool preamble
 * -------------------------------------------------------------------- */
    std::string build_tool_preamble(const std::string &tools_json) {
        std::ostringstream preamble;
        preamble << "You may call tools by emitting ONLY the JSON object:\n"
                 << "{\"tool_calls\":[{\"name\":\"NAME\",\"arguments\":{...}}]}\n"
                 << "Available tools (OpenAI schema):\n" << tools_json << "\n";
        return preamble.str();
    }

/* --------------------------------------------------------------------
 *  𝙱𝙸𝙰𝙶 𝚂𝚗𝚎𝚝𝚎𝚍 𝚐𝚓𝚙-𝚕𝚒𝚗𝚐 𝙱𝚢𝚝𝚞𝚝𝚑
 * -------------------------------------------------------------------- */
    std::string build_tool_grammar(const std::string &tools_json) {
        auto names = extract_tool_names(tools_json);
        std::ostringstream g;

        g << R"(root         ::= json
                json         ::= ws toolcall ws
                toolcall     ::= "{" ws "\"tool_calls\"" ws ":" ws "[" ws call ws "]" ws "}"
                call         ::= "{" ws "\"name\"" ws ":" ws toolname ws "," ws "\"arguments\"" ws ":" ws object ws "}"
                )";

        // Tool names
        g << "toolname     ::= ";
        if (!names.empty()) {
            for (size_t i = 0; i < names.size(); ++i) {
                if (i) g << " | ";
                g << R"("\")" << names[i] << R"(\"")";
            }
        } else {
            g << R"("\"unknown\"")";
        }
        g << "\n";

        // Fixed grammar rules
        g << R"(
object       ::= "{" ws "}" | "{" ws member (ws "," ws member)* ws "}"
member       ::= string ws ":" ws value
array        ::= "[" ws "]" | "[" ws value (ws "," ws value)* ws "]"
value        ::= string | number | object | array | "true" | "false" | "null"
string       ::= "\"" ([^"\\\n] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F]))* "\""
number       ::= "-"? ("0" | [1-9] [0-9]*) ("." [0-9]+)? ([eE] [+-]? [0-9]+)?
ws           ::= [ \t\n\r]*
)";

        return g.str();
    }

/* --------------------------------------------------------------------
 *  Extract tool names from JSON
 * -------------------------------------------------------------------- */
    std::vector<std::string> extract_tool_names(const std::string &tools_json) {
        std::vector<std::string> out;
        size_t pos = 0;
        while (true) {
            size_t k = tools_json.find("\"name\"", pos);
            if (k == std::string::npos) break;
            size_t colon = tools_json.find(':', k);
            if (colon == std::string::npos) break;
            size_t q1 = tools_json.find('"', colon + 1);
            if (q1 == std::string::npos) break;
            size_t q2 = tools_json.find('"', q1 + 1);
            if (q2 == std::string::npos) break;
            out.emplace_back(tools_json.substr(q1 + 1, q2 - q1 - 1));
            pos = q2 + 1;
        }
        return out;
    }

/* --------------------------------------------------------------------
 *  Apply chat template (LLaMA’s built‑in style)
 * -------------------------------------------------------------------- */
    std::string apply_template(const ::llama_model *model, const std::string &system_prompt,
                               const std::string &user_message, const std::string &custom_template,
                               bool add_assistant) {

        /* Prefer the user‑supplied template, otherwise fall back to the model’s
           default chat template. */
        const char *tmpl = custom_template.empty() ? ::llama_model_chat_template(model, nullptr)
                                                   : custom_template.c_str();

        if (!tmpl || !*tmpl) {
            // Fallback – plain textual prompt
            std::string out;
            if (!system_prompt.empty())
                out += "System: " + system_prompt + "\n";
            out += "User: " + user_message + "\n";
            if (add_assistant) out += "Assistant: ";
            return out;
        }

        // Build the minimal list of messages
        std::vector<llama_chat_message> msgs;
        if (!system_prompt.empty())
            msgs.emplace_back(llama_chat_message{"system", system_prompt.c_str()});
        msgs.emplace_back(llama_chat_message{"user", user_message.c_str()});

        // Compute required buffer size
        int32_t need = ::llama_chat_apply_template(tmpl, msgs.data(),
                                                   static_cast<int32_t>(msgs.size()), add_assistant,
                                                   nullptr, 0);
        if (need < 0) need = -need;

        std::string out(static_cast<size_t>(need), '\0');
        int32_t written = ::llama_chat_apply_template(tmpl, msgs.data(),
                                                      static_cast<int32_t>(msgs.size()),
                                                      add_assistant, out.data(), need);
        if (written < 0) written = -written;
        out.resize(static_cast<size_t>(written));
        return out;
    }

} // namespace chat