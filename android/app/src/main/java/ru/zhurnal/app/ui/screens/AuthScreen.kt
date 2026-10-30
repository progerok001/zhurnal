package ru.zhurnal.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.zhurnal.app.ui.AppViewModel
import ru.zhurnal.app.ui.AuthUiState
import ru.zhurnal.app.ui.components.BrandMark
import ru.zhurnal.app.ui.components.ErrorBanner
import ru.zhurnal.app.ui.components.JournalCard
import ru.zhurnal.app.ui.components.JournalField
import ru.zhurnal.app.ui.components.PrimaryButton
import ru.zhurnal.app.ui.components.RoundIconButton
import ru.zhurnal.app.ui.components.ScreenBackground
import ru.zhurnal.app.ui.components.TextLink

private const val MODE_LOGIN = "login"
private const val MODE_REGISTER = "register"

@Composable
fun AuthScreen(
    appVm: AppViewModel,
    baseUrl: String,
    authState: AuthUiState,
) {
    var mode by rememberSaveable { mutableStateOf(MODE_LOGIN) }
    var showServerSettings by rememberSaveable { mutableStateOf(false) }

    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var fullName by rememberSaveable { mutableStateOf("") }
    var groupCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(baseUrl) {
        showServerSettings = false
    }

    if (showServerSettings) {
        ServerSetupScreen(
            appVm = appVm,
            initialUrl = baseUrl,
            onBack = { showServerSettings = false },
        )
        return
    }

    val isRegister = mode == MODE_REGISTER
    val clearError = {
        if (authState.error != null) appVm.clearAuthError()
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
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(size = 38.dp)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Журнал",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "электронный журнал группы",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RoundIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Настройки сервера",
                    onClick = { showServerSettings = true },
                )
            }

            Spacer(Modifier.height(34.dp))
            Text(
                text = if (isRegister) "Создать\nаккаунт" else "С возвращением",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isRegister) {
                    "Зарегистрируйтесь по коду группы — его выдаёт администратор или староста."
                } else {
                    "Войдите, чтобы открыть журнал своей группы."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            AuthModeSwitch(
                isRegister = isRegister,
                onChange = { newMode ->
                    if (mode != newMode) {
                        mode = newMode
                        appVm.clearAuthError()
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
            JournalCard {
                Crossfade(targetState = isRegister, label = "authForm") { register ->
                    Column {
                        if (register) {
                            JournalField(
                                value = fullName,
                                onValueChange = {
                                    fullName = it
                                    clearError()
                                },
                                label = "Фамилия и имя",
                                leading = Icons.Rounded.Badge,
                                imeAction = ImeAction.Next,
                            )
                            Spacer(Modifier.height(12.dp))
                            JournalField(
                                value = groupCode,
                                onValueChange = {
                                    groupCode = it
                                    clearError()
                                },
                                label = "Код группы",
                                leading = Icons.Rounded.Key,
                                imeAction = ImeAction.Next,
                                supporting = "Например: IS21",
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        JournalField(
                            value = login,
                            onValueChange = {
                                login = it
                                clearError()
                            },
                            label = "Логин",
                            leading = Icons.Rounded.Person,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next,
                        )
                        Spacer(Modifier.height(12.dp))
                        JournalField(
                            value = password,
                            onValueChange = {
                                password = it
                                clearError()
                            },
                            label = "Пароль",
                            leading = Icons.Rounded.Lock,
                            password = true,
                            imeAction = ImeAction.Done,
                        )

                        if (authState.error != null) {
                            Spacer(Modifier.height(12.dp))
                            ErrorBanner(
                                message = authState.error,
                                onDismiss = { appVm.clearAuthError() },
                            )
                        }

                        Spacer(Modifier.height(18.dp))
                        PrimaryButton(
                            text = if (register) "Создать аккаунт" else "Войти",
                            icon = if (register) Icons.Rounded.PersonAdd else Icons.Rounded.ArrowForward,
                            loading = authState.loading,
                            onClick = {
                                if (register) {
                                    appVm.register(fullName, login, password, groupCode)
                                } else {
                                    appVm.login(login, password)
                                }
                            },
                        )
                        Spacer(Modifier.height(4.dp))
                        TextLink(
                            text = if (register) {
                                "Уже есть аккаунт? Войти"
                            } else {
                                "Нет аккаунта? Регистрация по коду группы"
                            },
                            onClick = {
                                mode = if (register) MODE_LOGIN else MODE_REGISTER
                                appVm.clearAuthError()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
                onClick = { showServerSettings = true },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "СЕРВЕР",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = baseUrl.removePrefix("https://").removePrefix("http://"),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "Изменить",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "Пароли хранятся в виде хешей. Данные не покидают ваш сервер.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun AuthModeSwitch(
    isRegister: Boolean,
    onChange: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AuthModeTab(
                text = "Вход",
                selected = !isRegister,
                modifier = Modifier.weight(1f),
                onClick = { onChange(MODE_LOGIN) },
            )
            AuthModeTab(
                text = "Регистрация",
                selected = isRegister,
                modifier = Modifier.weight(1f),
                onClick = { onChange(MODE_REGISTER) },
            )
        }
    }
}

@Composable
private fun AuthModeTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.surface
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
        modifier = modifier,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
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
