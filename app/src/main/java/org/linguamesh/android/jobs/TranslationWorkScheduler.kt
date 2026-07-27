package org.linguamesh.android.jobs

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.linguamesh.android.core.ProviderProfile

object TranslationWorkScheduler {
    fun enqueue(
        context: Context,
        jobId: String,
        profile: ProviderProfile,
        sourceText: String,
        targetLocale: String,
        sourceLocale: String? = null,
    ): UUID {
        val request = OneTimeWorkRequestBuilder<TranslationWorker>()
            .setInputData(
                TranslationWorker.inputData(
                    jobId = jobId,
                    profileId = profile.id,
                    sourceText = sourceText,
                    targetLocale = targetLocale,
                    sourceLocale = sourceLocale,
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(jobId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return request.id
    }

    fun uniqueWorkName(jobId: String): String {
        require(jobId.isNotBlank() && jobId.length <= TranslationWorker.MAX_FIELD_LENGTH)
        return "linguamesh-translation-$jobId"
    }
}

internal data class TranslationJob(
    val jobId: String,
    val profileId: String,
    val sourceText: String,
    val sourceLocale: String?,
    val targetLocale: String,
)

internal sealed interface TranslationJobOutcome {
    data class Completed(val outputText: String) : TranslationJobOutcome

    data class Retry(val errorCode: String) : TranslationJobOutcome

    data class Failed(val errorCode: String) : TranslationJobOutcome
}
