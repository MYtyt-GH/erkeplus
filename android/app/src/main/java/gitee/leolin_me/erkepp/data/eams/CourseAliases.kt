package gitee.leolin_me.erkepp.data.eams

import android.content.Context

/**
 * 课程别名：教务给的课名常常很长（「职业发展（生涯规划）指导」），
 * 桌面小组件和课表格子里放不下，允许用户起一个短名字，展示时优先用别名。
 *
 * 键用课程号（同一门课改名也还是它），没有课程号才退回课名；
 * 与 `ui/CourseColor.kt` 取色键的规则一致，保证「同一门课」在这两处是同一个东西。
 */
class CourseAliases(context: Context) {

    private val prefs = context.getSharedPreferences("erkepp_eams_alias", Context.MODE_PRIVATE)

    /** 这门课在别名表里的键。 */
    fun keyOf(lesson: EamsLesson): String =
        lesson.courseCode.trim().ifBlank { lesson.courseName.trim() }

    /** 全部别名：课程键 → 别名。 */
    fun all(): Map<String, String> = prefs.all.mapNotNull { (key, value) ->
        val alias = value as? String
        if (alias.isNullOrBlank()) null else key to alias
    }.toMap()

    fun set(lesson: EamsLesson, alias: String) {
        val key = keyOf(lesson)
        if (key.isEmpty()) return
        val trimmed = alias.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, trimmed).apply()
        }
    }

    fun clear(lesson: EamsLesson) {
        val key = keyOf(lesson)
        if (key.isNotEmpty()) prefs.edit().remove(key).apply()
    }
}

/** 展示名：有别名用别名，没有就用教务的原名。 */
fun Map<String, String>.displayName(lesson: EamsLesson): String {
    val key = lesson.courseCode.trim().ifBlank { lesson.courseName.trim() }
    return this[key]?.takeIf { it.isNotBlank() } ?: lesson.courseName
}
