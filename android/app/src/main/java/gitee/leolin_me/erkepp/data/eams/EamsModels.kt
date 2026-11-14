package gitee.leolin_me.erkepp.data.eams

import java.time.LocalDate

/** 教务登录后的会话：`token` 放请求头，`secret` 是后面所有请求的签名密钥。 */
data class EamsSession(
    val token: String,
    val secret: String,
    val userCode: String = "",
    val userName: String = "",
)

/**
 * `/verify-code` 的结果。`enable=false` 表示当前不需要验证码（2026-11-15 实测就是 false）。
 * `key` 只在需要验证码时用于 `/login-with-verify-code`。
 */
data class EamsVerifyCode(
    val enable: Boolean,
    val key: String,
)

/**
 * 一个学期。`startDate` 是第 1 周的周一（`weekStartDay=1`），开学日期可以直接和周次互算。
 * 实测样本：`340 / 2026-2027 / 1 / 秋 / 2026-08-31 ~ 2027-01-17`。
 */
data class EamsSemester(
    val id: String,
    val year: String,
    val name: String,
    val season: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val weekStartDay: Int,
) {
    /** 「2026-2027 第 1 学期」，界面上直接用。 */
    val label: String get() = "$year 第 $name 学期"
}

/** `/common/get-semester` 的结果。`currentId` 是服务端认为的当前学期。 */
data class EamsSemesterList(
    val currentId: String,
    val semesters: List<EamsSemester>,
) {
    fun current(): EamsSemester? =
        semesters.firstOrNull { it.id == currentId } ?: semesters.firstOrNull()
}

data class EamsRoom(
    val id: Long,
    val name: String,
    val building: String,
    val address: String,
) {
    /** 界面上优先显示「明理北楼 明理北103」这种具体位置，空则退回 name/address。 */
    val display: String
        get() = listOf(building, name).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { address }
}

data class EamsTeacher(
    val id: Long,
    val name: String,
)

/**
 * 一次课（不是一门课）。
 *
 * 服务端按「日期 + 节次」展开成扁平数组：单双周、调课都会拆成多条，
 * 同 `lessonId` 可能出现多次。`startTime`/`endTime` 是钟点，客户端不用自己算节次时间表。
 */
data class EamsLesson(
    val courseCode: String,
    val courseName: String,
    val date: LocalDate,
    val startUnit: Int,
    val endUnit: Int,
    val startTime: String,
    val endTime: String,
    val lessonId: Long,
    val lessonNo: String,
    val rooms: List<EamsRoom>,
    val teachers: List<EamsTeacher>,
    /** 「5,6,7,…」原始周次列表，仅作展示参考。 */
    val weeks: String,
    /** 「5-17」这种压缩写法，官方页面用它当小字。 */
    val weekstate: String,
    val studentCount: Int,
) {
    val roomNames: String get() = rooms.joinToString("、") { it.display }
    val teacherNames: String get() = teachers.joinToString("、") { it.name }
}

/** 一周的缓存：课程 + 拉取时间（毫秒）。`fetchedAt` 用来在界面上写「更新于 …」。 */
data class TimetableWeek(
    val lessons: List<EamsLesson>,
    val fetchedAt: Long,
)
