package zw.marketmoo.app.data.pack

import android.content.Context
import java.io.File
import java.security.MessageDigest
import zw.marketmoo.app.ServiceLocator
import zw.marketmoo.app.data.net.ApiResult
import zw.marketmoo.app.data.net.PackInfo

/** Checks the server's pack manifest and downloads newer district packs (resumable, checksum verified). */
object PackUpdater {
    data class Update(val info: PackInfo, val currentVersion: String)

    suspend fun check(sl: ServiceLocator): ApiResult<List<Update>> = when (val r = sl.api.packManifest()) {
        is ApiResult.Ok -> ApiResult.Ok(r.value.filter { it.version > sl.packs.version(it.district) }.map { Update(it, sl.packs.version(it.district)) })
        is ApiResult.Failure -> r
    }

    suspend fun download(ctx: Context, sl: ServiceLocator, u: Update, onProgress: (Long) -> Unit): Boolean {
        val dir = File(ctx.filesDir, "packs").also { it.mkdirs() }
        val part = File(dir, "pack_${u.info.district}.sqlite.part")
        // The manifest holds a short-lived signed URL, so refresh it if an earlier attempt left a partial file.
        val ok = sl.api.downloadResumable(u.info.url, part, u.info.size, onProgress)
        if (!ok || sha256(part) != u.info.sha256) {
            if (ok) part.delete() // complete but corrupt: start again next time
            return false
        }
        val target = File(dir, "pack_${u.info.district}.sqlite")
        sl.packs.close(u.info.district)
        part.copyTo(target, overwrite = true)
        part.delete()
        return true
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val buf = ByteArray(64 * 1024); while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
