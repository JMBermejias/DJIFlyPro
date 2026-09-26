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
        require(validation.isValid) { "Cannot save an invalid mission: ${validation.errors.joinToString("; ")}" }
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
        file.appendText(gson.toJson(payload) + "\n", Charsets.UTF_8)
    }
}
