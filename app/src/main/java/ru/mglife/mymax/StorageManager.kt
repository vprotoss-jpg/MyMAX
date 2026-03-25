package ru.mglife.mymax

import android.content.Context
import com.google.gson.Gson
import java.io.File

class StorageManager(
    private val context: Context,
    private val mls: MLSManager,
    private val crypto: CryptoManager,
    private val backupDepth: Int = 2
) {
    private val gson = Gson()
    private val NORMAL_PREFIX = "backup_v"
    private val EMERGENCY_FILE = "emergency_crash.dat"

    fun save(state: ChatState, isEmergency: Boolean = false) {
        val json = gson.toJson(state)
        android.util.Log.d("MAX_STORAGE", "Saving JSON: $json")
        
        // Получаем уникальный пароль из KeyStore через CryptoManager
        val backupPassword = crypto.getBackupPassword()
        val encryptedB64 = mls.createBackup(backupPassword, json)

        if (isEmergency) {
            File(context.filesDir, EMERGENCY_FILE).writeText(encryptedB64)
        } else {
            rotateAndSave(encryptedB64)
        }
    }

    private fun rotateAndSave(data: String) {
        for (i in backupDepth - 1 downTo 0) {
            val oldFile = File(context.filesDir, "$NORMAL_PREFIX$i.dat")
            if (oldFile.exists()) {
                val nextFile = File(context.filesDir, "$NORMAL_PREFIX${i + 1}.dat")
                oldFile.renameTo(nextFile)
            }
        }
        File(context.filesDir, "${NORMAL_PREFIX}0.dat").writeText(data)
        
        val limitFile = File(context.filesDir, "$NORMAL_PREFIX$backupDepth.dat")
        if (limitFile.exists()) limitFile.delete()
    }

    fun load(): ChatState? {
        // 1. Аварийный
        val emergency = File(context.filesDir, EMERGENCY_FILE)
        if (emergency.exists()) {
            val state = tryDecrypt(emergency.readText())
            if (state != null) {
                android.util.Log.d("MAX_STORAGE", "Restored from EMERGENCY file")
                emergency.delete()
                return state
            }
        }

        // 2. Обычные
        for (i in 0 until backupDepth) {
            val file = File(context.filesDir, "$NORMAL_PREFIX$i.dat")
            if (file.exists()) {
                val state = tryDecrypt(file.readText())
                if (state != null) {
                    android.util.Log.d("MAX_STORAGE", "Restored from normal backup v$i")
                    return state
                }
            }
        }
        return null
    }

    private fun tryDecrypt(b64: String): ChatState? {
        return try {
            // Для восстановления используем тот же пароль из KeyStore
            // Примечание: если ключ был изменен в настройках, старые бэкапы не откроются
            val backupPassword = crypto.getBackupPassword()
            
            // Мы используем decryptMessage для бэкапа, так как в Rust добавлена логика SYSTEM_INTERNAL
            val json = mls.decryptMessage(b64, "SYSTEM_INTERNAL", backupPassword)
            
            // ВАЖНО: Текущий JNI decryptMessage в Rust (lib.rs) использует ХАРДКОД "system_backup_key_123"
            // Нам нужно либо обновить Rust, чтобы он принимал пароль, либо 
            // передавать пароль через JNI. Но в текущей реализации Rust сам лезет за паролем?
            // Нет, в Rust методе decrypt_backup_internal пароль захардкожен.

            if (json.startsWith("ERROR_")) {
                android.util.Log.e("MAX_STORAGE", "Decrypt error: $json")
                return null
            }
            android.util.Log.d("MAX_STORAGE", "Decrypted JSON: $json")
            gson.fromJson(json, ChatState::class.java)
        } catch (e: Exception) {
            android.util.Log.e("MAX_STORAGE", "JSON parse error", e)
            null
        }
    }
}
