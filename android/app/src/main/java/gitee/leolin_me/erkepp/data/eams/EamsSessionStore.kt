package gitee.leolin_me.erkepp.data.eams

import android.content.Context
import gitee.leolin_me.erkepp.data.Crypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 教务会话的持久化，只在用户勾了「记住」时才有内容（见 ADR 0005）。
 *
 * **存的是 `token` + `secret`，不是密码。** 密码从不落盘，token 过期就请用户重新输入一次。
 * 加密复用 ADR 0002 的 Keystore AES-256/GCM（[Crypto]），密钥不可导出。
 */
class EamsSessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("erkepp_eams", Context.MODE_PRIVATE)
    private val json = Json

    /** 读回上次记住的会话。解密失败（换机、清过密钥）当作没有，顺手清掉坏数据。 */
    fun load(): EamsSession? {
        val encrypted = prefs.getString(KEY_SESSION, null) ?: return null
        val plain = runCatching { Crypto.decrypt(encrypted) }.getOrNull()
        if (plain == null) {
            clear()
            return null
        }
        return runCatching { decode(plain) }.getOrNull()?.takeIf { it.token.isNotBlank() }
    }

    fun save(session: EamsSession) {
        runCatching { prefs.edit().putString(KEY_SESSION, Crypto.encrypt(encode(session))).apply() }
    }

    /** 用户取消「记住」、点「清除课表凭据」、换账号时都要调它。 */
    fun clear() {
        prefs.edit().clear().apply()
    }

    fun remembered(): Boolean = prefs.contains(KEY_SESSION)

    private fun encode(session: EamsSession): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("token", JsonPrimitive(session.token))
            put("secret", JsonPrimitive(session.secret))
            put("userCode", JsonPrimitive(session.userCode))
            put("userName", JsonPrimitive(session.userName))
        },
    )

    private fun decode(text: String): EamsSession {
        val root = json.parseToJsonElement(text).jsonObject
        fun text(key: String): String = root[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        return EamsSession(
            token = text("token"),
            secret = text("secret"),
            userCode = text("userCode"),
            userName = text("userName"),
        )
    }

    private companion object {
        const val KEY_SESSION = "eams_session"
    }
}
