package ru.zhurnal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import ru.zhurnal.app.MainActivity
import ru.zhurnal.app.R
import ru.zhurnal.app.ZhurnalApplication
import ru.zhurnal.app.data.ApiResult
import ru.zhurnal.app.data.AppJson
import ru.zhurnal.app.data.SummaryResponse
import ru.zhurnal.app.ui.effectivePairTime
import ru.zhurnal.app.ui.formatAverage
import ru.zhurnal.app.ui.formatDateShort
import ru.zhurnal.app.ui.mondayOf
import ru.zhurnal.app.ui.weekTypeFor
import ru.zhurnal.app.ui.weekTypeTitleFor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Строка списка внутри виджета: время/дата, название и значение справа. */
@Serializable
data class WidgetRow(
    val left: String = "",
    val title: String = "",
    val right: String = "",
    val accent: String? = null,
)

data class WidgetSubject(
    val id: Int,
    val name: String,
    val average: Double?,
    val gradesCount: Int,
)

/**
 * Данные для коллекций виджетов лежат в SharedPreferences: RemoteViewsService
 * читает их в своём процессе и строит строки синхронно.
 */
object WidgetStore {
    private const val PREFS = "zhurnal_widgets"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rows(context: Context, widgetId: Int): List<WidgetRow> {
        val raw = prefs(context).getString("rows_$widgetId", null) ?: return emptyList()
        return runCatching {
            AppJson.decodeFromString(ListSerializer(WidgetRow.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun saveRows(context: Context, widgetId: Int, rows: List<WidgetRow>) {
        prefs(context).edit()
            .putString("rows_$widgetId", AppJson.encodeToString(ListSerializer(WidgetRow.serializer()), rows))
            .apply()
    }

    fun selectedSubject(context: Context, widgetId: Int): Int? =
        prefs(context).getInt("subject_$widgetId", 0).takeIf { it > 0 }

    fun saveSelectedSubject(context: Context, widgetId: Int, subjectId: Int) {
        prefs(context).edit().putInt("subject_$widgetId", subjectId).apply()
    }

    fun clear(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove("rows_$widgetId")
            .remove("subject_$widgetId")
            .apply()
    }
}

enum class WidgetKind(
    val layout: Int,
    val provider: Class<out BaseWidgetProvider>,
) {
    Small(R.layout.widget_small, SmallWidgetProvider::class.java),
    Medium(R.layout.widget_medium, MediumWidgetProvider::class.java),
    Large(R.layout.widget_large, LargeWidgetProvider::class.java),
}

/** Маленький виджет: пары на сегодня. */
class SmallWidgetProvider : BaseWidgetProvider() {
    override val kind: WidgetKind = WidgetKind.Small
}

/** Средний виджет: средний балл по всем предметам. */
class MediumWidgetProvider : BaseWidgetProvider() {
    override val kind: WidgetKind = WidgetKind.Medium
}

/** Большой виджет: оценки по выбранному предмету. */
class LargeWidgetProvider : BaseWidgetProvider() {
    override val kind: WidgetKind = WidgetKind.Large
}

abstract class BaseWidgetProvider : AppWidgetProvider() {
    abstract val kind: WidgetKind

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetUpdater.refresh(context, appWidgetManager, appWidgetIds, kind)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetUpdater.ACTION_REFRESH) {
            val pending = goAsync()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, kind.provider))
            WidgetUpdater.refresh(context, manager, ids, kind) { pending.finish() }
            return
        }
        if (intent.action == WidgetUpdater.ACTION_SELECT_SUBJECT) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            val subjectId = intent.getIntExtra(WidgetUpdater.EXTRA_SUBJECT_ID, 0)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && subjectId > 0) {
                WidgetStore.saveSelectedSubject(context, widgetId, subjectId)
                val pending = goAsync()
                val manager = AppWidgetManager.getInstance(context)
                WidgetUpdater.refresh(context, manager, intArrayOf(widgetId), kind) {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetStore.clear(context, it) }
        super.onDeleted(context, appWidgetIds)
    }
}

object WidgetUpdater {

    const val ACTION_REFRESH = "ru.zhurnal.app.widget.REFRESH"
    const val ACTION_SELECT_SUBJECT = "ru.zhurnal.app.widget.SELECT_SUBJECT"
    const val EXTRA_SUBJECT_ID = "ru.zhurnal.app.widget.SUBJECT_ID"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru"))
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale("ru"))

    private data class Snapshot(
        val authorized: Boolean = true,
        val groupName: String = "Журнал",
        val dateLabel: String = "",
        val rows: List<WidgetRow> = emptyList(),
        val subjects: List<WidgetSubject> = emptyList(),
        val overallAverage: Double? = null,
        val selectedSubjectId: Int? = null,
        val selectedSubjectName: String = "",
        val selectedAverage: Double? = null,
        val footer: String = "",
        val emptyText: String = "",
        val loading: Boolean = false,
        val error: String? = null,
    )

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        WidgetKind.entries.forEach { kind ->
            val ids = manager.getAppWidgetIds(ComponentName(context, kind.provider))
            if (ids.isNotEmpty()) refresh(context, manager, ids, kind)
        }
    }

    fun refresh(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        kind: WidgetKind,
        done: (() -> Unit)? = null,
    ) {
        if (ids.isEmpty()) {
            done?.invoke()
            return
        }
        val appContext = context.applicationContext
        val loading = Snapshot(
            dateLabel = dateLabel(LocalDate.now()),
            footer = "Обновление…",
            emptyText = "Загрузка…",
            loading = true,
        )
        ids.forEach { id ->
            runCatching {
                manager.updateAppWidget(
                    id,
                    render(appContext, kind, loading, id, WidgetStore.selectedSubject(appContext, id)),
                )
            }
        }

        scope.launch {
            ids.forEach { id ->
                val snapshot = runCatching { loadSnapshot(appContext, kind, id) }.getOrElse { error ->
                    failureSnapshot(error.message)
                }
                WidgetStore.saveRows(appContext, id, snapshot.rows)
                snapshot.selectedSubjectId?.let { subjectId ->
                    WidgetStore.saveSelectedSubject(appContext, id, subjectId)
                }
                runCatching {
                    manager.updateAppWidget(
                        id,
                        render(appContext, kind, snapshot, id, snapshot.selectedSubjectId),
                    )
                }
            }
            runCatching { manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list) }
            done?.invoke()
        }
    }

    private fun failureSnapshot(message: String?): Snapshot = Snapshot(
        dateLabel = dateLabel(LocalDate.now()),
        footer = "Не удалось обновить",
        emptyText = message ?: "Проверьте подключение",
        error = message ?: "Ошибка сети",
    )

    private suspend fun loadSnapshot(
        context: Context,
        kind: WidgetKind,
        widgetId: Int,
    ): Snapshot {
        val app = context.applicationContext as? ZhurnalApplication
            ?: return Snapshot(authorized = false, footer = "Откройте приложение")
        val session = app.container.sessionStore.current()
        val user = session.user
        val today = LocalDate.now()
        val label = dateLabel(today)

        if (session.baseUrl.isNullOrBlank() || session.token.isNullOrBlank() || user == null) {
            return Snapshot(
                authorized = false,
                dateLabel = label,
                footer = "Войдите в приложение",
                emptyText = "Нужно войти в аккаунт",
            )
        }

        val repository = app.container.repository
        val groupId = user.groupId
        if (groupId == null) {
            return loadWithoutGroup(repository, session.baseUrl, kind, label)
        }

        return when (kind) {
            WidgetKind.Small -> loadPairs(repository, groupId, user.groupName ?: "Журнал", today, label)
            WidgetKind.Medium -> loadSummary(repository, groupId, user.id, user.groupName ?: "Журнал", label)
            WidgetKind.Large -> loadGrades(
                repository,
                groupId,
                user.id,
                user.groupName ?: "Журнал",
                label,
                widgetId,
                WidgetStore.selectedSubject(context, widgetId),
            )
        }
    }

    private suspend fun loadWithoutGroup(
        repository: ru.zhurnal.app.data.ZhurnalRepository,
        baseUrl: String,
        kind: WidgetKind,
        label: String,
    ): Snapshot {
        if (kind != WidgetKind.Medium) {
            return Snapshot(
                dateLabel = label,
                footer = "Откройте приложение",
                emptyText = "Виджет доступен обучающимся",
            )
        }
        return when (val health = repository.testConnection(baseUrl)) {
            is ApiResult.Success -> {
                val counts = health.data.counts
                Snapshot(
                    groupName = "Журнал",
                    dateLabel = label,
                    rows = listOf(
                        WidgetRow(left = "", title = "Групп", right = "${counts?.groups ?: 0}"),
                        WidgetRow(left = "", title = "Пользователей", right = "${counts?.users ?: 0}"),
                    ),
                    footer = "Сводка сервера",
                    emptyText = "Нет данных",
                )
            }
            is ApiResult.Failure -> failureSnapshot(health.message)
        }
    }

    private suspend fun loadPairs(
        repository: ru.zhurnal.app.data.ZhurnalRepository,
        groupId: Int,
        groupName: String,
        today: LocalDate,
        label: String,
    ): Snapshot {
        val weekStart = mondayOf(today)
        val week = weekTypeFor(weekStart)
        return when (val result = repository.schedule(groupId, week, weekStart.toString())) {
            is ApiResult.Failure -> failureSnapshot(result.message)
            is ApiResult.Success -> {
                val data = result.data
                val day = today.dayOfWeek.value
                val entries = data.entries
                    .filter { it.day == day }
                    .sortedBy { it.pairNumber }
                val rows = entries.map { entry ->
                    WidgetRow(
                        left = effectivePairTime(data.times, day, entry.pairNumber).label,
                        title = if (entry.split) {
                            "${entry.subjectName} / ${entry.secondSubjectName.orEmpty()}"
                        } else {
                            entry.subjectName
                        },
                        right = if (entry.split) {
                            listOfNotNull(
                                entry.room?.takeIf { it.isNotBlank() },
                                entry.secondRoom?.takeIf { it.isNotBlank() },
                            ).joinToString("/")
                        } else {
                            entry.room.orEmpty()
                        },
                    )
                }
                Snapshot(
                    groupName = groupName.ifBlank { data.group.name },
                    dateLabel = label,
                    rows = rows,
                    footer = "${weekTypeTitleFor(weekStart)} · обновлено " +
                        LocalTime.now().format(timeFormatter),
                    emptyText = "Сегодня пар нет",
                )
            }
        }
    }

    private suspend fun loadSummary(
        repository: ru.zhurnal.app.data.ZhurnalRepository,
        groupId: Int,
        userId: Int,
        groupName: String,
        label: String,
    ): Snapshot {
        return when (val result = repository.summary(groupId)) {
            is ApiResult.Failure -> failureSnapshot(result.message)
            is ApiResult.Success -> {
                val data = result.data
                val subjects = subjectList(data, userId)
                val personal = data.students.firstOrNull { it.id == userId }
                val overall = personal?.avg ?: data.stats.average
                Snapshot(
                    groupName = groupName.ifBlank { data.group.name },
                    dateLabel = label,
                    subjects = subjects,
                    overallAverage = overall,
                    rows = subjects.map { subject ->
                        WidgetRow(
                            left = formatAverage(subject.average),
                            title = subject.name,
                            right = "${subject.gradesCount} отм.",
                            accent = averageAccent(subject.average),
                        )
                    },
                    footer = "Средний балл: ${formatAverage(overall)} · " +
                        LocalTime.now().format(timeFormatter),
                    emptyText = "Предметов пока нет",
                )
            }
        }
    }

    private suspend fun loadGrades(
        repository: ru.zhurnal.app.data.ZhurnalRepository,
        groupId: Int,
        userId: Int,
        groupName: String,
        label: String,
        widgetId: Int,
        storedSubject: Int?,
    ): Snapshot {
        val summaryResult = repository.summary(groupId)
        if (summaryResult is ApiResult.Failure) return failureSnapshot(summaryResult.message)
        val summary = (summaryResult as ApiResult.Success).data
        val allSubjects = subjectList(summary, userId)
        val chips = allSubjects.take(5)
        val subject = chips.firstOrNull { it.id == storedSubject } ?: chips.firstOrNull()
            ?: return Snapshot(
                groupName = groupName.ifBlank { summary.group.name },
                dateLabel = label,
                footer = "Нет предметов",
                emptyText = "В группе пока нет предметов",
            )

        return when (val journalResult = repository.journal(groupId, subject.id)) {
            is ApiResult.Failure -> failureSnapshot(journalResult.message)
            is ApiResult.Success -> {
                val journal = journalResult.data
                val rows = journal.lessons
                    .sortedWith(
                        compareByDescending<ru.zhurnal.app.data.LessonDto> { it.date }
                            .thenByDescending { it.pairNumber },
                    )
                    .mapNotNull { lesson ->
                        val record = journal.records.firstOrNull {
                            it.lessonId == lesson.id && it.studentId == userId
                        } ?: return@mapNotNull null
                        if (record.grade == null && !record.absent) return@mapNotNull null
                        WidgetRow(
                            left = "${formatDateShort(lesson.date)} · ${lesson.pairNumber}п",
                            title = lesson.topic?.takeIf { it.isNotBlank() } ?: subject.name,
                            right = if (record.absent) "Н" else record.grade?.toString().orEmpty(),
                            accent = if (record.absent) "#F2B8B5" else gradeAccent(record.grade),
                        )
                    }
                Snapshot(
                    groupName = groupName.ifBlank { journal.group.name },
                    dateLabel = label,
                    rows = rows,
                    subjects = chips,
                    selectedSubjectId = subject.id,
                    selectedSubjectName = subject.name,
                    selectedAverage = subject.average,
                    footer = "${subject.name} · средний ${formatAverage(subject.average)}",
                    emptyText = "Оценок по предмету пока нет",
                )
            }
        }
    }

    private fun subjectList(summary: SummaryResponse, userId: Int): List<WidgetSubject> {
        val personal = summary.students.firstOrNull { it.id == userId }
        return summary.subjects.map { subject ->
            val stat = personal?.subjectAverages?.firstOrNull { it.subjectId == subject.id }
            WidgetSubject(
                id = subject.id,
                name = subject.name,
                average = stat?.average ?: subject.average,
                gradesCount = stat?.gradesCount ?: subject.gradesCount,
            )
        }
    }

    private fun dateLabel(date: LocalDate): String =
        date.format(dateFormatter).replaceFirstChar { it.uppercase() }

    private fun averageAccent(average: Double?): String? = when {
        average == null -> null
        average >= 8.5 -> "#84C7B3"
        average >= 6.5 -> "#A9C9E6"
        average >= 4.5 -> "#E8B76C"
        else -> "#F2B8B5"
    }

    private fun gradeAccent(grade: Int?): String? = when {
        grade == null -> null
        grade >= 9 -> "#84C7B3"
        grade >= 7 -> "#A9C9E6"
        grade >= 5 -> "#E8B76C"
        else -> "#F2B8B5"
    }

    private fun render(
        context: Context,
        kind: WidgetKind,
        snapshot: Snapshot,
        widgetId: Int,
        selectedSubjectId: Int?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, kind.layout)
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, kind))

        views.setTextViewText(
            R.id.widget_group,
            if (snapshot.authorized) snapshot.groupName else "Журнал",
        )
        views.setTextViewText(R.id.widget_date, snapshot.dateLabel)
        views.setTextViewText(
            R.id.widget_footer,
            when {
                snapshot.loading -> "Обновление…"
                !snapshot.authorized -> "Войдите в приложение"
                snapshot.error != null -> "Не удалось обновить · нажмите ↻"
                else -> snapshot.footer
            },
        )
        views.setTextViewText(
            R.id.widget_empty,
            snapshot.emptyText.ifBlank { "Нет данных" },
        )
        val hasRows = snapshot.rows.isNotEmpty()
        views.setViewVisibility(R.id.widget_list, if (hasRows) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_empty, if (hasRows) View.GONE else View.VISIBLE)

        if (kind == WidgetKind.Large) {
            bindSubjects(context, views, snapshot, widgetId, selectedSubjectId)
        }
        bindList(context, views, widgetId)
        return views
    }

    private fun bindList(context: Context, views: RemoteViews, widgetId: Int) {
        val intent = Intent(context, WidgetListService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = Uri.parse("zhurnal-widget://$widgetId")
        }
        views.setRemoteAdapter(R.id.widget_list, intent)
    }

    private fun bindSubjects(
        context: Context,
        views: RemoteViews,
        snapshot: Snapshot,
        widgetId: Int,
        selectedSubjectId: Int?,
    ) {
        val chipIds = intArrayOf(
            R.id.widget_subject_1,
            R.id.widget_subject_2,
            R.id.widget_subject_3,
            R.id.widget_subject_4,
            R.id.widget_subject_5,
        )
        chipIds.forEachIndexed { index, chipId ->
            val subject = snapshot.subjects.getOrNull(index)
            if (subject == null) {
                views.setViewVisibility(chipId, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(chipId, View.VISIBLE)
            views.setTextViewText(chipId, subject.name)
            val active = subject.id == (snapshot.selectedSubjectId ?: selectedSubjectId)
            views.setInt(
                chipId,
                "setBackgroundColor",
                if (active) 0xFFE8B76C.toInt() else 0x1AFFFFFF,
            )
            views.setTextColor(
                chipId,
                if (active) 0xFF0B2236.toInt() else 0xFFDCE9F5.toInt(),
            )
            views.setOnClickPendingIntent(
                chipId,
                selectSubjectIntent(context, widgetId, subject.id, index),
            )
        }
    }

    private fun refreshIntent(context: Context, kind: WidgetKind): PendingIntent {
        val intent = Intent(context, kind.provider).apply {
            action = ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            100 + kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun selectSubjectIntent(
        context: Context,
        widgetId: Int,
        subjectId: Int,
        index: Int,
    ): PendingIntent {
        val intent = Intent(context, LargeWidgetProvider::class.java).apply {
            action = ACTION_SELECT_SUBJECT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(EXTRA_SUBJECT_ID, subjectId)
        }
        return PendingIntent.getBroadcast(
            context,
            3000 + widgetId * 10 + index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
