package app.toetoetoe.luz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface { LightScreen() } } }
    }
}

@Composable
fun LightScreen() {
    val context = LocalContext.current
    val client = remember { LightClient(context.applicationContext) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    var url by remember { mutableStateOf(client.savedUrl()) }
    var entity by remember { mutableStateOf(client.savedEntity()) }
    var token by remember { mutableStateOf("") }
    var hex by remember { mutableStateOf("#F6A85B") }
    var brightness by remember { mutableFloatStateOf(70f) }
    var temperature by remember { mutableFloatStateOf(4000f) }
    var connected by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("color") }
    var status by remember { mutableStateOf("Conecta tu luz mediante Home Assistant") }
    var busy by remember { mutableStateOf(false) }
    var sceneName by remember { mutableStateOf("") }
    val prefs = remember { context.getSharedPreferences("scenes", 0) }
    var scenes by remember { mutableStateOf(prefs.getString("values", "") ?: "") }
    fun runTask(block: () -> JSONObject, done: (JSONObject) -> Unit) {
        if (busy) return
        busy = true
        executor.execute {
            val result = runCatching(block)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                busy = false
                result.onSuccess(done).onFailure { status = it.message ?: "No se pudo conectar" }
            }
        }
    }
    fun credential(): String = token.ifBlank { require(url.trimEnd('/') == client.savedUrl().trimEnd('/')) { "Ingresa el token para esta dirección" }; client.savedToken() }
    fun send(power: Boolean, powerOnly: Boolean = false) {
        if (!connected) return
        if (!hex.matches(Regex("#[0-9a-fA-F]{6}"))) { status = "Ingresa un HEX válido"; return }
        val body = JSONObject().put("entity_id", entity)
        if (power && !powerOnly) {
            body.put("brightness_pct", brightness.toInt())
            if (mode == "temperature") body.put("color_temp_kelvin", temperature.toInt())
            else body.put("rgb_color", JSONArray().put(hex.substring(1,3).toInt(16)).put(hex.substring(3,5).toInt(16)).put(hex.substring(5,7).toInt(16)))
        }
        runTask({ client.request(url, credential(), "/api/services/light/" + if (power) "turn_on" else "turn_off", body) }) {
            on = power; status = "Comando enviado"
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Luz", style = MaterialTheme.typography.headlineLarge)
        Text(status, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(url, { url = it; connected = false }, label = { Text("Dirección HTTPS de Home Assistant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it; connected = false }, label = { Text("Token (vacío para usar el guardado)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(entity, { entity = it; connected = false }, label = { Text("Entidad, por ejemplo light.salon") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                if (!entity.matches(Regex("light\\.[a-z0-9_]+"))) { status = "Entidad inválida" }
                else runTask({
                    val selected = credential(); val state = client.request(url, selected, "/api/states/$entity")
                    check(state.optString("state") in listOf("on", "off")) { "Luz no disponible" }
                    client.save(url, selected, entity); state
                }) { state -> connected = true; on = state.optString("state") == "on"; token = ""; status = "Conectada" }
            }) { Text("Conectar") }
            OutlinedButton(onClick = { client.forget(); token = ""; url = ""; entity = ""; connected = false; status = "Conexión eliminada" }) { Text("Olvidar") }
        }
        val preview = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
        Spacer(Modifier.fillMaxWidth().height(72.dp).background(preview))
        OutlinedTextField(hex, { hex = it; mode = "color" }, label = { Text("Color HEX") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Brillo: ${brightness.toInt()} %")
        Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 1f..100f)
        Text("Blanco: ${temperature.toInt()} K")
        Slider(value = temperature, onValueChange = { temperature = it; mode = "temperature" }, valueRange = 1700f..6500f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = connected && !busy, onClick = { send(true) }) { Text("Aplicar") }
            OutlinedButton(enabled = connected && !busy, onClick = { send(!on, true) }) { Text(if (on) "Apagar" else "Encender") }
        }
        Text("Ambientes", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Lectura" to "#FFE3B0", "Noche" to "#E98260", "Creatividad" to "#C5AAFF").forEach { (name, color) ->
                TextButton(onClick = { hex = color; mode = "color" }) { Text(name) }
            }
        }
        OutlinedTextField(sceneName, { sceneName = it.take(30) }, label = { Text("Nombre del ambiente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            if (sceneName.isNotBlank() && hex.matches(Regex("#[0-9a-fA-F]{6}"))) {
                val name = sceneName.replace("|", " ").replace("\n", " ")
                scenes += "$name|$hex|${brightness.toInt()}\n"; prefs.edit().putString("values", scenes).apply(); sceneName = ""
            }
        }) { Text("Guardar ambiente") }
        scenes.lines().filter { it.isNotBlank() }.forEach { row ->
            val fields = row.split('|')
            if (fields.size == 3) Row {
                TextButton(onClick = { hex = fields[1]; brightness = fields[2].toFloatOrNull()?.coerceIn(1f, 100f) ?: 70f; mode = "color" }) { Text(fields[0]) }
                TextButton(onClick = { scenes = scenes.lines().filter { it != row && it.isNotBlank() }.joinToString("\n", postfix = "\n"); prefs.edit().putString("values", scenes).apply() }) { Text("Eliminar") }
            }
        }
        Text("Requiere una luz compatible configurada en Home Assistant.", style = MaterialTheme.typography.bodySmall)
    }
}
