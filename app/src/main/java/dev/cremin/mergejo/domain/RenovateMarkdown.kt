package dev.cremin.mergejo.domain

/**
 * Normalises the GitHub-flavoured markdown Renovate copies out of changelogs
 * into something the Compose markdown renderer displays cleanly: drops the
 * HTML it can't render (`<details>`, comments), Renovate's anti-mention
 * zero-width spaces (`@&#8203;user`), and maps the common `:shortcode:` emoji
 * that changelogs (babel, lerna-changelog, gitmoji…) use as section markers.
 */
object RenovateMarkdown {

    private val comment = Regex("""<!--[\s\S]*?-->""")
    private val detailsTag = Regex("""(?i)</?details[^>]*>""")
    private val summary = Regex("""(?is)<summary>(.*?)</summary>""")
    private val br = Regex("""(?i)<br\s*/?>""")
    // `\_` because Renovate escapes underscores: `:nail\_care:`.
    private val shortcode = Regex(""":((?:[a-z0-9+\-]|\\?_)+):""")
    private val excessBlankLines = Regex("""\n{3,}""")

    private val emoji = mapOf(
        "rocket" to "🚀", "bug" to "🐛", "house" to "🏠", "nail_care" to "💅",
        "memo" to "📝", "eyeglasses" to "👓", "running_woman" to "🏃‍♀️",
        "microscope" to "🔬", "leftwards_arrow_with_hook" to "↩️", "boom" to "💥",
        "sparkles" to "✨", "tada" to "🎉", "warning" to "⚠️", "lock" to "🔒",
        "zap" to "⚡", "wrench" to "🔧", "hammer" to "🔨", "books" to "📚",
        "art" to "🎨", "fire" to "🔥", "recycle" to "♻️", "package" to "📦",
        "arrow_up" to "⬆️", "arrow_down" to "⬇️", "white_check_mark" to "✅",
        "construction" to "🚧", "rotating_light" to "🚨", "heavy_plus_sign" to "➕",
        "heavy_minus_sign" to "➖", "globe_with_meridians" to "🌐", "lipstick" to "💄",
        "construction_worker" to "👷", "green_heart" to "💚", "pencil2" to "✏️",
        "truck" to "🚚", "page_facing_up" to "📄", "bookmark" to "🔖",
        "new" to "🆕", "x" to "❌", "heavy_check_mark" to "✔️", "gear" to "⚙️",
    )

    fun clean(markdown: String): String = markdown
        .replace("\r\n", "\n")
        .replace("&#8203;", "")
        .replace("​", "")
        .replace(comment, "")
        .replace(summary) { "**${it.groupValues[1].trim()}**" }
        .replace(detailsTag, "")
        .replace(br, "  \n")
        .replace(shortcode) { m -> emoji[m.groupValues[1].replace("\\_", "_")] ?: m.value }
        .replace(excessBlankLines, "\n\n")
        .trim()
}
