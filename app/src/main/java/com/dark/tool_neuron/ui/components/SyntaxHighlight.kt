package com.dark.tool_neuron.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

enum class CodeTokenType {
    Keyword, String, Comment, Number, Function, Type,
    Operator, Annotation, Punctuation, Property, Default
}

interface SyntaxHighlightTheme {
    val name: String
    val background: Color
    val lineNumberColor: Color
    val defaultColor: Color

    fun colorFor(token: CodeTokenType): Color
    fun styleFor(token: CodeTokenType): SpanStyle = when (token) {
        CodeTokenType.Keyword -> SpanStyle(color = colorFor(token), fontWeight = FontWeight.SemiBold)
        CodeTokenType.Comment -> SpanStyle(color = colorFor(token), fontStyle = FontStyle.Italic)
        CodeTokenType.Annotation -> SpanStyle(color = colorFor(token), fontStyle = FontStyle.Italic)
        else -> SpanStyle(color = colorFor(token))
    }

    val charLigatures: Map<String, String> get() = defaultLigatures
}

private val defaultLigatures = mapOf(
    "!=" to "\u2260", ">=" to "\u2265", "<=" to "\u2264",
    "->" to "\u2192", "=>" to "\u21D2", "::" to "\u2237",
    "&&" to "\u2227", "||" to "\u2228", "..." to "\u2026"
)

object OneDarkTheme : SyntaxHighlightTheme {
    override val name = "One Dark"
    override val background = Color(0xFF282C34)
    override val lineNumberColor = Color(0xFF636D83)
    override val defaultColor = Color(0xFFABB2BF)

    override fun colorFor(token: CodeTokenType): Color = when (token) {
        CodeTokenType.Keyword -> Color(0xFFC678DD)
        CodeTokenType.String -> Color(0xFF98C379)
        CodeTokenType.Comment -> Color(0xFF5C6370)
        CodeTokenType.Number -> Color(0xFFD19A66)
        CodeTokenType.Function -> Color(0xFF61AFEF)
        CodeTokenType.Type -> Color(0xFFE5C07B)
        CodeTokenType.Operator -> Color(0xFF56B6C2)
        CodeTokenType.Annotation -> Color(0xFFE06C75)
        CodeTokenType.Punctuation -> Color(0xFF7F848E)
        CodeTokenType.Property -> Color(0xFFE06C75)
        CodeTokenType.Default -> defaultColor
    }
}

object OneLightTheme : SyntaxHighlightTheme {
    override val name = "One Light"
    override val background = Color(0xFFFAFAFA)
    override val lineNumberColor = Color(0xFF9D9D9F)
    override val defaultColor = Color(0xFF383A42)

    override fun colorFor(token: CodeTokenType): Color = when (token) {
        CodeTokenType.Keyword -> Color(0xFFA626A4)
        CodeTokenType.String -> Color(0xFF50A14F)
        CodeTokenType.Comment -> Color(0xFFA0A1A7)
        CodeTokenType.Number -> Color(0xFF986801)
        CodeTokenType.Function -> Color(0xFF4078F2)
        CodeTokenType.Type -> Color(0xFFC18401)
        CodeTokenType.Operator -> Color(0xFF0184BC)
        CodeTokenType.Annotation -> Color(0xFFE45649)
        CodeTokenType.Punctuation -> Color(0xFF696C77)
        CodeTokenType.Property -> Color(0xFFE45649)
        CodeTokenType.Default -> defaultColor
    }
}

val LocalSyntaxTheme = staticCompositionLocalOf<SyntaxHighlightTheme?> { null }

@Composable
fun resolveSyntaxTheme(): SyntaxHighlightTheme {
    LocalSyntaxTheme.current?.let { return it }
    return if (isSystemInDarkTheme()) OneDarkTheme else OneLightTheme
}

// ── Language definitions ──

private data class LanguageRules(
    val keywords: Set<String>,
    val typeKeywords: Set<String> = emptySet(),
    val lineComment: String? = "//",
    val blockCommentStart: String? = "/*",
    val blockCommentEnd: String? = "*/",
    val hashComment: Boolean = false,
    val annotationPrefix: Char? = '@'
)

private val LANG_RULES: Map<String, LanguageRules> by lazy {
    mapOf(
        "kotlin" to LanguageRules(
            keywords = setOf(
                "fun", "val", "var", "class", "object", "interface", "enum", "sealed",
                "data", "if", "else", "when", "for", "while", "do", "return", "break",
                "continue", "throw", "try", "catch", "finally", "import", "package",
                "is", "as", "in", "out", "by", "init", "constructor", "companion",
                "abstract", "override", "open", "private", "protected", "public",
                "internal", "suspend", "inline", "crossinline", "noinline", "reified",
                "typealias", "annotation", "lateinit", "lazy", "const", "null",
                "true", "false", "this", "super", "it", "where"
            ),
            typeKeywords = setOf(
                "Int", "Long", "Float", "Double", "Boolean", "String", "Char", "Byte",
                "Short", "Unit", "Nothing", "Any", "List", "Map", "Set", "Array",
                "MutableList", "MutableMap", "MutableSet", "Pair", "Triple"
            )
        ),
        "java" to LanguageRules(
            keywords = setOf(
                "public", "private", "protected", "static", "final", "abstract",
                "class", "interface", "enum", "extends", "implements", "new",
                "if", "else", "for", "while", "do", "switch", "case", "default",
                "break", "continue", "return", "throw", "try", "catch", "finally",
                "import", "package", "void", "null", "true", "false", "this",
                "super", "synchronized", "volatile", "transient", "native",
                "instanceof", "throws", "assert", "var", "record", "sealed",
                "permits", "yield"
            ),
            typeKeywords = setOf(
                "int", "long", "float", "double", "boolean", "char", "byte",
                "short", "String", "Integer", "Long", "Float", "Double",
                "Boolean", "Object", "List", "Map", "Set", "Array"
            )
        ),
        "python" to LanguageRules(
            keywords = setOf(
                "def", "class", "if", "elif", "else", "for", "while", "break",
                "continue", "return", "yield", "import", "from", "as", "with",
                "try", "except", "finally", "raise", "pass", "lambda", "and",
                "or", "not", "is", "in", "True", "False", "None", "global",
                "nonlocal", "del", "assert", "async", "await", "match", "case"
            ),
            typeKeywords = setOf(
                "int", "float", "str", "bool", "list", "dict", "set", "tuple",
                "bytes", "type", "object", "None"
            ),
            lineComment = "#", blockCommentStart = null, blockCommentEnd = null,
            hashComment = true
        ),
        "javascript" to LanguageRules(
            keywords = setOf(
                "function", "var", "let", "const", "if", "else", "for", "while",
                "do", "switch", "case", "default", "break", "continue", "return",
                "throw", "try", "catch", "finally", "new", "delete", "typeof",
                "instanceof", "in", "of", "class", "extends", "super", "this",
                "import", "export", "from", "as", "async", "await",
                "yield", "true", "false", "null", "undefined", "void"
            ),
            typeKeywords = setOf(
                "Array", "Object", "String", "Number", "Boolean", "Symbol",
                "Promise", "Map", "Set", "WeakMap", "WeakSet", "BigInt"
            )
        ),
        "typescript" to LanguageRules(
            keywords = setOf(
                "function", "var", "let", "const", "if", "else", "for", "while",
                "do", "switch", "case", "default", "break", "continue", "return",
                "throw", "try", "catch", "finally", "new", "delete", "typeof",
                "instanceof", "in", "of", "class", "extends", "super", "this",
                "import", "export", "from", "as", "async", "await",
                "yield", "true", "false", "null", "undefined", "void",
                "type", "interface", "enum", "namespace", "module", "declare",
                "abstract", "implements", "readonly", "keyof", "infer",
                "satisfies", "override"
            ),
            typeKeywords = setOf(
                "string", "number", "boolean", "any", "unknown", "never", "void",
                "Array", "Object", "Promise", "Record", "Partial", "Required",
                "Readonly", "Pick", "Omit", "Exclude", "Extract", "Map", "Set"
            )
        ),
        "rust" to LanguageRules(
            keywords = setOf(
                "fn", "let", "mut", "const", "static", "struct", "enum", "impl",
                "trait", "type", "pub", "use", "mod", "crate", "self", "super",
                "if", "else", "match", "for", "while", "loop", "break", "continue",
                "return", "as", "in", "ref", "move", "unsafe", "async", "await",
                "dyn", "where", "true", "false"
            ),
            typeKeywords = setOf(
                "i8", "i16", "i32", "i64", "i128", "isize",
                "u8", "u16", "u32", "u64", "u128", "usize",
                "f32", "f64", "bool", "char", "str",
                "String", "Vec", "Box", "Rc", "Arc", "Option", "Result",
                "Self", "HashMap", "HashSet"
            )
        ),
        "go" to LanguageRules(
            keywords = setOf(
                "func", "var", "const", "type", "struct", "interface", "map",
                "chan", "if", "else", "for", "range", "switch", "case", "default",
                "break", "continue", "return", "go", "select", "defer", "package",
                "import", "fallthrough", "goto", "true", "false", "nil"
            ),
            typeKeywords = setOf(
                "int", "int8", "int16", "int32", "int64",
                "uint", "uint8", "uint16", "uint32", "uint64",
                "float32", "float64", "complex64", "complex128",
                "bool", "string", "byte", "rune", "error", "any"
            )
        ),
        "c" to LanguageRules(
            keywords = setOf(
                "auto", "break", "case", "char", "const", "continue", "default",
                "do", "double", "else", "enum", "extern", "float", "for", "goto",
                "if", "int", "long", "register", "return", "short", "signed",
                "sizeof", "static", "struct", "switch", "typedef", "union",
                "unsigned", "void", "volatile", "while", "NULL", "true", "false",
                "inline", "restrict", "_Bool", "_Complex", "_Imaginary"
            ),
            typeKeywords = setOf(
                "int", "char", "float", "double", "long", "short", "void",
                "unsigned", "signed", "size_t", "int8_t", "int16_t", "int32_t",
                "int64_t", "uint8_t", "uint16_t", "uint32_t", "uint64_t",
                "bool", "FILE"
            )
        ),
        "cpp" to LanguageRules(
            keywords = setOf(
                "auto", "break", "case", "class", "const", "constexpr", "continue",
                "default", "delete", "do", "else", "enum", "explicit", "extern",
                "for", "friend", "goto", "if", "inline", "namespace", "new",
                "noexcept", "operator", "private", "protected", "public", "return",
                "sizeof", "static", "struct", "switch", "template", "this",
                "throw", "try", "catch", "typedef", "typename", "union", "using",
                "virtual", "void", "volatile", "while", "override", "final",
                "nullptr", "true", "false", "static_cast", "dynamic_cast",
                "const_cast", "reinterpret_cast", "concept", "requires",
                "co_await", "co_return", "co_yield", "module", "import", "export"
            ),
            typeKeywords = setOf(
                "int", "char", "float", "double", "long", "short", "void",
                "unsigned", "signed", "bool", "string", "wchar_t", "char8_t",
                "char16_t", "char32_t", "size_t", "int8_t", "int16_t",
                "int32_t", "int64_t", "uint8_t", "uint16_t", "uint32_t",
                "uint64_t", "vector", "map", "set", "unordered_map",
                "unique_ptr", "shared_ptr", "optional", "variant", "any",
                "span", "string_view"
            )
        ),
        "swift" to LanguageRules(
            keywords = setOf(
                "func", "var", "let", "class", "struct", "enum", "protocol",
                "extension", "if", "else", "guard", "switch", "case", "default",
                "for", "while", "repeat", "break", "continue", "return", "throw",
                "try", "catch", "import", "as", "is", "in", "self", "super",
                "init", "deinit", "nil", "true", "false", "static", "override",
                "mutating", "typealias", "associatedtype", "where", "async", "await",
                "actor", "some", "any", "weak", "unowned", "lazy", "private",
                "public", "internal", "open", "fileprivate"
            ),
            typeKeywords = setOf(
                "Int", "Float", "Double", "Bool", "String", "Character",
                "Array", "Dictionary", "Set", "Optional", "Result", "Void",
                "Any", "AnyObject", "Error", "Codable", "Hashable", "Equatable"
            )
        ),
        "bash" to LanguageRules(
            keywords = setOf(
                "if", "then", "else", "elif", "fi", "for", "while", "do", "done",
                "case", "esac", "in", "function", "return", "local", "export",
                "source", "echo", "exit", "read", "set", "unset", "shift",
                "true", "false", "cd", "ls", "rm", "cp", "mv", "mkdir", "cat",
                "grep", "sed", "awk", "find", "chmod", "chown", "sudo",
                "apt", "pip", "npm", "git", "docker", "curl", "wget"
            ),
            lineComment = "#", blockCommentStart = null, blockCommentEnd = null,
            hashComment = true, annotationPrefix = null
        ),
        "json" to LanguageRules(
            keywords = setOf("true", "false", "null"),
            lineComment = null, blockCommentStart = null, blockCommentEnd = null,
            annotationPrefix = null
        ),
        "xml" to LanguageRules(
            keywords = emptySet(),
            lineComment = null, blockCommentStart = "<!--", blockCommentEnd = "-->",
            annotationPrefix = null
        ),
        "html" to LanguageRules(
            keywords = emptySet(),
            lineComment = null, blockCommentStart = "<!--", blockCommentEnd = "-->",
            annotationPrefix = null
        ),
        "css" to LanguageRules(
            keywords = setOf(
                "import", "media", "keyframes", "font-face", "charset",
                "supports", "namespace", "page"
            ),
            annotationPrefix = null
        ),
        "sql" to LanguageRules(
            keywords = setOf(
                "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "INSERT", "INTO",
                "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "ALTER",
                "DROP", "INDEX", "VIEW", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER",
                "ON", "AS", "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET",
                "UNION", "ALL", "DISTINCT", "NULL", "IS", "IN", "LIKE", "BETWEEN",
                "EXISTS", "CASE", "WHEN", "THEN", "ELSE", "END", "BEGIN",
                "COMMIT", "ROLLBACK", "TRANSACTION", "PRIMARY", "KEY", "FOREIGN",
                "REFERENCES", "CONSTRAINT", "DEFAULT", "CHECK", "UNIQUE",
                "select", "from", "where", "and", "or", "not", "insert", "into",
                "values", "update", "set", "delete", "create", "table", "alter",
                "drop", "join", "on", "as", "order", "by", "group", "having",
                "limit", "null", "is", "in", "like", "between", "exists",
                "case", "when", "then", "else", "end"
            ),
            lineComment = "--", annotationPrefix = null
        ),
        "yaml" to LanguageRules(
            keywords = setOf("true", "false", "null", "yes", "no", "on", "off"),
            lineComment = "#", blockCommentStart = null, blockCommentEnd = null,
            hashComment = true, annotationPrefix = null
        )
    )
}

private val LANG_ALIASES: Map<String, String> by lazy {
    mapOf(
        "kt" to "kotlin", "kts" to "kotlin",
        "js" to "javascript", "jsx" to "javascript", "mjs" to "javascript",
        "ts" to "typescript", "tsx" to "typescript",
        "py" to "python", "python3" to "python",
        "rs" to "rust",
        "sh" to "bash", "zsh" to "bash", "shell" to "bash",
        "c++" to "cpp", "cxx" to "cpp", "cc" to "cpp", "h" to "cpp", "hpp" to "cpp",
        "yml" to "yaml", "htm" to "html",
        "objective-c" to "c", "objc" to "c",
        "jsonc" to "json"
    )
}

// ── Tokenizer ──

private data class CodeToken(val text: String, val type: CodeTokenType)

private fun tokenize(code: String, language: String): List<CodeToken> {
    val lang = language.lowercase().let { LANG_ALIASES[it] ?: it }
    val rules = LANG_RULES[lang] ?: return listOf(CodeToken(code, CodeTokenType.Default))
    val tokens = mutableListOf<CodeToken>()
    var pos = 0

    while (pos < code.length) {
        if (rules.blockCommentStart != null && code.startsWith(rules.blockCommentStart, pos)) {
            val blockEnd = rules.blockCommentEnd ?: ""
            val end = code.indexOf(blockEnd, pos + rules.blockCommentStart.length)
            val commentEnd = if (end >= 0) end + blockEnd.length else code.length
            tokens.add(CodeToken(code.substring(pos, commentEnd), CodeTokenType.Comment))
            pos = commentEnd; continue
        }

        if (rules.lineComment != null && code.startsWith(rules.lineComment, pos)) {
            val end = code.indexOf('\n', pos)
            val commentEnd = if (end >= 0) end else code.length
            tokens.add(CodeToken(code.substring(pos, commentEnd), CodeTokenType.Comment))
            pos = commentEnd; continue
        }

        if (rules.hashComment && code[pos] == '#') {
            val end = code.indexOf('\n', pos)
            val commentEnd = if (end >= 0) end else code.length
            tokens.add(CodeToken(code.substring(pos, commentEnd), CodeTokenType.Comment))
            pos = commentEnd; continue
        }

        if (code[pos] == '"' || code[pos] == '\'' || code[pos] == '`') {
            val quote = code[pos]
            if (quote != '`' && pos + 2 < code.length && code[pos + 1] == quote && code[pos + 2] == quote) {
                val tripleQuote = "$quote$quote$quote"
                val end = code.indexOf(tripleQuote, pos + 3)
                val strEnd = if (end >= 0) end + 3 else code.length
                tokens.add(CodeToken(code.substring(pos, strEnd), CodeTokenType.String))
                pos = strEnd; continue
            }
            var j = pos + 1
            while (j < code.length) {
                if (code[j] == '\\') { j += 2; continue }
                if (code[j] == quote) { j++; break }
                if (quote != '`' && code[j] == '\n') break
                j++
            }
            tokens.add(CodeToken(code.substring(pos, j), CodeTokenType.String))
            pos = j; continue
        }

        if (rules.annotationPrefix != null && code[pos] == rules.annotationPrefix) {
            var j = pos + 1
            while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '.')) j++
            if (j > pos + 1) {
                tokens.add(CodeToken(code.substring(pos, j), CodeTokenType.Annotation))
                pos = j; continue
            }
        }

        if (code[pos].isDigit() || (code[pos] == '.' && pos + 1 < code.length && code[pos + 1].isDigit())) {
            var j = pos
            if (code[pos] == '0' && j + 1 < code.length && (code[j + 1] == 'x' || code[j + 1] == 'X')) {
                j += 2
                while (j < code.length && (code[j].isDigit() || code[j] in 'a'..'f' || code[j] in 'A'..'F' || code[j] == '_')) j++
            } else {
                while (j < code.length && (code[j].isDigit() || code[j] == '.' || code[j] == '_')) j++
                if (j < code.length && (code[j] == 'e' || code[j] == 'E')) {
                    j++
                    if (j < code.length && (code[j] == '+' || code[j] == '-')) j++
                    while (j < code.length && code[j].isDigit()) j++
                }
            }
            if (j < code.length && code[j] in "fFdDlLuU") j++
            tokens.add(CodeToken(code.substring(pos, j), CodeTokenType.Number))
            pos = j; continue
        }

        if (code[pos].isLetter() || code[pos] == '_') {
            var j = pos
            while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
            val word = code.substring(pos, j)
            val type = when {
                word in rules.keywords -> CodeTokenType.Keyword
                word in rules.typeKeywords -> CodeTokenType.Type
                word[0].isUpperCase() && rules.typeKeywords.isNotEmpty() -> CodeTokenType.Type
                j < code.length && code[j] == '(' -> CodeTokenType.Function
                else -> CodeTokenType.Default
            }
            tokens.add(CodeToken(word, type))
            pos = j; continue
        }

        if ((lang == "xml" || lang == "html") && code[pos] == '<') {
            var j = pos + 1
            if (j < code.length && code[j] == '/') j++
            while (j < code.length && code[j] != '>' && code[j] != ' ' && code[j] != '\n') j++
            if (j > pos + 1) {
                tokens.add(CodeToken(code.substring(pos, j), CodeTokenType.Keyword))
                pos = j; continue
            }
        }

        if (code[pos] in "+-*/%=<>!&|^~?:") {
            var j = pos + 1
            while (j < code.length && code[j] in "+-*/%=<>!&|^~?:." && j - pos < 3) j++
            tokens.add(CodeToken(code.substring(pos, j), CodeTokenType.Operator))
            pos = j; continue
        }

        if (code[pos] in "{}[]();,.$\\") {
            tokens.add(CodeToken(code[pos].toString(), CodeTokenType.Punctuation))
            pos++; continue
        }

        if (code[pos].isWhitespace()) {
            var j = pos
            while (j < code.length && code[j].isWhitespace()) j++
            tokens.add(CodeToken(code.substring(pos, j), CodeTokenType.Default))
            pos = j; continue
        }

        tokens.add(CodeToken(code[pos].toString(), CodeTokenType.Default))
        pos++
    }
    return tokens
}

// ── Public API ──

fun highlightCode(
    code: String,
    language: String,
    theme: SyntaxHighlightTheme,
    applyLigatures: Boolean = true
): AnnotatedString {
    val tokens = tokenize(code, language)
    return buildAnnotatedString {
        for (token in tokens) {
            val displayText = if (applyLigatures && theme.charLigatures.isNotEmpty()) {
                applyCharLigatures(token.text, theme.charLigatures)
            } else {
                token.text
            }
            withStyle(theme.styleFor(token.type)) { append(displayText) }
        }
    }
}

private fun applyCharLigatures(text: String, ligatures: Map<String, String>): String {
    var result = text
    for ((from, to) in ligatures.entries.sortedByDescending { it.key.length }) {
        result = result.replace(from, to)
    }
    return result
}
