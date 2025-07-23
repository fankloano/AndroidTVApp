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
import io.objectbox.Box
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class PlaylistUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val accountId = inputData.getLong("accountId", -1L)
        if (accountId == -1L) return Result.failure()

        val accountBox: Box<Accounts> = ObjectBox.store.boxFor(Accounts::class.java)

        val account = accountBox.get(accountId) ?: return Result.failure()

        try {
            if (account.isStalker) {
                PlaylistUpdateRepository.updateStalkerData(account)
            } else if (account.isXtream) {
                PlaylistUpdateRepository.updateXtreamData(account)
            } else {
                PlaylistUpdateRepository.updatePlexAccount(account)
            }

            // Aktualisiere das Last Updated Datum in der Datenbank
            account.lastUpdatedDate = System.currentTimeMillis() / 1000
            accountBox.put(account)
            scheduleNextWorker(account)
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry() // Wiederholen, wenn ein Fehler auftritt
        }
    }

    private fun scheduleNextWorker(accounts: Accounts) {
        if (accounts.autoUpdateHours != 0) {
            val delay = accounts.autoUpdateHours * 3600000L
            val executionTimeMillis = System.currentTimeMillis() + delay

            // Formatierte Zeit (z.B. "HH:mm:ss")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val executionTime = dateFormat.format(Date(executionTimeMillis))
            val nextWorkRequest = OneTimeWorkRequestBuilder<PlaylistUpdateWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("accountId" to accounts.id))
                .addTag("autoupdate_${accounts.id}")
                .build()
            Log.d(
                "WORKER",
                "NEUER Worker erstellt für: ${accounts.name} WIRD AUSGEFÜHRT UM: $executionTime"
            )
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "autoupdate_${accounts.id}",
                ExistingWorkPolicy.REPLACE,
                nextWorkRequest
            )
        }
    }
}

