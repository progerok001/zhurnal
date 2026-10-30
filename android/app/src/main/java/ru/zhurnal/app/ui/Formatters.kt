package ru.zhurnal.app.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDateFormatter = DateTimeFormatter.ofPattern("dd.MM", Locale("ru"))
private val longDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale("ru"))
private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale("ru"))

fun formatDateShort(iso: String): String = runCatching {
    LocalDate.parse(iso).format(shortDateFormatter)
}.getOrDefault(iso)

fun formatDateLong(iso: String): String = runCatching {
    LocalDate.parse(iso).format(longDateFormatter)
}.getOrDefault(iso)

fun formatWeekday(iso: String): String = runCatching {
    LocalDate.parse(iso).format(weekdayFormatter).replaceFirstChar { it.uppercase() }
}.getOrDefault("")

/** Понимает и ISO-время, и формат SQLite datetime('now') — оба в UTC. */
fun formatDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return runCatching {
        val instant = if (raw.contains('T')) {
            java.time.Instant.parse(
                if (raw.endsWith("Z") || raw.contains('+')) raw else "${raw}Z",
            )
        } else {
            java.time.LocalDateTime.parse(raw.replace(' ', 'T'))
                .toInstant(java.time.ZoneOffset.UTC)
        }
        instant.atZone(java.time.ZoneId.systemDefault()).format(dateTimeFormatter)
    }.getOrDefault(raw)
}

fun formatAverage(value: Double?): String {
    if (value == null) return "—"
    return String.format(Locale.US, "%.1f", value).replace('.', ',')
}

fun pluralPairs(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "пара"
        mod10 in 2..4 && mod100 !in 12..14 -> "пары"
        else -> "пар"
    }
}

fun pluralHours(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "час"
        mod10 in 2..4 && mod100 !in 12..14 -> "часа"
        else -> "часов"
    }
}

fun pluralStudents(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "обучающийся"
        mod10 in 2..4 && mod100 !in 12..14 -> "обучающихся"
        else -> "обучающихся"
    }
}

fun pluralSubjects(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "предмет"
        mod10 in 2..4 && mod100 !in 12..14 -> "предмета"
        else -> "предметов"
    }
}

fun todayLongLabel(): String {
    val formatted = LocalDate.now()
        .format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru")))
    return formatted.replaceFirstChar { it.uppercase() }
}

fun todayIso(): String = LocalDate.now().toString()
