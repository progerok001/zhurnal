package ru.zhurnal.app.ui

import ru.zhurnal.app.data.ScheduleTimeDto
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Стандартное время пар. Администратор может задать своё для каждого дня. */
data class PairTime(val start: String, val end: String) {
    /** «8:00–9:30» — без ведущего нуля, как привычно в расписании. */
    val label: String get() = "${shortClock(start)}–${shortClock(end)}"
}

fun shortClock(raw: String): String {
    val parts = raw.trim().split(":")
    if (parts.size != 2) return raw
    val hours = parts[0].trimStart('0').ifEmpty { "0" }
    return "$hours:${parts[1]}"
}

val DefaultPairTimes: List<PairTime> = listOf(
    // 45 минут + перерыв 10 минут + 45 минут; между парами 10 минут,
    // после второй пары — обед 40 минут.
    PairTime("08:00", "09:40"),
    PairTime("09:50", "11:30"),
    PairTime("12:10", "13:50"),
    PairTime("14:00", "15:40"),
    PairTime("15:50", "17:30"),
    PairTime("17:40", "19:20"),
    PairTime("19:30", "21:10"),
    PairTime("21:20", "23:00"),
)

fun defaultPairTime(pair: Int): PairTime =
    DefaultPairTimes.getOrElse(pair - 1) { PairTime("", "") }

/** Время пары для конкретного дня: своё для дня или стандартное. */
fun effectivePairTime(times: List<ScheduleTimeDto>, day: Int, pair: Int): PairTime {
    val override = times.firstOrNull { it.day == day && it.pairNumber == pair }
    return if (override != null) {
        PairTime(override.startTime, override.endTime)
    } else {
        defaultPairTime(pair)
    }
}

val DayShortNames: List<String> = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
val DayFullNames: List<String> = listOf(
    "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье",
)

/** Нижняя неделя — нечётная неделя месяца, верхняя — чётная. */
fun weekOfMonth(date: LocalDate = LocalDate.now()): Int = ((date.dayOfMonth - 1) / 7) + 1

fun weekTypeFor(date: LocalDate = LocalDate.now()): String =
    if (weekOfMonth(date) % 2 == 1) "lower" else "upper"

fun weekTypeHint(type: String): String =
    if (type == "lower") "нечётная неделя месяца" else "чётная неделя месяца"

/** Понедельник учебной недели, в которой находится дата. */
fun mondayOf(date: LocalDate = LocalDate.now()): LocalDate = date.with(DayOfWeek.MONDAY)

/** «27 октября – 1 ноября» — диапазон учебной недели. */
fun weekRangeLabel(monday: LocalDate): String {
    val saturday = monday.plusDays(5)
    val monthName = monday.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
        .replaceFirstChar { it.lowercase() }
    return if (monday.month == saturday.month) {
        "${monday.dayOfMonth}–${saturday.dayOfMonth} $monthName"
    } else {
        val secondMonth = saturday.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
            .replaceFirstChar { it.lowercase() }
        "${monday.dayOfMonth} $monthName – ${saturday.dayOfMonth} $secondMonth"
    }
}

/** «4-я неделя октября» — номер недели месяца. */
fun weekMonthLabel(monday: LocalDate): String {
    val monthName = monday.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
        .replaceFirstChar { it.lowercase() }
    return "${weekOfMonth(monday)}-я неделя $monthName"
}

fun weekTypeTitleFor(monday: LocalDate): String =
    if (weekTypeFor(monday) == "lower") "Нижняя неделя" else "Верхняя неделя"

/** «Эта неделя», «Следующая неделя», «Прошлая неделя» — подсказка у стрелок. */
fun weekOffsetTitle(monday: LocalDate, today: LocalDate = LocalDate.now()): String {
    val diff = java.time.temporal.ChronoUnit.WEEKS.between(mondayOf(today), monday).toInt()
    return when (diff) {
        0 -> "Эта неделя"
        1 -> "Следующая неделя"
        2 -> "Через две недели"
        -1 -> "Прошлая неделя"
        -2 -> "Две недели назад"
        else -> if (diff > 0) "Через $diff нед." else "${-diff} нед. назад"
    }
}

/** Цвета для выделения пар. null — обычная ячейка. */
data class ScheduleColorOption(val hex: String?, val title: String)

val ScheduleColorOptions: List<ScheduleColorOption> = listOf(
    ScheduleColorOption("#B3261E", "Красный — пол пары"),
    ScheduleColorOption("#C08135", "Янтарный"),
    ScheduleColorOption("#2F6B5B", "Зелёный"),
    ScheduleColorOption("#345E8C", "Синий"),
    ScheduleColorOption("#7B4B6B", "Сливовый"),
    ScheduleColorOption("#5C5B53", "Графит"),
    ScheduleColorOption(null, "Без цвета"),
)
