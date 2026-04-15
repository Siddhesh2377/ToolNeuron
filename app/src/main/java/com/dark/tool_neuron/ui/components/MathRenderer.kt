package com.dark.tool_neuron.ui.components

/**
 * LaTeX/Typst math expression → Unicode rendering engine.
 * 600+ symbol mappings, font commands, accents, environments.
 * All functions are pure — no Compose dependencies.
 */

private val mathSymbols = mapOf(
    // Greek lowercase
    "\\alpha" to "\u03B1", "\\beta" to "\u03B2", "\\gamma" to "\u03B3", "\\delta" to "\u03B4",
    "\\epsilon" to "\u03B5", "\\varepsilon" to "\u03B5", "\\zeta" to "\u03B6", "\\eta" to "\u03B7",
    "\\theta" to "\u03B8", "\\vartheta" to "\u03D1", "\\iota" to "\u03B9", "\\kappa" to "\u03BA",
    "\\lambda" to "\u03BB", "\\mu" to "\u03BC", "\\nu" to "\u03BD", "\\xi" to "\u03BE",
    "\\omicron" to "\u03BF", "\\pi" to "\u03C0", "\\varpi" to "\u03D6", "\\rho" to "\u03C1",
    "\\varrho" to "\u03F1", "\\sigma" to "\u03C3", "\\varsigma" to "\u03C2", "\\tau" to "\u03C4",
    "\\upsilon" to "\u03C5", "\\phi" to "\u03C6", "\\varphi" to "\u03D5", "\\chi" to "\u03C7",
    "\\psi" to "\u03C8", "\\omega" to "\u03C9",
    // Greek uppercase
    "\\Alpha" to "\u0391", "\\Beta" to "\u0392", "\\Gamma" to "\u0393", "\\Delta" to "\u0394",
    "\\Epsilon" to "\u0395", "\\Zeta" to "\u0396", "\\Eta" to "\u0397", "\\Theta" to "\u0398",
    "\\Iota" to "\u0399", "\\Kappa" to "\u039A", "\\Lambda" to "\u039B", "\\Mu" to "\u039C",
    "\\Nu" to "\u039D", "\\Xi" to "\u039E", "\\Omicron" to "\u039F", "\\Pi" to "\u03A0",
    "\\Rho" to "\u03A1", "\\Sigma" to "\u03A3", "\\Tau" to "\u03A4", "\\Upsilon" to "\u03A5",
    "\\Phi" to "\u03A6", "\\Chi" to "\u03A7", "\\Psi" to "\u03A8", "\\Omega" to "\u03A9",
    // Binary operators
    "\\pm" to "\u00B1", "\\mp" to "\u2213", "\\times" to "\u00D7", "\\div" to "\u00F7",
    "\\cdot" to "\u00B7", "\\ast" to "\u2217", "\\star" to "\u22C6", "\\circ" to "\u2218",
    "\\bullet" to "\u2022", "\\oplus" to "\u2295", "\\ominus" to "\u2296", "\\otimes" to "\u2297",
    "\\oslash" to "\u2298", "\\odot" to "\u2299", "\\dagger" to "\u2020", "\\ddagger" to "\u2021",
    "\\sqcap" to "\u2293", "\\sqcup" to "\u2294", "\\barwedge" to "\u22BC", "\\veebar" to "\u22BB",
    "\\triangleleft" to "\u25C1", "\\triangleright" to "\u25B7", "\\bigtriangleup" to "\u25B3",
    "\\bigtriangledown" to "\u25BD", "\\wr" to "\u2240", "\\ltimes" to "\u22C9",
    "\\rtimes" to "\u22CA", "\\uplus" to "\u228E", "\\amalg" to "\u2A3F",
    "\\curlywedge" to "\u22CF", "\\curlyvee" to "\u22CE", "\\intercal" to "\u22BA",
    "\\dotplus" to "\u2214", "\\divideontimes" to "\u22C7", "\\smallsetminus" to "\u2216",
    "\\boxplus" to "\u229E", "\\boxminus" to "\u229F", "\\boxtimes" to "\u22A0", "\\boxdot" to "\u22A1",
    "\\circleddash" to "\u229D", "\\circledast" to "\u229B", "\\circledcirc" to "\u229A",
    "\\centerdot" to "\u22C5",
    // Relations
    "\\leq" to "\u2264", "\\le" to "\u2264", "\\geq" to "\u2265", "\\ge" to "\u2265",
    "\\neq" to "\u2260", "\\ne" to "\u2260", "\\equiv" to "\u2261", "\\approx" to "\u2248",
    "\\sim" to "\u223C", "\\simeq" to "\u2243", "\\cong" to "\u2245", "\\propto" to "\u221D",
    "\\ll" to "\u226A", "\\gg" to "\u226B", "\\subset" to "\u2282", "\\supset" to "\u2283",
    "\\subseteq" to "\u2286", "\\supseteq" to "\u2287", "\\in" to "\u2208", "\\notin" to "\u2209",
    "\\ni" to "\u220B", "\\perp" to "\u22A5", "\\parallel" to "\u2225",
    "\\prec" to "\u227A", "\\succ" to "\u227B", "\\preceq" to "\u2AAF", "\\succeq" to "\u2AB0",
    "\\sqsubset" to "\u228F", "\\sqsupset" to "\u2290", "\\sqsubseteq" to "\u2291",
    "\\sqsupseteq" to "\u2292", "\\doteq" to "\u2250", "\\asymp" to "\u224D",
    "\\bowtie" to "\u22C8", "\\models" to "\u22A8", "\\vdash" to "\u22A2", "\\dashv" to "\u22A3",
    "\\smile" to "\u2323", "\\frown" to "\u2322", "\\mid" to "\u2223",
    "\\nsubset" to "\u2284", "\\nsupset" to "\u2285", "\\nprec" to "\u2280", "\\nsucc" to "\u2281",
    "\\triangleq" to "\u225C", "\\bumpeq" to "\u224F", "\\Bumpeq" to "\u224E",
    "\\leqq" to "\u2266", "\\geqq" to "\u2267", "\\leqslant" to "\u2A7D", "\\geqslant" to "\u2A7E",
    "\\lesssim" to "\u2272", "\\gtrsim" to "\u2273", "\\lessgtr" to "\u2276", "\\gtrless" to "\u2277",
    "\\preccurlyeq" to "\u227C", "\\succcurlyeq" to "\u227D",
    "\\vDash" to "\u22A8", "\\Vdash" to "\u22A9", "\\Vvdash" to "\u22AA",
    // Negated relations
    "\\nless" to "\u226E", "\\ngtr" to "\u226F", "\\nleq" to "\u2270", "\\ngeq" to "\u2271",
    "\\nsubseteq" to "\u2288", "\\nsupseteq" to "\u2289", "\\nmid" to "\u2224",
    "\\nparallel" to "\u2226", "\\nsim" to "\u2241", "\\ncong" to "\u2247",
    "\\nvdash" to "\u22AC", "\\nvDash" to "\u22AD", "\\nVdash" to "\u22AE", "\\nVDash" to "\u22AF",
    "\\ntriangleleft" to "\u22EA", "\\ntriangleright" to "\u22EB",
    "\\ntrianglelefteq" to "\u22EC", "\\ntrianglerighteq" to "\u22ED",
    // Arrows
    "\\leftarrow" to "\u2190", "\\rightarrow" to "\u2192", "\\uparrow" to "\u2191",
    "\\downarrow" to "\u2193", "\\leftrightarrow" to "\u2194",
    "\\Leftarrow" to "\u21D0", "\\Rightarrow" to "\u21D2", "\\Leftrightarrow" to "\u21D4",
    "\\mapsto" to "\u21A6", "\\to" to "\u2192",
    "\\longrightarrow" to "\u27F6", "\\longleftarrow" to "\u27F5",
    "\\Uparrow" to "\u21D1", "\\Downarrow" to "\u21D3", "\\Updownarrow" to "\u21D5",
    "\\updownarrow" to "\u2195", "\\nearrow" to "\u2197", "\\searrow" to "\u2198",
    "\\swarrow" to "\u2199", "\\nwarrow" to "\u2196",
    "\\hookleftarrow" to "\u21A9", "\\hookrightarrow" to "\u21AA",
    "\\leftharpoonup" to "\u21BC", "\\leftharpoondown" to "\u21BD",
    "\\rightharpoonup" to "\u21C0", "\\rightharpoondown" to "\u21C1",
    "\\rightleftharpoons" to "\u21CC", "\\leftrightharpoons" to "\u21CB",
    "\\implies" to "\u27F9", "\\impliedby" to "\u27F8", "\\iff" to "\u27FA",
    "\\longleftrightarrow" to "\u27F7", "\\longmapsto" to "\u27FC", "\\leadsto" to "\u21DD",
    "\\circlearrowleft" to "\u21BA", "\\circlearrowright" to "\u21BB",
    "\\curvearrowleft" to "\u21B6", "\\curvearrowright" to "\u21B7",
    "\\leftleftarrows" to "\u21C7", "\\rightrightarrows" to "\u21C9",
    "\\leftrightarrows" to "\u21C6", "\\rightleftarrows" to "\u21C4",
    "\\twoheadleftarrow" to "\u219E", "\\twoheadrightarrow" to "\u21A0",
    "\\nleftarrow" to "\u219A", "\\nrightarrow" to "\u219B",
    "\\nLeftarrow" to "\u21CD", "\\nRightarrow" to "\u21CF",
    // Big operators
    "\\sum" to "\u2211", "\\prod" to "\u220F", "\\coprod" to "\u2210",
    "\\int" to "\u222B", "\\oint" to "\u222E", "\\iint" to "\u222C", "\\iiint" to "\u222D",
    "\\iiiint" to "\u2A0C", "\\oiint" to "\u222F", "\\oiiint" to "\u2230",
    "\\bigcup" to "\u22C3", "\\bigcap" to "\u22C2", "\\bigvee" to "\u22C1", "\\bigwedge" to "\u22C0",
    // Logic & Set Theory
    "\\top" to "\u22A4", "\\bot" to "\u22A5", "\\therefore" to "\u2234", "\\because" to "\u2235",
    "\\forall" to "\u2200", "\\exists" to "\u2203", "\\nexists" to "\u2204",
    "\\neg" to "\u00AC", "\\lnot" to "\u00AC", "\\land" to "\u2227", "\\lor" to "\u2228",
    "\\emptyset" to "\u2205", "\\varnothing" to "\u2205",
    "\\cap" to "\u2229", "\\cup" to "\u222A", "\\setminus" to "\u2216",
    // Calculus & Analysis
    "\\infty" to "\u221E", "\\partial" to "\u2202", "\\nabla" to "\u2207",
    "\\Re" to "\u211C", "\\Im" to "\u2111", "\\aleph" to "\u2135",
    "\\beth" to "\u2136", "\\gimel" to "\u2137", "\\daleth" to "\u2138",
    "\\ell" to "\u2113", "\\wp" to "\u2118", "\\hbar" to "\u210F", "\\hslash" to "\u210F",
    // Geometry
    "\\angle" to "\u2220", "\\measuredangle" to "\u2221", "\\sphericalangle" to "\u2222",
    "\\triangle" to "\u25B3", "\\square" to "\u25A1", "\\diamond" to "\u25C7", "\\Box" to "\u25A1",
    "\\blacktriangle" to "\u25B4", "\\blacktriangledown" to "\u25BE",
    "\\blacksquare" to "\u25AA", "\\blacklozenge" to "\u29EB",
    "\\lozenge" to "\u25CA", "\\bigstar" to "\u2605",
    // Miscellaneous
    "\\clubsuit" to "\u2663", "\\diamondsuit" to "\u2662", "\\heartsuit" to "\u2661",
    "\\spadesuit" to "\u2660", "\\flat" to "\u266D", "\\natural" to "\u266E", "\\sharp" to "\u266F",
    "\\checkmark" to "\u2713", "\\copyright" to "\u00A9", "\\yen" to "\u00A5", "\\pounds" to "\u00A3",
    "\\sqrt" to "\u221A", "\\surd" to "\u221A", "\\prime" to "\u2032", "\\degree" to "\u00B0",
    "\\complement" to "\u2201", "\\mho" to "\u2127", "\\eth" to "\u00F0",
    // Dots
    "\\ldots" to "\u2026", "\\cdots" to "\u22EF", "\\vdots" to "\u22EE",
    "\\ddots" to "\u22F1", "\\iddots" to "\u22F0",
    // Brackets & Delimiters
    "\\langle" to "\u27E8", "\\rangle" to "\u27E9",
    "\\lceil" to "\u2308", "\\rceil" to "\u2309", "\\lfloor" to "\u230A", "\\rfloor" to "\u230B",
    "\\lbrace" to "{", "\\rbrace" to "}",
    "\\ulcorner" to "\u231C", "\\urcorner" to "\u231D", "\\llcorner" to "\u231E", "\\lrcorner" to "\u231F",
    "\\lvert" to "|", "\\rvert" to "|", "\\lVert" to "\u2016", "\\rVert" to "\u2016",
    "\\llbracket" to "\u27E6", "\\rrbracket" to "\u27E7",
    // Functions
    "\\sin" to "sin", "\\cos" to "cos", "\\tan" to "tan", "\\cot" to "cot",
    "\\sec" to "sec", "\\csc" to "csc", "\\arcsin" to "arcsin", "\\arccos" to "arccos",
    "\\arctan" to "arctan", "\\sinh" to "sinh", "\\cosh" to "cosh", "\\tanh" to "tanh",
    "\\log" to "log", "\\ln" to "ln", "\\exp" to "exp", "\\lim" to "lim",
    "\\max" to "max", "\\min" to "min", "\\sup" to "sup", "\\inf" to "inf",
    "\\det" to "det", "\\arg" to "arg", "\\deg" to "deg", "\\dim" to "dim",
    "\\gcd" to "gcd", "\\hom" to "hom", "\\ker" to "ker", "\\lg" to "lg",
    "\\Pr" to "Pr", "\\limsup" to "lim sup", "\\liminf" to "lim inf",
    // Typst
    "#alpha" to "\u03B1", "#beta" to "\u03B2", "#gamma" to "\u03B3", "#delta" to "\u03B4",
    "#epsilon" to "\u03B5", "#pi" to "\u03C0", "#sigma" to "\u03C3", "#omega" to "\u03C9",
    "#sum" to "\u2211", "#product" to "\u220F", "#integral" to "\u222B", "#infinity" to "\u221E",
    "#partial" to "\u2202", "#nabla" to "\u2207",
    "#arrow.r" to "\u2192", "#arrow.l" to "\u2190", "#arrow.t" to "\u2191", "#arrow.b" to "\u2193"
)

private val sortedMathSymbols: List<Pair<String, String>> by lazy {
    mathSymbols.entries.sortedByDescending { it.key.length }.map { it.key to it.value }
}

private val superscriptMap = mapOf(
    '0' to '\u2070', '1' to '\u00B9', '2' to '\u00B2', '3' to '\u00B3', '4' to '\u2074',
    '5' to '\u2075', '6' to '\u2076', '7' to '\u2077', '8' to '\u2078', '9' to '\u2079',
    '+' to '\u207A', '-' to '\u207B', '=' to '\u207C', '(' to '\u207D', ')' to '\u207E',
    'a' to '\u1D43', 'b' to '\u1D47', 'c' to '\u1D9C', 'd' to '\u1D48', 'e' to '\u1D49',
    'f' to '\u1DA0', 'g' to '\u1D4D', 'h' to '\u02B0', 'i' to '\u2071', 'j' to '\u02B2',
    'k' to '\u1D4F', 'l' to '\u02E1', 'm' to '\u1D50', 'n' to '\u207F', 'o' to '\u1D52',
    'p' to '\u1D56', 'r' to '\u02B3', 's' to '\u02E2', 't' to '\u1D57', 'u' to '\u1D58',
    'v' to '\u1D5B', 'w' to '\u02B7', 'x' to '\u02E3', 'y' to '\u02B8', 'z' to '\u1DBB',
    'A' to '\u1D2C', 'B' to '\u1D2E', 'D' to '\u1D30', 'E' to '\u1D31', 'G' to '\u1D33',
    'H' to '\u1D34', 'I' to '\u1D35', 'J' to '\u1D36', 'K' to '\u1D37', 'L' to '\u1D38',
    'M' to '\u1D39', 'N' to '\u1D3A', 'O' to '\u1D3C', 'P' to '\u1D3E', 'R' to '\u1D3F',
    'T' to '\u1D40', 'U' to '\u1D41', 'V' to '\u2C7D', 'W' to '\u1D42'
)

private val subscriptMap = mapOf(
    '0' to '\u2080', '1' to '\u2081', '2' to '\u2082', '3' to '\u2083', '4' to '\u2084',
    '5' to '\u2085', '6' to '\u2086', '7' to '\u2087', '8' to '\u2088', '9' to '\u2089',
    '+' to '\u208A', '-' to '\u208B', '=' to '\u208C', '(' to '\u208D', ')' to '\u208E',
    'a' to '\u2090', 'e' to '\u2091', 'h' to '\u2095', 'i' to '\u1D62', 'j' to '\u2C7C',
    'k' to '\u2096', 'l' to '\u2097', 'm' to '\u2098', 'n' to '\u2099', 'o' to '\u2092',
    'p' to '\u209A', 'r' to '\u1D63', 's' to '\u209B', 't' to '\u209C', 'u' to '\u1D64',
    'v' to '\u1D65', 'x' to '\u2093'
)

// ── Font command processors ──

private fun processBlackboardBold(input: String): String {
    val bb = mapOf(
        "A" to "\uD835\uDD38", "B" to "\uD835\uDD39", "C" to "\u2102", "D" to "\uD835\uDD3B",
        "E" to "\uD835\uDD3C", "F" to "\uD835\uDD3D", "G" to "\uD835\uDD3E", "H" to "\u210D",
        "I" to "\uD835\uDD40", "J" to "\uD835\uDD41", "K" to "\uD835\uDD42", "L" to "\uD835\uDD43",
        "M" to "\uD835\uDD44", "N" to "\u2115", "O" to "\uD835\uDD46", "P" to "\u2119",
        "Q" to "\u211A", "R" to "\u211D", "S" to "\uD835\uDD4A", "T" to "\uD835\uDD4B",
        "U" to "\uD835\uDD4C", "V" to "\uD835\uDD4D", "W" to "\uD835\uDD4E", "X" to "\uD835\uDD4F",
        "Y" to "\uD835\uDD50", "Z" to "\u2124"
    )
    return Regex("""\\mathbb\{([A-Z])\}""").replace(input) { bb[it.groupValues[1]] ?: it.value }
}

private fun processCalligraphic(input: String): String {
    val cal = mapOf(
        "A" to "\uD835\uDCD0", "B" to "\uD835\uDCD1", "C" to "\uD835\uDCD2", "D" to "\uD835\uDCD3",
        "E" to "\uD835\uDCD4", "F" to "\uD835\uDCD5", "G" to "\uD835\uDCD6", "H" to "\uD835\uDCD7",
        "I" to "\uD835\uDCD8", "J" to "\uD835\uDCD9", "K" to "\uD835\uDCDA", "L" to "\uD835\uDCDB",
        "M" to "\uD835\uDCDC", "N" to "\uD835\uDCDD", "O" to "\uD835\uDCDE", "P" to "\uD835\uDCDF",
        "Q" to "\uD835\uDCE0", "R" to "\uD835\uDCE1", "S" to "\uD835\uDCE2", "T" to "\uD835\uDCE3",
        "U" to "\uD835\uDCE4", "V" to "\uD835\uDCE5", "W" to "\uD835\uDCE6", "X" to "\uD835\uDCE7",
        "Y" to "\uD835\uDCE8", "Z" to "\uD835\uDCE9"
    )
    return Regex("""\\mathcal\{([A-Z])\}""").replace(input) { cal[it.groupValues[1]] ?: it.value }
}

private fun processAccents(input: String): String {
    val accentMap = mapOf(
        "hat" to "\u0302", "tilde" to "\u0303", "bar" to "\u0304", "vec" to "\u20D7",
        "dot" to "\u0307", "ddot" to "\u0308", "acute" to "\u0301", "grave" to "\u0300",
        "breve" to "\u0306", "check" to "\u030C"
    )
    var result = input
    accentMap.forEach { (cmd, combining) ->
        result = Regex("""\\$cmd\{(.)\}""").replace(result) { it.groupValues[1] + combining }
    }
    return result
}

private fun processWideAccents(input: String): String {
    var r = input
    r = Regex("""\\widehat\{([^}]+)\}""").replace(r) { it.groupValues[1].map { c -> "$c\u0302" }.joinToString("") }
    r = Regex("""\\widetilde\{([^}]+)\}""").replace(r) { it.groupValues[1].map { c -> "$c\u0303" }.joinToString("") }
    return r
}

private fun processOverUnderline(input: String): String {
    var r = input
    r = Regex("""\\overline\{([^}]+)\}""").replace(r) { it.groupValues[1].map { c -> "$c\u0305" }.joinToString("") }
    r = Regex("""\\underline\{([^}]+)\}""").replace(r) { it.groupValues[1].map { c -> "$c\u0332" }.joinToString("") }
    return r
}

private fun processCancel(input: String): String {
    var r = input
    r = Regex("""\\cancel\{([^}]+)\}""").replace(r) { it.groupValues[1].map { c -> "$c\u0336" }.joinToString("") }
    r = Regex("""\\[bx]cancel\{([^}]+)\}""").replace(r) { it.groupValues[1].map { c -> "$c\u0336" }.joinToString("") }
    return r
}

private fun processTextCommands(input: String): String {
    var r = input
    r = Regex("""\\text\{([^}]+)\}""").replace(r) { it.groupValues[1] }
    r = Regex("""\\(textbf|mathbf)\{([^}]+)\}""").replace(r) { it.groupValues[2] }
    r = Regex("""\\(textit|mathit)\{([^}]+)\}""").replace(r) { it.groupValues[2] }
    r = Regex("""\\mathrm\{([^}]+)\}""").replace(r) { it.groupValues[1] }
    return r
}

private fun processSuperscripts(input: String): String {
    var r = input
    r = Regex("""\^\{([^}]+)\}""").replace(r) { it.groupValues[1].map { c -> superscriptMap[c] ?: c }.joinToString("") }
    r = Regex("""\^([a-zA-Z0-9])""").replace(r) { superscriptMap[it.groupValues[1][0]]?.toString() ?: "^${it.groupValues[1]}" }
    return r
}

private fun processSubscripts(input: String): String {
    var r = input
    r = Regex("""_\{([^}]+)\}""").replace(r) { it.groupValues[1].map { c -> subscriptMap[c] ?: c }.joinToString("") }
    r = Regex("""_([a-zA-Z0-9])""").replace(r) { subscriptMap[it.groupValues[1][0]]?.toString() ?: "_${it.groupValues[1]}" }
    return r
}

private fun processFractions(input: String): String =
    Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""").replace(input) {
        val n = it.groupValues[1]; val d = it.groupValues[2]
        if (n.length == 1 && d.length == 1) "$n\u2044$d" else "($n)/($d)"
    }

private fun processSqrt(input: String): String {
    var r = input
    r = Regex("""\\sqrt\[([^\]]+)\]\{([^}]+)\}""").replace(r) {
        val n = it.groupValues[1].map { c -> superscriptMap[c] ?: c }.joinToString("")
        "${n}\u221A(${it.groupValues[2]})"
    }
    r = Regex("""\\sqrt\{([^}]+)\}""").replace(r) { "\u221A(${it.groupValues[1]})" }
    return r
}

private fun processBinomials(input: String): String =
    Regex("""\\binom\{([^}]+)\}\{([^}]+)\}""").replace(input) { "(${it.groupValues[1]}\u00A6${it.groupValues[2]})" }

private fun processModular(input: String): String {
    var r = input
    r = Regex("""\\pmod\{([^}]+)\}""").replace(r) { "(mod ${it.groupValues[1]})" }
    r = r.replace("\\bmod", " mod ").replace("\\mod", " mod ")
    return r
}

private fun processBoxed(input: String): String =
    Regex("""\\boxed\{([^}]+)\}""").replace(input) { "[${it.groupValues[1]}]" }

private fun processStacking(input: String): String {
    var r = input
    r = r.replace("\\stackrel{def}{=}", "\u225D").replace("\\stackrel{?}{=}", "\u225F")
    r = Regex("""\\overset\{([^}]+)\}\{([^}]+)\}""").replace(r) { "${it.groupValues[2]}^{${it.groupValues[1]}}" }
    r = Regex("""\\underset\{([^}]+)\}\{([^}]+)\}""").replace(r) { "${it.groupValues[2]}_{${it.groupValues[1]}}" }
    return r
}

private fun processEnvironments(input: String): String {
    var r = input
    r = Regex("""\\begin\{cases\}(.*?)\\end\{cases\}""", RegexOption.DOT_MATCHES_ALL).replace(r) {
        val lines = it.groupValues[1].trim().split("\\\\\\\\").map { l -> l.trim() }.filter { l -> l.isNotEmpty() }
        "{\n" + lines.joinToString("\n") { l -> "  $l" } + "\n"
    }
    for (mt in listOf("matrix", "pmatrix", "bmatrix", "vmatrix", "Vmatrix")) {
        r = Regex("""\\begin\{$mt\}(.*?)\\end\{$mt\}""", RegexOption.DOT_MATCHES_ALL).replace(r) {
            val rows = it.groupValues[1].trim().split("\\\\\\\\").map { l -> l.trim() }.filter { l -> l.isNotEmpty() }
            val (lb, rb) = when (mt) {
                "pmatrix" -> "(" to ")"; "bmatrix" -> "[" to "]"
                "vmatrix" -> "|" to "|"; "Vmatrix" -> "\u2016" to "\u2016"; else -> "" to ""
            }
            val body = rows.joinToString("\n") { row -> "  " + row.split("&").joinToString("  ") { c -> c.trim() } }
            if (lb.isNotEmpty()) "$lb\n$body\n$rb" else body
        }
    }
    r = Regex("""\\begin\{align\*?\}(.*?)\\end\{align\*?\}""", RegexOption.DOT_MATCHES_ALL).replace(r) {
        it.groupValues[1].trim().split("\\\\\\\\").map { l -> l.trim().replace("&", "") }.filter { l -> l.isNotEmpty() }.joinToString("\n")
    }
    return r
}

private fun processDelimiters(input: String): String {
    var r = input
    for ((pat, rep) in listOf(
        "\\\\left\\{" to "{", "\\\\right\\}" to "}", "\\\\left\\(" to "(", "\\\\right\\)" to ")",
        "\\\\left\\[" to "[", "\\\\right\\]" to "]", "\\\\left\\|" to "|", "\\\\right\\|" to "|",
        "\\\\left\\." to "", "\\\\right\\." to "", "\\\\left<" to "\u27E8", "\\\\right>" to "\u27E9"
    )) { r = r.replace(pat, rep) }
    return r
}

private fun processSpacing(input: String): String =
    input.replace("\\,", "\u2009").replace("\\:", "\u2005").replace("\\>", "\u2005")
        .replace("\\;", "\u2004").replace("\\!", "").replace("\\quad", "\u2003")
        .replace("\\qquad", "\u2003\u2003").replace("\\ ", " ")

private fun processLineBreaks(input: String): String {
    var r = input
    r = r.replace(Regex("\\\\\\\\\\[.*?\\]"), "\n")
    r = r.replace(Regex("\\\\\\\\(?![a-zA-Z])"), "\n")
    return r
}

internal fun renderMathToUnicode(expression: String): String {
    var r = expression
    r = processTextCommands(r)
    r = processBlackboardBold(r)
    r = processCalligraphic(r)
    r = processBoxed(r)
    r = processModular(r)
    r = processOverUnderline(r)
    r = processStacking(r)
    r = processWideAccents(r)
    r = processCancel(r)
    r = processEnvironments(r)
    r = processDelimiters(r)
    sortedMathSymbols.forEach { (latex, unicode) -> r = r.replace(latex, unicode) }
    r = processAccents(r)
    r = processSuperscripts(r)
    r = processSubscripts(r)
    r = processFractions(r)
    r = processBinomials(r)
    r = processSqrt(r)
    r = processLineBreaks(r)
    r = processSpacing(r)
    r = r.replace("{", "").replace("}", "")
    return r.trim()
}
