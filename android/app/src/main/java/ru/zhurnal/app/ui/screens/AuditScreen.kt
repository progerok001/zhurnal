package ru.zhurnal.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zhurnal.app.data.AuditEntryDto
import ru.zhurnal.app.data.ZhurnalRepository
import ru.zhurnal.app.ui.AuditViewModel
import ru.zhurnal.app.ui.components.EmptyState
import ru.zhurnal.app.ui.components.ErrorBanner
import ru.zhurnal.app.ui.components.JournalCard
import ru.zhurnal.app.ui.components.JournalField
import ru.zhurnal.app.ui.components.JournalTopBar
import ru.zhurnal.app.ui.components.LoadingContent
import ru.zhurnal.app.ui.components.Pill
import ru.zhurnal.app.ui.components.RoundIconButton
import ru.zhurnal.app.ui.components.ScreenBackground
import ru.zhurnal.app.ui.components.SecondaryButton
import ru.zhurnal.app.ui.formatDateTime

private val auditFilters = listOf(
    "all" to "Все",
    "record" to "Оценки",
    "lesson" to "Пары",
    "user" to "Аккаунты",
    "group" to "Группы",
    "subject" to "Предметы",
)

@Composable
fun AuditScreen(
    repository: ZhurnalRepository,
    onBack: () -> Unit,
) {
    val vm: AuditViewModel = viewModel(factory = AuditViewModel.factory(repository))
    val state by vm.state.collectAsStateWithLifecycle()

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            JournalTopBar(
                title = "Журнал аудита",
                subtitle = if (state.total > 0) "Всего записей: ${state.total}" else "Действия пользователей",
                onBack = onBack,
                actions = {
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

            Column(modifier = Modifier.fillMaxSize()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(auditFilters, key = { it.first }) { (value, label) ->
                        Pill(
                            text = label,
                            selected = state.filter == value,
                            onClick = { vm.setFilter(value) },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                JournalField(
                    value = state.query,
                    onValueChange = vm::search,
                    label = "Поиск по описанию или имени",
                    leading = Icons.Rounded.Search,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(10.dp))

                when {
                    state.loading -> LoadingContent(Modifier.weight(1f))
                    state.entries.isEmpty() -> EmptyState(
                        icon = Icons.Rounded.History,
                        title = "Записей пока нет",
                        text = if (state.query.isNotBlank()) {
                            "По этому запросу ничего не найдено."
                        } else {
                            "Здесь появятся изменения оценок, пар, аккаунтов и групп."
                        },
                        modifier = Modifier.weight(1f),
                    )
                    else -> {
                        if (state.error != null) {
                            ErrorBanner(
                                message = state.error!!,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                onDismiss = { vm.load(refresh = true) },
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 4.dp,
                                bottom = 30.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.entries, key = { it.id }) { entry ->
                                AuditCard(entry)
                            }
                            if (state.hasMore) {
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    SecondaryButton(
                                        text = if (state.loadingMore) "Загрузка…" else "Показать ещё",
                                        enabled = !state.loadingMore,
                                        onClick = vm::loadMore,
                                    )
                                }
                            }
                            item {
                                Text(
                                    text = "Показано ${state.entries.size} из ${state.total}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
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
private fun AuditCard(entry: AuditEntryDto) {
    val (icon, tint) = when (entry.entityType) {
        "record" -> Icons.Rounded.Grade to MaterialTheme.colorScheme.primary
        "lesson" -> Icons.Rounded.CalendarMonth to MaterialTheme.colorScheme.tertiary
        "user" -> Icons.Rounded.Person to MaterialTheme.colorScheme.secondary
        "group" -> Icons.Rounded.Groups to MaterialTheme.colorScheme.secondary
        "subject" -> Icons.Rounded.MenuBook to MaterialTheme.colorScheme.tertiary
        else -> Icons.Rounded.History to MaterialTheme.colorScheme.onSurfaceVariant
    }

    JournalCard(padding = 13.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(entry.userName)
                        if (entry.userLogin.isNotBlank()) append(" · @${entry.userLogin}")
                        val role = roleLabel(entry.userRole)
                        if (role.isNotBlank()) append(" · $role")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDateTime(entry.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!entry.groupName.isNullOrBlank()) {
                        Text(
                            text = " · ${entry.groupName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "admin" -> "администратор"
    "teacher" -> "преподаватель"
    "headman" -> "староста"
    "student" -> "обучающийся"
    else -> ""
}
