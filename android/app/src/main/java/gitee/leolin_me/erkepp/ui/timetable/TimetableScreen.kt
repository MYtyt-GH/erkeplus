package gitee.leolin_me.erkepp.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import gitee.leolin_me.erkepp.data.eams.EamsSession
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
 * 三条硬规则：
 * 1. **缓存先上屏**：学期与课程都缓存，所以没登录、没网络也能看到上次抓的课表，
 *    并在顶部写清「显示的是几点抓的」；
 * 2. **联网只是刷新**：刷新失败不影响已显示的缓存；刷新成功才覆盖；
 * 3. **清凭据不等于清课表**：用户清掉教务凭据后，缓存的课表仍然留着。
 *
 * 实现上有个坑值得记一笔：加载函数一律把「学期/会话」当参数传进去，
 * 不要读 `semester` / `session` 这两个 state。Compose 的 `LaunchedEffect` 拿到的是
 * 启动那一刻的闭包，登录成功后立刻在里面读 `semester` 还是旧值（null），
 * 表现就是「登录完成但一直转圈、切一次周才出现」（2026-11-15 真机踩到）。
 */
@Composable
fun TimetableScreen(
    repository: EamsRepository,
    sessionStore: EamsSessionStore,
    cache: TimetableCache,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf(sessionStore.load()) }
    var semester by remember { mutableStateOf(cache.loadSemester()) }
    var semestersLoaded by remember { mutableStateOf(false) }
    var maxUnit by remember { mutableIntStateOf(DEFAULT_UNIT) }
    var week by remember { mutableIntStateOf(0) }
    var lessons by remember { mutableStateOf<List<EamsLesson>>(emptyList()) }
    var fetchedAt by remember { mutableLongStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<EamsLesson?>(null) }

    val today = remember { LocalDate.now() }
    val currentWeek = semester?.let { SemesterCalendar.weekOf(it, today) } ?: 0

    fun applyCache(current: EamsSemester, target: Int) {
        val cached = cache.load(current.id, SemesterCalendar.weekStart(current, target)) ?: return
        lessons = cached.lessons
        fetchedAt = cached.fetchedAt
    }

    suspend fun refreshWeek(active: EamsSession, current: EamsSemester, target: Int) {
        val start = SemesterCalendar.weekStart(current, target)
        applyCache(current, target)
        loading = true
        error = null
        try {
            val fresh = repository.weekLessons(active, current.id, start)
            // 连着点两周时，先发的请求可能后回来。周次已经变了就丢掉这次结果，
            // 否则会把上一周的课画到这一周的格子里（表现为「这一周 0 次课」）。
            if (week != target) return
            val saved = cache.save(current.id, start, fresh, System.currentTimeMillis())
            lessons = fresh
            fetchedAt = saved.fetchedAt
        } catch (failure: Throwable) {
            if (failure is EamsException && failure.tokenInvalid) {
                sessionStore.clear()
                session = null
                semestersLoaded = false
                showLogin = true
                loginError = "登录已过期，请重新输入教务账号"
            }
            error = failure.eamsMessage()
        } finally {
            loading = false
        }
    }

    suspend fun refreshAll(active: EamsSession) {
        loading = true
        error = null
        try {
            val list = repository.semesters(active)
            val current = list.current() ?: throw EamsException("no_semester", "教务没有返回学期信息")
            semester = current
            cache.saveSemester(current)
            semestersLoaded = true
            maxUnit = runCatching { repository.maxUnit(active) }
                .getOrDefault(DEFAULT_UNIT)
                .coerceAtLeast(MIN_UNIT)
            val target = if (week > 0) week else SemesterCalendar.weekOf(current, today)
            week = target
            applyCache(current, target)
            refreshWeek(active, current, target)
        } catch (failure: Throwable) {
            if (failure is EamsException && failure.tokenInvalid) {
                sessionStore.clear()
                session = null
                semestersLoaded = false
                showLogin = true
                loginError = "登录已过期，请重新输入教务账号"
            }
            error = failure.eamsMessage()
            loading = false
        }
    }

    // 进页面：先用缓存的学期把周次定下来并把课表铺上（离线也能看）
    LaunchedEffect(Unit) {
        val cached = semester
        if (cached != null) {
            val target = SemesterCalendar.weekOf(cached, today)
            week = target
            applyCache(cached, target)
        } else if (session == null) {
            // 既没缓存也没凭据：只能先登录
            showLogin = true
        }
    }

    // 有会话就联网刷新一次；登录成功后 semestersLoaded 会被重置，这里会再跑
    LaunchedEffect(session) {
        val active = session ?: return@LaunchedEffect
        if (semestersLoaded) return@LaunchedEffect
        refreshAll(active)
    }

    fun goWeek(target: Int) {
        val current = semester ?: return
        if (!SemesterCalendar.hasWeek(current, target)) return
        week = target
        applyCache(current, target)
        val active = session ?: return
        scope.launch { refreshWeek(active, current, target) }
    }

    fun refreshNow() {
        val active = session
        if (active == null) {
            showLogin = true
            return
        }
        scope.launch {
            val current = semester
            if (current == null || !semestersLoaded) {
                refreshAll(active)
            } else {
                refreshWeek(active, current, week.coerceAtLeast(1))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Header(
            semester = semester,
            week = week,
            fetchedAt = fetchedAt,
            loading = loading,
            onBack = onBack,
            onUpdate = { refreshNow() },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            // 状态变量是 delegated property，不能直接 smart cast；先落成本地值再用
            val shownSemester = semester
            if (shownSemester != null && week > 0) {
                Summary(
                    semester = shownSemester,
                    week = week,
                    lessons = lessons,
                    today = today,
                    currentWeek = currentWeek,
                )
                WeekBar(
                    week = week,
                    currentWeek = currentWeek,
                    maxWeek = SemesterCalendar.weekCount(shownSemester),
                    onPrev = { goWeek(week - 1) },
                    onNext = { goWeek(week + 1) },
                    onThisWeek = { goWeek(currentWeek) },
                )
            }

            if (session == null && shownSemester != null) {
                Text(
                    text = "未登录教务：下面是本地缓存的课表，点上面的「更新」登录后可刷新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
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
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            when {
                shownSemester == null && session == null ->
                    EmptyHint("还没有登录教务。点上面的「更新」，输入学号和密码")
                shownSemester == null -> EmptyHint("正在读取学期信息…")
                lessons.isEmpty() && loading -> EmptyHint("正在加载课表…")
                lessons.isEmpty() -> EmptyHint("这一周没有课，或者还没有缓存")
                else -> WeekGrid(
                    weekStart = SemesterCalendar.weekStart(shownSemester, week.coerceAtLeast(1)),
                    lessons = lessons,
                    maxUnit = maxUnit,
                    today = today,
                    onOpen = { detail = it },
                )
            }

            Spacer(Modifier.height(16.dp))

            if (sessionStore.remembered()) {
                Spacer(Modifier.height(8.dp))
                // 清凭据不清课表：缓存留着，离线还能看
                TextButton(
                    onClick = {
                        sessionStore.clear()
                        session = null
                        semestersLoaded = false
                        error = "已清除登录凭据；课表来自本地缓存，点「更新」可重新登录"
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
                        semestersLoaded = false
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
        TextButton(onClick = onThisWeek, enabled = currentWeek > 0 && week != currentWeek) { Text("本周") }
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
    // 一屏放不下 7 天就横向滑：宁可滑，也不要把课程名和教室压成一两个字。
    // 左侧固定一列节次，右边整块（表头 + 网格）共用一个横向滚动状态，保证列对齐。
    val columnWidth = 112.dp
    val gutterWidth = 30.dp
    val rowHeight = 74.dp
    val headerHeight = 38.dp
    val hScroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左上角留白，和表头对齐
            Spacer(Modifier.width(gutterWidth).height(headerHeight))
            Box(modifier = Modifier.weight(1f).horizontalScroll(hScroll)) {
                Row {
                    repeat(7) { index ->
                        val date = weekStart.plusDays(index.toLong())
                        val isToday = date == today
                        Column(
                            modifier = Modifier.width(columnWidth).height(headerHeight),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "${WEEK_LABELS[index]} ${date.dayOfMonth}",
                                style = MaterialTheme.typography.bodyMedium,
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
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            // 节次栏
            Column(modifier = Modifier.width(gutterWidth)) {
                repeat(maxUnit) { index ->
                    Box(modifier = Modifier.height(rowHeight), contentAlignment = Alignment.TopCenter) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).horizontalScroll(hScroll)) {
                BoxWithConstraints(
                    modifier = Modifier
                        .width(columnWidth * 7f)
                        .height(rowHeight * maxUnit.toFloat()),
                ) {
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
                                .width(columnWidth * 7f)
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
                                    x = columnWidth * day.toFloat() + 2.dp,
                                    y = rowHeight * start.toFloat() + 2.dp,
                                )
                                .width(columnWidth - 4.dp)
                                .height(rowHeight * span.toFloat() - 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isToday) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                )
                                .clickable { onOpen(lesson) }
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = lesson.courseName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = if (span >= 2) 4 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // 教室一定要看得见：优先「楼 + 房间」，没有就退回地址
                                val place = lesson.rooms.firstOrNull()
                                    ?.let { it.display.ifBlank { it.address } }
                                    .orEmpty()
                                if (place.isNotEmpty()) {
                                    Text(
                                        text = place,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (span >= 2 && lesson.startTime.isNotEmpty()) {
                                    Text(
                                        text = lesson.startTime,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "左右滑动看完整一周；点课程块看教师与上课时间。这一周 ${lessons.size} 次课",
            style = MaterialTheme.typography.labelMedium,
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
                Text(
                    text = "初始密码可能是身份证号后六位；改过密码的话用新密码。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
