package dji.sampleV5.aircraft.pro.mission

import android.content.Context
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MissionStore(context: Context) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val root = File(context.filesDir, "missions").apply { mkdirs() }

    fun save(plan: MissionPlan): File {
        val validation = MissionValidator.validate(plan.request, plan)
        require(validation.isValid) { "No se puede guardar una misión no válida: ${validation.errors.joinToString("; ")}" }
        val file = File(root, "${safeName(plan.id)}.json")
        file.writeText(gson.toJson(plan), Charsets.UTF_8)
        return file
    }

    fun load(id: String): MissionPlan? {
        val file = File(root, "${safeName(id)}.json")
        if (!file.exists()) return null
        return runCatching { parseValidPlan(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun latest(): MissionPlan? = runCatching { list().maxByOrNull { it.createdAtEpochMs } }.getOrNull()

    fun list(): List<MissionPlan> = root.listFiles { file ->
        file.isFile && file.extension.equals("json", ignoreCase = true)
    }.orEmpty().mapNotNull { file ->
        runCatching { parseValidPlan(file.readText(Charsets.UTF_8)) }.getOrNull()
    }.sortedByDescending { it.createdAtEpochMs }

    fun delete(id: String): Boolean = File(root, "${safeName(id)}.json").delete()

    fun exportText(plan: MissionPlan): String = gson.toJson(plan)

    private fun parseValidPlan(raw: String): MissionPlan? {
        val plan = runCatching { gson.fromJson(raw, MissionPlan::class.java) }.getOrNull() ?: return null
        return plan.takeIf { candidate ->
            runCatching { MissionValidator.validate(candidate.request, candidate).isValid }.getOrDefault(false)
        }
    }

    private fun safeName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

class AuditLog(context: Context) {
    private val file = File(context.filesDir, "audit/audit.jsonl").apply { parentFile?.mkdirs() }
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    @Synchronized
    fun append(event: String, missionId: String?, details: Map<String, Any?> = emptyMap()) {
        val payload = linkedMapOf<String, Any?>(
            "timestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date()),
            "event" to event,
            "missionId" to missionId
        )
        payload.putAll(details)
        runCatching { file.appendText(gson.toJson(payload) + "\n", Charsets.UTF_8) }
        trim()
    }

    /**
     * Keeps the newest half of [MAX_BYTES] in the live trail and rotates the
     * rest into `audit.previous.jsonl`, always on a line boundary so no record
     * is left half written. An audit trail nobody can open is not an audit
     * trail, and a phone's storage is finite.
     */
    private fun trim() {
        if (!file.exists() || file.length() <= MAX_BYTES) return
        runCatching {
            val all = file.readBytes()
            val cut = all.size - (MAX_BYTES / 2).toInt()
            if (cut <= 0 || cut >= all.size) return@runCatching
            val newline = all.indexOfFirstFrom(NEWLINE, cut)
            if (newline < 0) return@runCatching
            File(file.parentFile, ARCHIVED_NAME).writeBytes(all.copyOfRange(0, newline + 1))
            file.writeBytes(all.copyOfRange(newline + 1, all.size))
        }
    }

    /** Index of [element] at or after [from], or -1. */
    private fun ByteArray.indexOfFirstFrom(element: Byte, from: Int): Int {
        for (i in from until size) {
            if (this[i] == element) return i
        }
        return -1
    }

    companion object {
        const val MAX_BYTES = 2L * 1024 * 1024
        const val ARCHIVED_NAME = "audit.previous.jsonl"
        private val NEWLINE = 0x0A.toByte()
    }
}
