package com.school.birthdaywidget

import android.content.Context
import androidx.work.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SyncWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result = try {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) Tasks.await(auth.signInAnonymously())
        val snap = Tasks.await(
            FirebaseFirestore.getInstance().collection("students")
                .whereIn("birthMD", windowMDs()).get()
        )
        val arr = JSONArray()
        snap.documents.sortedBy { it.getString("name") ?: "" }.forEach {
            arr.put(
                JSONObject()
                    .put("md", it.getString("birthMD") ?: "")
                    .put("n", it.getString("name") ?: "")
                    .put("s", it.getString("standard") ?: "")
            )
        }
        applicationContext.getSharedPreferences("bday", 0).edit()
            .putString("list", arr.toString()).apply()
        BirthdayWidget.refresh(applicationContext)
        Result.success()
    } catch (e: Exception) { Result.retry() }
}

object SyncScheduler {
    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    fun now(c: Context) {
        WorkManager.getInstance(c).enqueueUniqueWork(
            "now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(net).build()
        )
    }
    fun periodic(c: Context) {
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(
            "periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS).setConstraints(net).build()
        )
    }
}
