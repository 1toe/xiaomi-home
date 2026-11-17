package app.toetoetoe.luz

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LightClient(context: Context) {
    private val prefs = context.getSharedPreferences("luz_native", Context.MODE_PRIVATE)
    private val key: SecretKey
        get() {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias("luz-native-token")) {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                generator.init(KeyGenParameterSpec.Builder("luz-native-token", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generator.generateKey()
            }
            return ks.getKey("luz-native-token", null) as SecretKey
        }
    fun save(url: String, token: String, entity: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("url", url).putString("entity", entity)
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit())
    }
    fun saveLan(ip: String, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("lan_ip", ip)
            .putString("lan_token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("lan_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit())
    }
    fun lanIp(): String = prefs.getString("lan_ip", "") ?: ""
    fun lanToken(): String {
        val encrypted = prefs.getString("lan_token", null) ?: return ""
        val iv = prefs.getString("lan_iv", null) ?: return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
    }
    fun forgetLan() { prefs.edit().remove("lan_ip").remove("lan_token").remove("lan_iv").apply() }
    fun forget() { prefs.edit().remove("url").remove("entity").remove("token").remove("iv").apply() }
    fun savedUrl(): String = prefs.getString("url", "") ?: ""
    fun savedEntity(): String = prefs.getString("entity", "") ?: ""
    fun savedToken(): String {
        val encrypted = prefs.getString("token", null) ?: return ""
        val iv = prefs.getString("iv", null) ?: return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
    }
    fun request(base: String, token: String, path: String, body: JSONObject? = null): JSONObject {
        val url = URL(base.trimEnd('/') + path)
        require(url.protocol == "https" && url.userInfo == null && url.host.isNotEmpty() && url.query == null && url.ref == null)
        require(token.isNotBlank())
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 8000; connection.readTimeout = 8000; connection.instanceFollowRedirects = false
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
        if (body != null) {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        try {
            check(connection.responseCode in 200..299) { "Home Assistant respondió ${connection.responseCode}" }
            val data = connection.inputStream.bufferedReader().use { it.readText() }
            return if (data.startsWith("[")) JSONObject() else JSONObject(data)
        } finally { connection.disconnect() }
    }
}
