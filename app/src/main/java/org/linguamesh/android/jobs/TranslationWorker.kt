package org.linguamesh.android.jobs

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import org.linguamesh.android.CoreGatewayFactory
import org.linguamesh.android.core.CoreErrorKind
import org.linguamesh.android.core.CoreEvent
import org.linguamesh.android.core.CoreGateway
import org.linguamesh.android.core.CoreGatewayException
import org.linguamesh.android.core.SecretResolver
import org.linguamesh.android.core.TranslationCommand
import org.linguamesh.android.preferences.DataStoreProviderProfileRepository
import org.linguamesh.android.preferences.ProviderProfileRepository
import org.linguamesh.android.security.AndroidKeystoreCredentialStore
import org.linguamesh.android.security.CredentialStore

class TranslationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private var gatewayOverride: CoreGateway? = null
    private var credentialStoreOverride: CredentialStore? = null
    private var profileRepositoryOverride: ProviderProfileRepository? = null

    internal constructor(
        appContext: Context,
        workerParams: WorkerParameters,
        gateway: CoreGateway,
        credentialStore: CredentialStore,
        profileRepository: ProviderProfileRepository,
    ) : this(appContext, workerParams) {
        gatewayOverride = gateway
        credentialStoreOverride = credentialStore
        profileRepositoryOverride = profileRepository
    }

    override suspend fun doWork(): Result {
        val job = inputData.toTranslationJob() ?: return failure("invalid_input")
        val gateway = gatewayOverride ?: CoreGatewayFactory.create()
        val credentialStore = credentialStoreOverride
            ?: AndroidKeystoreCredentialStore(applicationContext)
        val profileRepository = profileRepositoryOverride
            ?: DataStoreProviderProfileRepository(applicationContext)
        val outcome = try {
            TranslationJobRunner(gateway, credentialStore, profileRepository).run(job)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            TranslationJobOutcome.Failed("worker_exception")
        } finally {
            runCatching { gateway.close() }
        }
        return outcome.toWorkResult()
    }

    private fun TranslationJobOutcome.toWorkResult(): Result = when (this) {
        is TranslationJobOutcome.Completed -> Result.success(
            workDataOf(OUTPUT_TEXT to outputText, JOB_ID to inputData.getString(JOB_ID)),
        )
        is TranslationJobOutcome.Retry -> Result.retry()
        is TranslationJobOutcome.Failed -> failure(errorCode)
    }

    private fun failure(errorCode: String): Result = Result.failure(
        workDataOf(ERROR_CODE to errorCode, JOB_ID to inputData.getString(JOB_ID)),
    )

    companion object {
        const val JOB_ID = "job_id"
        const val PROFILE_ID = "profile_id"
        const val SOURCE_TEXT = "source_text"
        const val SOURCE_LOCALE = "source_locale"
        const val TARGET_LOCALE = "target_locale"
        const val OUTPUT_TEXT = "output_text"
        const val ERROR_CODE = "error_code"
        const val MAX_FIELD_LENGTH = 256
        const val MAX_TEXT_LENGTH = 4_096
        const val MAX_TEXT_BYTES = 6_000
        const val MAX_OUTPUT_LENGTH = 4_096

        fun inputData(
            jobId: String,
            profileId: String,
            sourceText: String,
            targetLocale: String,
            sourceLocale: String? = null,
        ): Data {
            require(jobId.isNotBlank() && jobId.length <= MAX_FIELD_LENGTH)
            require(profileId.isNotBlank() && profileId.length <= MAX_FIELD_LENGTH)
            require(sourceText.isNotBlank() && sourceText.isWithinWorkDataBounds())
            require(targetLocale.isNotBlank() && targetLocale.length <= MAX_FIELD_LENGTH)
            require(sourceLocale == null || sourceLocale.length <= MAX_FIELD_LENGTH)
            return Data.Builder()
                .putString(JOB_ID, jobId)
                .putString(PROFILE_ID, profileId)
                .putString(SOURCE_TEXT, sourceText)
                .putString(TARGET_LOCALE, targetLocale)
                .apply { sourceLocale?.let { putString(SOURCE_LOCALE, it) } }
                .build()
        }

        private fun Data.toTranslationJob(): TranslationJob? {
            val jobId = getString(JOB_ID) ?: return null
            val profileId = getString(PROFILE_ID) ?: return null
            val sourceText = getString(SOURCE_TEXT) ?: return null
            val targetLocale = getString(TARGET_LOCALE) ?: return null
            val sourceLocale = getString(SOURCE_LOCALE)
            if (
                jobId.isBlank() || jobId.length > MAX_FIELD_LENGTH ||
                profileId.isBlank() || profileId.length > MAX_FIELD_LENGTH ||
                sourceText.isBlank() || !sourceText.isWithinWorkDataBounds() ||
                targetLocale.isBlank() || targetLocale.length > MAX_FIELD_LENGTH ||
                sourceLocale?.length?.let { it > MAX_FIELD_LENGTH } == true
            ) {
                return null
            }
            return TranslationJob(jobId, profileId, sourceText, sourceLocale, targetLocale)
        }

        private fun String.isWithinWorkDataBounds(): Boolean =
            length <= MAX_TEXT_LENGTH && toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES
    }
}

internal class TranslationJobRunner(
    private val gateway: CoreGateway,
    private val credentialStore: CredentialStore,
    private val profileRepository: ProviderProfileRepository,
) {
    suspend fun run(job: TranslationJob): TranslationJobOutcome {
        val profile = profileRepository.state.first().profiles
            .firstOrNull { it.id == job.profileId }
            ?: return TranslationJobOutcome.Failed("profile_not_found")
        return try {
            gateway.saveProviderProfile(profile)
            val output = StringBuilder()
            var outputBytes = 0
            var terminal = false
            var failure: CoreErrorKind? = null
            gateway.translate(
                TranslationCommand(
                    sourceText = job.sourceText,
                    sourceLocale = job.sourceLocale,
                    targetLocale = job.targetLocale,
                    profile = profile,
                ),
                SecretResolver(credentialStore::resolve),
            ).collect { event ->
                when (event) {
                    CoreEvent.Started -> Unit
                    is CoreEvent.TextDelta -> {
                        output.append(event.text)
                        outputBytes += event.text.toByteArray(StandardCharsets.UTF_8).size
                        if (
                            output.length > TranslationWorker.MAX_OUTPUT_LENGTH ||
                            outputBytes > TranslationWorker.MAX_TEXT_BYTES
                        ) {
                            throw CoreGatewayException(
                                CoreErrorKind.Protocol,
                                "Translation output exceeded the worker limit",
                            )
                        }
                    }
                    CoreEvent.Completed -> terminal = true
                    CoreEvent.Cancelled -> {
                        terminal = true
                        failure = CoreErrorKind.Cancelled
                    }
                    is CoreEvent.Failed -> {
                        terminal = true
                        failure = event.kind
                    }
                }
            }
            if (!terminal) {
                TranslationJobOutcome.Failed("protocol")
            } else {
                failure.toOutcome(output.toString())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: CoreGatewayException) {
            error.kind.toOutcome("")
        } catch (_: Exception) {
            TranslationJobOutcome.Failed("worker_exception")
        }
    }

    private fun CoreErrorKind?.toOutcome(output: String): TranslationJobOutcome = when (this) {
        null -> TranslationJobOutcome.Completed(output)
        CoreErrorKind.Network -> TranslationJobOutcome.Retry("network")
        CoreErrorKind.Cancelled -> TranslationJobOutcome.Failed("cancelled")
        else -> TranslationJobOutcome.Failed(name.lowercase())
    }
}
