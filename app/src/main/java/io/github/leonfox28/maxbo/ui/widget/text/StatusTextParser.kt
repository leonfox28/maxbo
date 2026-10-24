package io.github.leonfox28.maxbo.ui.widget.text

import io.github.leonfox28.maxbo.core.util.readUrl
import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.data.model.UrlEntity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** A piece of a status body. */
sealed interface TextSegment {
    data class Plain(val text: String) : TextSegment

    /** [iconUrl]: the small type icon Weibo puts before some links (web page, video, …). */
    data class Link(val text: String, val link: StatusLink, val iconUrl: String? = null) :
        TextSegment

    /** An inline emoticon image such as `[doge]`. */
    data class Emoticon(val url: String, val alt: String) : TextSegment

    /** The "展开" marker at the end of a truncated long post. */
    data object Expand : TextSegment
}

/** Where a link in a status body leads. */
sealed interface StatusLink {
    data class Mention(val screenName: String) : StatusLink

    /** A `#话题#`, without the surrounding `#`. */
    data class Topic(val topic: String) : StatusLink

    /**
     * Pictures linked from the text rather than attached, such as those of a repost along the chain
     * (`查看2张图片`); [pics] are empty when [url] isn't a picture on Weibo's image hosts.
     */
    data class Image(val url: HttpUrl, val pics: List<PicInfo>) : StatusLink

    data class Web(val url: HttpUrl) : StatusLink
}

/**
 * Splits the HTML `text` of a status into segments.
 *
 * Weibo's markup is loose (e.g. unquoted `href` values), which jsoup tolerates. [urls] resolves
 * `t.cn` short links. Links to the pictures with ids in [hiddenPictures] are left out with their
 * label.
 */
fun parseStatusText(
    text: String,
    urls: List<UrlEntity> = emptyList(),
    hiddenPictures: List<String> = emptyList(),
): List<TextSegment> {
    val segments = mutableListOf<TextSegment>()
    fun addText(s: String) {
        if (s.isEmpty()) return
        val last = segments.lastOrNull()
        if (last is TextSegment.Plain) {
            segments[segments.lastIndex] = TextSegment.Plain(last.text + s)
        } else {
            segments += TextSegment.Plain(s)
        }
    }

    fun visit(node: Node) {
        if (node is TextNode) return addText(node.wholeText)
        if (node !is Element) return
        when {
            node.normalName() == "br" -> addText("\n")
            node.normalName() == "img" -> {
                val url = readUrl(node.attr("src"))
                val alt = if (node.hasAttr("alt")) node.attr("alt") else node.attr("title")
                if (url != null) segments += TextSegment.Emoticon(url, alt) else addText(alt)
            }
            node.normalName() == "span" && node.hasClass("expand") -> segments += TextSegment.Expand
            node.normalName() == "a" &&
                node.hasAttr("data-pid") &&
                node.attr("data-pid") in hiddenPictures -> {}
            node.normalName() == "a" -> {
                val label = node.wholeText().trim()
                val link = linkOf(node, label, urls)
                if (link == null || label.isEmpty()) {
                    addText(node.wholeText())
                } else {
                    val icon = node.selectFirst("img.icon-link")
                    segments +=
                        TextSegment.Link(
                            label,
                            link,
                            iconUrl = icon?.let { readUrl(it.attr("src")) },
                        )
                }
            }
            else -> node.childNodes().forEach(::visit)
        }
    }

    Jsoup.parseBodyFragment(text).body().childNodes().forEach(::visit)
    return segments
}

/**
 * The HTML `text` of a status or comment as one line of plain text, for previews: emoticons become
 * their `[name]`, line breaks spaces.
 */
fun plainStatusText(text: String): String =
    parseStatusText(text)
        .joinToString("") {
            when (it) {
                is TextSegment.Plain -> it.text
                is TextSegment.Link -> it.text
                is TextSegment.Emoticon -> it.alt
                TextSegment.Expand -> ""
            }
        }
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun linkOf(a: Element, label: String, urls: List<UrlEntity>): StatusLink? {
    val href = a.attr("href").trim()
    if (href.isEmpty()) return null

    // Mentions: <a href=/n/name usercard="name=name">@name</a>
    if (a.hasAttr("usercard") || href.startsWith("/n/")) {
        val name = if (label.startsWith("@")) label.substring(1) else label
        return if (name.isEmpty()) null else StatusLink.Mention(name)
    }

    val absolute =
        when {
            href.startsWith("//") -> "https:$href"
            href.startsWith("/") -> "https://weibo.com$href"
            else -> href
        }
    // App-only schemes such as sinaweibo:// can't be opened; keep them as
    // text.
    val url = absolute.toHttpUrlOrNull() ?: return null

    if (
        url.host == "s.weibo.com" &&
            label.length >= 2 &&
            label.startsWith("#") &&
            label.endsWith("#")
    ) {
        val topic = label.substring(1, label.length - 1)
        if (topic.isNotEmpty()) return StatusLink.Topic(topic)
    }
    if (a.hasAttr("data-pids") || a.hasAttr("data-pid") || url.host.endsWith(".sinaimg.cn")) {
        return StatusLink.Image(url, linkedPics(a, url, urls))
    }
    if (url.host == "t.cn") {
        for (entity in urls) {
            val long = entity.longUrl?.toHttpUrlOrNull() ?: continue
            if (sameUrl(entity.shortUrl, url)) return StatusLink.Web(long)
        }
    }
    return StatusLink.Web(url)
}

// A link to several pictures names them all in `data-pids` but links to the
// first; one to a single picture may name it in `data-pid`. The url_struct
// entry of the link has them in full; failing that, they are built from
// their pids, or the link itself.
private fun linkedPics(a: Element, url: HttpUrl, urls: List<UrlEntity>): List<PicInfo> {
    val pids = (a.attr("data-pids").split(',') + a.attr("data-pid")).map(String::trim)
    val known = urls.flatMap { it.pics }.associateBy { it.id }
    val pics =
        pids.filter(String::isNotEmpty).distinct().mapNotNull {
            known[it] ?: PicInfo.tryFromPid(it)
        }
    return when {
        pics.isNotEmpty() -> pics
        url.host.endsWith(".sinaimg.cn") -> listOf(PicInfo.fromUrl(url.toString()))
        else -> emptyList()
    }
}

private fun sameUrl(short: String, url: HttpUrl): Boolean {
    val parsed = short.toHttpUrlOrNull() ?: return false
    return parsed.host == url.host && parsed.encodedPath == url.encodedPath
}
