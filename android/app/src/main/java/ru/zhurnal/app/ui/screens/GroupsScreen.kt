package ru.zhurnal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zhurnal.app.data.GroupDto
import ru.zhurnal.app.data.UserDto
import ru.zhurnal.app.data.ZhurnalRepository
import ru.zhurnal.app.ui.GroupsViewModel
import ru.zhurnal.app.ui.components.Avatar
import ru.zhurnal.app.ui.components.EmptyState
import ru.zhurnal.app.ui.components.ErrorBanner
import ru.zhurnal.app.ui.components.JournalCard
import ru.zhurnal.app.ui.components.JournalField
import ru.zhurnal.app.ui.components.JournalTopBar
import ru.zhurnal.app.ui.components.LoadingContent
import ru.zhurnal.app.ui.components.RoundIconButton
import ru.zhurnal.app.ui.components.ScreenBackground
import ru.zhurnal.app.ui.components.SecondaryButton
import ru.zhurnal.app.ui.components.SectionTitle
import ru.zhurnal.app.ui.components.ClickableCard
import ru.zhurnal.app.ui.formatAverage
import ru.zhurnal.app.ui.pluralPairs
import ru.zhurnal.app.ui.pluralStudents
import ru.zhurnal.app.ui.pluralSubjects
import ru.zhurnal.app.ui.theme.Ink
import ru.zhurnal.app.ui.theme.Paper
import ru.zhurnal.app.ui.todayLongLabel

@Composable
fun GroupsScreen(
    user: UserDto,
    repository: ZhurnalRepository,
    onOpenGroup: (Int) -> Unit,
    onAdmin: () -> Unit,
    onProfile: () -> Unit,
    onSubstitutions: () -> Unit,
    onSchedule: () -> Unit,
) {
    val vm: GroupsViewModel = viewModel(factory = GroupsViewModel.factory(repository))
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(state.groups, query) {
        if (query.isBlank()) {
            state.groups
        } else {
            state.groups.filter {
                it.name.contains(query.trim(), ignoreCase = true) ||
                    it.code.contains(query.trim(), ignoreCase = true)
            }
        }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            JournalTopBar(
                title = "Журнал",
                subtitle = when {
                    user.isAdmin -> "Все группы"
                    user.isTeacher -> "Ваши группы"
                    else -> "Ваша группа"
                },
                actions = {
                    if (user.isAdmin) {
                        RoundIconButton(
                            icon = Icons.Rounded.AdminPanelSettings,
                            contentDescription = "Управление",
                            onClick = onAdmin,
                        )
                    }
                    RoundIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Обновить",
                        onClick = { vm.load(refresh = true) },
                    )
                    RoundIconButton(
                        icon = Icons.Rounded.Person,
                        contentDescription = "Профиль",
                        onClick = onProfile,
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    GreetingHero(user = user)
                }

                item {
                    SecondaryButton(
                        text = "Расписание пар и ДЗ",
                        icon = Icons.Rounded.CalendarMonth,
                        onClick = onSchedule,
                    )
                }

                item {
                    SecondaryButton(
                        text = "Замены с сайта колледжа",
                        icon = Icons.Rounded.Description,
                        onClick = onSubstitutions,
                    )
                }

                if (state.groups.size > 3) {
                    item {
                        JournalField(
                            value = query,
                            onValueChange = { query = it },
                            label = "Поиск группы",
                            leading = Icons.Rounded.Search,
                        )
                    }
                }

                item {
                    SectionTitle(
                        title = if (user.hasGroup) "Группа" else "Выберите группу",
                        action = {
                            if (state.groups.isNotEmpty()) {
                                Text(
                                    text = "${state.groups.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }

                when {
                    state.loading -> item { LoadingContent() }
                    state.error != null && state.groups.isEmpty() -> item {
                        Column {
                            ErrorBanner(message = state.error!!)
                            Spacer(Modifier.height(12.dp))
                            SecondaryButton(
                                text = "Повторить",
                                onClick = { vm.load() },
                            )
                        }
                    }
                    filtered.isEmpty() && query.isNotBlank() -> item {
                        EmptyState(
                            icon = Icons.Rounded.Search,
                            title = "Ничего не найдено",
                            text = "Попробуйте другое название или код группы.",
                        )
                    }
                    state.groups.isEmpty() -> item {
                        when {
                            user.hasGroup -> EmptyState(
                                icon = Icons.Rounded.Group,
                                title = "Вы пока не в группе",
                                text = "Обратитесь к администратору, чтобы вас добавили в группу.",
                            )
                            user.isTeacher -> EmptyState(
                                icon = Icons.Rounded.Group,
                                title = "Нет назначенных групп",
                                text = "Администратор назначит вас преподавателем предмета — группы появятся здесь.",
                            )
                            else -> EmptyState(
                                icon = Icons.Rounded.Group,
                                title = "Групп пока нет",
                                text = "Создайте первую группу и добавьте обучающихся в разделе управления.",
                                action = {
                                    SecondaryButton(
                                        text = "Перейти в управление",
                                        onClick = onAdmin,
                                    )
                                },
                            )
                        }
                    }
                    else -> {
                        if (state.error != null) {
                            item {
                                ErrorBanner(
                                    message = state.error!!,
                                    onDismiss = { vm.load() },
                                )
                            }
                        }
                        items(filtered, key = { it.id }) { group ->
                            GroupCard(
                                group = group,
                                showCode = !user.hasGroup,
                                onClick = { onOpenGroup(group.id) },
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Средний балл и пропуски считаются автоматически по всем отметкам.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GreetingHero(user: UserDto) {
    val firstName = user.fullName.trim().split(Regex("\\s+")).let { parts ->
        if (parts.size > 1) parts[1] else parts.firstOrNull() ?: user.fullName
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Ink, Color(0xFF2B5A7A)))),
    ) {
        Avatar(
            fullName = user.fullName,
            avatar = user.avatar,
            size = 64.dp,
            background = Paper.copy(alpha = 0.12f),
            contentColor = Paper,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
        )
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = "ЖУРНАЛ · ${user.roleTitle.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = Paper.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Здравствуйте,\n$firstName",
                style = MaterialTheme.typography.headlineMedium,
                color = Paper,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = todayLongLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = Paper.copy(alpha = 0.7f),
            )
            if (user.hasGroup && !user.groupName.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Paper.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Group,
                        contentDescription = null,
                        tint = Paper,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = user.groupName,
                        style = MaterialTheme.typography.labelLarge,
                        color = Paper,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: GroupDto,
    showCode: Boolean,
    onClick: () -> Unit,
) {
    ClickableCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                fullName = group.name,
                avatar = group.avatar,
                size = 56.dp,
                shape = RoundedCornerShape(18.dp),
                background = MaterialTheme.colorScheme.primaryContainer,
            )
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append("${group.studentsCount} ${pluralStudents(group.studentsCount)}")
                        append(" · ${group.subjectsCount} ${pluralSubjects(group.subjectsCount)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (group.average != null) {
                        SmallChip(
                            text = "ср. ${formatAverage(group.average)}",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (group.absentPairs > 0) {
                        SmallChip(
                            text = "${group.absentPairs} ${pluralPairs(group.absentPairs)} пропущено",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (showCode) {
                        SmallChip(
                            text = "код ${group.code}",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SmallChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}
