package io.github.leonfox28.maxbo.core.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private val months =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private val weiboDate =
    Regex("""^\w{3} (\w{3}) (\d{1,2}) (\d{2}):(\d{2}):(\d{2}) ([+-])(\d{2})(\d{2}) (\d{4})$""")

/**
 * Parses Weibo's `created_at`, e.g. `Mon Oct 12 13:17:46 +0800 2026`; null if it has another
 * format.
 */
fun parseWeiboDate(value: String?): Instant? {
    val m = value?.trim()?.let { weiboDate.find(it) } ?: return null
    val (monthName, day, hour, minute, second, sign, offsetHours, offsetMinutes, year) =
        m.destructured
    val month = months.indexOf(monthName) + 1
    if (month == 0) return null
    val direction = if (sign == "+") 1 else -1
    return runCatching {
        val offset =
            ZoneOffset.ofHoursMinutes(
                offsetHours.toInt() * direction,
                offsetMinutes.toInt() * direction,
            )
        LocalDateTime.of(
                year.toInt(),
                month,
                day.toInt(),
                hour.toInt(),
                minute.toInt(),
                second.toInt(),
            )
            .toInstant(offset)
    }
        .getOrNull()
}

/** A post time the way Weibo shows it: relative when recent, a date when not. */
fun formatPostTime(
    time: Instant,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val diff = Duration.between(time, now)
    if (diff.toMinutes() < 1) return "刚刚"
    if (diff.toHours() < 1) return "${diff.toMinutes()}分钟前"
    val t = time.atZone(zone)
    val today = now.atZone(zone).toLocalDate().atStartOfDay(zone)
    if (!t.isBefore(today)) return "${diff.toHours()}小时前"
    val hm = "${two(t.hour)}:${two(t.minute)}"
    if (!t.isBefore(today.minusDays(1))) return "昨天 $hm"
    if (t.year == today.year) return "${two(t.monthValue)}-${two(t.dayOfMonth)} $hm"
    return "${t.year}-${two(t.monthValue)}-${two(t.dayOfMonth)}"
}

/** A count in Chinese units, rounded down so it never overstates: 9999, 1.2万, 3亿. */
fun formatCount(count: Long): String {
    fun scaled(unit: Long, suffix: String): String {
        val tenths = count * 10 / unit
        val whole = tenths / 10
        val fraction = tenths % 10
        return "${if (fraction == 0L || whole >= 100) "$whole" else "$whole.$fraction"}$suffix"
    }
    if (count < 10_000) return "$count"
    if (count < 100_000_000) return scaled(10_000, "万")
    return scaled(100_000_000, "亿")
}

fun formatCount(count: Int): String = formatCount(count.toLong())

/** Seconds as `m:ss`, or `h:mm:ss` from an hour up. */
fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = two(seconds % 60)
    return if (h > 0) "$h:${two(m)}:$s" else "$m:$s"
}

/** A byte size in MB from below 1 GB, else in GB: `0 MB`, `2.5 MB`, `300 MB`, `1 GB`, `1.5 GB`. */
fun formatBytes(bytes: Long): String {
    val mb = 1024.0 * 1024
    fun trim(v: Double) =
        if (v == Math.rint(v) || v >= 10) "${Math.round(v)}"
        else String.format(java.util.Locale.ROOT, "%.1f", v)
    if (bytes >= 1024 * mb) return "${trim(bytes / (1024 * mb))} GB"
    return "${trim(bytes / mb)} MB"
}

private fun two(n: Int) = n.toString().padStart(2, '0')
