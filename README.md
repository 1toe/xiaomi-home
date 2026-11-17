# Xiaomi Home · Luz

Interfaz de iluminación con selector HEX y RGB, brillo y ambientes guardados en el navegador. El control físico utiliza una entidad de luz de Home Assistant ya configurada. La compatibilidad depende del modelo y de las capacidades que exponga esa entidad; el APK de Xiaomi Home no identifica la lámpara. La vista previa no controla el dispositivo.

## Ejecutar

Se requiere Node.js 24.22.1 o superior compatible con el proyecto y una instalación de Home Assistant con la luz integrada. Crear un token de acceso de larga duración en el perfil de Home Assistant. En una terminal local:

```bash
npm run build
HA_URL=http://127.0.0.1:8123 HA_TOKEN=tu_token npm start
```

Abrir http://localhost:4173 y escribir la entidad, por ejemplo `light.salon`. El servidor solo escucha en el equipo local y mantiene el token fuera del navegador. No compartas el token ni publiques el servidor. Sin configuración de Home Assistant se puede usar la vista previa y guardar ambientes. El brillo y el color dependen de las capacidades de la lámpara. Para acceder desde otro dispositivo se requiere una solución de acceso seguro adicional.

## Compilación

`npm test` verifica el servidor y `npm run build` genera `dist/`. GitHub Actions guarda `releases/` como artefacto de cada ejecución; `SHA.txt` y el nombre del ZIP identifican el commit compilado. Al descargar el ZIP, extraerlo y ejecutar `node server.mjs` con las variables anteriores. Los artefactos tienen retención de 90 días. Una etiqueta `v*` publica además un release permanente con el ZIP y el SHA.

## REA

El repositorio fija `rea-agents` en 6.9.0. Ejecuta `npm install` y después `npm run rea:setup:codex` o `npm run rea:setup:claude`; verifica con `npm run rea:doctor:codex` o `npm run rea:doctor:claude`.
