package ru.zhurnal.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.zhurnal.app.data.DocBlockDto
import ru.zhurnal.app.data.DocCellDto
import ru.zhurnal.app.data.ZhurnalRepository
import ru.zhurnal.app.ui.SubstitutionsViewModel
import ru.zhurnal.app.ui.components.EmptyState
import ru.zhurnal.app.ui.components.ErrorBanner
import ru.zhurnal.app.ui.components.JournalCard
import ru.zhurnal.app.ui.components.JournalTopBar
import ru.zhurnal.app.ui.components.LoadingContent
import ru.zhurnal.app.ui.components.RoundIconButton
import ru.zhurnal.app.ui.components.ScreenBackground
import ru.zhurnal.app.ui.components.SecondaryButton
import ru.zhurnal.app.ui.formatDateTime

@Composable
fun SubstitutionsScreen(
    repository: ZhurnalRepository,
    onBack: () -> Unit,
) {
    val vm: SubstitutionsViewModel = viewModel(factory = SubstitutionsViewModel.factory(repository))
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
                title = "Замены",
                subtitle = "С сайта колледжа",
                onBack = onBack,
                actions = {
                    RoundIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Обновить замены",
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
                    ErrorBanner(
                        message = state.error ?: "Не удалось получить замены",
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        text = "Повторить",
                        icon = Icons.Rounded.Refresh,
                        onClick = { vm.load(refresh = true) },
                    )
                }
                data.lines.isEmpty() && data.blocks.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Description,
                    title = "Замены пусты",
                    text = "На сайте колледжа пока нет опубликованного документа.",
                    modifier = Modifier.weight(1f),
                )
                else -> {
                    if (state.error != null) {
                        ErrorBanner(
                            message = state.error!!,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            onDismiss = { vm.load(refresh = true) },
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 8.dp,
                            bottom = 36.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item {
                            JournalCard(padding = 16.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.size(9.dp))
                                    Text(
                                        text = data.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = buildString {
                                        append("Опубликовано: ${formatDateTime(data.publishedAt)}")
                                        if (data.fromCache) append(" · из кэша")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = data.fileName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        if (data.blocks.isNotEmpty()) {
                            itemsIndexed(data.blocks) { index, block ->
                                DocBlockView(block = block, isFirst = index == 0)
                                Spacer(Modifier.height(10.dp))
                            }
                        } else {
                            itemsIndexed(data.lines) { index, line ->
                                if (line.isBlank()) {
                                    Spacer(Modifier.height(7.dp))
                                } else {
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Источник: bobruisk.belstu.by · обновлено ${formatDateTime(data.fetchedAt)}",
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

@Composable
private fun DocBlockView(block: DocBlockDto, isFirst: Boolean) {
    if (block.type == "table" && block.rows.isNotEmpty()) {
        DocTableView(block)
    } else if (block.text.isNotBlank()) {
        Text(
            text = block.text,
            style = if (isFirst) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (block.bold || isFirst) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = when (block.align) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                else -> TextAlign.Start
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DocTableView(block: DocBlockDto) {
    val columns = remember(block) {
        block.rows.maxOfOrNull { row ->
            row.sumOf { it.colspan.coerceIn(1, 20) }
        }?.coerceIn(1, 12) ?: 1
    }
    val tableWidth = (columns * 88).dp.coerceAtLeast(560.dp)
    val headerRows = block.headerRows.coerceIn(0, block.rows.size)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.width(tableWidth)) {
            block.rows.forEachIndexed { rowIndex, row ->
                val isHeader = rowIndex < headerRows
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    row.forEach { cell ->
                        DocCellView(
                            cell = cell,
                            isHeader = isHeader,
                            striped = rowIndex % 2 == 1,
                            modifier = Modifier
                                .weight(cell.colspan.coerceIn(1, 20).toFloat())
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocCellView(
    cell: DocCellDto,
    isHeader: Boolean,
    striped: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                when {
                    isHeader -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    striped -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                    else -> Color.Transparent
                },
            )
            .padding(horizontal = 7.dp, vertical = 6.dp),
    ) {
        if (cell.text.isNotBlank()) {
            Text(
                text = cell.text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                ),
                fontWeight = if (isHeader || cell.bold) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
