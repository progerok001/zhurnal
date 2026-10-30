package ru.zhurnal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.zhurnal.app.ui.AppViewModel
import ru.zhurnal.app.ui.components.BrandMark
import ru.zhurnal.app.ui.components.ErrorBanner
import ru.zhurnal.app.ui.components.JournalCard
import ru.zhurnal.app.ui.components.JournalField
import ru.zhurnal.app.ui.components.PrimaryButton
import ru.zhurnal.app.ui.components.ScreenBackground
import ru.zhurnal.app.ui.components.TextLink

@Composable
fun ServerSetupScreen(
    appVm: AppViewModel,
    initialUrl: String,
    onBack: (() -> Unit)?,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun connect(check: Boolean) {
        if (url.isBlank()) {
            error = "Введите адрес сервера"
            return
        }
        error = null
        loading = true
        val callback: (String?) -> Unit = { message ->
            loading = false
            if (message != null) error = message
        }
        if (check) {
            appVm.testAndSaveServer(url, callback)
        } else {
            appVm.saveServerUnchecked(url, callback)
        }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            if (onBack != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                }
            } else {
                Spacer(Modifier.height(32.dp))
            }

            Spacer(Modifier.height(8.dp))
            BrandMark(size = 66.dp)
            Spacer(Modifier.height(22.dp))
            Text(
                text = "Подключение\nк серверу",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Журнал работает с вашим сервером на компьютере. Вставьте адрес, который выдаёт portmap.io.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            JournalCard {
                JournalField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    label = "Адрес сервера",
                    leading = Icons.Rounded.Link,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                    supporting = "Например: https://school.portmap.io:12345",
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = "Проверить и подключиться",
                    onClick = { connect(true) },
                    loading = loading,
                )
                Spacer(Modifier.height(4.dp))
                TextLink(
                    text = "Сохранить без проверки",
                    onClick = { connect(false) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = error!!)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Оценки, аккаунты и группы хранятся только на вашем сервере.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

