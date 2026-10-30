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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import ru.zhurnal.app.data.HomeworkTopEntryDto
import ru.zhurnal.app.data.ZhurnalRepository
import ru.zhurnal.app.ui.HomeworkTopViewModel
import ru.zhurnal.app.ui.components.Avatar
import ru.zhurnal.app.ui.components.EmptyState
import ru.zhurnal.app.ui.components.ErrorBanner
import ru.zhurnal.app.ui.components.JournalCard
import ru.zhurnal.app.ui.components.JournalTopBar
import ru.zhurnal.app.ui.components.LoadingContent
import ru.zhurnal.app.ui.components.RoleBadge
import ru.zhurnal.app.ui.components.RoundIconButton
import ru.zhurnal.app.ui.components.ScreenBackground
import ru.zhurnal.app.ui.components.SecondaryButton
import ru.zhurnal.app.ui.formatDateTime
import ru.zhurnal.app.ui.theme.Ink
import ru.zhurnal.app.ui.theme.Ochre
import ru.zhurnal.app.ui.theme.Paper

@Composable
fun HomeworkTopScreen(
    repository: ZhurnalRepository,
    groupId: Int,
    onBack: () -> Unit,
) {
    val vm: HomeworkTopViewModel = viewModel(
        factory = HomeworkTopViewModel.factory(repository, groupId),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val data = state.data

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            JournalTopBar(
                title = "Топ по ДЗ",
                subtitle = data?.group?.name ?: "Активность группы",
                onBack = onBack,
                actions = {
                    RoundIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Обновить",
                        onClick = { vm.load() },
                    )
                },
            )

            TopPeriodSwitch(
                period = state.period,
                onPeriodChange = { vm.setPeriod(it) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            when {
                state.loading && data == null -> LoadingContent(Modifier.weight(1f))
                data == null -> Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    ErrorBanner(message = state.error ?: "Не удалось загрузить топ")
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        text = "Повторить",
                        icon = Icons.Rounded.Refresh,
                        onClick = { vm.load() },
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        TopHero(
                            total = data.total,
                            period = data.period,
                            since = data.since,
                            leader = data.items.firstOrNull(),
                            people = data.items.size,
                        )
                    }
                    if (state.error != null) {
                        item {
                            ErrorBanner(
                                message = state.error!!,
                                onDismiss = { vm.load() },
                            )
                        }
                    }

                    if (data.items.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.EmojiEvents,
                                title = "Топ пока пуст",
                                text = "За этот период никто не добавлял домашние задания. " +
                                    "Запишите первое — и вы окажетесь на вершине.",
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = if (data.period == "week") {
                                    "ИТОГИ НЕДЕЛИ · С ПОНЕДЕЛЬНИКА"
                                } else {
                                    "ИТОГИ МЕСЯЦА · С 1 ЧИСЛА"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                            )
                        }
                        val maxCount = data.items.maxOf { it.count }.coerceAtLeast(1)
                        itemsIndexed(data.items, key = { _, item -> item.userId ?: item.fullName }) { index, item ->
                            TopRow(
                                rank = index + 1,
                                item = item,
                                maxCount = maxCount,
                            )
                        }
                        item {
                            Text(
                                text = "Считаются все домашние задания, добавленные участниками группы " +
                                    "за выбранный период. Учитываются и задания, которые позже изменили.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = 4.dp,
                                    end = 4.dp,
                                    top = 6.dp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopPeriodSwitch(
    period: String,
    onPeriodChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        shadowElevation = 2.dp,
    ) {
        Row(modifier = Modifier.padding(6.dp)) {
            listOf("week" to "За неделю", "month" to "За месяц").forEach { option ->
                val selected = period == option.first
                Surface(
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    onClick = { onPeriodChange(option.first) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = option.second,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
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
private fun TopHero(
    total: Int,
    period: String,
    since: String,
    leader: HomeworkTopEntryDto?,
    people: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Ink, Color(0xFF2B5A7A)))),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = if (period == "week") "ТОП НЕДЕЛИ" else "ТОП МЕСЯЦА",
                style = MaterialTheme.typography.labelSmall,
                color = Paper.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$total",
                        style = MaterialTheme.typography.displaySmall,
                        color = Paper,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = pluralHomeworkTop(total),
                        style = MaterialTheme.typography.bodySmall,
                        color = Paper.copy(alpha = 0.78f),
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Ochre.copy(alpha = 0.22f),
                ) {
                    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = Ochre,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (leader != null) {
                    "Лидирует ${leader.fullName} · ${leader.count} ${pluralEntries(leader.count)}"
                } else {
                    "Пока нет записанных заданий"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Paper,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append("Участников с заданиями: $people")
                    if (since.isNotBlank()) append(" · с ${formatDateTime(since)}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = Paper.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun TopRow(
    rank: Int,
    item: HomeworkTopEntryDto,
    maxCount: Int,
) {
    val rankColor = when (rank) {
        1 -> Ochre
        2 -> Color(0xFF8E9AA6)
        3 -> Color(0xFFA9683F)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    JournalCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(rankColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = rankColor,
                )
            }
            Spacer(Modifier.size(11.dp))
            Avatar(
                fullName = item.fullName,
                avatar = item.avatar,
                size = 42.dp,
            )
            Spacer(Modifier.size(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.size(6.dp))
                    RoleBadge(role = item.role)
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { (item.count.toFloat() / maxCount).coerceIn(0.04f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                    color = if (rank <= 3) rankColor else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${item.count} ${pluralEntries(item.count)} · последняя запись ${formatDateTime(item.lastAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun pluralHomeworkTop(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "домашнее задание за период"
        mod10 in 2..4 && mod100 !in 12..14 -> "домашних задания за период"
        else -> "домашних заданий за период"
    }
}

private fun pluralEntries(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "запись"
        mod10 in 2..4 && mod100 !in 12..14 -> "записи"
        else -> "записей"
    }
}
