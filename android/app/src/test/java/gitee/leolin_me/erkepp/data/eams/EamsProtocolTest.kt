package gitee.leolin_me.erkepp.data.eams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教务协议的三件套：MD5、签名、请求体。
 *
 * 期望值不是本实现算出来的——是用 .NET 的 MD5 独立算过一遍再写进来的，
 * 所以这组用例能真的抓出「我照着前端抄错了」这类错误。
 * 签名公式若被改动，线上的 `err_code` 会变成错误码，界面上表现为「教务拒绝了这次请求」。
 */
class EamsProtocolTest {

    private val secret = EamsProtocol.DEFAULT_SECRET
    private val timestamp = 1794678794993L
    private val random = 78829

    @Test
    fun `md5 与标准向量一致`() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", EamsProtocol.md5Hex("abc"))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", EamsProtocol.md5Hex(""))
    }

    @Test
    fun `md5 走的是 UTF-8 而不是平台默认编码`() {
        assertEquals("e2b6169f3a37d83b16da8a4950fdfde6", EamsProtocol.md5Hex("学期日历"))
    }

    @Test
    fun `无参数的签名`() {
        assertEquals(
            "14ABF2FF2645627E07B09716C2B58D5D",
            EamsProtocol.sign(secret, emptyMap(), timestamp, random),
        )
    }

    @Test
    fun `参数按 key 升序参与签名`() {
        // 传进来的顺序故意与升序相反，签名必须仍按 biz_type_id, token 排列
        val params = linkedMapOf("token" to "abc", "biz_type_id" to "1")
        assertEquals(
            "041C3EB0ABFD60FBEF438DFE6C638BBF",
            EamsProtocol.sign(secret, params, timestamp, random),
        )
    }

    @Test
    fun `请求体升序拼接且以 与 开头`() {
        val body = EamsProtocol.body(secret, mapOf("biz_type_id" to "1"), timestamp, random)
        assertTrue("请求体必须以 & 开头：$body", body.startsWith("&biz_type_id=1&random="))
        assertTrue(body.contains("&sign="))
        assertTrue(body.contains("&timestamp=$timestamp"))
        // sign/timestamp/random 也要按字母序排进请求体
        assertTrue(body.indexOf("&random=") < body.indexOf("&sign="))
        assertTrue(body.indexOf("&sign=") < body.indexOf("&timestamp="))
    }

    @Test
    fun `token 作为业务参数时也进请求体`() {
        val params = mapOf("biz_type_id" to "1", "token" to "abc")
        val body = EamsProtocol.body(secret, params, timestamp, random)
        assertTrue(body.contains("&token=abc"))
        // token 在 timestamp 之后（字母序 t-o-k 大于 t-i-m）
        assertTrue(body.indexOf("&timestamp=") < body.indexOf("&token=abc"))
    }

    @Test
    fun `密码是 UTF-8 Base64 而不是哈希`() {
        assertEquals("MDAwMDAw", EamsProtocol.encodePassword("000000"))
        assertEquals("YWJj", EamsProtocol.encodePassword("abc"))
    }

    @Test
    fun `信封里的业务数据是 Base64 的 JSON`() {
        assertEquals("""{"enable":false}""", EamsProtocol.decodeBusinessData("eyJlbmFibGUiOmZhbHNlfQ=="))
    }

    @Test
    fun `只有 20 算 token 失效`() {
        assertTrue(EamsException(EamsProtocol.CODE_TOKEN_INVALID, "token invalid").tokenInvalid)
        assertFalse(EamsException(EamsProtocol.CODE_BUSINESS, "用户名或密码不正确！").tokenInvalid)
        assertFalse(EamsException("http_500", "连不上").tokenInvalid)
    }
}
