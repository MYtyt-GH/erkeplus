package gitee.leolin_me.erkepp.data.eams

import android.content.Context
import java.io.File
import java.time.LocalDate

/**
 * 课表缓存：按「学期 + 周一日期」一个文件，落在应用私有目录。
 *
 * 约定（见 docs/design/timetable.md）：
 * - **读是同步的、不抛异常**：桌面小组件与后台提醒在没有网络、没有前台进程时也要能读；
 * - 写由调用方放到 IO 线程；
 * - 缓存是「保底」不是「真相」：拉取失败时继续用它，拉取成功才覆盖。
 */
class TimetableCache(context: Context) {

    private val dir = File(context.filesDir, "timetable").apply { mkdirs() }

    /** 读某一周的缓存；没有或损坏都返回 null。 */
    fun load(semesterId: String, weekStart: LocalDate): TimetableWeek? {
        val file = fileOf(semesterId, weekStart)
        if (!file.isFile) return null
        return runCatching { TimetableJson.decode(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    /** 写某一周的缓存并返回写入的时间戳（毫秒）。 */
    fun save(semesterId: String, weekStart: LocalDate, lessons: List<EamsLesson>, fetchedAt: Long): TimetableWeek {
        val week = TimetableWeek(lessons = lessons, fetchedAt = fetchedAt)
        runCatching {
            val file = fileOf(semesterId, weekStart)
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(TimetableJson.encode(week), Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                file.writeText(TimetableJson.encode(week), Charsets.UTF_8)
                temp.delete()
            }
        }
        return week
    }

    /** 最近一次成功拉取的时间：任意一周里最新的那个。没有缓存则 0。 */
    fun lastFetchedAt(): Long =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".json") }
            .maxOfOrNull { it.lastModified() }
            ?: 0L

    /** 「清除课表缓存」用：删掉全部周文件。 */
    fun clear() {
        (dir.listFiles() ?: emptyArray()).forEach { runCatching { it.delete() } }
    }

    private fun fileOf(semesterId: String, weekStart: LocalDate) =
        File(dir, "${semesterId.ifBlank { "unknown" }}-$weekStart.json")
}
