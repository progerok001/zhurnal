package ru.zhurnal.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.zhurnal.app.data.UserDto
import ru.zhurnal.app.ui.AppViewModel
import ru.zhurnal.app.ui.SessionState
import ru.zhurnal.app.ui.components.Avatar
import ru.zhurnal.app.ui.components.AvatarPicker
import ru.zhurnal.app.ui.components.ErrorBanner
import ru.zhurnal.app.ui.components.JournalCard
import ru.zhurnal.app.ui.components.JournalField
import ru.zhurnal.app.ui.components.JournalTopBar
import ru.zhurnal.app.ui.components.PrimaryButton
import ru.zhurnal.app.ui.components.RoleBadge
import ru.zhurnal.app.ui.components.ScreenBackground
import ru.zhurnal.app.ui.components.SecondaryButton
import ru.zhurnal.app.ui.components.SoftDivider
import ru.zhurnal.app.ui.theme.Ink
import ru.zhurnal.app.ui.theme.Paper

@Composable
fun ProfileScreen(
    user: UserDto,
    appVm: AppViewModel,
    onBack: () -> Unit,
) {
    val sessionState by appVm.session.collectAsStateWithLifecycle()
    val baseUrl = (sessionState as? SessionState.Ready)?.session?.baseUrl

    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var passwordChanged by remember { mutableStateOf(false) }

    LaunchedEffect(passwordChanged) {
        if (passwordChanged) {
            delay(4000)
            passwordChanged = false
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
                title = "Профиль",
                subtitle = "Аккаунт и настройки",
                onBack = onBack,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProfileHero(user = user, onEditAvatar = { showAvatarDialog = true })

                SecondaryButton(
                    text = "Сменить аватарку",
                    icon = Icons.Rounded.Person,
                    onClick = { showAvatarDialog = true },
                )

                if (passwordChanged) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "Пароль обновлён",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                JournalCard(padding = 0.dp) {
                    ProfileRow(
                        icon = Icons.Rounded.Person,
                        label = "Логин",
                        value = user.login,
                    )
                    SoftDivider(Modifier.padding(horizontal = 16.dp))
                    ProfileRow(
                        icon = Icons.Rounded.Badge,
                        label = "Роль",
                        value = user.roleTitle,
                    )
                    if (user.hasGroup) {
                        SoftDivider(Modifier.padding(horizontal = 16.dp))
                        ProfileRow(
                            icon = Icons.Rounded.Group,
                            label = "Группа",
                            value = user.groupName ?: "Не назначена",
                        )
                    }
                    SoftDivider(Modifier.padding(horizontal = 16.dp))
                    ProfileRow(
                        icon = Icons.Rounded.Shield,
                        label = "Статус",
                        value = if (user.isActive) "Активен" else "Отключён",
                        valueColor = if (user.isActive) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }

                JournalCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Сервер", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = baseUrl?.removePrefix("https://")
                                    ?.removePrefix("http://")
                                    ?: "Не настроен",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { showServerDialog = true }) {
                            Text("Изменить")
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "При смене сервера потребуется войти заново.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SecondaryButton(
                    text = "Сменить пароль",
                    icon = Icons.Rounded.Lock,
                    onClick = { showPasswordDialog = true },
                )

                Surface(
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f)),
                    onClick = { showLogoutDialog = true },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "Выйти из аккаунта",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Журнал · 1.0.0\nОценки и аккаунты хранятся на вашем сервере.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showAvatarDialog) {
        ProfileAvatarDialog(
            user = user,
            onDismiss = { showAvatarDialog = false },
            onSave = { avatar, onResult -> appVm.updateAvatar(avatar, onResult) },
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onSubmit = { current, new, onResult ->
                appVm.changePassword(current, new, onResult)
            },
            onSuccess = {
                showPasswordDialog = false
                passwordChanged = true
            },
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Вы сможете войти снова, используя логин и пароль.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        appVm.logout()
                    },
                ) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Сменить сервер?") },
            text = {
                Text(
                    "Текущий аккаунт будет отключён в приложении. Убедитесь, что на новом сервере есть ваш аккаунт.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showServerDialog = false
                        appVm.clearServer()
                    },
                ) {
                    Text("Сменить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun ProfileHero(user: UserDto, onEditAvatar: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Ink, Color(0xFF2B5A7A)))),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(110.dp)
                .clip(CircleShape)
                .background(Paper.copy(alpha = 0.05f)),
        )
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Avatar(
                    fullName = user.fullName,
                    avatar = user.avatar,
                    size = 64.dp,
                    background = Paper.copy(alpha = 0.14f),
                    contentColor = Paper,
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp),
                    shape = CircleShape,
                    color = Paper,
                    onClick = onEditAvatar,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Сменить аватарку",
                            tint = Ink,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.fullName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Paper,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "@${user.login} · ${user.roleTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Paper.copy(alpha = 0.7f),
                )
                if (user.hasGroup && !user.groupName.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Группа ${user.groupName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Paper.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatarDialog(
    user: UserDto,
    onDismiss: () -> Unit,
    onSave: (String?, (String?) -> Unit) -> Unit,
) {
    var avatar by remember { mutableStateOf(user.avatar) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Аватарка профиля",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "Видна в журнале и списке группы",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { if (!saving) onDismiss() }) {
                        Text("Закрыть")
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(
                        fullName = user.fullName,
                        avatar = avatar,
                        size = 56.dp,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = user.fullName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(14.dp))
                AvatarPicker(
                    selected = avatar,
                    onSelect = { avatar = it },
                )

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
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
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, (String?) -> Unit) -> Unit,
    onSuccess: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!loading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Смена пароля",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "Минимум 6 символов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { if (!loading) onDismiss() }) {
                        Text("Закрыть")
                    }
                }

                Spacer(Modifier.height(16.dp))
                JournalField(
                    value = current,
                    onValueChange = {
                        current = it
                        error = null
                    },
                    label = "Текущий пароль",
                    leading = Icons.Rounded.Lock,
                    password = true,
                    imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(10.dp))
                JournalField(
                    value = new,
                    onValueChange = {
                        new = it
                        error = null
                    },
                    label = "Новый пароль",
                    leading = Icons.Rounded.Lock,
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
                    label = "Повторите новый пароль",
                    leading = Icons.Rounded.Lock,
                    password = true,
                    imeAction = ImeAction.Done,
                )

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    ErrorBanner(message = error!!)
                }

                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = "Сохранить",
                    loading = loading,
                    onClick = {
                        when {
                            current.isBlank() -> error = "Введите текущий пароль"
                            new.length < 6 -> error = "Новый пароль должен быть не короче 6 символов"
                            new != confirm -> error = "Пароли не совпадают"
                            else -> {
                                loading = true
                                error = null
                                onSubmit(current, new) { message ->
                                    loading = false
                                    if (message == null) {
                                        onSuccess()
                                    } else {
                                        error = message
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

