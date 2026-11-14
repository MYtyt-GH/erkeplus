package gitee.leolin_me.erkepp.data.eams

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/**
 * 课表的取数入口。
 *
 * 只负责「拿数据」，不管缓存（[TimetableCache]）、不管凭据（[EamsSessionStore]）、
 * 也不管界面。提醒与桌面小组件将来直接复用这里的查询函数（[lessonsOn] / [weekLessons]）。
 */
class EamsRepository(
    private val client: EamsClient = EamsClient(),
) {

    /** 当前教务系统要不要验证码。决定登录弹窗里是否出现验证码那一行。 */
    suspend fun verifyCode(): EamsVerifyCode =
        EamsParsers.verifyCode(
            client.call("/verify-code", EamsProtocol.DEFAULT_SECRET),
        )

    /**
     * 账号密码登录。密码在这里编码成 Base64 就发出去，**调用方用完必须立刻丢掉明文**。
     * 失败抛 [EamsException]；`CODE_BUSINESS` 的 message 可以直接显示
     * （实测文案「用户名或密码不正确！」）。
     */
    suspend fun login(userCode: String, password: String): EamsSession {
        val data = client.call(
            path = "/login",
            secret = EamsProtocol.DEFAULT_SECRET,
            params = mapOf(
                "user_code" to userCode,
                "passwd" to EamsProtocol.encodePassword(password),
            ),
        )
        val session = EamsParsers.login(data)
        if (session.token.isBlank() || session.secret.isBlank()) {
            throw EamsException("bad_login", "教务没有返回登录凭据")
        }
        return session
    }

    /** 学期列表，用来算周次与显示「2026-2027 第 1 学期」。 */
    suspend fun semesters(session: EamsSession): EamsSemesterList =
        EamsParsers.semesters(
            client.call(
                path = "/common/get-semester",
                secret = session.secret,
                token = session.token,
                params = mapOf("biz_type_id" to EamsProtocol.BIZ_TYPE_ID),
            ),
        )

    /** 每天最多几节（实测 12）。周表格的行数取它，不要写死。 */
    suspend fun maxUnit(session: EamsSession): Int {
        val data = client.call(
            path = "/course/schedule/get-max-unit",
            secret = session.secret,
            token = session.token,
            params = mapOf("biz_type_id" to EamsProtocol.BIZ_TYPE_ID),
        )
        val obj = data as? JsonObject ?: return DEFAULT_MAX_UNIT
        return obj["max_unit"]?.jsonPrimitive?.intOrNull ?: DEFAULT_MAX_UNIT
    }

    /** 某一段日期里的全部课。服务端按次返回，单双周与调课已经拆好。 */
    suspend fun lessonsBetween(
        session: EamsSession,
        semesterId: String,
        start: LocalDate,
        end: LocalDate,
    ): List<EamsLesson> =
        EamsParsers.lessons(
            client.call(
                path = "/student/course/schedule/get-course-tables",
                secret = session.secret,
                token = session.token,
                params = mapOf(
                    "biz_type_id" to EamsProtocol.BIZ_TYPE_ID,
                    "semester_id" to semesterId,
                    "start_date" to start.toString(),
                    "end_date" to end.toString(),
                ),
            ),
        )

    /** 某一周（周一起七天）的课。 */
    suspend fun weekLessons(
        session: EamsSession,
        semesterId: String,
        weekStart: LocalDate,
    ): List<EamsLesson> = lessonsBetween(session, semesterId, weekStart, weekStart.plusDays(6))

    /** 当天有哪些课。给今日摘要与将来的上课提醒用，不依赖界面。 */
    suspend fun lessonsOn(
        session: EamsSession,
        semesterId: String,
        date: LocalDate,
    ): List<EamsLesson> = lessonsBetween(session, semesterId, date, date).on(date)

    private companion object {
        const val DEFAULT_MAX_UNIT = 12
    }
}
