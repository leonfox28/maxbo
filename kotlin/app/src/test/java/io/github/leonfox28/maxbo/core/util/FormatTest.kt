package io.github.leonfox28.maxbo.core.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test
    fun `parseWeiboDate reads the offset`() {
        assertEquals(
            Instant.parse("2026-10-12T05:17:46Z"),
            parseWeiboDate("Mon Oct 12 13:17:46 +0800 2026"),
        )
        assertEquals(
            Instant.parse("2026-01-03T00:30:00Z"),
            parseWeiboDate("Fri Jan 2 23:00:00 -0130 2026"),
        )
    }

    @Test
    fun `parseWeiboDate returns null for other formats`() {
        assertNull(parseWeiboDate(null))
        assertNull(parseWeiboDate("2026-10-12 13:17"))
        assertNull(parseWeiboDate("Mon Foo 12 13:17:46 +0800 2026"))
    }

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = local(2026, 10, 13, 15, 30)

    private fun local(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()

    private fun fmt(t: Instant) = formatPostTime(t, now = now, zone = zone)

    @Test
    fun `formatPostTime is relative within today`() {
        assertEquals("刚刚", fmt(now.minusSeconds(20)))
        assertEquals("刚刚", fmt(now.plusSeconds(120)))
        assertEquals("5分钟前", fmt(now.minusSeconds(300)))
        assertEquals("15小时前", fmt(local(2026, 10, 13, 0, 10)))
    }

    @Test
    fun `formatPostTime names yesterday, then dates`() {
        assertEquals("昨天 23:59", fmt(local(2026, 10, 12, 23, 59)))
        assertEquals("10-11 08:05", fmt(local(2026, 10, 11, 8, 5)))
        assertEquals("2025-12-31", fmt(local(2025, 12, 31, 8, 5)))
    }

    @Test
    fun `formatCount rounds down into 万 and 亿`() {
        assertEquals("0", formatCount(0))
        assertEquals("9999", formatCount(9999))
        assertEquals("1万", formatCount(10000))
        assertEquals("1.2万", formatCount(12999))
        assertEquals("123万", formatCount(1234567))
        assertEquals("9999万", formatCount(99999999))
        assertEquals("1.5亿", formatCount(150000000))
    }

    @Test
    fun `formatDuration`() {
        assertEquals("0:05", formatDuration(5))
        assertEquals("12:34", formatDuration(754))
        assertEquals("1:02:05", formatDuration(3725))
    }

    @Test
    fun `formatBytes`() {
        val mb = 1024L * 1024
        assertEquals("0 MB", formatBytes(0))
        assertEquals("2.5 MB", formatBytes(mb * 5 / 2))
        assertEquals("123 MB", formatBytes(mb * 123 + 1000))
        assertEquals("300 MB", formatBytes(300 * mb))
        assertEquals("1 GB", formatBytes(1024 * mb))
        assertEquals("1.5 GB", formatBytes(1536 * mb))
    }
}
