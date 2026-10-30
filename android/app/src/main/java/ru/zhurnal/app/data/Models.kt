package ru.zhurnal.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    coerceInputValues = true
    isLenient = true
}

@Serializable
data class UserDto(
    val id: Int,
    val login: String,
    val fullName: String,
    val role: String,
    val groupId: Int? = null,
    val groupName: String? = null,
    val avatar: String? = null,
    val isActive: Boolean = true,
    val createdAt: String? = null,
) {
    val isAdmin: Boolean get() = role == "admin"
    val isTeacher: Boolean get() = role == "teacher"
    val isStudent: Boolean get() = role == "student"
    val isHeadman: Boolean get() = role == "headman"

    /** Староста тоже учится в группе, поэтому группа показывается как у обучающегося. */
    val hasGroup: Boolean get() = isStudent || isHeadman

    val roleTitle: String
        get() = when (role) {
            "admin" -> "Администратор"
            "teacher" -> "Преподаватель"
            "headman" -> "Староста"
            else -> "Обучающийся"
        }

    val shortName: String
        get() {
            val parts = fullName.trim().split(Regex("\\s+"))
            return when {
                parts.size >= 2 -> "${parts[0]} ${parts[1].firstOrNull()?.let { "$it." } ?: ""}"
                else -> fullName
            }.trim()
        }
}

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserDto,
)

@Serializable
data class MeResponse(val user: UserDto)

@Serializable
data class GroupRefDto(
    val id: Int,
    val name: String,
    val code: String = "",
    val avatar: String? = null,
)

@Serializable
data class GroupDto(
    val id: Int,
    val name: String,
    val code: String = "",
    val avatar: String? = null,
    val studentsCount: Int = 0,
    val subjectsCount: Int = 0,
    val absentPairs: Int = 0,
    val average: Double? = null,
    val createdAt: String? = null,
)

@Serializable
data class GroupsResponse(val groups: List<GroupDto> = emptyList())

@Serializable
data class GroupResponse(val group: GroupDto? = null)

@Serializable
data class SubjectDto(
    val id: Int,
    val name: String,
    val groupId: Int? = null,
    val groupName: String? = null,
    val teacherId: Int? = null,
    val teacherName: String? = null,
    val lessonsCount: Int = 0,
)

@Serializable
data class SubjectsResponse(val subjects: List<SubjectDto> = emptyList())

@Serializable
data class StudentStatsDto(
    val id: Int,
    val fullName: String,
    val avatar: String? = null,
    val avg: Double? = null,
    val gradesCount: Int = 0,
    val absentPairs: Int = 0,
    val absentHours: Int = 0,
)

@Serializable
data class LessonDto(
    val id: Int,
    val subjectId: Int,
    val date: String,
    val pairNumber: Int,
    val topic: String? = null,
)

@Serializable
data class RecordDto(
    val id: Int,
    val lessonId: Int,
    val studentId: Int,
    val grade: Int? = null,
    val absent: Boolean = false,
    val reason: String? = null,
)

@Serializable
data class JournalStatsDto(
    val average: Double? = null,
    val gradesCount: Int = 0,
    val absentPairs: Int = 0,
    val studentsCount: Int = 0,
)

@Serializable
data class JournalResponse(
    val group: GroupRefDto,
    val subjects: List<SubjectDto> = emptyList(),
    val subject: SubjectDto? = null,
    val students: List<StudentStatsDto> = emptyList(),
    val lessons: List<LessonDto> = emptyList(),
    val records: List<RecordDto> = emptyList(),
    val stats: JournalStatsDto = JournalStatsDto(),
    val canEdit: Boolean = false,
)

@Serializable
data class SubjectAverageDto(
    val subjectId: Int,
    val average: Double? = null,
    val gradesCount: Int = 0,
)

@Serializable
data class SummarySubjectDto(
    val id: Int,
    val name: String,
    val teacherId: Int? = null,
    val teacherName: String? = null,
    val average: Double? = null,
    val gradesCount: Int = 0,
    val absentPairs: Int = 0,
)

@Serializable
data class SummaryStudentDto(
    val id: Int,
    val fullName: String,
    val avatar: String? = null,
    val avg: Double? = null,
    val gradesCount: Int = 0,
    val absentPairs: Int = 0,
    val absentHours: Int = 0,
    val subjectAverages: List<SubjectAverageDto> = emptyList(),
)

@Serializable
data class SummaryResponse(
    val group: GroupRefDto,
    val subjects: List<SummarySubjectDto> = emptyList(),
    val students: List<SummaryStudentDto> = emptyList(),
    val stats: JournalStatsDto = JournalStatsDto(),
)

@Serializable
data class UsersResponse(val users: List<UserDto> = emptyList())

@Serializable
data class UserResponse(val user: UserDto? = null)

@Serializable
data class LessonResponse(val lesson: LessonDto? = null)

@Serializable
data class RecordResponse(val record: RecordDto? = null)

@Serializable
data class IdResponse(val id: Int? = null)

@Serializable
data class SimpleOk(val ok: Boolean = true)

@Serializable
data class HealthCounts(val groups: Int = 0, val users: Int = 0)

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val name: String = "",
    val version: String = "",
    val counts: HealthCounts? = null,
)

@Serializable
data class AppUpdateDto(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val notes: String? = null,
    val mandatory: Boolean = true,
    val publishedAt: String? = null,
    val size: Long = 0,
    val sha256: String? = null,
)

@Serializable
data class CheckCodeResponse(val group: GroupRefDto)

@Serializable
data class ErrorResponse(val error: String = "")

@Serializable
data class LoginRequest(val login: String, val password: String)

@Serializable
data class RegisterRequest(
    val fullName: String,
    val login: String,
    val password: String,
    val groupCode: String,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class CheckCodeRequest(val code: String)

@Serializable
data class RecordRequest(
    val lessonId: Int,
    val studentId: Int,
    val grade: Int? = null,
    val absent: Boolean = false,
    val reason: String? = null,
)

@Serializable
data class CreateLessonRequest(
    val subjectId: Int,
    val date: String,
    val pairNumber: Int,
    val topic: String? = null,
)

@Serializable
data class UpdateLessonRequest(
    val date: String? = null,
    val pairNumber: Int? = null,
    val topic: String? = null,
)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val code: String? = null,
    val avatar: String? = null,
)

@Serializable
data class UpdateGroupRequest(
    val name: String? = null,
    val code: String? = null,
    val avatar: String? = null,
)

@Serializable
data class CreateSubjectRequest(
    val name: String,
    val groupId: Int,
    val teacherId: Int? = null,
)

@Serializable
data class UpdateSubjectRequest(
    val name: String? = null,
    // Без значения по умолчанию: null сериализуется явно и снимает преподавателя.
    val teacherId: Int?,
)

@Serializable
data class CreateUserRequest(
    val fullName: String,
    val login: String,
    val password: String,
    val role: String,
    val groupId: Int? = null,
    val avatar: String? = null,
)

@Serializable
data class UpdateUserRequest(
    val fullName: String? = null,
    val login: String? = null,
    val password: String? = null,
    val role: String? = null,
    val groupId: Int? = null,
    val avatar: String? = null,
    val isActive: Boolean? = null,
)

@Serializable
data class UpdateAvatarRequest(val avatar: String?)

@Serializable
data class SubstitutionResponse(
    val title: String = "",
    val fileUrl: String = "",
    val fileName: String = "",
    val publishedAt: String? = null,
    val text: String = "",
    val lines: List<String> = emptyList(),
    val blocks: List<DocBlockDto> = emptyList(),
    val sourcePage: String = "",
    val fetchedAt: String = "",
    val fromCache: Boolean = false,
)

@Serializable
data class DocCellDto(
    val text: String = "",
    val bold: Boolean = false,
    val colspan: Int = 1,
)

@Serializable
data class DocBlockDto(
    val type: String = "paragraph",
    val text: String = "",
    val bold: Boolean = false,
    val align: String? = null,
    val headerRows: Int = 0,
    val rows: List<List<DocCellDto>> = emptyList(),
)

@Serializable
data class ScheduleGroupDto(
    val id: Int,
    val name: String,
    val avatar: String? = null,
)

@Serializable
data class ScheduleGroupsResponse(val groups: List<ScheduleGroupDto> = emptyList())

@Serializable
data class ScheduleHomeworkPreviewDto(
    val id: Int,
    val text: String = "",
    val authorName: String = "",
    val createdAt: String = "",
)

@Serializable
data class ScheduleTimeDto(
    val day: Int,
    val pairNumber: Int,
    val startTime: String = "",
    val endTime: String = "",
)

@Serializable
data class ScheduleEntryDto(
    val id: Int,
    val groupId: Int,
    val subjectId: Int,
    val subjectName: String = "",
    val teacherId: Int? = null,
    val teacherName: String? = null,
    val day: Int,
    val pairNumber: Int,
    val weekType: String = "all",
    val room: String? = null,
    val color: String? = null,
    val note: String? = null,
    val lessonDate: String? = null,
    val split: Boolean = false,
    val secondSubjectId: Int? = null,
    val secondSubjectName: String? = null,
    val secondTeacherId: Int? = null,
    val secondTeacherName: String? = null,
    val secondRoom: String? = null,
    val homeworkCount: Int = 0,
    val secondHomeworkCount: Int = 0,
    val homeworkPreview: ScheduleHomeworkPreviewDto? = null,
)

@Serializable
data class ScheduleResponse(
    val group: GroupRefDto,
    val week: String = "upper",
    val entries: List<ScheduleEntryDto> = emptyList(),
    val times: List<ScheduleTimeDto> = emptyList(),
    val canEdit: Boolean = false,
    val canViewHomework: Boolean = false,
    val isOwnGroup: Boolean = false,
)

@Serializable
data class CreateScheduleRequest(
    val groupId: Int,
    val subjectId: Int,
    val day: Int,
    val pairNumber: Int,
    val weekType: String = "all",
    val room: String? = null,
    val color: String? = null,
    val note: String? = null,
    val secondSubjectId: Int? = null,
    val secondRoom: String? = null,
)

@Serializable
data class UpdateScheduleRequest(
    val subjectId: Int,
    val day: Int,
    val pairNumber: Int,
    val weekType: String = "all",
    val room: String? = null,
    val color: String? = null,
    val note: String? = null,
    val secondSubjectId: Int? = null,
    val secondRoom: String? = null,
)

@Serializable
data class ScheduleTimeEntryRequest(
    val pairNumber: Int,
    val startTime: String,
    val endTime: String,
)

@Serializable
data class ScheduleTimesRequest(
    val groupId: Int,
    val day: Int,
    val entries: List<ScheduleTimeEntryRequest>,
)

@Serializable
data class HomeworkDto(
    val id: Int,
    val groupId: Int,
    val subjectId: Int,
    val text: String = "",
    val dueDate: String? = null,
    val lessonDate: String? = null,
    val authorId: Int? = null,
    val authorName: String = "",
    val authorRole: String = "student",
    val authorAvatar: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val canEdit: Boolean = false,
)

@Serializable
data class HomeworkSubjectDto(
    val id: Int,
    val name: String,
    val teacherId: Int? = null,
    val teacherName: String? = null,
)

@Serializable
data class HomeworkResponse(
    val group: GroupRefDto,
    val subject: HomeworkSubjectDto,
    val date: String? = null,
    val items: List<HomeworkDto> = emptyList(),
    val canPost: Boolean = false,
    val canModerate: Boolean = false,
    val isOwnGroup: Boolean = false,
)

@Serializable
data class CreateHomeworkRequest(
    val groupId: Int,
    val subjectId: Int,
    val text: String,
    val dueDate: String? = null,
    val lessonDate: String? = null,
)

@Serializable
data class UpdateHomeworkRequest(
    val text: String,
    val dueDate: String? = null,
)

@Serializable
data class HomeworkTopEntryDto(
    val userId: Int? = null,
    val fullName: String = "",
    val role: String = "student",
    val avatar: String? = null,
    val count: Int = 0,
    val lastAt: String = "",
)

@Serializable
data class HomeworkTopResponse(
    val group: GroupRefDto,
    val period: String = "week",
    val since: String = "",
    val total: Int = 0,
    val items: List<HomeworkTopEntryDto> = emptyList(),
)

@Serializable
data class AuditEntryDto(
    val id: Int,
    val userId: Int? = null,
    val userLogin: String = "",
    val userName: String = "",
    val userRole: String = "",
    val action: String = "",
    val entityType: String = "",
    val entityId: Int? = null,
    val groupId: Int? = null,
    val groupName: String? = null,
    val summary: String = "",
    val details: JsonElement? = null,
    val createdAt: String = "",
)

@Serializable
data class AuditResponse(
    val entries: List<AuditEntryDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 50,
    val offset: Int = 0,
    val hasMore: Boolean = false,
)
