package com.example.mj_player_tv.repository

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.mj_player_tv.database.ObjectBox
import com.example.mj_player_tv.database.entity.Accounts
import com.example.mj_player_tv.database.entity.EpgDataOB
import com.example.mj_player_tv.database.entity.EpgSource
import io.objectbox.Box
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class EpgUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {

        val epgSourceId = inputData.getLong("epgSourceId", -1L)
        if (epgSourceId == -1L) {
            return Result.failure()
        }

        val epgSourceBox: Box<EpgSource> = ObjectBox.store.boxFor(EpgSource::class.java)
        val epgSource = epgSourceBox.get(epgSourceId) ?: run {
            return Result.failure()
        }

        try {
            EpgUpdateRepository.downloadEpgFromExternalSource(epgSource.url, epgSource)

            // Aktualisiere das Last Updated Datum in der Datenbank
            epgSource.lastUpdatedDate = System.currentTimeMillis() / 1000
            epgSourceBox.put(epgSource)

            scheduleNextWorker(epgSource)
            return Result.success()
        } catch (e: Exception) {
            return Result.retry() // Wiederholen, wenn ein Fehler auftritt
        }
    }

    private fun scheduleNextWorker(epgSource: EpgSource) {
        val delay = epgSource.automaticUpdateDays * 3600000L
        val executionTimeMillis = System.currentTimeMillis() + delay
        val now = System.currentTimeMillis() / 1000L

        // Berechnung der Grenzzeiten in Unix-Timestamps
        val minTime = now - (epgSource.minDays * 24 * 60 * 60)
        val maxTime = now + (epgSource.maxDays * 24 * 60 * 60)
        // Formatierte Zeit (z.B. "HH:mm:ss")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val executionTime = dateFormat.format(Date(executionTimeMillis))
        val nextWorkRequest = OneTimeWorkRequestBuilder<EpgUpdateWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("epgSourceId" to epgSource.id))
            .addTag("autoupdate_${epgSource.name}")
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork("autoupdateepg_${epgSource.id}",ExistingWorkPolicy.REPLACE, nextWorkRequest)
    }
}

