package gitee.leolin_me.erkepp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.eams.EamsLesson

/**
 * 课程配色：同一门课在课表页和桌面小组件里永远是同一个颜色。
 *
 * 颜色值放在 `res/values{,-night}/colors.xml`（Compose 的配色没法当资源给 RemoteViews 用），
 * 这里只负责两件事：**哪门课用第几色**（纯函数，可单测）和取色。
 *
 * 取色按课程号的稳定哈希，不是按「今天第几门课」——同一门课每次画都是同一色，
 * 一周里换位置也不会变脸。
 *
 * 8 色而不是 6 色：一门课一天四节，6 色时「相邻两节课撞成同色」是常见事件，
 * 桌面上一排圆点看着像没生效。8 色仍会撞，但概率明显低，且撞色只影响观感、不影响信息。
 */
object CourseColor {

    const val COUNT = 8

    private val colors = intArrayOf(
        R.color.course_color_1,
        R.color.course_color_2,
        R.color.course_color_3,
        R.color.course_color_4,
        R.color.course_color_5,
        R.color.course_color_6,
        R.color.course_color_7,
        R.color.course_color_8,
    )

    private val tints = intArrayOf(
        R.color.course_tint_1,
        R.color.course_tint_2,
        R.color.course_tint_3,
        R.color.course_tint_4,
        R.color.course_tint_5,
        R.color.course_tint_6,
        R.color.course_tint_7,
        R.color.course_tint_8,
    )

    private val dots = intArrayOf(
        R.drawable.widget_dot_1,
        R.drawable.widget_dot_2,
        R.drawable.widget_dot_3,
        R.drawable.widget_dot_4,
        R.drawable.widget_dot_5,
        R.drawable.widget_dot_6,
        R.drawable.widget_dot_7,
        R.drawable.widget_dot_8,
    )

    /** 优先用课程号（同一门课改名也还是它），没有就退回课名。 */
    fun indexOf(courseCode: String, courseName: String): Int {
        val key = courseCode.trim().ifBlank { courseName.trim() }
        if (key.isEmpty()) return 0
        var hash = 0
        for (ch in key) hash = hash * 31 + ch.code
        // Murmur3 的收尾混合：课程号大多是 6 位数字，光靠 31 的乘法低位几乎不变，
        // 取模后会挤在少数几个颜色上；混一遍高低位才散得开。
        hash = hash xor (hash ushr 16)
        hash *= -0x7A143595
        hash = hash xor (hash ushr 13)
        hash *= -0x3D4D51CB
        hash = hash xor (hash ushr 16)
        return (hash and 0x7fff_ffff) % COUNT
    }

    fun indexOf(lesson: EamsLesson): Int = indexOf(lesson.courseCode, lesson.courseName)

    /** 课表页课程块的主色（细边框、强调）。 */
    @Composable
    fun color(index: Int): Color = colorResource(colors[index.mod(COUNT)])

    /** 课表页课程块的淡底。 */
    @Composable
    fun tint(index: Int): Color = colorResource(tints[index.mod(COUNT)])

    /** 小组件时间轴上的圆点。 */
    fun dotRes(index: Int): Int = dots[index.mod(COUNT)]
}
