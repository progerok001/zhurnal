package ru.zhurnal.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ZhurnalApi {
    @GET("api/health")
    suspend fun health(): HealthResponse

    @GET("api/app/update")
    suspend fun appUpdate(): AppUpdateDto

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("api/auth/check-code")
    suspend fun checkCode(@Body body: CheckCodeRequest): CheckCodeResponse

    @GET("api/auth/me")
    suspend fun me(): MeResponse

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): SimpleOk

    @PATCH("api/auth/me/avatar")
    suspend fun updateMyAvatar(@Body body: UpdateAvatarRequest): UserResponse

    @GET("api/substitutions")
    suspend fun substitutions(@Query("refresh") refresh: Int? = null): SubstitutionResponse

    @GET("api/schedule/groups")
    suspend fun scheduleGroups(): ScheduleGroupsResponse

    @GET("api/schedule")
    suspend fun schedule(
        @Query("groupId") groupId: Int,
        @Query("week") week: String,
        @Query("weekStart") weekStart: String? = null,
    ): ScheduleResponse

    @POST("api/schedule")
    suspend fun createSchedule(@Body body: CreateScheduleRequest): IdResponse

    @PATCH("api/schedule/{id}")
    suspend fun updateSchedule(@Path("id") id: Int, @Body body: UpdateScheduleRequest): SimpleOk

    @DELETE("api/schedule/{id}")
    suspend fun deleteSchedule(@Path("id") id: Int): SimpleOk

    @PUT("api/schedule/times")
    suspend fun saveScheduleTimes(@Body body: ScheduleTimesRequest): SimpleOk

    @GET("api/homework")
    suspend fun homework(
        @Query("groupId") groupId: Int,
        @Query("subjectId") subjectId: Int,
        @Query("date") date: String? = null,
    ): HomeworkResponse

    @POST("api/homework")
    suspend fun createHomework(@Body body: CreateHomeworkRequest): IdResponse

    @PATCH("api/homework/{id}")
    suspend fun updateHomework(
        @Path("id") id: Int,
        @Body body: UpdateHomeworkRequest,
    ): SimpleOk

    @DELETE("api/homework/{id}")
    suspend fun deleteHomework(@Path("id") id: Int): SimpleOk

    @GET("api/homework/top")
    suspend fun homeworkTop(
        @Query("groupId") groupId: Int,
        @Query("period") period: String,
    ): HomeworkTopResponse

    @GET("api/audit")
    suspend fun audit(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("entityType") entityType: String? = null,
        @Query("groupId") groupId: Int? = null,
        @Query("q") query: String? = null,
    ): AuditResponse

    @GET("api/groups")
    suspend fun groups(): GroupsResponse

    @POST("api/groups")
    suspend fun createGroup(@Body body: CreateGroupRequest): GroupResponse

    @PATCH("api/groups/{id}")
    suspend fun updateGroup(@Path("id") id: Int, @Body body: UpdateGroupRequest): GroupResponse

    @DELETE("api/groups/{id}")
    suspend fun deleteGroup(@Path("id") id: Int): SimpleOk

    @GET("api/subjects")
    suspend fun subjects(@Query("groupId") groupId: Int? = null): SubjectsResponse

    @POST("api/subjects")
    suspend fun createSubject(@Body body: CreateSubjectRequest): IdResponse

    @PATCH("api/subjects/{id}")
    suspend fun updateSubject(@Path("id") id: Int, @Body body: UpdateSubjectRequest): SimpleOk

    @DELETE("api/subjects/{id}")
    suspend fun deleteSubject(@Path("id") id: Int): SimpleOk

    @GET("api/users")
    suspend fun users(
        @Query("role") role: String? = null,
        @Query("groupId") groupId: Int? = null,
        @Query("q") query: String? = null,
    ): UsersResponse

    @POST("api/users")
    suspend fun createUser(@Body body: CreateUserRequest): UserResponse

    @PATCH("api/users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body body: UpdateUserRequest): UserResponse

    @DELETE("api/users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): SimpleOk

    @GET("api/groups/{id}/journal")
    suspend fun journal(
        @Path("id") groupId: Int,
        @Query("subjectId") subjectId: Int? = null,
    ): JournalResponse

    @GET("api/groups/{id}/summary")
    suspend fun summary(@Path("id") groupId: Int): SummaryResponse

    @POST("api/lessons")
    suspend fun createLesson(@Body body: CreateLessonRequest): LessonResponse

    @PATCH("api/lessons/{id}")
    suspend fun updateLesson(@Path("id") id: Int, @Body body: UpdateLessonRequest): LessonResponse

    @DELETE("api/lessons/{id}")
    suspend fun deleteLesson(@Path("id") id: Int): SimpleOk

    @PUT("api/records")
    suspend fun putRecord(@Body body: RecordRequest): RecordResponse
}
