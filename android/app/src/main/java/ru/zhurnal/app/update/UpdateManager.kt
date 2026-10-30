package ru.zhurnal.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Скачивание и установка APK, опубликованного на нашем сервере. */
object UpdateManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        context: Context,
        url: String,
        versionCode: Int,
        onProgress: (Float?) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()
                ?.filter { it.name != "zhurnal-$versionCode.apk" }
                ?.forEach { it.delete() }

            val target = File(dir, "zhurnal-$versionCode.apk")
            if (target.exists() && target.length() > 0) {
                onProgress(1f)
                return@runCatching target
            }
            val partial = File(dir, "${target.name}.part")
            partial.delete()

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Сервер вернул ошибку ${response.code}")
                }
                val body = response.body ?: throw IOException("Пустой ответ сервера")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(
                                if (total > 0) {
                                    (downloaded.toDouble() / total).toFloat().coerceIn(0f, 1f)
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }

            if (partial.length() <= 0) throw IOException("Скачанный файл пуст")
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            target
        }
    }

    fun install(context: Context, file: File): Result<Unit> = runCatching {
        if (!file.exists() || file.length() <= 0) {
            throw IOException("Файл обновления не найден")
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
