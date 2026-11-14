package gitee.leolin_me.erkepp.data.eams

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 教务接口的最小 HTTP 封装：签名、POST、拆信封。
 *
 * 与二课的 [ApiClient] 完全两套协议：这里全是 POST + 表单串，鉴权靠 `token` 请求头，
 * 业务数据在 `business_data` 里再包一层 Base64 JSON。协议细节见 [EamsProtocol]。
 *
 * 超时比二课宽一点：教务系统在高峰期会慢，20 秒内都算正常。
 */
class EamsClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val base: String = EamsProtocol.BASE,
) {

    /**
     * 调一个接口，返回解开信封后的业务 JSON（`business_data` 里那层）。
     *
     * @param secret 登录前用 [EamsProtocol.DEFAULT_SECRET]，登录后用会话里的 secret
     * @param token 未登录传 null；登录后同时进请求头与业务参数（与官方 H5 一致）
     */
    suspend fun call(
        path: String,
        secret: String,
        token: String? = null,
        params: Map<String, String> = emptyMap(),
    ): JsonElement = withContext(Dispatchers.IO) {
        val signedParams = if (token.isNullOrEmpty()) params else params + ("token" to token)
        val timestamp = System.currentTimeMillis()
        val random = (0..99999).random()
        val body = EamsProtocol.body(secret, signedParams, timestamp, random)

        val request = Request.Builder()
            .url(base + path)
            .header("token", token.orEmpty())
            .post(body.toRequestBody(FORM))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw EamsException("http_${response.code}", "教务系统暂时连不上（HTTP ${response.code}）")
            }
            val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
                ?: throw EamsException("bad_json", "教务返回了无法解析的内容")
            val code = root["err_code"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val message = root["err_msg"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (code != EamsProtocol.CODE_OK) {
                throw EamsException(code, message.ifBlank { "教务拒绝了这次请求（$code）" })
            }
            val encoded = root["business_data"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (encoded.isBlank()) return@withContext JsonNull
            val decoded = runCatching { EamsProtocol.decodeBusinessData(encoded) }.getOrNull()
                ?: throw EamsException("bad_data", "教务返回的数据解不开")
            runCatching { Json.parseToJsonElement(decoded) }.getOrNull()
                ?: throw EamsException("bad_data", "教务返回的数据不是合法 JSON")
        }
    }

    private companion object {
        val FORM = "application/x-www-form-urlencoded".toMediaType()
    }
}
