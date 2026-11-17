package app.toetoetoe.luz;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(name = "HomeAssistant")
public class HomeAssistantPlugin extends Plugin {
  private static final String ALIAS = "luz-ha-token-v1";
  private final ExecutorService io = Executors.newSingleThreadExecutor();

  private SharedPreferences prefs() { return getContext().getSharedPreferences("home_assistant", 0); }
  private SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
    if (!store.containsAlias(ALIAS)) {
      KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
      generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
      generator.generateKey();
    }
    return (SecretKey) store.getKey(ALIAS, null);
  }
  private void save(String base, String token) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
    String encrypted = Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    String iv = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
    if (!prefs().edit().putString("url", base).putString("token", encrypted).putString("iv", iv).commit()) throw new Exception();
  }
  private String token() throws Exception {
    String data = prefs().getString("token", ""), iv = prefs().getString("iv", "");
    if (data.isEmpty() || iv.isEmpty()) throw new Exception();
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
    return new String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), StandardCharsets.UTF_8);
  }
  private String validateBase(String input) throws Exception {
    URL url = new URL(input);
    if (!url.getProtocol().equals("https") || url.getUserInfo() != null || url.getHost().isEmpty() || url.getQuery() != null || url.getRef() != null) throw new Exception();
    return input.replaceAll("/+$", "");
  }
  private JSONObject request(String base, String auth, String path, JSONObject body) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(base + path).openConnection();
    connection.setConnectTimeout(8000); connection.setReadTimeout(8000); connection.setInstanceFollowRedirects(false);
    connection.setRequestProperty("Authorization", "Bearer " + auth); connection.setRequestProperty("Accept", "application/json");
    if (body != null) {
      connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
      try (OutputStream out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
    }
    try {
      int status = connection.getResponseCode(); if (status < 200 || status >= 300) throw new Exception();
      try (InputStream in = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[4096]; int n, total = 0;
        while ((n = in.read(buffer)) != -1) { total += n; if (total > 1048576) throw new Exception(); bytes.write(buffer, 0, n); }
        String response = bytes.toString("UTF-8");
        return response.startsWith("[") ? new JSONObject().put("ok", true) : new JSONObject(response);
      }
    } finally { connection.disconnect(); }
  }
  private boolean entityValid(String entity) { return entity != null && entity.matches("light\\.[a-z0-9_]+"); }
  private void async(PluginCall call, Task task) { io.execute(() -> { try { call.resolve(task.run()); } catch (Exception e) { call.reject("No se pudo conectar. Revisa la dirección HTTPS, el token y la entidad."); } }); }
  private interface Task { JSObject run() throws Exception; }
  @PluginMethod public void connect(PluginCall call) {
    String base = call.getString("url", ""), auth = call.getString("token", ""), entity = call.getString("entity", "");
    async(call, () -> {
      if (!entityValid(entity)) throw new Exception();
      String validated = validateBase(base);
      if (auth.isEmpty() && !validated.equals(prefs().getString("url", ""))) throw new Exception();
      String selectedToken = auth.isEmpty() ? token() : auth;
      JSONObject state = request(validated, selectedToken, "/api/states/" + entity, null);
      save(validated, selectedToken); return JSObject.fromJSONObject(state);
    });
  }
  @PluginMethod public void command(PluginCall call) {
    String entity = call.getString("entity", ""), color = call.getString("color", "");
    Boolean on = call.getBoolean("on"); Integer brightness = call.getInt("brightness");
    async(call, () -> {
      if (!entityValid(entity) || on == null || brightness == null || brightness < 1 || brightness > 100 || !color.matches("#[0-9a-fA-F]{6}")) throw new Exception();
      JSONObject data = new JSONObject().put("entity_id", entity);
      if (on) data.put("brightness_pct", brightness).put("rgb_color", new JSONArray().put(Integer.parseInt(color.substring(1,3),16)).put(Integer.parseInt(color.substring(3,5),16)).put(Integer.parseInt(color.substring(5,7),16)));
      request(validateBase(prefs().getString("url", "")), token(), "/api/services/light/" + (on ? "turn_on" : "turn_off"), data);
      JSObject result = new JSObject(); result.put("ok", true); return result;
    });
  }
  @PluginMethod public void saved(PluginCall call) { JSObject result = new JSObject(); result.put("url", prefs().getString("url", "")); result.put("configured", prefs().contains("token")); call.resolve(result); }
  @PluginMethod public void forget(PluginCall call) { prefs().edit().clear().apply(); call.resolve(); }
  @Override protected void handleOnDestroy() { io.shutdownNow(); super.handleOnDestroy(); }
}
