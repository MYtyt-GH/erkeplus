package gitee.leolin_me.erkepp.ui.timetable

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.eams.EamsException
import gitee.leolin_me.erkepp.data.eams.EamsLesson
import gitee.leolin_me.erkepp.data.eams.EamsRepository
import gitee.leolin_me.erkepp.data.eams.EamsSemester
import gitee.leolin_me.erkepp.data.eams.EamsSemesterList
import gitee.leolin_me.erkepp.data.eams.EamsSessionStore
import gitee.leolin_me.erkepp.data.eams.SemesterCalendar
import gitee.leolin_me.erkepp.data.eams.TimetableCache
import gitee.leolin_me.erkepp.data.eams.on
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 「我的课表」：教务课表的原生化，数据来自 `jwglyd`（设计与取舍见 docs/design/timetable.md）。
 *
 * 这一页自己管三件事：
 * 1. 凭据：进来先读 [EamsSessionStore]；没有就弹登录表单，登录成功后按用户的选择决定存不存；
 * 2. 数据：按「学期 + 周」取，先用 [TimetableCache] 的缓存铺屏，再联网刷新；
 * 3. 兜底：联网失败保留缓存并写明「显示的是几点抓的课表」，绝不把空结果当真课表。
 *
 * 周表格用固定行高 + 绝对定位画课程块：一次课跨 `startUnit..endUnit` 行。
 * 单双周、连堂课不用额外处理——服务端已经把每次课拆成独立记录。
 */
@Composable
fun TimetableScreen(
    repository: EamsRepository,
    sessionStore: EamsSessionStore,
    cache: TimetableCache,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf(sessionStore.load()) }
    var semesterList by remember { mutableStateOf<EamsSemesterList?>(null) }
    var maxUnit by remember { mutableIntStateOf(DEFAULT_UNIT) }
    var week by remember { mutableIntStateOf(0) }
    var lessons by remember { mutableStateOf<List<EamsLesson>>(emptyList()) }
    var fetchedAt by remember { mutableLongStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showLogin by remember { mutableStateOf(session == null) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<EamsLesson?>(null) }

    val today = remember { LocalDate.now() }
    val semester = semesterList?.current()
    val currentWeek = semester?.let { SemesterCalendar.weekOf(it, today) } ?: 0

    suspend fun loadWeek(target: Int) {
        val current = semester ?: return
        val start = SemesterCalendar.weekStart(current, target)
        // 先上缓存：进页面立刻有内容，不转圈
        cache.load(current.id, start)?.let {
            lessons = it.lessons
            fetchedAt = it.fetchedAt
        }
        val active = session ?: return
        loading = true
        error = null
        try {
            val fresh = repository.weekLessons(active, current.id, start)
            val saved = cache.save(current.id, start, fresh, System.currentTimeMillis())
            lessons = fresh
            fetchedAt = saved.fetchedAt
        } catch (failure: Throwable) {
            if (failure is EamsException && failure.tokenInvalid) {
                // token 过期：清掉「记住」的凭据，回到登录表单
                sessionStore.clear()
                session = null
                showLogin = true
                loginError = "登录已过期，请重新输入教务账号"
            }
            error = failure.eamsMessage()
        } finally {
            loading = false
        }
    }

    // 有会话就把学期、每天节数、当前周拉齐，再取这一周
    LaunchedEffect(session) {
        val active = session ?: return@LaunchedEffect
        if (semesterList != null) return@LaunchedEffect
        loading = true
        error = null
        try {
            val list = repository.semesters(active)
            semesterList = list
            maxUnit = runCatching { repository.maxUnit(active) }
                .getOrDefault(DEFAULT_UNIT)
                .coerceAtLeast(MIN_UNIT)
            val target = list.current()?.let { SemesterCalendar.weekOf(it, today) } ?: 1
            week = target
            loadWeek(target)
        } catch (failure: Throwable) {
            if (failure is EamsException && failure.tokenInvalid) {
                sessionStore.clear()
                session = null
                showLogin = true
            }
            error = failure.eamsMessage()
            loading = false
        }
    }

    fun goWeek(target: Int) {
        val current = semester ?: return
        if (!SemesterCalendar.hasWeek(current, target)) return
        week = target
        scope.launch { loadWeek(target) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Header(
            semester = semester,
            week = week,
            fetchedAt = fetchedAt,
            loading = loading,
            onBack = onBack,
            onUpdate = { if (session == null) showLogin = true else scope.launch { loadWeek(week) } },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            if (semester != null && week > 0) {
                Summary(semester = semester, week = week, lessons = lessons, today = today, currentWeek = currentWeek)
                WeekBar(
                    week = week,
                    currentWeek = currentWeek,
                    maxWeek = SemesterCalendar.weekCount(semester),
                    onPrev = { goWeek(week - 1) },
                    onNext = { goWeek(week + 1) },
                    onThisWeek = { goWeek(currentWeek) },
                )
            }

            error?.let { message ->
                Text(
                    text = if (fetchedAt > 0) {
                        "$message；下面显示的是 ${formatTime(fetchedAt)} 抓的课表"
                    } else {
                        message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            when {
                session == null -> EmptyHint("还没有登录教务。点上面的「更新」，输入学号和密码")
                semester == null -> EmptyHint("正在读取学期信息…")
                lessons.isEmpty() && !loading -> EmptyHint(
                    if (error == null) "这一周没有课" else "暂时拿不到课表",
                )
                else -> WeekGrid(
                    weekStart = SemesterCalendar.weekStart(semester, week.coerceAtLeast(1)),
                    lessons = lessons,
                    maxUnit = maxUnit,
                    today = today,
                    onOpen = { detail = it },
                )
            }

            Spacer(Modifier.height(16.dp))
            PlaceholderRow("成绩查询") { Toast.makeText(context, "成绩查询还没做，敬请期待", Toast.LENGTH_SHORT).show() }
            PlaceholderRow("考试安排") { Toast.makeText(context, "考试安排还没做，敬请期待", Toast.LENGTH_SHORT).show() }

            if (sessionStore.remembered()) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        sessionStore.clear()
                        session = null
                        showLogin = true
                    },
                ) { Text("清除记住的登录凭据") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLogin) {
        EamsLoginDialog(
            initialError = loginError,
            onDismiss = {
                showLogin = false
                loginError = null
            },
            onLogin = { userCode, password, remember, reportError, done ->
                scope.launch {
                    try {
                        val fresh = repository.login(userCode, password)
                        if (remember) sessionStore.save(fresh) else sessionStore.clear()
                        // 换账号：把上一份学期与课表清干净，让 LaunchedEffect 重新拉
                        semesterList = null
                        lessons = emptyList()
                        fetchedAt = 0L
                        error = null
                        loginError = null
                        session = fresh
                        showLogin = false
                        done()
                    } catch (failure: Throwable) {
                        reportError(failure.eamsMessage())
                    }
                }
            },
        )
    }

    detail?.let { lesson -> LessonDialog(lesson = lesson, onDismiss = { detail = null }) }
}

@Composable
private fun Header(
    semester: EamsSemester?,
    week: Int,
    fetchedAt: Long,
    loading: Boolean,
    onBack: () -> Unit,
    onUpdate: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "我的课表",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            TextButton(onClick = onUpdate) { Text("更新") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    if (week > 0) append("第 $week 周")
                    semester?.let {
                        if (isNotEmpty()) append("  ·  ")
                        append(it.label)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (fetchedAt > 0) {
                Text(
                    text = "更新于 ${formatTime(fetchedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Summary(
    semester: EamsSemester,
    week: Int,
    lessons: List<EamsLesson>,
    today: LocalDate,
    currentWeek: Int,
) {
    val weekStart = SemesterCalendar.weekStart(semester, week)
    val text = if (week == currentWeek) {
        val mine = lessons.on(today)
        if (mine.isEmpty()) {
            "今天没有课"
        } else {
            val first = mine.first()
            "今天 ${mine.size} 节课 · ${first.startTime} ${first.courseName}"
        }
    } else {
        "${weekStart.format(MONTH_DAY)} ~ ${weekStart.plusDays(6).format(MONTH_DAY)}"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun WeekBar(
    week: Int,
    currentWeek: Int,
    maxWeek: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = onPrev, enabled = week > 1) { Text("上一周") }
        TextButton(onClick = onThisWeek, enabled = week != currentWeek) { Text("本周") }
        TextButton(onClick = onNext, enabled = week < maxWeek) { Text("下一周") }
        Spacer(Modifier.weight(1f))
        Text(
            text = "共 $maxWeek 周",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekGrid(
    weekStart: LocalDate,
    lessons: List<EamsLesson>,
    maxUnit: Int,
    today: LocalDate,
    onOpen: (EamsLesson) -> Unit,
) {
    val rowHeight = 52.dp
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(7) { index ->
                val date = weekStart.plusDays(index.toLong())
                val isToday = date == today
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = WEEK_LABELS[index],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(rowHeight * maxUnit.toFloat())) {
            val columnWidth = maxWidth / 7f
            repeat(8) { index ->
                Box(
                    modifier = Modifier
                        .offset(x = columnWidth * index.toFloat())
                        .width(1.dp)
                        .height(rowHeight * maxUnit.toFloat())
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            repeat(maxUnit + 1) { index ->
                Box(
                    modifier = Modifier
                        .offset(y = rowHeight * index.toFloat())
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            lessons.forEach { lesson ->
                val day = ChronoUnit.DAYS.between(weekStart, lesson.date).toInt()
                if (day !in 0..6) return@forEach
                val start = (lesson.startUnit - 1).coerceAtLeast(0)
                val span = (lesson.endUnit - lesson.startUnit + 1).coerceAtLeast(1)
                val isToday = lesson.date == today
                Box(
                    modifier = Modifier
                        .offset(
                            x = columnWidth * day.toFloat() + 1.dp,
                            y = rowHeight * start.toFloat() + 1.dp,
                        )
                        .width(columnWidth - 2.dp)
                        .height(rowHeight * span.toFloat() - 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isToday) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .clickable { onOpen(lesson) }
                        .padding(horizontal = 3.dp, vertical = 3.dp),
                ) {
                    Column {
                        Text(
                            text = lesson.courseName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = if (span >= 2) 3 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (lesson.roomNames.isNotEmpty()) {
                            Text(
                                text = lesson.roomNames,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "点课程块看教师与上课时间；这一周 ${lessons.size} 次课",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun PlaceholderRow(title: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "敬请期待",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 登录表单：两个输入框就够（官方现在不开验证码；将来开了，在同一个弹窗里加一行图形码即可）。
 *
 * 密码只在这个弹窗和一次登录请求里活着，不落盘；勾「记住」时存的是登录后的凭据（见 ADR 0005）。
 */
@Composable
private fun EamsLoginDialog(
    initialError: String?,
    onDismiss: () -> Unit,
    onLogin: (
        userCode: String,
        password: String,
        remember: Boolean,
        reportError: (String) -> Unit,
        done: () -> Unit,
    ) -> Unit,
) {
    var userCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(initialError) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录教务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "只用于查询课表，不会上传到任何第三方服务器。" +
                        "不勾「记住」的话，密码只用这一次；勾了也只保存登录凭据，不保存密码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = userCode,
                    onValueChange = { userCode = it.trim() },
                    label = { Text("教务账号（学号）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { visible = !visible }) {
                            Text(if (visible) "隐藏" else "显示", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { remember = !remember }
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = if (remember) "☑" else "☐",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(26.dp).padding(start = 4.dp),
                    )
                    Text("记住，下次直接看课表", style = MaterialTheme.typography.bodySmall)
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting && userCode.isNotBlank() && password.isNotBlank(),
                onClick = {
                    submitting = true
                    error = null
                    onLogin(
                        userCode,
                        password,
                        remember,
                        { message ->
                            submitting = false
                            error = message
                        },
                    ) { submitting = false }
                },
            ) { Text(if (submitting) "登录中…" else "登录") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun LessonDialog(lesson: EamsLesson, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lesson.courseName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${lesson.startTime} - ${lesson.endTime}（第 ${lesson.startUnit}-${lesson.endUnit} 节）")
                if (lesson.teacherNames.isNotEmpty()) Text("教师：${lesson.teacherNames}")
                if (lesson.roomNames.isNotEmpty()) Text("地点：${lesson.roomNames}")
                Text("周次：${lesson.weekstate}")
                Text(
                    text = "课程编号 ${lesson.lessonNo}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

private fun Throwable.eamsMessage(): String = when (this) {
    is EamsException -> when {
        tokenInvalid -> "登录已过期，请重新输入教务账号"
        code.startsWith("http_") -> "教务系统暂时连不上（HTTP ${code.removePrefix("http_")}）"
        message.isNullOrBlank() -> "教务拒绝了这次请求（$code）"
        else -> message!!
    }
    is java.io.IOException -> "网络连接失败，请检查网络后重试"
    else -> message?.takeIf { it.isNotBlank() } ?: "出了点问题，请稍后重试"
}

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US))

private const val DEFAULT_UNIT = 12
private const val MIN_UNIT = 8
private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private val MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd", Locale.US)
