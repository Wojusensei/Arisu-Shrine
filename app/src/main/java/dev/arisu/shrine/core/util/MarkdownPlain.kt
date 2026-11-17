package dev.arisu.shrine.core.util

/**
 * Markdown 摘要：去掉标记只留可读文字。
 * [keepImages] 为 true 时图片取替代文字（旧摘要口径）；分享卡自己画图，传 false 把图片记号整体去掉。
 */
fun markdownPlainText(markdown: String, keepImages: Boolean = true): String {
    var s = markdown
    // 未闭合的代码围栏也整体摘掉：漏打收尾 ``` 的帖子，分享出去不该带大段记号
    s = s.replace(Regex("```[\\s\\S]*?(?:```|$)")) { m ->
        m.value.removePrefix("```").removeSuffix("```").substringAfter('\n', "")
    }
    s = if (keepImages) {
        s.replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
    } else {
        s.replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "")
    }
    s = s.replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
    s = s.replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
    s = s.replace(Regex("(?m)^\\s*>\\s?"), "")
    s = s.replace(Regex("(?m)^\\s*(?:[-*+]|\\d+\\.)\\s+"), "")
    s = s.replace("**", "").replace("__", "")
    s = s.replace(Regex("~~|`"), "")
    // 单个 * / _ 只在词边界上去掉；User_000001 这类下划线不能动
    s = s.replace(Regex("(?<!\\w)[*_]|[*_](?!\\w)"), "")
    s = s.replace(Regex("<[^>]+>"), "")
    s = s.replace(Regex("\\n{3,}"), "\n\n")
    return s.trim()
}
