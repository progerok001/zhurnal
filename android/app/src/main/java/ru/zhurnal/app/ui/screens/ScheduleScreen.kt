package ru.zhurnal.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Room
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zhurnal.app.data.ScheduleEntryDto
import ru.zhurnal.app.data.ScheduleGroupDto
import ru.zhurnal.app.data.ScheduleResponse
import ru.zhurnal.app.data.ScheduleTimeDto
import ru.zhurnal.app.data.ScheduleTimeEntryRequest
import ru.zhurnal.app.data.SubjectDto
import ru.zhurnal.app.data.ZhurnalRepository
import ru.zhurnal.app.ui.DayFullNames
import ru.zhurnal.app.ui.DayShortNames
import ru.zhurnal.app.ui.PairTime
import ru.zhurnal.app.ui.ScheduleColorOptions
import ru.zhurnal.app.ui.ScheduleViewModel
import ru.zhurnal.app.ui.defaultPairTime
import ru.zhurnal.app.ui.effectivePairTime
import ru.zhurnal.app.ui.mondayOf
import ru.zhurnal.app.ui.shortClock
import ru.zhurnal.app.ui.weekMonthLabel
import ru.zhurnal.app.ui.weekOffsetTitle
import ru.zhurnal.app.ui.weekRangeLabel
import ru.zhurnal.app.ui.weekTypeFor
import ru.zhurnal.app.ui.weekTypeHint
import ru.zhurnal.app.ui.weekTypeTitleFor
import ru.zhurnal.app.ui.components.EmptyState
import ru.zhurnal.app.ui.components.ErrorBanner
import ru.zhurnal.app.ui.components.JournalCard
import ru.zhurnal.app.ui.components.JournalField
import ru.zhurnal.app.ui.components.JournalTopBar
import ru.zhurnal.app.ui.components.LoadingContent
import ru.zhurnal.app.ui.components.PrimaryButton
import ru.zhurnal.app.ui.components.RoundIconButton
import ru.zhurnal.app.ui.components.ScreenBackground
import ru.zhurnal.app.ui.components.SecondaryButton
import java.time.LocalDate
import java.util.Locale

private val TimeColumnWidth: Dp = 58.dp
private val CellWidth: Dp = 102.dp
private val CellHeight: Dp = 118.dp
private val CellGap: Dp = 3.dp
private val HeaderHeight: Dp = 48.dp
private val Days = listOf(1, 2, 3, 4, 5, 6)

private data class EditorTarget(
    val day: Int,
    val pairNumber: Int,
    val entry: ScheduleEntryDto? = null,
)

@Composable
fun ScheduleScreen(
    repository: ZhurnalRepository,
    ownGroupId: Int?,
    onBack: () -> Unit,
    onOpenHomework: (groupId: Int, subjectId: Int, lessonDate: String) -> Unit,
    onOpenTop: (groupId: Int) -> Unit,
) {
    val vm: ScheduleViewModel = viewModel(
        factory = ScheduleViewModel.factory(repository, ownGroupId),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val data = state.data

    var editMode by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    var timesDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ScheduleEntryDto?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    val canEdit = data?.canEdit == true
    LaunchedEffect(canEdit) {
        if (!canEdit) editMode = false
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            JournalTopBar(
                title = "Расписание",
                subtitle = "Пары и домашние задания",
                onBack = onBack,
                actions = {
                    if (canEdit) {
                        RoundIconButton(
                            icon = if (editMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                            contentDescription = if (editMode) {
                                "Выйти из режима правки"
                            } else {
                                "Режим правки расписания"
                            },
                            tint = if (editMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            onClick = { editMode = !editMode },
                        )
                    }
                    if (canEdit && editMode) {
                        RoundIconButton(
                            icon = Icons.Rounded.Schedule,
                            contentDescription = "Время пар по дням",
                            onClick = { timesDialog = true },
                        )
                    }
                    if (data?.canViewHomework == true && state.selectedGroupId != null) {
                        RoundIconButton(
                            icon = Icons.Rounded.EmojiEvents,
                            contentDescription = "Топ по домашним заданиям",
                            onClick = { onOpenTop(state.selectedGroupId!!) },
                        )
                    }
                    RoundIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Обновить расписание",
                        onClick = { vm.refresh() },
                    )
                },
            )

            if (state.refreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = Color.Transparent,
                )
            }

            WeekIsland(
                weekStart = state.weekStart,
                onShiftWeek = { vm.shiftWeek(it) },
                onToday = { vm.goToToday() },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            if (state.groups.size > 1) {
                GroupSelector(
                    groups = state.groups,
                    selectedId = state.selectedGroupId,
                    ownGroupId = ownGroupId,
                    onSelect = { vm.selectGroup(it) },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            when {
                state.loading && data == null -> LoadingContent(Modifier.weight(1f))
                data == null -> Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    ErrorBanner(message = state.error ?: "Не удалось загрузить расписание")
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        text = "Повторить",
                        icon = Icons.Rounded.Refresh,
                        onClick = { vm.refresh() },
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (actionError != null) {
                        item {
                            ErrorBanner(
                                message = actionError!!,
                                onDismiss = { actionError = null },
                            )
                        }
                    }
                    if (state.error != null) {
                        item {
                            ErrorBanner(
                                message = state.error!!,
                                onDismiss = { vm.refresh() },
                            )
                        }
                    }
                    if (canEdit && editMode) {
                        item {
                            EditModeBanner(
                                groupName = data.group.name,
                                onOpenTimes = { timesDialog = true },
                                onDone = { editMode = false },
                            )
                        }
                    }
                    if (!data.isOwnGroup) {
                        item {
                            HintBanner(
                                text = "Это расписание группы «${data.group.name}». " +
                                    "Домашние задания видны только участникам группы.",
                            )
                        }
                    }

                    item {
                        ScheduleTable(
                            data = data,
                            weekStart = state.weekStart,
                            editing = canEdit && editMode,
                            onOpenHomework = onOpenHomework,
                            onEdit = { day, pair, entry ->
                                editor = EditorTarget(day, pair, entry)
                            },
                            onAdd = { day, pair -> editor = EditorTarget(day, pair) },
                        )
                    }

                    item {
                        JournalCard(padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = "Верхняя неделя — нечётная неделя месяца, нижняя — чётная. " +
                                        "Стрелками выше можно листать недели вперёд и назад.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (canEdit) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Расписание ведёт администратор. Включите режим правки " +
                                        "карандашом сверху: появятся «+» в пустых ячейках и кнопка " +
                                        "«Время пар» для настройки звонков по дням.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (data.canViewHomework) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Нажмите на предмет, чтобы открыть его домашние задания. " +
                                        "У пары из двух предметов каждая половина открывает своё задание.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (data.entries.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.CalendarMonth,
                                title = "Пока пусто",
                                text = if (canEdit) {
                                    "В расписании этой группы ещё нет пар. Включите режим правки и нажмите «+» в нужной ячейке."
                                } else {
                                    "Расписание этой группы ещё не заполнено."
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val target = editor
    if (target != null) {
        ScheduleEditorDialog(
            target = target,
            subjects = state.subjects,
            saving = state.saving,
            onDismiss = { editor = null },
            onSave = { subjectId, day, pair, weekType, room, color, note, secondSubjectId, secondRoom, done ->
                vm.saveEntry(
                    existing = target.entry,
                    subjectId = subjectId,
                    day = day,
                    pairNumber = pair,
                    weekType = weekType,
                    room = room,
                    color = color,
                    note = note,
                    secondSubjectId = secondSubjectId,
                    secondRoom = secondRoom,
                    onDone = done,
                )
            },
            onDelete = target.entry?.let { entry ->
                { editor = null; pendingDelete = entry }
            },
        )
    }

    if (timesDialog && data != null) {
        TimesDialog(
            days = if (data.entries.any { it.day == 7 }) (1..7).toList() else Days,
            times = data.times,
            saving = state.saving,
            onDismiss = { timesDialog = false },
            onSave = { day, entries, done ->
                vm.saveTimes(day, entries, done)
            },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить пару?") },
            text = {
                Text(
                    "«${toDelete.subjectName}»" +
                        (toDelete.secondSubjectName?.let { " / $it" } ?: "") +
                        ", ${DayFullNames.getOrElse(toDelete.day - 1) { "" }} · " +
                        "${toDelete.pairNumber}-я пара. Действие можно отменить только вручную.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val entry = toDelete
                        pendingDelete = null
                        vm.deleteEntry(entry.id) { error -> actionError = error }
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun WeekIsland(
    weekStart: LocalDate,
    onShiftWeek: (Long) -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val isCurrent = weekStart == mondayOf(today)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeekNavButton(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = "Прошлая неделя",
                    onClick = { onShiftWeek(-1) },
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = weekRangeLabel(weekStart),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "${weekTypeTitleFor(weekStart)} · ${weekMonthLabel(weekStart)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                WeekNavButton(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = "Следующая неделя",
                    onClick = { onShiftWeek(1) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = weekOffsetTitle(weekStart, today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = weekTypeHint(weekTypeFor(weekStart)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isCurrent) {
                    TextButton(
                        onClick = onToday,
                        modifier = Modifier.height(32.dp).padding(start = 4.dp),
                    ) {
                        Text("Сегодня", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun EditModeBanner(
    groupName: String,
    onOpenTimes: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Режим правки · $groupName",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Изменения сохраняются сразу. Нажмите «+» в пустой ячейке или карандаш у пары.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    onClick = onOpenTimes,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Время пар", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    onClick = onDone,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Готово", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupSelector(
    groups: List<ScheduleGroupDto>,
    selectedId: Int?,
    ownGroupId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.forEach { group ->
            val selected = group.id == selectedId
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                onClick = { onSelect(group.id) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (group.id == ownGroupId) "★ ${group.name}" else group.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleTable(
    data: ScheduleResponse,
    weekStart: LocalDate,
    editing: Boolean,
    onOpenHomework: (groupId: Int, subjectId: Int, lessonDate: String) -> Unit,
    onEdit: (day: Int, pair: Int, entry: ScheduleEntryDto) -> Unit,
    onAdd: (day: Int, pair: Int) -> Unit,
) {
    val horizontal = rememberScrollState()
    val bySlot = remember(data.entries) {
        data.entries.associateBy { it.day to it.pairNumber }
    }
    val today = remember { LocalDate.now() }
    val days = remember(data.entries) {
        if (data.entries.any { it.day == 7 }) (1..7).toList() else Days
    }

    JournalCard(padding = 8.dp) {
        Row {
            Box(
                modifier = Modifier
                    .width(TimeColumnWidth)
                    .height(HeaderHeight)
                    .padding(CellGap),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Время",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.horizontalScroll(horizontal)) {
                days.forEach { day ->
                    val date = weekStart.plusDays((day - 1).toLong())
                    val isToday = date == today
                    Box(
                        modifier = Modifier
                            .width(CellWidth)
                            .height(HeaderHeight)
                            .padding(CellGap)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isToday) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = DayShortNames[day - 1],
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            )
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Row {
            Column(Modifier.width(TimeColumnWidth)) {
                for (pair in 1..8) {
                    TimeCell(pair = pair, time = defaultPairTime(pair))
                }
            }
            Column(Modifier.horizontalScroll(horizontal)) {
                for (pair in 1..8) {
                    Row {
                        days.forEach { day ->
                            val entry = bySlot[day to pair]
                            val date = weekStart.plusDays((day - 1).toLong())
                            val override = data.times.any {
                                it.day == day && it.pairNumber == pair
                            }
                            val timeLabel = if (override) {
                                effectivePairTime(data.times, day, pair).label
                            } else {
                                null
                            }
                            Box(
                                modifier = Modifier
                                    .width(CellWidth)
                                    .height(CellHeight)
                                    .padding(CellGap),
                            ) {
                                if (entry != null) {
                                    EntryCell(
                                        entry = entry,
                                        editing = editing,
                                        canViewHomework = data.canViewHomework,
                                        timeLabel = timeLabel,
                                        onOpenFirst = {
                                            onOpenHomework(data.group.id, entry.subjectId, date.toString())
                                        },
                                        onOpenSecond = {
                                            entry.secondSubjectId?.let { secondId ->
                                                onOpenHomework(data.group.id, secondId, date.toString())
                                            }
                                        },
                                        onEdit = { onEdit(day, pair, entry) },
                                    )
                                } else {
                                    EmptyCell(
                                        canEdit = editing,
                                        timeLabel = timeLabel,
                                        onAdd = { onAdd(day, pair) },
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
private fun TimeCell(pair: Int, time: PairTime) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CellHeight)
            .padding(CellGap)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = pair.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = shortClock(time.start),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .width(14.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Text(
                text = shortClock(time.end),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryCell(
    entry: ScheduleEntryDto,
    editing: Boolean,
    canViewHomework: Boolean,
    timeLabel: String?,
    onOpenFirst: () -> Unit,
    onOpenSecond: () -> Unit,
    onEdit: () -> Unit,
) {
    val accent = entry.color.toScheduleColor()
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        color = accent?.copy(alpha = 0.15f) ?: MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            accent?.copy(alpha = 0.55f) ?: MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box {
            if (accent != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(accent),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (accent != null) 9.dp else 6.dp,
                        end = 4.dp,
                        top = 4.dp,
                        bottom = 4.dp,
                    ),
            ) {
                if (timeLabel != null) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        color = accent ?: MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        modifier = Modifier.padding(
                            end = if (editing) 14.dp else 0.dp,
                            bottom = 1.dp,
                        ),
                    )
                }
                if (entry.split) {
                    SplitHalf(
                        subjectName = entry.subjectName,
                        teacherName = entry.teacherName,
                        room = entry.room,
                        homeworkCount = entry.homeworkCount,
                        canViewHomework = canViewHomework,
                        onClick = onOpenFirst,
                        modifier = Modifier.weight(1f),
                    )
                    SplitDivider()
                    SplitHalf(
                        subjectName = entry.secondSubjectName.orEmpty(),
                        teacherName = entry.secondTeacherName,
                        room = entry.secondRoom,
                        homeworkCount = entry.secondHomeworkCount,
                        canViewHomework = canViewHomework,
                        onClick = onOpenSecond,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    FullEntryContent(
                        entry = entry,
                        canViewHomework = canViewHomework,
                        onClick = onOpenFirst,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (editing) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Изменить пару",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FullEntryContent(
    entry: ScheduleEntryDto,
    canViewHomework: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = canViewHomework, onClick = onClick),
    ) {
        Text(
            text = entry.subjectName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        entry.teacherName?.takeIf { it.isNotBlank() }?.let { teacher ->
            Text(
                text = teacher,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        entry.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        RoomRow(room = entry.room, homeworkCount = entry.homeworkCount, showHomework = canViewHomework)
    }
}

@Composable
private fun SplitHalf(
    subjectName: String,
    teacherName: String?,
    room: String?,
    homeworkCount: Int,
    canViewHomework: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = canViewHomework, onClick = onClick)
            .padding(vertical = 1.dp),
    ) {
        Text(
            text = subjectName,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        teacherName?.takeIf { it.isNotBlank() }?.let { teacher ->
            Text(
                text = teacher,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        RoomRow(room = room, homeworkCount = homeworkCount, showHomework = canViewHomework)
    }
}

@Composable
private fun RoomRow(room: String?, homeworkCount: Int, showHomework: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        room?.takeIf { it.isNotBlank() }?.let { value ->
            Icon(
                imageVector = Icons.Rounded.Room,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } ?: Spacer(Modifier.weight(1f))
        if (showHomework && homeworkCount > 0) {
            Icon(
                imageVector = Icons.Rounded.Assignment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = homeworkCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun SplitDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            text = "45′",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 3.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
private fun EmptyCell(canEdit: Boolean, timeLabel: String?, onAdd: () -> Unit) {
    if (canEdit) {
        Surface(
            modifier = Modifier.fillMaxSize().clickable(onClick = onAdd),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (timeLabel != null) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Добавить пару",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {}
    }
}

@Composable
private fun HintBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(9.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ScheduleEditorDialog(
    target: EditorTarget,
    subjects: List<SubjectDto>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        subjectId: Int,
        day: Int,
        pair: Int,
        weekType: String,
        room: String?,
        color: String?,
        note: String?,
        secondSubjectId: Int?,
        secondRoom: String?,
        done: (String?) -> Unit,
    ) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var subjectId by remember {
        mutableStateOf(target.entry?.subjectId ?: subjects.firstOrNull()?.id)
    }
    var day by remember { mutableStateOf(target.day) }
    var pair by remember { mutableStateOf(target.pairNumber) }
    var weekType by remember { mutableStateOf(target.entry?.weekType ?: "all") }
    var room by remember { mutableStateOf(target.entry?.room.orEmpty()) }
    var note by remember { mutableStateOf(target.entry?.note.orEmpty()) }
    var color by remember { mutableStateOf(target.entry?.color) }
    var split by remember { mutableStateOf(target.entry?.split == true) }
    var secondSubjectId by remember { mutableStateOf(target.entry?.secondSubjectId) }
    var secondRoom by remember { mutableStateOf(target.entry?.secondRoom.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(subjects) {
        if (subjectId == null) subjectId = subjects.firstOrNull()?.id
        if (split && secondSubjectId == null) {
            secondSubjectId = subjects.firstOrNull { it.id != subjectId }?.id
        }
    }

    val selectedSubject = subjects.firstOrNull { it.id == subjectId }
    val selectedSecond = subjects.firstOrNull { it.id == secondSubjectId }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(max = 720.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (target.entry == null) "Новая пара" else "Пара в расписании",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = "${DayFullNames.getOrElse(day - 1) { "" }} · " +
                                    "$pair-я пара · ${defaultPairTime(pair).label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { if (!saving) onDismiss() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    SubjectPicker(
                        label = "ПРЕДМЕТ",
                        subjects = subjects,
                        selectedId = subjectId,
                        onSelect = { subjectId = it },
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "ДЕНЬ НЕДЕЛИ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val dayOptions = if (day == 7) (1..7).toList() else Days
                    dayOptions.chunked(4).forEach { dayRow ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            dayRow.forEach { index ->
                                ChoiceChip(
                                    text = DayShortNames[index - 1],
                                    selected = day == index,
                                    onClick = { day = index },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(4 - dayRow.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "НОМЕР ПАРЫ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    listOf(1..4, 5..8).forEach { range ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            range.forEach { index ->
                                ChoiceChip(
                                    text = index.toString(),
                                    selected = pair == index,
                                    onClick = { pair = index },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                    }

                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = "НЕДЕЛЯ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(
                            "all" to "Каждую",
                            "upper" to "Верхняя",
                            "lower" to "Нижняя",
                        ).forEach { option ->
                            ChoiceChip(
                                text = option.second,
                                selected = weekType == option.first,
                                onClick = { weekType = option.first },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Разделить на два предмета",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "Квадрат пары разделится по середине: две половины по 45 минут.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                            Switch(
                                checked = split,
                                onCheckedChange = { checked ->
                                    split = checked
                                    if (checked) {
                                        secondSubjectId = subjects.firstOrNull {
                                            it.id != subjectId
                                        }?.id
                                    }
                                },
                            )
                        }
                    }

                    if (split) {
                        Spacer(Modifier.height(12.dp))
                        SubjectPicker(
                            label = "ВТОРОЙ ПРЕДМЕТ · ВТОРАЯ ПОЛОВИНА",
                            subjects = subjects,
                            selectedId = secondSubjectId,
                            onSelect = { secondSubjectId = it },
                        )
                        Spacer(Modifier.height(10.dp))
                        JournalField(
                            value = secondRoom,
                            onValueChange = { secondRoom = it.take(40) },
                            label = "Аудитория второй половины",
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    JournalField(
                        value = room,
                        onValueChange = { room = it.take(40) },
                        label = "Аудитория",
                    )
                    Spacer(Modifier.height(10.dp))
                    JournalField(
                        value = note,
                        onValueChange = { note = it.take(200) },
                        label = "Заметка (необязательно)",
                        singleLine = false,
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "ЦВЕТ ПАРЫ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScheduleColorOptions.forEach { option ->
                            val optionColor = option.hex.toScheduleColor()
                            val selected = color == option.hex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        optionColor
                                            ?: MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    .clickable { color = option.hex },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (optionColor == null) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = ScheduleColorOptions.firstOrNull { it.hex == color }?.title
                            ?: "Цвет не выбран",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        ErrorBanner(message = error!!)
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SecondaryButton(
                            text = "Отмена",
                            onClick = { if (!saving) onDismiss() },
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = "Сохранить",
                            loading = saving,
                            enabled = selectedSubject != null && (!split || selectedSecond != null),
                            onClick = {
                                val first = subjectId
                                if (first == null) {
                                    error = "Выберите предмет"
                                    return@PrimaryButton
                                }
                                if (split) {
                                    val second = secondSubjectId
                                    if (second == null) {
                                        error = "Выберите второй предмет"
                                        return@PrimaryButton
                                    }
                                    if (second == first) {
                                        error = "Вторая половина должна быть другим предметом"
                                        return@PrimaryButton
                                    }
                                }
                                error = null
                                onSave(
                                    first,
                                    day,
                                    pair,
                                    weekType,
                                    room.trim().ifEmpty { null },
                                    color,
                                    note.trim().ifEmpty { null },
                                    if (split) secondSubjectId else null,
                                    if (split) secondRoom.trim().ifEmpty { null } else null,
                                ) { message ->
                                    if (message == null) onDismiss() else error = message
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (onDelete != null) {
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { if (!saving) onDelete() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("Удалить пару", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (subjects.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Предметы создаёт администратор в разделе управления. " +
                                "Пока их нет, пару добавить нельзя.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectPicker(
    label: String,
    subjects: List<SubjectDto>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val selected = subjects.firstOrNull { it.id == selectedId }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Box {
        Surface(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            onClick = { menu = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected?.name
                        ?: if (subjects.isEmpty()) {
                            "В группе ещё нет предметов"
                        } else {
                            "Выберите предмет"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            subjects.forEach { subject ->
                DropdownMenuItem(
                    text = { Text(subject.name) },
                    onClick = {
                        onSelect(subject.id)
                        menu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TimesDialog(
    days: List<Int>,
    times: List<ScheduleTimeDto>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (day: Int, entries: List<ScheduleTimeEntryRequest>, done: (String?) -> Unit) -> Unit,
) {
    var day by remember { mutableStateOf(days.firstOrNull() ?: 1) }
    var starts by remember { mutableStateOf(List(8) { "" }) }
    var ends by remember { mutableStateOf(List(8) { "" }) }
    var resetDay by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun fill(target: Int, useDefaults: Boolean) {
        starts = (1..8).map { pair ->
            val time = if (useDefaults) defaultPairTime(pair) else effectivePairTime(times, target, pair)
            shortClock(time.start)
        }
        ends = (1..8).map { pair ->
            val time = if (useDefaults) defaultPairTime(pair) else effectivePairTime(times, target, pair)
            shortClock(time.end)
        }
    }

    LaunchedEffect(day) {
        resetDay = false
        fill(day, useDefaults = false)
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(max = 720.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Время пар", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                text = "Своё время для каждого дня — например, короткая суббота.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { if (!saving) onDismiss() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        days.forEach { index ->
                            ChoiceChip(
                                text = DayShortNames[index - 1],
                                selected = day == index,
                                onClick = { day = index },
                                modifier = Modifier.width(44.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    for (pair in 1..8) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = pair.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(18.dp),
                            )
                            CompactTimeField(
                                value = starts.getOrElse(pair - 1) { "" },
                                label = "Начало",
                                onValueChange = { value ->
                                    starts = starts.toMutableList().also { it[pair - 1] = value }
                                    resetDay = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CompactTimeField(
                                value = ends.getOrElse(pair - 1) { "" },
                                label = "Конец",
                                onValueChange = { value ->
                                    ends = ends.toMutableList().also { it[pair - 1] = value }
                                    resetDay = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    Text(
                        text = "Пустые поля означают, что для пары используется обычное время.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (error != null) {
                        Spacer(Modifier.height(10.dp))
                        ErrorBanner(message = error!!)
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SecondaryButton(
                            text = "Обычное время",
                            onClick = {
                                resetDay = true
                                fill(day, useDefaults = true)
                                error = null
                            },
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = "Сохранить",
                            loading = saving,
                            onClick = {
                                if (resetDay) {
                                    error = null
                                    onSave(day, emptyList()) { message ->
                                        if (message == null) onDismiss() else error = message
                                    }
                                    return@PrimaryButton
                                }
                                val entries = mutableListOf<ScheduleTimeEntryRequest>()
                                var problem: String? = null
                                for (pair in 1..8) {
                                    val rawStart = starts.getOrElse(pair - 1) { "" }.trim()
                                    val rawEnd = ends.getOrElse(pair - 1) { "" }.trim()
                                    if (rawStart.isEmpty() && rawEnd.isEmpty()) continue
                                    if (rawStart.isEmpty() || rawEnd.isEmpty()) {
                                        problem = "Заполните начало и конец для $pair-й пары"
                                        break
                                    }
                                    val start = normalizeClock(rawStart)
                                    val end = normalizeClock(rawEnd)
                                    if (start == null || end == null) {
                                        problem = "Время в формате ЧЧ:ММ, например 9:40"
                                        break
                                    }
                                    if (start >= end) {
                                        problem = "У $pair-й пары начало должно быть раньше конца"
                                        break
                                    }
                                    entries += ScheduleTimeEntryRequest(pair, start, end)
                                }
                                if (problem != null) {
                                    error = problem
                                    return@PrimaryButton
                                }
                                error = null
                                onSave(day, entries) { message ->
                                    if (message == null) onDismiss() else error = message
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "В таблице слева показано обычное время, а в ячейках — время этого дня, " +
                            "если оно отличается.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTimeField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(5)) },
        modifier = modifier.height(56.dp),
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** «9:5» и «09:05» → «09:05», иначе null. */
private fun normalizeClock(raw: String): String? {
    val parts = raw.trim().replace('.', ':').split(':')
    if (parts.size != 2) return null
    val hours = parts[0].trim().toIntOrNull() ?: return null
    val minutes = parts[1].trim().toIntOrNull() ?: return null
    if (hours !in 0..23 || minutes !in 0..59) return null
    return String.format(Locale.US, "%02d:%02d", hours, minutes)
}

@Composable
private fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(11.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        onClick = onClick,
        enabled = enabled,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private fun String?.toScheduleColor(): Color? = this?.let { hex ->
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
}
