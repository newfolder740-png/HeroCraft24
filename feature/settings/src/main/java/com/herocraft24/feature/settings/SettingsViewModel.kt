package com.herocraft24.feature.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.herocraft24.core.data.ContentRepository
import com.herocraft24.core.model.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val repository = ContentRepository.get(app)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        repository.initialize()
        AppLocale.current = getLanguage()
    }

    fun getTheme(): String = prefs.getString("theme", "system") ?: "system"
    fun setTheme(theme: String) { prefs.edit().putString("theme", theme).apply() }

    fun getLanguage(): String = prefs.getString("language", "en") ?: "en"
    fun setLanguage(lang: String) {
        prefs.edit().putString("language", lang).commit()
        AppLocale.current = lang
    }

    fun getPackIds(): List<String> {
        val ids = mutableListOf<String>()
        try { app.assets.list("packs")?.forEach { ids.add(it) } } catch (_: Exception) {}
        return ids
    }

    fun getPackObjectCount(packId: String): Int {
        var count = 0
        count += repository.getClassIds().count { it.startsWith("$packId:") }
        count += repository.getSpeciesIds().count { it.startsWith("$packId:") }
        count += repository.getBackgroundIds().count { it.startsWith("$packId:") }
        count += repository.getFeatIds().count { it.startsWith("$packId:") }
        count += repository.getConditionIds().count { it.startsWith("$packId:") }
        count += repository.getMonsterIds().count { it.startsWith("$packId:") }
        count += repository.getMechanicIds().count { it.startsWith("$packId:") }
        count += repository.getSpellIds().count { it.startsWith("$packId:") }
        count += repository.getItemIds().count { it.startsWith("$packId:") }
        return count
    }

    suspend fun createBackup(): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(app.filesDir, "characters")
            val backupDir = File(app.getExternalFilesDir(null), "backups")
            backupDir.mkdirs()
            val backupFile = File(backupDir, "herocraft24_backup_${System.currentTimeMillis()}.json")
            val root = JSONObject()

            dir.listFiles()?.forEach { f ->
                try { root.put(f.nameWithoutExtension, JSONObject(f.readText())) } catch (_: Exception) {}
            }

            val spellFavs = app.getSharedPreferences("spells_favs", Context.MODE_PRIVATE)
                .getStringSet("ids", emptySet())?.joinToString(",") ?: ""
            root.put("_fav_spells", spellFavs)

            val equipFavs = app.getSharedPreferences("equip_favs", Context.MODE_PRIVATE)
                .getStringSet("ids", emptySet())?.joinToString(",") ?: ""
            root.put("_fav_equipment", equipFavs)

            val settingsObj = JSONObject()
            prefs.all.forEach { (k, v) -> settingsObj.put(k, v.toString()) }
            root.put("_settings", settingsObj)

            FileOutputStream(backupFile).use { it.write(root.toString(2).toByteArray()) }
            backupFile
        } catch (e: Exception) { null }
    }

    suspend fun restoreBackup(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(file.readText())
            val charDir = File(app.filesDir, "characters")
            charDir.mkdirs()

            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when {
                    key == "_fav_spells" -> {
                        val ids = root.optString(key, "").split(",").filter { it.isNotBlank() }.toSet()
                        app.getSharedPreferences("spells_favs", Context.MODE_PRIVATE)
                            .edit().putStringSet("ids", ids).apply()
                    }
                    key == "_fav_equipment" -> {
                        val ids = root.optString(key, "").split(",").filter { it.isNotBlank() }.toSet()
                        app.getSharedPreferences("equip_favs", Context.MODE_PRIVATE)
                            .edit().putStringSet("ids", ids).apply()
                    }
                    key == "_settings" -> {
                        val settingsObj = root.optJSONObject(key) ?: continue
                        val editor = prefs.edit()
                        val sk = settingsObj.keys()
                        while (sk.hasNext()) { val k = sk.next(); editor.putString(k, settingsObj.optString(k)) }
                        editor.apply()
                    }
                    !key.startsWith("_") -> {
                        File(charDir, "$key.json").writeText(root.getJSONObject(key).toString(2))
                    }
                }
            }
            true
        } catch (e: Exception) { false }
    }

    fun getAppVersion(): String = try {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) { "1.0.0" }
}