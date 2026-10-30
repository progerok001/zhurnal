package ru.zhurnal.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zhurnal.app.data.GroupDto
import ru.zhurnal.app.data.SubjectDto
import ru.zhurnal.app.data.UserDto
import ru.zhurnal.app.data.ZhurnalRepository
import ru.zhurnal.app.ui.AdminViewModel
import ru.zhurnal.app.ui.components.Avatar
import ru.zhurnal.app.ui.components.AvatarPicker
import ru.zhurnal.app.ui.components.EmptyState
import ru.zhurnal.app.ui.components.ErrorBanner
import ru.zhurnal.app.ui.components.JournalCard
import ru.zhurnal.app.ui.components.JournalField
import ru.zhurnal.app.ui.components.JournalTopBar
import ru.zhurnal.app.ui.components.LoadingContent
import ru.zhurnal.app.ui.components.Pill
import ru.zhurnal.app.ui.components.PrimaryButton
import ru.zhurnal.app.ui.components.RoleBadge
import ru.zhurnal.app.ui.components.RoundIconButton
import ru.zhurnal.app.ui.components.ScreenBackground
import ru.zhurnal.app.ui.components.SecondaryButton
import ru.zhurnal.app.ui.components.SoftDivider
import ru.zhurnal.app.ui.pluralPairs
import ru.zhurnal.app.ui.pluralStudents
import ru.zhurnal.app.ui.pluralSubjects

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    repository: ZhurnalRepository,
    onBack: () -> Unit,
    onAudit: () -> Unit,
) {
    val vm: AdminViewModel = viewModel(factory = AdminViewModel.factory(repository))
    val state by vm.state.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(0) }
    var subjectGroupFilter by rememberSaveable { mutableStateOf(0) }
    var userSearch by rememberSaveable { mutableStateOf("") }
    var userRoleFilter by rememberSaveable { mutableStateOf("all") }

    var showGroupDialog by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<GroupDto?>(null) }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var editSubject by remember { mutableStateOf<SubjectDto?>(null) }
    var showUserDialog by remember { mutableStateOf(false) }
    var resetUser by remember { mutableStateOf<UserDto?>(null) }
    var avatarUser by remember { mutableStateOf<UserDto?>(null) }
    var deleteGroup by remember { mutableStateOf<GroupDto?>(null) }
    var deleteSubject by remember { mutableStateOf<SubjectDto?>(null) }
    var deleteUser by remember { mutableStateOf<UserDto?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    val teachers = remember(state.users) { state.users.filter { it.isTeacher } }

    ScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                JournalTopBar(
                    title = "Управление",
                    subtitle = "Группы, предметы и аккаунты",
                    onBack = onBack,
                    actions = {
                        RoundIconButton(
                            icon = Icons.Rounded.History,
                            contentDescription = "Журнал аудита",
                            onClick = onAudit,
                        )
                        RoundIconButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "Обновить",
                            onClick = { vm.loadAll(silent = true) },
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

                AdminTabs(
                    tab = tab,
                    onTabChange = { tab = it },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )

                when {
                    state.loading -> LoadingContent(Modifier.weight(1f))
                    state.error != null && state.groups.isEmpty() && state.users.isEmpty() -> Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column {
                            ErrorBanner(
                                message = state.error!!,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                SecondaryButton(
                                    text = "Повторить",
                                    onClick = { vm.loadAll() },
                                )
                            }
                        }
                    }
                    else -> {
                        Column(modifier = Modifier.weight(1f)) {
                            if (state.error != null) {
                                ErrorBanner(
                                    message = state.error!!,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                    onDismiss = { vm.loadAll(silent = true) },
                                )
                            }
                            if (actionError != null) {
                                ErrorBanner(
                                    message = actionError!!,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                    onDismiss = { actionError = null },
                                )
                            }

                            when (tab) {
                                0 -> GroupsTab(
                                    groups = state.groups,
                                    onAdd = {
                                        editGroup = null
                                        showGroupDialog = true
                                    },
                                    onEdit = {
                                        editGroup = it
                                        showGroupDialog = true
                                    },
                                    onDelete = { deleteGroup = it },
                                    modifier = Modifier.weight(1f),
                                )
                                1 -> SubjectsTab(
                                    subjects = state.subjects,
                                    groups = state.groups,
                                    filterGroupId = subjectGroupFilter,
                                    onFilterChange = { subjectGroupFilter = it },
                                    onAdd = {
                                        editSubject = null
                                        showSubjectDialog = true
                                    },
                                    onEdit = {
                                        editSubject = it
                                        showSubjectDialog = true
                                    },
                                    onDelete = { deleteSubject = it },
                                    modifier = Modifier.weight(1f),
                                )
                                else -> UsersTab(
                                    users = state.users,
                                    search = userSearch,
                                    onSearchChange = { userSearch = it },
                                    roleFilter = userRoleFilter,
                                    onRoleFilterChange = { userRoleFilter = it },
                                    onResetPassword = { resetUser = it },
                                    onAvatar = { avatarUser = it },
                                    onToggleActive = { user ->
                                        actionError = null
                                        vm.setUserActive(user.id, !user.isActive) { message ->
                                            if (message != null) actionError = message
                                        }
                                    },
                                    onDelete = { deleteUser = it },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            if (!state.loading && (state.groups.isNotEmpty() || state.users.isNotEmpty() || tab == 0)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        when (tab) {
                            0 -> {
                                editGroup = null
                                showGroupDialog = true
                            }
                            1 -> {
                                editSubject = null
                                showSubjectDialog = true
                            }
                            else -> showUserDialog = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(20.dp),
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = {
                        Text(
                            when (tab) {
                                0 -> "Группа"
                                1 -> "Предмет"
                                else -> "Аккаунт"
                            },
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }

    if (showGroupDialog) {
        val editing = editGroup
        GroupDialog(
            initial = editing,
            onDismiss = { showGroupDialog = false },
            onSave = { name, code, avatar, done ->
                if (editing == null) {
                    vm.createGroup(name, code, avatar, done)
                } else {
                    vm.updateGroup(editing.id, name, code, avatar, done)
                }
            },
        )
    }

    if (showSubjectDialog) {
        val editing = editSubject
        SubjectDialog(
            initial = editing,
            groups = state.groups,
            teachers = teachers,
            onDismiss = { showSubjectDialog = false },
            onSave = { name, groupId, teacherId, done ->
                if (editing == null) {
                    vm.createSubject(name, groupId, teacherId, done)
                } else {
                    vm.updateSubject(editing.id, name, teacherId, done)
                }
            },
        )
    }

    if (showUserDialog) {
        UserDialog(
            groups = state.groups,
            onDismiss = { showUserDialog = false },
            onSave = { fullName, login, password, role, groupId, avatar, done ->
                vm.createUser(fullName, login, password, role, groupId, avatar, done)
            },
        )
    }

    if (avatarUser != null) {
        val user = avatarUser!!
        AvatarDialog(
            user = user,
            onDismiss = { avatarUser = null },
            onSave = { avatar, done ->
                vm.setUserAvatar(user.id, avatar, done)
            },
        )
    }

    if (resetUser != null) {
        val user = resetUser!!
        ResetPasswordDialog(
            user = user,
            onDismiss = { resetUser = null },
            onSave = { password, done ->
                vm.resetPassword(user.id, password, done)
            },
        )
    }

    if (deleteGroup != null) {
        val group = deleteGroup!!
        AlertDialog(
            onDismissRequest = { deleteGroup = null },
            title = { Text("Удалить группу «${group.name}»?") },
            text = {
                Text(
                    "Вместе с группой удалятся её предметы, пары и все отметки. Обучающиеся останутся без группы.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteGroup = null
                        actionError = null
                        vm.deleteGroup(group.id) { message ->
                            if (message != null) actionError = message
                        }
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroup = null }) { Text("Отмена") }
            },
        )
    }

    if (deleteSubject != null) {
        val subject = deleteSubject!!
        AlertDialog(
            onDismissRequest = { deleteSubject = null },
            title = { Text("Удалить предмет «${subject.name}»?") },
            text = { Text("Все пары и отметки по этому предмету будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteSubject = null
                        actionError = null
                        vm.deleteSubject(subject.id) { message ->
                            if (message != null) actionError = message
                        }
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSubject = null }) { Text("Отмена") }
            },
        )
    }

    if (deleteUser != null) {
        val user = deleteUser!!
        AlertDialog(
            onDismissRequest = { deleteUser = null },
            title = { Text("Удалить аккаунт?") },
            text = {
                Text(
                    "Аккаунт «${user.fullName}» (@${user.login}) будет удалён вместе с его отметками.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteUser = null
                        actionError = null
                        vm.deleteUser(user.id) { message ->
                            if (message != null) actionError = message
                        }
                    },
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteUser = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun AdminTabs(
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
            listOf("Группы", "Предметы", "Люди").forEachIndexed { index, label ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(11.dp),
                    color = if (tab == index) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Transparent
                    },
                    border = if (tab == index) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    } else {
                        null
                    },
                    onClick = { onTabChange(index) },
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (tab == index) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupsTab(
    groups: List<GroupDto>,
    onAdd: () -> Unit,
    onEdit: (GroupDto) -> Unit,
    onDelete: (GroupDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text(
                    text = "Код группы",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Обучающиеся вводят его при регистрации. Код можно изменить в любой момент.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (groups.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Groups,
                    title = "Групп пока нет",
                    text = "Создайте группу, добавьте предметы и назначьте преподавателей.",
                    action = {
                        SecondaryButton(
                            text = "Создать группу",
                            icon = Icons.Rounded.Add,
                            onClick = onAdd,
                        )
                    },
                )
            }
        } else {
            items(groups, key = { it.id }) { group ->
                JournalCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            fullName = group.name,
                            avatar = group.avatar,
                            size = 48.dp,
                            shape = RoundedCornerShape(15.dp),
                            background = MaterialTheme.colorScheme.primaryContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${group.studentsCount} ${pluralStudents(group.studentsCount)} · ${group.subjectsCount} ${pluralSubjects(group.subjectsCount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(5.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            ) {
                                Text(
                                    text = "код ${group.code}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        IconButton(onClick = { onEdit(group) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Изменить",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { onDelete(group) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectsTab(
    subjects: List<SubjectDto>,
    groups: List<GroupDto>,
    filterGroupId: Int,
    onFilterChange: (Int) -> Unit,
    onAdd: () -> Unit,
    onEdit: (SubjectDto) -> Unit,
    onDelete: (SubjectDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(subjects, filterGroupId) {
        if (filterGroupId == 0) subjects else subjects.filter { it.groupId == filterGroupId }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Pill(
                        text = "Все группы",
                        selected = filterGroupId == 0,
                        onClick = { onFilterChange(0) },
                    )
                }
                items(groups, key = { it.id }) { group ->
                    Pill(
                        text = group.name,
                        selected = filterGroupId == group.id,
                        onClick = { onFilterChange(group.id) },
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.MenuBook,
                    title = if (groups.isEmpty()) "Сначала создайте группу" else "Предметов пока нет",
                    text = if (groups.isEmpty()) {
                        "Предметы добавляются внутри группы и закрепляются за преподавателем."
                    } else {
                        "Добавьте предмет и назначьте преподавателя, который будет вести пары."
                    },
                    action = if (groups.isNotEmpty()) {
                        {
                            SecondaryButton(
                                text = "Добавить предмет",
                                icon = Icons.Rounded.Add,
                                onClick = onAdd,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        } else {
            items(filtered, key = { it.id }) { subject ->
                JournalCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = subject.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = subject.groupName ?: "Группа не указана",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subject.teacherName ?: "Преподаватель не назначен",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (subject.teacherName == null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Пар: ${subject.lessonsCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        IconButton(onClick = { onEdit(subject) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Изменить",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { onDelete(subject) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersTab(
    users: List<UserDto>,
    search: String,
    onSearchChange: (String) -> Unit,
    roleFilter: String,
    onRoleFilterChange: (String) -> Unit,
    onResetPassword: (UserDto) -> Unit,
    onAvatar: (UserDto) -> Unit,
    onToggleActive: (UserDto) -> Unit,
    onDelete: (UserDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roleFilters = listOf(
        "all" to "Все",
        "student" to "Обучающиеся",
        "headman" to "Старосты",
        "teacher" to "Преподаватели",
        "admin" to "Админы",
    )
    val filtered = remember(users, search, roleFilter) {
        users.filter { user ->
            val roleMatches = roleFilter == "all" || user.role == roleFilter
            val query = search.trim()
            val textMatches = query.isEmpty() ||
                user.fullName.contains(query, ignoreCase = true) ||
                user.login.contains(query, ignoreCase = true) ||
                user.groupName?.contains(query, ignoreCase = true) == true
            roleMatches && textMatches
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            JournalField(
                value = search,
                onValueChange = onSearchChange,
                label = "Поиск по имени или логину",
                leading = Icons.Rounded.Search,
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(roleFilters, key = { it.first }) { (value, label) ->
                    Pill(
                        text = label,
                        selected = roleFilter == value,
                        onClick = { onRoleFilterChange(value) },
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Person,
                    title = "Никого не найдено",
                    text = "Измените запрос или создайте новый аккаунт.",
                )
            }
        } else {
            items(filtered, key = { it.id }) { user ->
                UserCard(
                    user = user,
                    onResetPassword = { onResetPassword(user) },
                    onAvatar = { onAvatar(user) },
                    onToggleActive = { onToggleActive(user) },
                    onDelete = { onDelete(user) },
                )
            }
        }
    }
}

@Composable
private fun UserCard(
    user: UserDto,
    onResetPassword: () -> Unit,
    onAvatar: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    JournalCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(fullName = user.fullName, avatar = user.avatar, size = 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!user.isActive) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "отключён",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    text = "@${user.login} · ${user.roleTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (user.hasGroup && !user.groupName.isNullOrBlank()) {
                    Text(
                        text = user.groupName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            RoleBadge(role = user.role)
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Действия",
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Изменить аватарку") },
                        onClick = {
                            menuOpen = false
                            onAvatar()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Сбросить пароль") },
                        onClick = {
                            menuOpen = false
                            onResetPassword()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (user.isActive) "Отключить" else "Включить")
                        },
                        onClick = {
                            menuOpen = false
                            onToggleActive()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminDialog(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp)
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
                        .padding(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.headlineSmall)
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun GroupDialog(
    initial: GroupDto?,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, (String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var code by remember { mutableStateOf(initial?.code.orEmpty()) }
    var avatar by remember { mutableStateOf(initial?.avatar) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AdminDialog(
        title = if (initial == null) "Новая группа" else "Изменить группу",
        subtitle = "Код нужен для регистрации обучающихся",
        onDismiss = { if (!saving) onDismiss() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                fullName = name.ifBlank { "Группа" },
                avatar = avatar,
                size = 52.dp,
                shape = RoundedCornerShape(16.dp),
                background = MaterialTheme.colorScheme.primaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = name.ifBlank { "Название группы" },
                style = MaterialTheme.typography.titleMedium,
                color = if (name.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        JournalField(
            value = name,
            onValueChange = {
                name = it
                error = null
            },
            label = "Название группы",
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(10.dp))
        JournalField(
            value = code,
            onValueChange = {
                code = it
                error = null
            },
            label = "Код группы",
            supporting = if (initial == null) {
                "Оставьте пустым — сгенерируем автоматически"
            } else {
                "Оставьте пустым, чтобы не менять"
            },
            imeAction = ImeAction.Done,
        )
        Spacer(Modifier.height(12.dp))
        AvatarPicker(
            selected = avatar,
            onSelect = { avatar = it },
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            ErrorBanner(message = error!!)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Сохранить",
            loading = saving,
            onClick = {
                when {
                    name.trim().length < 2 -> error = "Название слишком короткое"
                    code.isNotBlank() && !Regex("^[A-Za-z0-9_-]{3,32}$").matches(code.trim()) -> {
                        error = "Код: 3–32 символа (латиница, цифры, _ и -)"
                    }
                    else -> {
                        saving = true
                        error = null
                        onSave(name.trim(), code.trim().takeIf { it.isNotEmpty() }, avatar ?: "") { message ->
                            saving = false
                            if (message == null) onDismiss() else error = message
                        }
                    }
                }
            },
        )
    }
}

private data class SelectOption(val value: Int?, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(
    label: String,
    value: String?,
    placeholder: String,
    options: List<SelectOption>,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            enabled = enabled,
            onClick = { expanded = true },
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = value ?: placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (value == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SubjectDialog(
    initial: SubjectDto?,
    groups: List<GroupDto>,
    teachers: List<UserDto>,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int?, (String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var groupId by remember { mutableStateOf(initial?.groupId ?: groups.firstOrNull()?.id) }
    var teacherId by remember { mutableStateOf(initial?.teacherId) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val teacherOptions = listOf(SelectOption(null, "Без преподавателя")) +
        teachers.map { SelectOption(it.id, it.fullName) }

    AdminDialog(
        title = if (initial == null) "Новый предмет" else "Изменить предмет",
        subtitle = if (initial == null) {
            "Предмет закрепляется за группой и преподавателем"
        } else {
            initial.groupName
        },
        onDismiss = { if (!saving) onDismiss() },
    ) {
        JournalField(
            value = name,
            onValueChange = {
                name = it
                error = null
            },
            label = "Название предмета",
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(10.dp))
        SelectField(
            label = "Группа",
            value = groups.firstOrNull { it.id == groupId }?.name,
            placeholder = "Выберите группу",
            options = groups.map { SelectOption(it.id, it.name) },
            onSelect = {
                groupId = it
                error = null
            },
            enabled = initial == null,
        )
        Spacer(Modifier.height(10.dp))
        SelectField(
            label = "Преподаватель",
            value = teachers.firstOrNull { it.id == teacherId }?.fullName,
            placeholder = "Без преподавателя",
            options = teacherOptions,
            onSelect = { teacherId = it },
        )

        if (groups.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            ErrorBanner(message = "Сначала создайте хотя бы одну группу")
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            ErrorBanner(message = error!!)
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Сохранить",
            loading = saving,
            enabled = groups.isNotEmpty(),
            onClick = {
                val targetGroup = groupId
                when {
                    name.trim().length < 2 -> error = "Название слишком короткое"
                    targetGroup == null -> error = "Выберите группу"
                    else -> {
                        saving = true
                        error = null
                        onSave(name.trim(), targetGroup, teacherId) { message ->
                            saving = false
                            if (message == null) onDismiss() else error = message
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun UserDialog(
    groups: List<GroupDto>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int?, String?, (String?) -> Unit) -> Unit,
) {
    var role by remember { mutableStateOf("student") }
    var fullName by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf(groups.firstOrNull()?.id) }
    var avatar by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val needsGroup = role == "student" || role == "headman"

    AdminDialog(
        title = "Новый аккаунт",
        subtitle = "Логин и пароль нужно передать пользователю",
        onDismiss = { if (!saving) onDismiss() },
    ) {
        Text(
            text = "РОЛЬ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        listOf(
            "student" to "Обучающийся",
            "headman" to "Староста",
            "teacher" to "Преподаватель",
            "admin" to "Администратор",
        ).chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowOptions.forEach { (value, label) ->
                    Surface(
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (role == value) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (role == value) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        border = if (role == value) {
                            null
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        },
                        onClick = {
                            role = value
                            error = null
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(12.dp))
        JournalField(
            value = fullName,
            onValueChange = {
                fullName = it
                error = null
            },
            label = "Фамилия и имя",
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(10.dp))
        JournalField(
            value = login,
            onValueChange = {
                login = it
                error = null
            },
            label = "Логин",
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next,
            supporting = "3–32 символа: латиница, цифры, . _ -",
        )
        Spacer(Modifier.height(10.dp))
        JournalField(
            value = password,
            onValueChange = {
                password = it
                error = null
            },
            label = "Пароль",
            password = true,
            imeAction = ImeAction.Next,
            supporting = "Не короче 6 символов",
        )

        if (needsGroup) {
            Spacer(Modifier.height(10.dp))
            SelectField(
                label = if (role == "headman") "Группа старосты" else "Группа",
                value = groups.firstOrNull { it.id == groupId }?.name,
                placeholder = "Выберите группу",
                options = groups.map { SelectOption(it.id, it.name) },
                onSelect = {
                    groupId = it
                    error = null
                },
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                fullName = fullName.ifBlank { "Новый аккаунт" },
                avatar = avatar,
                size = 44.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = fullName.ifBlank { "Фамилия и имя" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        AvatarPicker(
            selected = avatar,
            onSelect = { avatar = it },
        )

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            ErrorBanner(message = error!!)
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Создать",
            loading = saving,
            onClick = {
                when {
                    fullName.trim().length < 2 -> error = "Укажите фамилию и имя"
                    !Regex("^[a-zA-Z0-9._-]{3,32}$").matches(login.trim()) -> {
                        error = "Логин: 3–32 символа (латиница, цифры, . _ -)"
                    }
                    password.length < 6 -> error = "Пароль должен быть не короче 6 символов"
                    needsGroup && groupId == null -> error = "Выберите группу"
                    else -> {
                        saving = true
                        error = null
                        onSave(
                            fullName.trim(),
                            login.trim(),
                            password,
                            role,
                            if (needsGroup) groupId else null,
                            avatar,
                        ) { message ->
                            saving = false
                            if (message == null) onDismiss() else error = message
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun AvatarDialog(
    user: UserDto,
    onDismiss: () -> Unit,
    onSave: (String?, (String?) -> Unit) -> Unit,
) {
    var avatar by remember { mutableStateOf(user.avatar) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AdminDialog(
        title = "Аватарка профиля",
        subtitle = "${user.fullName} · @${user.login}",
        onDismiss = { if (!saving) onDismiss() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                fullName = user.fullName,
                avatar = avatar,
                size = 64.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(text = user.fullName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Будет видна в журнале и списке группы",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        AvatarPicker(
            selected = avatar,
            onSelect = { avatar = it },
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            ErrorBanner(message = error!!)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Сохранить аватарку",
            loading = saving,
            onClick = {
                saving = true
                error = null
                onSave(avatar) { message ->
                    saving = false
                    if (message == null) onDismiss() else error = message
                }
            },
        )
    }
}

@Composable
private fun ResetPasswordDialog(
    user: UserDto,
    onDismiss: () -> Unit,
    onSave: (String, (String?) -> Unit) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AdminDialog(
        title = "Новый пароль",
        subtitle = "${user.fullName} · @${user.login}",
        onDismiss = { if (!saving) onDismiss() },
    ) {
        JournalField(
            value = password,
            onValueChange = {
                password = it
                error = null
            },
            label = "Пароль",
            password = true,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(10.dp))
        JournalField(
            value = confirm,
            onValueChange = {
                confirm = it
                error = null
            },
            label = "Повторите пароль",
            password = true,
            imeAction = ImeAction.Done,
        )
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            ErrorBanner(message = error!!)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Сохранить пароль",
            loading = saving,
            onClick = {
                when {
                    password.length < 6 -> error = "Пароль должен быть не короче 6 символов"
                    password != confirm -> error = "Пароли не совпадают"
                    else -> {
                        saving = true
                        error = null
                        onSave(password) { message ->
                            saving = false
                            if (message == null) onDismiss() else error = message
                        }
                    }
                }
            },
        )
    }
}
