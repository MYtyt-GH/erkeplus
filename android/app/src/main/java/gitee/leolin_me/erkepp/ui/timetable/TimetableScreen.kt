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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    // 今天与周次在**组合时**就算好，不等 LaunchedEffect：
    // 否则会有一帧 week=0，界面按「第 1 周」把开学那周画出来，看着像「这周没有课」
    val today = remember { LocalDate.now() }
    var week by remember {
        mutableIntStateOf(semester?.let { SemesterCalendar.weekOf(it, today) } ?: 0)
    }
    var lessons by remember { mutableStateOf<List<EamsLesson>>(emptyList()) }
    var fetchedAt by remember { mutableLongStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<EamsLesson?>(null) }

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

    // 这一页是 7 列 × 12 行的密排表格，系统字号放到 1.5、2.0 时再跟着放大会把表格挤没。
    // 这里把字号上限压到 1.3 倍，其余页面仍然完全跟随系统设置（2026-11-15 真机在 2.0 下验证过：
    // 不压的话标题就把半屏吃完，课表只剩一行）。压到 1.3 后仍能靠竖向滚动照顾大字用户。
    val systemDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = systemDensity.density,
            fontScale = systemDensity.fontScale.coerceAtMost(MAX_FONT_SCALE),
        ),
    ) {
        // 整页不上下滚动：上半是固定的信息区，下半由课表占满，只有课表可以左右滑
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                semester = semester,
                week = week,
                fetchedAt = fetchedAt,
                loading = loading,
                onBack = onBack,
                onUpdate = { refreshNow() },
            )

        val shownSemester = semester
        if (shownSemester != null && week > 0) {
            WeekHeader(
                semester = shownSemester,
                week = week,
                currentWeek = currentWeek,
                lessons = lessons,
                today = today,
                canPrev = week > 1,
                canNext = week < SemesterCalendar.weekCount(shownSemester),
                onPrev = { goWeek(week - 1) },
                onNext = { goWeek(week + 1) },
                onThisWeek = { goWeek(currentWeek) },
            )
        }

        val hint = when {
            session == null && shownSemester != null -> "离线模式：显示的是上次更新的课表，点「更新」可重新登录并刷新"
            error != null && fetchedAt > 0 -> "$error；下面是 ${formatTime(fetchedAt)} 的课表"
            error != null -> error
            else -> null
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            )
        }

        if (shownSemester == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = when {
                        session == null -> "先登录教务，才能看到课表"
                        loading -> "正在读取学期信息…"
                        else -> "暂时读不到学期信息，稍后重试"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            TimetableGrid(
                weekStart = SemesterCalendar.weekStart(shownSemester, week.coerceAtLeast(1)),
                lessons = lessons,
                maxUnit = maxUnit,
                today = today,
                loading = loading,
                onOpen = { detail = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (lessons.isEmpty() && !loading) "这一周没有课" else "左右滑动看整周 · 点课程看详情",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (sessionStore.remembered()) {
                TextButton(
                    onClick = {
                        sessionStore.clear()
                        session = null
                        semestersLoaded = false
                        error = null
                    },
                ) { Text("退出教务登录", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
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
        Column(modifier = Modifier.fillMaxWidth()) {
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (fetchedAt > 0) {
                Text(
                    text = "更新于 ${formatTime(fetchedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WeekHeader(
    semester: EamsSemester,
    week: Int,
    currentWeek: Int,
    lessons: List<EamsLesson>,
    today: LocalDate,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisWeek: () -> Unit,
) {
    val weekStart = SemesterCalendar.weekStart(semester, week)
    val isThisWeek = week == currentWeek
    val summary = if (isThisWeek) {
        val mine = lessons.on(today)
        if (mine.isEmpty()) {
            "今天没有课"
        } else {
            "今天 ${mine.size} 节课 · ${mine.first().startTime} ${mine.first().courseName}"
        }
    } else {
        "${weekStart.format(MONTH_DAY)} ~ ${weekStart.plusDays(6).format(MONTH_DAY)}"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrev, enabled = canPrev) { Text("‹", fontSize = 22.sp) }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !isThisWeek) { onThisWeek() }
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isThisWeek) "第 $week 周 · 本周" else "第 $week 周",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = if (isThisWeek) summary else "$summary · 点这里回本周",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onNext, enabled = canNext) { Text("›", fontSize = 22.sp) }
    }
}

/**
 * 课表本体，按屏幕大小自适应。
 *
 * 用户手上的手机尺寸不一样，所以这里没有写死「一屏几行、每列多宽」：
 * - **列宽**取 `屏宽 / 7` 再夹到 96dp–168dp：宽屏（平板、横屏）7 天一次看全，
 *   窄屏就横向滑，一屏大约 3 天；
 * - **行高**取 `(可用高度 − 表头) / 每天节数`，12 节默认一屏装满；
 * - 但行高压到 34dp×字体缩放 以下时，字就放不下了 —— 这时**放弃「一屏装满」**，
 *   给行高一个下限并允许整块竖向滚动。宁可滚动，也不把三行字挤成一条线；
 * - 行高 < 46dp 视为紧凑模式：课程块只显示课程名 + 教室（教室名本身带楼宇前缀），
 *   节次栏只显示节次号。
 *
 * 左侧固定节次与上课时间，右侧表头与网格共用一个横向滚动状态，列始终对齐。
 */
@Composable
private fun TimetableGrid(
    weekStart: LocalDate,
    lessons: List<EamsLesson>,
    maxUnit: Int,
    today: LocalDate,
    loading: Boolean,
    onOpen: (EamsLesson) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    BoxWithConstraints(modifier = modifier) {
        val metrics = TimetableLayout.of(
            availableWidthDp = maxWidth.value,
            availableHeightDp = maxHeight.value,
            maxUnit = maxUnit,
            fontScale = fontScale,
        )
        val headerHeight = TimetableLayout.HEADER_DP.dp
        val gutterWidth = metrics.gutterWidthDp.dp
        val columnWidth = metrics.columnWidthDp.dp
        val rowHeight = metrics.rowHeightDp.dp
        val dense = metrics.dense
        val scrollVertically = metrics.mode == TimetableMode.SCROLL
        val ultraDense = rowHeight < 26.dp

        val table: @Composable () -> Unit = {
            Row(modifier = Modifier.fillMaxWidth()) {
                // 节次 + 上课时间
                Column(modifier = Modifier.width(gutterWidth)) {
                    Spacer(Modifier.height(headerHeight))
                    repeat(maxUnit) { index ->
                        val slot = LESSON_SLOTS.getOrNull(index)
                        Column(
                            modifier = Modifier.height(rowHeight).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (ultraDense) 9.sp else 10.sp,
                                ),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (slot != null && !ultraDense) {
                                Text(
                                    text = slot.start,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    // 表头：周几 + 日期
                    Row(modifier = Modifier.height(headerHeight).horizontalScroll(hScroll)) {
                        repeat(7) { index ->
                            val date = weekStart.plusDays(index.toLong())
                            val isToday = date == today
                            Box(
                                modifier = Modifier.width(columnWidth).fillMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${WEEK_LABELS[index]} ${date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
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

                    Box(
                        modifier = Modifier
                            .height(rowHeight * maxUnit.toFloat())
                            .horizontalScroll(hScroll),
                    ) {
                        Box(
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
                                            x = columnWidth * day.toFloat() + 1.5.dp,
                                            y = rowHeight * start.toFloat() + 1.dp,
                                        )
                                        .width(columnWidth - 3.dp)
                                        .height(rowHeight * span.toFloat() - 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isToday) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            },
                                        )
                                        .clickable { onOpen(lesson) }
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                ) {
                                    CourseBlock(lesson = lesson, span = span, dense = dense, ultraDense = ultraDense)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (scrollVertically) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(vScroll)) { table() }
        } else {
            Box(modifier = Modifier.fillMaxSize()) { table() }
        }

        if (lessons.isEmpty() && !loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "这一周没有课",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 课程块。屏幕上能塞几行就画几行：
 * 宽裕时「课程名 / 楼栋 / 房间号」三行（楼栋不带编号、房间号带编号）；
 * 行高压到紧凑时只画课程名 + 教室，再挤就只画课程名，剩下的点开看。
 */
@Composable
private fun CourseBlock(lesson: EamsLesson, span: Int, dense: Boolean, ultraDense: Boolean) {
    val room = lesson.rooms.firstOrNull()
    val place = room?.let { it.name.ifBlank { it.building } }.orEmpty()
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = lesson.courseName,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (ultraDense) 9.sp else 11.sp),
            fontWeight = FontWeight.Medium,
            maxLines = if (span >= 2) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!ultraDense && room != null) {
            if (!dense && room.building.isNotBlank()) {
                Text(
                    text = room.building,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (place.isNotEmpty()) {
                Text(
                    text = place,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (span >= 2) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!dense && span >= 2 && lesson.rooms.size > 1) {
                Text(
                    text = "等 ${lesson.rooms.size} 处",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 官方作息表（用户 2026-11-15 提供）：第 N 节的起止时间。 */
private data class LessonSlot(val start: String, val end: String)

private val LESSON_SLOTS = listOf(
    LessonSlot("08:00", "08:45"),
    LessonSlot("08:55", "09:40"),
    LessonSlot("10:00", "10:45"),
    LessonSlot("10:55", "11:40"),
    LessonSlot("14:00", "14:45"),
    LessonSlot("14:55", "15:40"),
    LessonSlot("16:00", "16:45"),
    LessonSlot("16:55", "17:40"),
    LessonSlot("17:50", "18:35"),
    LessonSlot("18:45", "19:30"),
    LessonSlot("19:40", "20:25"),
    LessonSlot("20:35", "21:20"),
)

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

/** 课表页字号缩放上限：超过这个值表格就摆不下了（见 TimetableScreen 里的说明）。 */
private const val MAX_FONT_SCALE = 1.3f
private val WEEK_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private val MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd", Locale.US)
