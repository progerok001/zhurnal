package ru.zhurnal.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zhurnal.app.data.JournalResponse
import ru.zhurnal.app.data.LessonDto
import ru.zhurnal.app.data.RecordDto
import ru.zhurnal.app.data.StudentStatsDto
import ru.zhurnal.app.data.SubjectDto
import ru.zhurnal.app.data.SummaryResponse
import ru.zhurnal.app.data.SummaryStudentDto
import ru.zhurnal.app.data.UserDto
import ru.zhurnal.app.data.ZhurnalRepository
import ru.zhurnal.app.ui.JournalViewModel
import ru.zhurnal.app.ui.components.Avatar
import ru.zhurnal.app.ui.components.EmptyState
import ru.zhurnal.app.ui.components.ErrorBanner
import ru.zhurnal.app.ui.components.JournalCard
import ru.zhurnal.app.ui.components.JournalField
import ru.zhurnal.app.ui.components.JournalTopBar
import ru.zhurnal.app.ui.components.LoadingContent
import ru.zhurnal.app.ui.components.Pill
import ru.zhurnal.app.ui.components.PrimaryButton
import ru.zhurnal.app.ui.components.RoundIconButton
import ru.zhurnal.app.ui.components.ScreenBackground
import ru.zhurnal.app.ui.components.SecondaryButton
import ru.zhurnal.app.ui.components.SoftDivider
import ru.zhurnal.app.ui.components.StatTile
import ru.zhurnal.app.ui.formatAverage
import ru.zhurnal.app.ui.formatDateLong
import ru.zhurnal.app.ui.formatDateShort
import ru.zhurnal.app.ui.formatWeekday
import ru.zhurnal.app.ui.pluralPairs
import ru.zhurnal.app.ui.pluralStudents
import ru.zhurnal.app.ui.pluralSubjects
import ru.zhurnal.app.ui.todayIso
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val NAME_COLUMN_WIDTH = 150.dp
private val HEADER_HEIGHT = 58.dp
private val ROW_HEIGHT = 56.dp
private val CELL_WIDTH = 54.dp

private data class CellTarget(
    val lesson: LessonDto,
    val student: StudentStatsDto,
    val record: RecordDto?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    groupId: Int,
    user: UserDto,
    repository: ZhurnalRepository,
    onBack: () -> Unit,
) {
    val vm: JournalViewModel = viewModel(
        key = "journal-$groupId",
        factory = JournalViewModel.factory(repository, groupId),
    )
    val state by vm.state.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(0) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var cellTarget by remember { mutableStateOf<CellTarget?>(null) }
    var showAddLesson by remember { mutableStateOf(false) }
    var editingLesson by remember { mutableStateOf<LessonDto?>(null) }

    val journal = state.journal
    val subject = journal?.subject
    val canEdit = journal?.canEdit == true
    val records = remember(journal?.records) {
        journal?.records?.associateBy { it.lessonId to it.studentId } ?: emptyMap()
    }

    LaunchedEffect(subject?.id, tab) {
        if (tab == 1 && journal?.subjects?.isNotEmpty() == true) {
            vm.loadSummary()
        }
    }

    ScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                if (!fullscreen) {
                    JournalTopBar(
                        title = journal?.group?.name ?: "Журнал",
                        subtitle = subject?.teacherName?.let { "Преподаватель: $it" }
                            ?: "Загрузка журнала",
                        onBack = onBack,
                        actions = {
                            RoundIconButton(
                                icon = Icons.Rounded.Fullscreen,
                                contentDescription = "На весь экран",
                                onClick = {
                                    tab = 0
                                    fullscreen = true
                                },
                            )
                            RoundIconButton(
                                icon = Icons.Rounded.Refresh,
                                contentDescription = "Обновить",
                                onClick = {
                                    vm.load(silent = true)
                                    if (tab == 1) vm.loadSummary()
                                },
                            )
                        },
                    )
                }

                if (state.refreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = Color.Transparent,
                    )
                }

                when {
                    state.loading && journal == null -> LoadingContent(Modifier.weight(1f))
                    journal == null -> Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column {
                            ErrorBanner(
                                message = state.error ?: "Не удалось загрузить журнал",
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                SecondaryButton(
                                    text = "Повторить",
                                    onClick = { vm.load() },
                                )
                            }
                        }
                    }
                    else -> {
                        if (state.error != null) {
                            ErrorBanner(
                                message = state.error!!,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                onDismiss = { vm.load(silent = true) },
                            )
                        }
                        JournalBody(
                            modifier = Modifier.weight(1f),
                            journal = journal,
                            user = user,
                            tab = tab,
                            onTabChange = { tab = it },
                            onSelectSubject = vm::selectSubject,
                            records = records,
                            canEdit = canEdit,
                            fullscreen = fullscreen,
                            onCellClick = { lesson, student, record ->
                                cellTarget = CellTarget(lesson, student, record)
                            },
                            onLessonClick = { editingLesson = it },
                            onAddLesson = { showAddLesson = true },
                            summaryState = state,
                            onRetrySummary = vm::loadSummary,
                        )
                    }
                }
            }

            if (canEdit && tab == 0 && subject != null) {
                ExtendedFloatingActionButton(
                    onClick = { showAddLesson = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(20.dp),
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Пара") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }

            if (fullscreen) {
                RoundIconButton(
                    icon = Icons.Rounded.FullscreenExit,
                    contentDescription = "Выйти из полноэкранного режима",
                    onClick = { fullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(10.dp),
                )
            }
        }
    }

    if (cellTarget != null) {
        val target = cellTarget!!
        RecordSheet(
            target = target,
            onDismiss = { cellTarget = null },
            onSave = { grade, absent, reason, done ->
                vm.saveRecord(
                    lessonId = target.lesson.id,
                    studentId = target.student.id,
                    grade = grade,
                    absent = absent,
                    reason = reason,
                    onDone = done,
                )
            },
        )
    }

    if (showAddLesson && subject != null) {
        LessonEditorDialog(
            initial = null,
            subject = subject,
            onDismiss = { showAddLesson = false },
            onSave = { date, pair, topic, done ->
                vm.addLesson(subject.id, date, pair, topic, done)
            },
            onDelete = null,
        )
    }

    if (editingLesson != null && subject != null) {
        val lesson = editingLesson!!
        LessonEditorDialog(
            initial = lesson,
            subject = subject,
            onDismiss = { editingLesson = null },
            onSave = { date, pair, topic, done ->
                vm.updateLesson(lesson.id, date, pair, topic, done)
            },
            onDelete = { done -> vm.deleteLesson(lesson.id, done) },
        )
    }
}

@Composable
private fun JournalBody(
    journal: JournalResponse,
    user: UserDto,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onSelectSubject: (Int) -> Unit,
    records: Map<Pair<Int, Int>, RecordDto>,
    canEdit: Boolean,
    fullscreen: Boolean,
    onCellClick: (LessonDto, StudentStatsDto, RecordDto?) -> Unit,
    onLessonClick: (LessonDto) -> Unit,
    onAddLesson: () -> Unit,
    summaryState: JournalViewModel.UiState,
    onRetrySummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val personal = journal.students.firstOrNull { it.id == user.id }
    val average = if (user.hasGroup) personal?.avg else journal.stats.average
    val absentPairs = if (user.hasGroup) personal?.absentPairs ?: 0 else journal.stats.absentPairs
    val absentHours = if (user.hasGroup) personal?.absentHours ?: 0 else journal.stats.absentPairs * 2
    val studentCount = journal.stats.studentsCount

    Column(modifier = modifier.fillMaxSize()) {
        if (journal.subjects.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Groups,
                title = "В группе нет предметов",
                text = if (user.isAdmin) {
                    "Добавьте предметы и назначьте преподавателей в разделе управления."
                } else {
                    "Администратор ещё не добавил предметы для этой группы."
                },
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        if (!fullscreen) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(journal.subjects, key = { it.id }) { subject ->
                    Pill(
                        text = subject.name,
                        selected = subject.id == journal.subject?.id,
                        onClick = { onSelectSubject(subject.id) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                StatTile(
                    label = if (user.hasGroup) "Мой балл" else "Средний балл",
                    value = formatAverage(average),
                    hint = if (user.hasGroup) "по предмету" else "по предмету",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Пропуски",
                    value = "$absentPairs",
                    hint = if (absentPairs == 0) {
                        "нет пропусков"
                    } else {
                        "$absentHours ч"
                    },
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.error,
                )
                StatTile(
                    label = "В группе",
                    value = "$studentCount",
                    hint = pluralStudents(studentCount),
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondary,
                )
            }

            Spacer(Modifier.height(14.dp))
            JournalTabs(
                tab = tab,
                onTabChange = onTabChange,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
        } else {
            Spacer(Modifier.height(44.dp))
        }

        if (tab == 0) {
            JournalTable(
                modifier = Modifier.weight(1f),
                journal = journal,
                records = records,
                currentUserId = if (user.hasGroup) user.id else null,
                canEdit = canEdit,
                onCellClick = onCellClick,
                onLessonClick = onLessonClick,
                onAddLesson = onAddLesson,
            )
        } else {
            SummaryContent(
                modifier = Modifier.weight(1f),
                summary = summaryState.summary,
                loading = summaryState.summaryLoading,
                error = summaryState.summaryError,
                user = user,
                onRetry = onRetrySummary,
            )
        }
    }
}

@Composable
private fun JournalTabs(
    tab: Int,
    onTabChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            JournalTab(
                text = "Журнал",
                selected = tab == 0,
                modifier = Modifier.weight(1f),
                onClick = { onTabChange(0) },
            )
            JournalTab(
                text = "Сводка",
                selected = tab == 1,
                modifier = Modifier.weight(1f),
                onClick = { onTabChange(1) },
            )
        }
    }
}

@Composable
private fun JournalTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun JournalTable(
    journal: JournalResponse,
    records: Map<Pair<Int, Int>, RecordDto>,
    currentUserId: Int?,
    canEdit: Boolean,
    onCellClick: (LessonDto, StudentStatsDto, RecordDto?) -> Unit,
    onLessonClick: (LessonDto) -> Unit,
    onAddLesson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hScroll = rememberScrollState()

    JournalCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = if (canEdit) 84.dp else 12.dp),
        padding = 0.dp,
    ) {
        when {
            journal.students.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.Groups,
                    title = "В группе нет обучающихся",
                    text = "Администратор может добавить их в разделе управления.",
                )
            }
            journal.lessons.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.CalendarMonth,
                    title = "Пар пока нет",
                    text = if (canEdit) {
                        "Добавьте первую пару — после этого можно выставлять оценки и отмечать пропуски."
                    } else {
                        "Преподаватель ещё не добавил пары по этому предмету."
                    },
                    action = if (canEdit) {
                        {
                            SecondaryButton(
                                text = "Добавить пару",
                                icon = Icons.Rounded.Add,
                                onClick = onAddLesson,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            else -> {
                Row(modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT)) {
                    Box(
                        modifier = Modifier
                            .width(NAME_COLUMN_WIDTH)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                            .padding(start = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "ОБУЧАЮЩИЙСЯ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(hScroll),
                    ) {
                        journal.lessons.forEach { lesson ->
                            LessonHeaderCell(
                                lesson = lesson,
                                canEdit = canEdit,
                                onClick = { onLessonClick(lesson) },
                            )
                        }
                    }
                }
                SoftDivider()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(modifier = Modifier.width(NAME_COLUMN_WIDTH)) {
                        journal.students.forEach { student ->
                            StudentNameCell(
                                student = student,
                                isMe = student.id == currentUserId,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(hScroll),
                    ) {
                        journal.students.forEach { student ->
                            Row {
                                journal.lessons.forEach { lesson ->
                                    val record = records[lesson.id to student.id]
                                    GradeCell(
                                        record = record,
                                        canEdit = canEdit,
                                        onClick = { onCellClick(lesson, student, record) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonHeaderCell(
    lesson: LessonDto,
    canEdit: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(CELL_WIDTH)
            .height(HEADER_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
            .clickable(enabled = canEdit, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = formatDateShort(lesson.date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = "${lesson.pairNumber} пара",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (!lesson.topic.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
            )
        }
    }
}

@Composable
private fun StudentNameCell(
    student: StudentStatsDto,
    isMe: Boolean,
) {
    Row(
        modifier = Modifier
            .width(NAME_COLUMN_WIDTH)
            .height(ROW_HEIGHT)
            .background(
                if (isMe) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                } else {
                    Color.Transparent
                },
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(fullName = student.fullName, avatar = student.avatar, size = 28.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            text = student.fullName,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
            fontWeight = if (isMe) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeCell(
    record: RecordDto?,
    canEdit: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val absent = record?.absent == true
    val grade = record?.grade
    val tint = when {
        absent -> MaterialTheme.colorScheme.error
        grade != null -> gradeColor(grade)
        else -> MaterialTheme.colorScheme.outline
    }
    val background = when {
        absent || grade != null -> tint.copy(alpha = 0.11f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .width(CELL_WIDTH)
            .height(ROW_HEIGHT)
            .padding(4.dp)
            .clip(shape)
            .background(background)
            .then(
                if (absent || grade != null) {
                    Modifier.border(1.dp, tint.copy(alpha = 0.25f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = canEdit, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            absent -> Text(
                text = "Н",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            grade != null -> Text(
                text = "$grade",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            canEdit -> Text(
                text = "+",
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
            )
            else -> Text(
                text = "·",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun SummaryContent(
    summary: SummaryResponse?,
    loading: Boolean,
    error: String?,
    user: UserDto,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        loading && summary == null -> LoadingContent(modifier)
        summary == null -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column {
                ErrorBanner(
                    message = error ?: "Сводка пока недоступна",
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SecondaryButton(text = "Повторить", onClick = onRetry)
                }
            }
        }
        summary.students.isEmpty() -> EmptyState(
            icon = Icons.Rounded.Groups,
            title = "Нет данных",
            text = "В группе пока нет обучающихся.",
            modifier = modifier,
        )
        else -> {
            val sorted = remember(summary, user.id) {
                summary.students.sortedWith(
                    compareByDescending<SummaryStudentDto> { it.id == user.id }
                        .thenByDescending { it.avg ?: -1.0 }
                        .thenBy { it.fullName },
                )
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = "Средний балл по всем предметам · ${summary.subjects.size} ${pluralSubjects(summary.subjects.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                if (error != null) {
                    item { ErrorBanner(message = error, onDismiss = onRetry) }
                }
                items(sorted, key = { it.id }) { student ->
                    SummaryStudentCard(
                        student = student,
                        subjects = summary.subjects,
                        isMe = student.id == user.id,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStudentCard(
    student: SummaryStudentDto,
    subjects: List<ru.zhurnal.app.data.SummarySubjectDto>,
    isMe: Boolean,
) {
    JournalCard(
        padding = 14.dp,
        borderColor = if (isMe) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(fullName = student.fullName, avatar = student.avatar, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.fullName + if (isMe) " · вы" else "",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("${student.gradesCount} отметок")
                        if (student.absentPairs > 0) {
                            append(" · ${student.absentPairs} ${pluralPairs(student.absentPairs)} пропущено")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (student.absentPairs > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatAverage(student.avg),
                    style = MaterialTheme.typography.headlineSmall,
                    color = averageColor(student.avg),
                )
                Text(
                    text = "средний",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (subjects.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                subjects.forEach { subject ->
                    val avg = student.subjectAverages
                        .firstOrNull { it.subjectId == subject.id }
                        ?.average
                    SubjectAverageChip(name = subject.name, average = avg)
                }
            }
        }
    }
}

@Composable
private fun SubjectAverageChip(name: String, average: Double?) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 74.dp, max = 128.dp)
                .padding(horizontal = 9.dp, vertical = 7.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatAverage(average),
                style = MaterialTheme.typography.titleSmall,
                color = averageColor(average),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordSheet(
    target: CellTarget,
    onDismiss: () -> Unit,
    onSave: (Int?, Boolean, String?, (String?) -> Unit) -> Unit,
) {
    var grade by remember { mutableStateOf(target.record?.grade) }
    var absent by remember { mutableStateOf(target.record?.absent == true) }
    var reason by remember { mutableStateOf(target.record?.reason.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save(clear: Boolean = false) {
        if (saving) return
        if (!clear && grade == null && !absent) return
        saving = true
        error = null
        onSave(
            if (clear) null else grade,
            if (clear) false else absent,
            if (!clear && absent) reason.takeIf { it.isNotBlank() } else null,
        ) { message ->
            saving = false
            if (message == null) onDismiss() else error = message
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(
                            fullName = target.student.fullName,
                            avatar = target.student.avatar,
                            size = 42.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = target.student.fullName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${formatDateLong(target.lesson.date)} · ${target.lesson.pairNumber} пара",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { if (!saving) onDismiss() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                        }
                    }

                    SoftDivider()

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = "ОЦЕНКА",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(7.dp))
                        for (row in 0..1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                for (column in 1..5) {
                                    val value = row * 5 + column
                                    GradeChoice(
                                        grade = value,
                                        selected = grade == value,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            grade = value
                                            absent = false
                                            error = null
                                        },
                                    )
                                }
                            }
                            if (row == 0) Spacer(Modifier.height(6.dp))
                        }

                        Spacer(Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RemoveCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Отсутствовал",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = absent,
                                    onCheckedChange = { checked ->
                                        absent = checked
                                        if (checked) grade = null
                                        error = null
                                    },
                                )
                            }
                        }

                        if (absent) {
                            Spacer(Modifier.height(10.dp))
                            JournalField(
                                value = reason,
                                onValueChange = { reason = it },
                                label = "Причина (необязательно)",
                                leading = Icons.Rounded.EditNote,
                                singleLine = false,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                            )
                        }

                        if (error != null) {
                            Spacer(Modifier.height(10.dp))
                            ErrorBanner(message = error!!)
                        }
                    }

                    SoftDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PrimaryButton(
                            text = "Сохранить",
                            onClick = { save() },
                            loading = saving,
                            enabled = grade != null || absent,
                        )
                        if (target.record != null) {
                            TextButton(
                                onClick = { save(clear = true) },
                                enabled = !saving,
                            ) {
                                Text(
                                    text = "Очистить отметку",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeChoice(
    grade: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = gradeColor(grade)
    Surface(
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) color else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "$grade",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonEditorDialog(
    initial: LessonDto?,
    subject: SubjectDto,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, (String?) -> Unit) -> Unit,
    onDelete: (((String?) -> Unit) -> Unit)?,
) {
    var date by remember { mutableStateOf(initial?.date ?: todayIso()) }
    var pair by remember { mutableStateOf(initial?.pairNumber ?: 1) }
    var topic by remember { mutableStateOf(initial?.topic.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (initial == null) "Новая пара" else "Пара",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = subject.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { if (!saving) onDismiss() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "ДАТА",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatDateLong(date),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = formatWeekday(date),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "Изменить",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "ПАРА",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    for (row in 0..1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (column in 1..4) {
                                val value = row * 4 + column
                                Surface(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (pair == value) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    contentColor = if (pair == value) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    border = if (pair == value) {
                                        null
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    },
                                    onClick = { pair = value },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$value",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                }
                            }
                        }
                        if (row == 0) Spacer(Modifier.height(6.dp))
                    }

                    Spacer(Modifier.height(16.dp))
                    JournalField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = "Тема (необязательно)",
                        leading = Icons.Rounded.Schedule,
                        singleLine = false,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    )

                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        ErrorBanner(message = error!!)
                    }

                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(
                        text = "Сохранить",
                        loading = saving,
                        onClick = {
                            if (saving) return@PrimaryButton
                            if (topic.length > 200) {
                                error = "Тема не должна превышать 200 символов"
                                return@PrimaryButton
                            }
                            saving = true
                            error = null
                            onSave(date, pair, topic) { message ->
                                saving = false
                                if (message == null) onDismiss() else error = message
                            }
                        },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onDelete != null) {
                            TextButton(
                                onClick = { confirmDelete = true },
                                enabled = !saving,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text("Удалить", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { if (!saving) onDismiss() },
                            enabled = !saving,
                        ) {
                            Text("Отмена")
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = runCatching {
            LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("Готово")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Отмена")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить пару?") },
            text = { Text("Вместе с парой удалятся все оценки и отметки о пропусках.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        saving = true
                        error = null
                        onDelete { message ->
                            saving = false
                            if (message == null) onDismiss() else error = message
                        }
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun gradeColor(grade: Int): Color = when {
    grade >= 9 -> MaterialTheme.colorScheme.tertiary
    grade >= 7 -> MaterialTheme.colorScheme.primary
    grade >= 5 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun averageColor(average: Double?): Color = when {
    average == null -> MaterialTheme.colorScheme.onSurfaceVariant
    average >= 8.5 -> MaterialTheme.colorScheme.tertiary
    average >= 6.5 -> MaterialTheme.colorScheme.primary
    average >= 4.5 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}
