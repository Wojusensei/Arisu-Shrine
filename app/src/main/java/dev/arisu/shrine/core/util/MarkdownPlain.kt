package dev.arisu.shrine.core.util

fun markdownPlainText(markdown: String): String {
    var s = markdown
    s = s.replace(Regex("```[\\s\\S]*?```")) { m ->
        m.value.removePrefix("```").removeSuffix("```").substringAfter('\n', "")
    }
    s = s.replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
    s = s.replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
    s = s.replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
    s = s.replace(Regex("(?m)^\\s*>\\s?"), "")
    s = s.replace(Regex("(?m)^\\s*(?:[-*+]|\\d+\\.)\\s+"), "")
    s = s.replace("**", "").replace("__", "")
    s = s.replace(Regex("~~|`"), "")

    s = s.replace(Regex("(?<!\\w)[*_]|[*_](?!\\w)"), "")
    s = s.replace(Regex("<[^>]+>"), "")
    s = s.replace(Regex("\\n{3,}"), "\n\n")
    return s.trim()
}
