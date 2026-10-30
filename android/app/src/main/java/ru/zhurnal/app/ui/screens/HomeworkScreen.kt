package ru.zhurnal.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zhurnal.app.data.HomeworkDto
import ru.zhurnal.app.data.ZhurnalRepository
import ru.zhurnal.app.ui.HomeworkViewModel
import ru.zhurnal.app.ui.components.Avatar
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
import ru.zhurnal.app.ui.formatDateLong
import ru.zhurnal.app.ui.formatDateShort
import ru.zhurnal.app.ui.formatDateTime
import ru.zhurnal.app.ui.formatWeekday

@Composable
fun HomeworkScreen(
    repository: ZhurnalRepository,
    groupId: Int,
    subjectId: Int,
    lessonDate: String? = null,
    onBack: () -> Unit,
    onOpenTop: (Int) -> Unit,
) {
    val vm: HomeworkViewModel = viewModel(
        factory = HomeworkViewModel.factory(repository, groupId, subjectId, lessonDate),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val data = state.data

    var editor by remember { mutableStateOf<HomeworkDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HomeworkDto?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            JournalTopBar(
                title = "Домашнее задание",
                subtitle = data?.subject?.name ?: "Загрузка…",
                onBack = onBack,
                actions = {
                    RoundIconButton(
                        icon = Icons.Rounded.EmojiEvents,
                        contentDescription = "Топ по домашним заданиям",
                        onClick = { onOpenTop(groupId) },
                    )
                    RoundIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Обновить",
                        onClick = { vm.load(refresh = true) },
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

            when {
                state.loading && data == null -> LoadingContent(Modifier.weight(1f))
                data == null -> Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    ErrorBanner(message = state.error ?: "Не удалось загрузить задания")
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        text = "Повторить",
                        icon = Icons.Rounded.Refresh,
                        onClick = { vm.load() },
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                onDismiss = { vm.load(refresh = true) },
                            )
                        }
                    }

                    item {
                        SubjectHeader(
                            subjectName = data.subject.name,
                            teacherName = data.subject.teacherName,
                            groupName = data.group.name,
                            lessonDate = lessonDate,
                            total = data.items.size,
                        )
                    }

                    if (data.canPost) {
                        item {
                            PrimaryButton(
                                text = "Записать домашнее задание",
                                icon = Icons.Rounded.Assignment,
                                onClick = { creating = true },
                            )
                        }
                    }

                    if (data.items.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.Assignment,
                                title = "Заданий пока нет",
                                text = if (lessonDate != null) {
                                    "На этот день заданий пока нет. Запишите первое — его увидят все в группе."
                                } else {
                                    "Запишите первое домашнее задание — его увидят все в группе."
                                },
                            )
                        }
                    } else {
                        items(data.items, key = { it.id }) { item ->
                            HomeworkCard(
                                item = item,
                                showOwnerActions = item.canEdit,
                                onEdit = { editor = item },
                                onDelete = { pendingDelete = item },
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Автор задания указывается под текстом. " +
                                "Редактировать и удалять запись может автор, староста, преподаватель или администратор.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 4.dp,
                                end = 4.dp,
                                top = 4.dp,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        HomeworkEditorDialog(
            title = "Новое задание",
            subtitle = buildString {
                append(data?.subject?.name.orEmpty())
                lessonDate?.let { append(" · на ${formatDateLong(it)}") }
            },
            initialText = "",
            initialDueDate = null,
            saving = state.saving,
            onDismiss = { creating = false },
            onSave = { text, dueDate, done ->
                vm.add(text, dueDate) { error ->
                    if (error == null) creating = false else done(error)
                }
            },
        )
    }

    val editing = editor
    if (editing != null) {
        HomeworkEditorDialog(
            title = "Изменить задание",
            subtitle = buildString {
                append(data?.subject?.name.orEmpty())
                (editing.lessonDate ?: lessonDate)?.let { append(" · на ${formatDateLong(it)}") }
            },
            initialText = editing.text,
            initialDueDate = editing.dueDate,
            saving = state.saving,
            onDismiss = { editor = null },
            onSave = { text, dueDate, done ->
                vm.update(editing.id, text, dueDate) { error ->
                    if (error == null) editor = null else done(error)
                }
            },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить задание?") },
            text = { Text("Запись исчезнет для всей группы. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val item = toDelete
                        pendingDelete = null
                        vm.delete(item.id) { error -> actionError = error }
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
private fun SubjectHeader(
    subjectName: String,
    teacherName: String?,
    groupName: String,
    lessonDate: String?,
    total: Int,
) {
    JournalCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Assignment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append(groupName)
                        if (!teacherName.isNullOrBlank()) append(" · $teacherName")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (lessonDate != null) {
            Text(
                text = "Занятие: ${formatWeekday(lessonDate)}, ${formatDateLong(lessonDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text = if (total == 0) {
                "Домашних заданий пока нет"
            } else {
                "$total ${pluralHomework(total)} на этот день"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeworkCard(
    item: HomeworkDto,
    showOwnerActions: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    JournalCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Avatar(
                fullName = item.authorName,
                avatar = item.authorAvatar,
                size = 34.dp,
            )
            Spacer(Modifier.size(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                item.lessonDate?.takeIf { it.isNotBlank() }?.let { date ->
                    Text(
                        text = "на ${formatDateLong(date)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = authorLine(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = buildString {
                        append(formatDateTime(item.createdAt))
                        if (item.updatedAt.isNotBlank() && item.updatedAt != item.createdAt) {
                            append(" · изменено")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.dueDate?.takeIf { it.isNotBlank() }?.let { due ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                        ),
                    ) {
                        Text(
                            text = "Сдать до ${formatDateShort(due)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            if (showOwnerActions) {
                Column {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Изменить",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Удалить",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun authorLine(item: HomeworkDto): String {
    val role = when (item.authorRole) {
        "headman" -> " · староста"
        "teacher" -> " · преподаватель"
        "admin" -> " · администратор"
        else -> " · обучающийся"
    }
    return "— ${item.authorName}$role"
}

private fun pluralHomework(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "задание"
        mod10 in 2..4 && mod100 !in 12..14 -> "задания"
        else -> "заданий"
    }
}

@Composable
private fun HomeworkEditorDialog(
    title: String,
    subtitle: String,
    initialText: String,
    initialDueDate: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (text: String, dueDate: String?, done: (String?) -> Unit) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var dueDate by remember { mutableStateOf(initialDueDate.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

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
                        .heightIn(max = 680.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.headlineSmall)
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { if (!saving) onDismiss() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    JournalField(
                        value = text,
                        onValueChange = { text = it.take(4000) },
                        label = "Домашнее задание",
                        singleLine = false,
                        modifier = Modifier.heightIn(min = 130.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    JournalField(
                        value = dueDate,
                        onValueChange = { dueDate = it.take(10) },
                        label = "Сдать до (необязательно)",
                        supporting = "Формат: 2026-11-05",
                    )
                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        ErrorBanner(message = error!!)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SecondaryButton(
                            text = "Отмена",
                            onClick = { if (!saving) onDismiss() },
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = "Сохранить",
                            loading = saving,
                            enabled = text.trim().length >= 3,
                            onClick = {
                                error = null
                                onSave(
                                    text.trim(),
                                    dueDate.trim().ifEmpty { null },
                                ) { message -> error = message }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            text = "Под заданием появится ваше имя — его увидят все в группе.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
