package zw.marketmoo.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import zw.marketmoo.app.ServiceLocator
import zw.marketmoo.app.data.local.SyncOp
import zw.marketmoo.app.data.local.SyncStatus
import zw.marketmoo.app.data.net.ApiResult

/**
 * Pushes the operation log to the API (POST /v1/sync). Rules (Design Document 5.4):
 * - items stay Pending until signed in, and until a connection exists (WorkManager waits for the network);
 * - each operation carries a client UUID, so a retry after a dropped connection cannot create duplicates;
 * - the text record goes first; a listing photo is uploaded afterwards straight to object storage
 *   (with Data Saver on, only on an unmetered network such as Wi-Fi);
 * - the server answers per operation: applied or duplicate means Synced, rejected means Needs attention with the reason.
 * Demo mode (Sync status screen) marks items Synced without contacting any server, for presentations only.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val sl = ServiceLocator.get(applicationContext)
        val dao = sl.db.sync()
        val ops = dao.outstanding()

        if (sl.settings.demoSync) {
            ops.forEach { markDone(sl, it) }
            return Result.success()
        }
        if (!sl.session.isSignedIn) return Result.success() // Pending until the user signs in

        var needRetry = false
        for (chunk in ops.chunked(50)) {
            when (val res = sl.api.sync(chunk.map { Triple(it.id, it.entity, it.entityId to it.payload) })) {
                is ApiResult.Ok -> res.value.forEach { r ->
                    val op = chunk.firstOrNull { it.id == r.id } ?: return@forEach
                    if (r.status == "applied" || r.status == "duplicate") markDone(sl, op)
                    else {
                        dao.update(op.id, SyncStatus.FAILED.name, 1, r.error ?: "Rejected by server")
                        setEntityStatus(sl, op, SyncStatus.FAILED)
                    }
                }
                is ApiResult.Failure -> {
                    if (res.unauthorized) { chunk.forEach { dao.update(it.id, SyncStatus.PENDING.name, 0, "Sign in again to sync") }; return Result.success() }
                    chunk.forEach { dao.update(it.id, SyncStatus.PENDING.name, 1, res.message) }
                    needRetry = true // offline or server waking up: exponential backoff
                }
            }
        }
        if (!needRetry) uploadPendingPhotos(sl)
        return if (needRetry) Result.retry() else Result.success()
    }

    private suspend fun markDone(sl: ServiceLocator, op: SyncOp) {
        sl.db.sync().update(op.id, SyncStatus.SYNCED.name, 1, "")
        setEntityStatus(sl, op, SyncStatus.SYNCED)
    }

    private suspend fun setEntityStatus(sl: ServiceLocator, op: SyncOp, s: SyncStatus) {
        when (op.entity) {
            "record" -> sl.db.records().setStatus(op.entityId, s.name)
            "listing" -> sl.db.listings().setStatus(op.entityId, s.name)
        }
    }

    /** Photos go last and best-effort: the listing is already safe on the server and simply shows without a photo until this succeeds. */
    private suspend fun uploadPendingPhotos(sl: ServiceLocator) {
        val metered = applicationContext.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
        for (l in sl.db.listings().withPendingPhotos()) {
            val photo = l.photoPath?.let(::File)
            if (photo == null || !photo.exists()) { sl.db.listings().clearPhotoPath(l.id); continue }
            if (sl.settings.dataSaver && metered) { schedule(applicationContext, unmetered = true); return }
            val presign = (sl.api.presignPhoto(l.id) as? ApiResult.Ok)?.value ?: continue
            if (!sl.api.uploadPhoto(presign.uploadUrl, photo.readBytes())) continue
            // A second, idempotent listing operation tells the server which key holds the photo.
            val base = sl.db.sync().payloadFor(l.id) ?: continue
            val payload = JSONObject(base).put("photo_key", presign.key).toString()
            sl.db.sync().enqueue(SyncOp(UUID.randomUUID().toString(), "listing", l.id, payload))
            sl.db.listings().clearPhotoPath(l.id)
            photo.delete()
            enqueue(applicationContext)
        }
    }

    companion object {
        fun enqueue(context: Context) = schedule(context, unmetered = false)

        fun schedule(context: Context, unmetered: Boolean) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(if (unmetered) "marketmoo-sync-wifi" else "marketmoo-sync", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
