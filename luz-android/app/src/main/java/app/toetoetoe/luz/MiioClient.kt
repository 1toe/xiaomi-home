package app.toetoetoe.luz

import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Control local de una color5 ya configurada en Xiaomi Home. */
class MiioClient(private val ip: String, token: String) {
    private val tokenBytes: ByteArray
    init {
        require(ip.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))) { "Ingresa una IP local válida" }
        val address = InetAddress.getByName(ip)
        require(address.isSiteLocalAddress && ip.split('.').all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }) { "Ingresa una IP privada de tu red" }
        require(token.matches(Regex("[0-9a-fA-F]{32}"))) { "El token debe tener 32 caracteres hexadecimales" }
        tokenBytes = ByteArray(16) { token.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("MD5").digest(bytes)
    private fun exchange(socket: DatagramSocket, data: ByteArray): ByteArray {
        socket.send(DatagramPacket(data, data.size))
        val packet = DatagramPacket(ByteArray(8192), 8192)
        socket.receive(packet)
        return packet.data.copyOf(packet.length)
    }
    private fun call(method: String, params: JSONArray): JSONObject {
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName(ip), 54321)
            socket.soTimeout = 5000
            val hello = ByteArray(32) { 0xff.toByte() }.apply { this[0] = 0x21; this[1] = 0x31; this[2] = 0; this[3] = 32 }
            val reply = exchange(socket, hello)
            check(reply.size == 32 && reply[0] == 0x21.toByte() && reply[1] == 0x31.toByte()) { "La luz no respondió correctamente" }
            val body = JSONObject().put("id", 1).put("method", method).put("params", params).toString().toByteArray(Charsets.UTF_8)
            val key = digest(tokenBytes)
            val iv = digest(key + tokenBytes)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val encrypted = cipher.doFinal(body)
            val header = ByteBuffer.allocate(16).putShort(0x2131.toShort()).putShort((32 + encrypted.size).toShort())
                .putInt(0).put(reply, 8, 8).array()
            val response = exchange(socket, header + digest(header + tokenBytes + encrypted) + encrypted)
            check(response.size >= 48 && response[0] == 0x21.toByte() && response[1] == 0x31.toByte() &&
                ((response[2].toInt() and 255) shl 8 or (response[3].toInt() and 255)) == response.size &&
                response.copyOfRange(8, 12).contentEquals(reply.copyOfRange(8, 12))) { "Respuesta inválida de la luz" }
            val payload = response.copyOfRange(32, response.size)
            check(MessageDigest.isEqual(response.copyOfRange(16, 32), digest(response.copyOfRange(0, 16) + tokenBytes + payload))) { "Token incorrecto o respuesta inválida" }
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val result = JSONObject(String(cipher.doFinal(payload), Charsets.UTF_8).trimEnd('\u0000'))
            check(result.optInt("id", -1) == 1 && !result.has("error")) { "La luz rechazó la operación" }
            return result
        }
    }
    fun verify(): JSONObject {
        val info = call("miIO.info", JSONArray()).getJSONObject("result")
        check(info.optString("model") == "yeelink.light.color5") { "Modelo incompatible: ${info.optString("model", "desconocido")}" }
        val result = call("get_properties", JSONArray().put(property(1))).getJSONArray("result").getJSONObject(0)
        check(result.optInt("code", -1) == 0 && result.has("value")) { "No se pudo leer el estado" }
        return JSONObject().put("on", result.getBoolean("value"))
    }
    private fun property(id: Int) = JSONObject().put("did", "light-$id").put("siid", 2).put("piid", id)
    fun set(power: Boolean, brightness: Int?, color: Int?, temperature: Int?) {
        val params = JSONArray().put(property(1).put("value", power))
        if (power && brightness != null) params.put(property(2).put("value", brightness.coerceIn(1, 100)))
        if (power && color != null) params.put(property(4).put("value", color))
        if (power && temperature != null) params.put(property(3).put("value", temperature.coerceIn(1700, 6500)))
        val result = call("set_properties", params).getJSONArray("result")
        check(result.length() == params.length()) { "Respuesta incompleta de la luz" }
        for (i in 0 until result.length()) check(result.getJSONObject(i).optInt("code", -1) == 0) { "La luz no aceptó todos los cambios" }
    }
}
