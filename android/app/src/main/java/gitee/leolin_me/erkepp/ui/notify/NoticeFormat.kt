package gitee.leolin_me.erkepp.ui.notify

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 消息时间：列表里一行只放得下「什么时候」，不放年份以外的信息。
 *
 * - 今天 / 昨天：说「今天 14:20」，比一串日期好认
 * - 今年以内：`11-15 14:20`
 * - 跨年：`2025-11-15`（月份日子已经不重要了，年份才有）
 *
 * [zone] 与 [now] 都可以传，单元测试不用依赖跑测试那台机器的时区和当前时间。
 */
internal fun noticeTimeText(
    ctime: Long,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val millis = normalizeMillis(ctime)
    if (millis <= 0L) return ""
    val moment = Instant.ofEpochMilli(millis).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day: LocalDate = moment.toLocalDate()
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.US).format(moment)
    return when {
        day == today -> "今天 $clock"
        day == today.minusDays(1) -> "昨天 $clock"
        day.year == today.year -> DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US).format(moment)
        else -> DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).format(moment)
    }
}

/** 服务端这一族接口给的是毫秒，但秒级时间戳也认；认不出来就是 0（界面不显示时间）。 */
private fun normalizeMillis(value: Long): Long {
    if (value <= 0L) return 0L
    // 1e9..1e11 之间当秒级处理：1700000000（2023 年）到这个量级，13 位的毫秒显然更大
    if (value in 1_000_000_000L..99_999_999_999L) return value * 1000
    return value
}
