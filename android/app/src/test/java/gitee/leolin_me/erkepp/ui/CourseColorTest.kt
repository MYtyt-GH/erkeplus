package gitee.leolin_me.erkepp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 课程配色：同一门课永远同一色，不同课尽量分散。 */
class CourseColorTest {

    @Test
    fun `课程号优先 改课名不换色`() {
        assertEquals(
            CourseColor.indexOf(courseCode = "0821001", courseName = "高等数学"),
            CourseColor.indexOf(courseCode = "0821001", courseName = "高等数学（上）"),
        )
    }

    @Test
    fun `没有课程号时用课名`() {
        assertEquals(
            CourseColor.indexOf(courseCode = "", courseName = "大学物理"),
            CourseColor.indexOf(courseCode = "  ", courseName = " 大学物理 "),
        )
    }

    @Test
    fun `空课程落到第一色而不是崩掉`() {
        assertEquals(0, CourseColor.indexOf(courseCode = "", courseName = ""))
    }

    @Test
    fun `常见课名分布得开`() {
        val names = listOf("高等数学", "大学物理", "大学英语", "中国传统文化", "程序设计基础", "线性代数", "思想道德与法治", "体育")
        val used = names.map { CourseColor.indexOf("", it) }.toSet()
        assertTrue("六色里至少要用到 4 色，否则桌面上一片同色：$used", used.size >= 4)
        assertTrue(used.all { it in 0 until CourseColor.COUNT })
    }

    @Test
    fun `同一门课每次取色一致`() {
        val first = CourseColor.indexOf("", "数据结构")
        repeat(5) { assertEquals(first, CourseColor.indexOf("", "数据结构")) }
    }

    @Test
    fun `课程号散得开`() {
        // 六位数字课程号是主流，纯 31 乘法取模会挤在少数颜色上，这一条盯住「别挤」
        val codes = (1..30).map { "20%04d".format(2500 + it * 7) }
        val used = codes.map { CourseColor.indexOf(it, "") }.toSet()
        assertTrue("30 个课程号至少要用到 6 色：$used", used.size >= 6)
        assertTrue(used.all { it in 0 until CourseColor.COUNT })
    }
}
