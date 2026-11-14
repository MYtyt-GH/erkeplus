package gitee.leolin_me.erkepp.data.eams

import java.security.MessageDigest
import java.util.Base64

/**
 * 教务 App（`jwglyd`）的线上协议：签名、请求体、信封。
 *
 * 全部是纯函数，不碰网络也不碰 Android，便于单测。
 * 2026-11-15 实测：用本文件的算法签名后，服务端返回 `err_code=00000`，
 * 说明算法与线上一致（见 docs/api/eams-jwglyd.md）。
 *
 * 三个容易写错的地方：
 * 1. 请求体不是 JSON，是 `&k1=v1&k2=v2…` 的升序拼接，**开头就有一个 `&`**；
 * 2. 签名只覆盖业务参数值（升序）与 timestamp/random，不含 `sign` 自身；
 * 3. `err_code` 是**字符串**且长度不固定（`"20"`、`"00000"`），不要按数字比较。
 */
object EamsProtocol {

    const val BASE = "https://jwglyd.aust.edu.cn/app-ws/ws/app-service"

    /** 登录前用的固定密钥（前端常量 `defaultSecret`）。 */
    const val DEFAULT_SECRET = "supwisdom_eams_app_secret"

    /** 官方所有业务请求都带 `biz_type_id=1`（本科）。 */
    const val BIZ_TYPE_ID = "1"

    const val CODE_OK = "00000"

    /** token 失效。 */
    const val CODE_TOKEN_INVALID = "20"

    /** 业务错误，`err_msg` 可以直接给用户看（例如「用户名或密码不正确！」）。 */
    const val CODE_BUSINESS = "03000"

    /** 验证码错误（仅在开启验证码时出现）。 */
    const val CODE_CAPTCHA = "03010"

    private val HEX = "0123456789abcdef".toCharArray()

    fun md5Hex(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return out.toString()
    }

    /**
     * `MD5(secret|v1|v2|…|timestamp|random)` 取大写。
     * `v` 是按 key 升序排列的**业务参数值**，每个值后面跟一个 `|`。
     */
    fun sign(secret: String, params: Map<String, String>, timestamp: Long, random: Int): String {
        val text = StringBuilder(secret).append('|')
        params.keys.sorted().forEach { key -> text.append(params.getValue(key)).append('|') }
        text.append(timestamp).append('|').append(random)
        return md5Hex(text.toString()).uppercase()
    }

    /** 拼出真正发出去的请求体：业务参数 + `sign`/`timestamp`/`random`，按 key 升序，前缀 `&`。 */
    fun body(secret: String, params: Map<String, String>, timestamp: Long, random: Int): String {
        val all = params.toMutableMap()
        all["sign"] = sign(secret, params, timestamp, random)
        all["timestamp"] = timestamp.toString()
        all["random"] = random.toString()
        return all.keys.sorted().joinToString(separator = "") { key -> "&$key=${all.getValue(key)}" }
    }

    /** `business_data` 是 Base64 的 UTF-8 JSON。 */
    fun decodeBusinessData(encoded: String): String =
        String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)

    /** `passwd` 字段：明文密码的 UTF-8 Base64（**编码不是加密**，安全性全靠 https）。 */
    fun encodePassword(plain: String): String =
        Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8))
}

/** 教务接口的错误。`code` 是服务端的 `err_code`，网络层问题用 `http_xxx` / `bad_json` 之类。 */
class EamsException(
    val code: String,
    message: String,
) : Exception(message) {

    /** token 失效：调用方应清掉本地凭据并提示重新登录。 */
    val tokenInvalid: Boolean get() = code == EamsProtocol.CODE_TOKEN_INVALID
}
