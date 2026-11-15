package gitee.leolin_me.erkepp.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** 看板课名的折行规则：每行最多 4 个字，超过两行用省略号收尾。 */
class BoardNameLinesTest {

    @Test
    fun `四个字以内不折行`() {
        assertEquals("高数", boardNameLines("高数"))
        assertEquals("中国文化", boardNameLines("中国文化"))
    }

    @Test
    fun `六个字折成四加二`() {
        val lines = boardNameLines("中国传统文化").lines()
        assertEquals(2, lines.size)
        assertEquals("中国传统", lines[0])
        assertEquals("文化", lines[1])
    }

    @Test
    fun `带括号的课名也按四字折`() {
        assertEquals("大学英语\n（一）", boardNameLines("大学英语（一）"))
    }

    @Test
    fun `超过八个字用省略号`() {
        val lines = boardNameLines("中国近现代史纲要研究").lines()
        assertEquals(2, lines.size)
        assertEquals("中国近现", lines[0])
        assertEquals("代史纲…", lines[1])
    }
}
