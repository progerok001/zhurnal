package ru.zhurnal.app.data

import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.zhurnal.app.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val message: String, val code: Int? = null) : ApiResult<Nothing>

    fun dataOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): String? = (this as? Failure)?.message
}

class ZhurnalRepository(private val store: SessionStore) {

    private val cache = LinkedHashMap<String, ZhurnalApi>()

    fun normalizeBaseUrl(raw: String): String? {
        var value = raw.trim()
        if (value.isEmpty()) return null
        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            value = "http://$value"
        }
        val url = value.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank()) return null
        return url.toString().trimEnd('/')
    }

    private fun apiFor(baseUrl: String, token: String?): ZhurnalApi {
        val key = "$baseUrl|${token.orEmpty()}"
        synchronized(cache) {
            cache[key]?.let { return it }
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .writeTimeout(40, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val builder = chain.request().newBuilder()
                        .header("Accept", "application/json")
                    if (!token.isNullOrBlank()) {
                        builder.header("Authorization", "Bearer $token")
                    }
                    chain.proceed(builder.build())
                }
                .apply {
                    if (BuildConfig.DEBUG) {
                        addInterceptor(
                            HttpLoggingInterceptor().apply {
                                level = HttpLoggingInterceptor.Level.BASIC
                            },
                        )
                    }
                }
                .build()

            val api = Retrofit.Builder()
                .baseUrl("${baseUrl.trimEnd('/')}/")
                .client(client)
                .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(ZhurnalApi::class.java)

            cache[key] = api
            if (cache.size > 6) {
                val eldest = cache.keys.firstOrNull()
                if (eldest != null) cache.remove(eldest)
            }
            return api
        }
    }

    private suspend fun api(): ZhurnalApi {
        val session = store.current()
        val baseUrl = session.baseUrl
            ?: throw IllegalStateException("Сервер не настроен")
        return apiFor(baseUrl, session.token)
    }

    suspend fun testConnection(baseUrl: String): ApiResult<HealthResponse> {
        val normalized = normalizeBaseUrl(baseUrl)
            ?: return ApiResult.Failure("Некорректный адрес сервера")
        return safeRequest { apiFor(normalized, null).health() }
    }

    /** Проверка обновления не требует входа: сервер отдаёт её публично. */
    suspend fun appUpdate(): ApiResult<AppUpdateDto> {
        val baseUrl = store.current().baseUrl
            ?: return ApiResult.Failure("Сначала настройте адрес сервера")
        return safeRequest { apiFor(baseUrl, null).appUpdate() }
    }

    /** Полный адрес файла обновления относительно настроенного сервера. */
    suspend fun updateDownloadUrl(raw: String): String? {
        val baseUrl = store.current().baseUrl ?: return null
        if (raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        ) {
            return raw
        }
        return "${baseUrl.trimEnd('/')}/${raw.trimStart('/')}"
    }

    suspend fun login(login: String, password: String): ApiResult<AuthResponse> {
        val session = store.current()
        val baseUrl = session.baseUrl
            ?: return ApiResult.Failure("Сначала настройте адрес сервера")
        val result = safeRequest {
            apiFor(baseUrl, null).login(LoginRequest(login = login.trim(), password = password))
        }
        if (result is ApiResult.Success) {
            store.saveAuth(result.data.token, result.data.user)
        }
        return result
    }

    suspend fun register(
        fullName: String,
        login: String,
        password: String,
        groupCode: String,
    ): ApiResult<AuthResponse> {
        val session = store.current()
        val baseUrl = session.baseUrl
            ?: return ApiResult.Failure("Сначала настройте адрес сервера")
        val result = safeRequest {
            apiFor(baseUrl, null).register(
                RegisterRequest(
                    fullName = fullName.trim(),
                    login = login.trim(),
                    password = password,
                    groupCode = groupCode.trim(),
                ),
            )
        }
        if (result is ApiResult.Success) {
            store.saveAuth(result.data.token, result.data.user)
        }
        return result
    }

    suspend fun checkCode(code: String): ApiResult<GroupRefDto> =
        safeRequest { api().checkCode(CheckCodeRequest(code.trim())).group }

    suspend fun refreshMe(): ApiResult<UserDto> {
        val result = safeRequest { api().me().user }
        if (result is ApiResult.Success) store.updateUser(result.data)
        return result
    }

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> =
        safeRequest { api().changePassword(ChangePasswordRequest(current, new)); Unit }

    suspend fun updateMyAvatar(avatar: String?): ApiResult<UserDto> {
        val result = safeRequest { api().updateMyAvatar(UpdateAvatarRequest(avatar)).user }
        return when (result) {
            is ApiResult.Success -> {
                val user = result.data
                if (user == null) {
                    ApiResult.Failure("Сервер не вернул профиль")
                } else {
                    store.updateUser(user)
                    ApiResult.Success(user)
                }
            }
            is ApiResult.Failure -> result
        }
    }

    suspend fun substitutions(refresh: Boolean = false): ApiResult<SubstitutionResponse> =
        safeRequest { api().substitutions(if (refresh) 1 else null) }

    suspend fun scheduleGroups(): ApiResult<List<ScheduleGroupDto>> =
        safeRequest { api().scheduleGroups().groups }

    suspend fun schedule(
        groupId: Int,
        week: String,
        weekStart: String,
    ): ApiResult<ScheduleResponse> =
        safeRequest { api().schedule(groupId, week, weekStart) }

    suspend fun createSchedule(
        groupId: Int,
        subjectId: Int,
        day: Int,
        pairNumber: Int,
        weekType: String,
        room: String?,
        color: String?,
        note: String?,
        secondSubjectId: Int?,
        secondRoom: String?,
    ): ApiResult<Int?> = safeRequest {
        api().createSchedule(
            CreateScheduleRequest(
                groupId = groupId,
                subjectId = subjectId,
                day = day,
                pairNumber = pairNumber,
                weekType = weekType,
                room = room?.trim()?.takeIf { it.isNotEmpty() },
                color = color,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                secondSubjectId = secondSubjectId,
                secondRoom = secondRoom?.trim()?.takeIf { it.isNotEmpty() },
            ),
        ).id
    }

    suspend fun updateSchedule(
        id: Int,
        subjectId: Int,
        day: Int,
        pairNumber: Int,
        weekType: String,
        room: String?,
        color: String?,
        note: String?,
        secondSubjectId: Int?,
        secondRoom: String?,
    ): ApiResult<Unit> = safeRequest {
        api().updateSchedule(
            id,
            UpdateScheduleRequest(
                subjectId = subjectId,
                day = day,
                pairNumber = pairNumber,
                weekType = weekType,
                // Пустая строка очищает поле на сервере.
                room = room?.trim().orEmpty(),
                color = color ?: "",
                note = note?.trim().orEmpty(),
                // 0 — «второй половины нет».
                secondSubjectId = secondSubjectId ?: 0,
                secondRoom = secondRoom?.trim().orEmpty(),
            ),
        )
        Unit
    }

    suspend fun saveScheduleTimes(
        groupId: Int,
        day: Int,
        entries: List<ScheduleTimeEntryRequest>,
    ): ApiResult<Unit> = safeRequest {
        api().saveScheduleTimes(
            ScheduleTimesRequest(groupId = groupId, day = day, entries = entries),
        )
        Unit
    }

    suspend fun deleteSchedule(id: Int): ApiResult<Unit> =
        safeRequest { api().deleteSchedule(id); Unit }

    suspend fun homework(
        groupId: Int,
        subjectId: Int,
        date: String? = null,
    ): ApiResult<HomeworkResponse> =
        safeRequest { api().homework(groupId, subjectId, date) }

    suspend fun createHomework(
        groupId: Int,
        subjectId: Int,
        text: String,
        dueDate: String?,
        lessonDate: String? = null,
    ): ApiResult<Int?> = safeRequest {
        api().createHomework(
            CreateHomeworkRequest(
                groupId = groupId,
                subjectId = subjectId,
                text = text.trim(),
                dueDate = dueDate,
                lessonDate = lessonDate,
            ),
        ).id
    }

    suspend fun updateHomework(id: Int, text: String, dueDate: String?): ApiResult<Unit> =
        safeRequest {
            api().updateHomework(
                id,
                // Пустая строка очищает срок сдачи на сервере.
                UpdateHomeworkRequest(text = text.trim(), dueDate = dueDate ?: ""),
            )
            Unit
        }

    suspend fun deleteHomework(id: Int): ApiResult<Unit> =
        safeRequest { api().deleteHomework(id); Unit }

    suspend fun homeworkTop(groupId: Int, period: String): ApiResult<HomeworkTopResponse> =
        safeRequest { api().homeworkTop(groupId, period) }

    suspend fun audit(
        limit: Int = 50,
        offset: Int = 0,
        entityType: String? = null,
        groupId: Int? = null,
        query: String? = null,
    ): ApiResult<AuditResponse> =
        safeRequest {
            api().audit(
                limit = limit,
                offset = offset,
                entityType = entityType?.takeIf { it != "all" },
                groupId = groupId,
                query = query?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

    suspend fun groups(): ApiResult<List<GroupDto>> =
        safeRequest { api().groups().groups }

    suspend fun journal(groupId: Int, subjectId: Int?): ApiResult<JournalResponse> =
        safeRequest { api().journal(groupId, subjectId) }

    suspend fun summary(groupId: Int): ApiResult<SummaryResponse> =
        safeRequest { api().summary(groupId) }

    suspend fun putRecord(
        lessonId: Int,
        studentId: Int,
        grade: Int?,
        absent: Boolean,
        reason: String?,
    ): ApiResult<RecordDto?> = safeRequest {
        api().putRecord(
            RecordRequest(
                lessonId = lessonId,
                studentId = studentId,
                grade = grade,
                absent = absent,
                reason = reason?.trim()?.takeIf { it.isNotEmpty() },
            ),
        ).record
    }

    suspend fun createLesson(
        subjectId: Int,
        date: String,
        pairNumber: Int,
        topic: String?,
    ): ApiResult<LessonDto?> = safeRequest {
        api().createLesson(
            CreateLessonRequest(
                subjectId = subjectId,
                date = date,
                pairNumber = pairNumber,
                topic = topic?.trim()?.takeIf { it.isNotEmpty() },
            ),
        ).lesson
    }

    suspend fun updateLesson(
        lessonId: Int,
        date: String?,
        pairNumber: Int?,
        topic: String?,
    ): ApiResult<LessonDto?> = safeRequest {
        api().updateLesson(
            lessonId,
            UpdateLessonRequest(date = date, pairNumber = pairNumber, topic = topic),
        ).lesson
    }

    suspend fun deleteLesson(lessonId: Int): ApiResult<Unit> =
        safeRequest { api().deleteLesson(lessonId); Unit }

    suspend fun subjects(groupId: Int? = null): ApiResult<List<SubjectDto>> =
        safeRequest { api().subjects(groupId).subjects }

    suspend fun users(role: String? = null, groupId: Int? = null, query: String? = null): ApiResult<List<UserDto>> =
        safeRequest { api().users(role, groupId, query).users }

    suspend fun createGroup(name: String, code: String?, avatar: String? = null): ApiResult<GroupDto?> =
        safeRequest {
            api().createGroup(
                CreateGroupRequest(
                    name = name.trim(),
                    code = code?.trim()?.takeIf { it.isNotEmpty() },
                    avatar = avatar,
                ),
            ).group
        }

    suspend fun updateGroup(
        id: Int,
        name: String?,
        code: String?,
        avatar: String? = null,
    ): ApiResult<GroupDto?> =
        safeRequest {
            api().updateGroup(
                id,
                UpdateGroupRequest(
                    name = name?.trim()?.takeIf { it.isNotEmpty() },
                    code = code?.trim()?.takeIf { it.isNotEmpty() },
                    avatar = avatar,
                ),
            ).group
        }

    suspend fun deleteGroup(id: Int): ApiResult<Unit> =
        safeRequest { api().deleteGroup(id); Unit }

    suspend fun createSubject(name: String, groupId: Int, teacherId: Int?): ApiResult<Int?> =
        safeRequest { api().createSubject(CreateSubjectRequest(name.trim(), groupId, teacherId)).id }

    suspend fun updateSubject(id: Int, name: String?, teacherId: Int?): ApiResult<Unit> =
        safeRequest { api().updateSubject(id, UpdateSubjectRequest(name, teacherId)); Unit }

    suspend fun deleteSubject(id: Int): ApiResult<Unit> =
        safeRequest { api().deleteSubject(id); Unit }

    suspend fun createUser(
        fullName: String,
        login: String,
        password: String,
        role: String,
        groupId: Int?,
        avatar: String? = null,
    ): ApiResult<UserDto?> = safeRequest {
        api().createUser(
            CreateUserRequest(
                fullName = fullName.trim(),
                login = login.trim(),
                password = password,
                role = role,
                groupId = groupId,
                avatar = avatar,
            ),
        ).user
    }

    suspend fun updateUser(
        id: Int,
        fullName: String? = null,
        login: String? = null,
        password: String? = null,
        role: String? = null,
        groupId: Int? = null,
        avatar: String? = null,
        isActive: Boolean? = null,
    ): ApiResult<UserDto?> = safeRequest {
        api().updateUser(
            id,
            UpdateUserRequest(
                fullName = fullName?.trim()?.takeIf { it.isNotEmpty() },
                login = login?.trim()?.takeIf { it.isNotEmpty() },
                password = password?.takeIf { it.isNotEmpty() },
                role = role,
                groupId = groupId,
                avatar = avatar,
                isActive = isActive,
            ),
        ).user
    }

    suspend fun deleteUser(id: Int): ApiResult<Unit> =
        safeRequest { api().deleteUser(id); Unit }

    suspend fun logout() = store.clearAuth()

    private suspend fun <T> safeRequest(block: suspend () -> T): ApiResult<T> {
        return try {
            ApiResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: HttpException) {
            // Аккаунт могли удалить или отключить: сбрасываем сохранённую сессию,
            // чтобы приложение не открывалось под несуществующим пользователем.
            if (error.code() == 401) store.clearAuth()
            ApiResult.Failure(httpMessage(error), error.code())
        } catch (error: IOException) {
            ApiResult.Failure("Нет связи с сервером. Проверьте адрес и подключение к интернету.")
        } catch (error: Exception) {
            ApiResult.Failure(error.message?.takeIf { it.isNotBlank() } ?: "Не удалось выполнить запрос")
        }
    }

    private fun httpMessage(error: HttpException): String {
        val raw = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
        val parsed = raw?.let {
            runCatching { AppJson.decodeFromString(ErrorResponse.serializer(), it).error }.getOrNull()
        }
        if (!parsed.isNullOrBlank()) return parsed
        return when (error.code()) {
            401 -> "Сессия истекла, войдите заново"
            403 -> "Недостаточно прав"
            404 -> "Данные не найдены"
            else -> "Ошибка сервера (${error.code()})"
        }
    }
}
