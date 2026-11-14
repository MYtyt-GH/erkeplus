package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileValidationTest {

    @Test
    fun `accepts mainland numbers`() {
        assertTrue(isValidMobile("13000000000"))
        assertTrue(isValidMobile("13000000000"))
    }

    @Test
    fun `rejects wrong shape`() {
        assertFalse(isValidMobile(""))
        assertFalse(isValidMobile("1956586628")) // 10 位
        assertFalse(isValidMobile("130000000001")) // 12 位
        assertFalse(isValidMobile("29565866281")) // 不是 1 开头
        assertFalse(isValidMobile("1956586628a"))
    }
}
