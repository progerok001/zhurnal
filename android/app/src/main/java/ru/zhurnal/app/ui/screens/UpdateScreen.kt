package ru.zhurnal.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.zhurnal.app.data.AppUpdateDto
import ru.zhurnal.app.ui.UpdateUiState
import ru.zhurnal.app.ui.components.BrandMark
import ru.zhurnal.app.ui.components.ErrorBanner
import ru.zhurnal.app.ui.components.JournalCard
import ru.zhurnal.app.ui.components.PrimaryButton
import ru.zhurnal.app.ui.components.ScreenBackground
import ru.zhurnal.app.ui.components.SecondaryButton

@Composable
fun UpdateScreen(
    state: UpdateUiState,
    currentVersionName: String,
    onInstall: () -> Unit,
    onRetryCheck: () -> Unit,
    onOpenInstallSettings: () -> Unit,
) {
    val update = state.update ?: return
    val progress = state.progress

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark(size = 76.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Доступно обновление",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Версия $currentVersionName → ${update.versionName}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(20.dp))

            JournalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Что нового",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = update.notes?.takeIf { it.isNotBlank() }
                        ?: "Исправления и улучшения приложения.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Обновление обязательно: без него приложение не откроется.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(22.dp))

            if (state.downloading) {
                LinearProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (progress != null) {
                        "Скачивание… ${(progress * 100).toInt()}%"
                    } else {
                        "Скачивание обновления…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                PrimaryButton(
                    text = "Установить обновление",
                    icon = Icons.Rounded.SystemUpdate,
                    onClick = onInstall,
                )
            }

            if (state.error != null) {
                Spacer(Modifier.height(14.dp))
                ErrorBanner(message = state.error)
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "Повторить",
                    onClick = onInstall,
                )
            }

            if (state.needInstallPermission) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "Разрешить установку приложений",
                    onClick = onOpenInstallSettings,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Если установка не началась, разрешите установку из этого источника и нажмите ещё раз.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Проверить обновление снова",
                onClick = onRetryCheck,
                enabled = !state.downloading,
            )
        }
    }
}
