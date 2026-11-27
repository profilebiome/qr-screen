# QR Screen — escáner de QR por pantalla (sin cámara)

App Android que lee códigos QR **directamente de la pantalla del celular**. No usa la cámara:
captura la pantalla con `MediaProjection` y decodifica los píxeles con ZXing.

## Qué hace

- **Escaneo en vivo**: botón «Escanear pantalla» → permiso de captura → aparece un visor flotante
  sobre cualquier app, navegador, PDF, video o chat. Cuando detecta un código, muestra una tarjeta
  con **Copiar / Abrir / Compartir**.
- **Detección automática**: primero mira el área del marco y después barre toda la pantalla
  (reducida y a resolución nativa), así que el QR no tiene que estar perfectamente centrado.
- **Modo ajuste**: desde la píldora «Ajustar» se puede mover y redimensionar el marco; el área se
  guarda para la próxima vez.
- **Desde imagen**: decodifica una foto o captura de la galería, o una imagen compartida a QR Screen.
- **Historial**: últimas 100 lecturas, guardadas solo en el dispositivo, con copiar/abrir/compartir.
- **Sin cámara**: el manifest no declara `android.permission.CAMERA`.

## Instalar

1. Copia `dist/QR-Screen-1.0.apk` al teléfono (cable, Drive, Telegram…).
2. Ábrelo y acepta «Instalar apps desconocidas» para el explorador/gestor de archivos.
3. Al abrir la app:
   - concede **Mostrar sobre otras apps** (visor y tarjeta de resultados),
   - concede **Notificaciones** (Android 13+),
   - al pulsar «Escanear pantalla», acepta la **captura de pantalla** del sistema.

> La aceptación de captura se pide cada vez que inicias el escáner: es un requisito de Android.
> La app nunca pide cámara ni acceso a internet.

## Cómo se usa

1. Pulsa **Escanear pantalla** y acepta la captura.
2. La app se minimiza sola; abre la app donde esté el QR.
3. Apunta con el marco (o deja que detecte en cualquier parte).
4. En la tarjeta: **Copiar**, **Abrir** (enlaces, teléfono, correo) o **Compartir**.
5. Para terminar: **✕** en la píldora superior, **Detener** en la notificación o en la app.

## Si no detecta

- Sube el **brillo** de la pantalla y evita filtros de luz nocturna muy agresivos.
- Usa **Ajustar** para encuadrar el código dentro del marco.
- Los QR muy pequeños o con mucho movimiento (video) tardan un poco más: la app reintenta cada
  ~0,2 s.
- En Xiaomi/Redmi, POCO, OPPO, vivo, Realme: activa «Inicio automático» y quita la optimización de
  batería para QR Screen, o el sistema puede cerrar el servicio en segundo plano.

## Compilar

Requisitos: JDK 17 y Android SDK con platform 34 y build-tools 34.0.0.

```bash
# local.properties con sdk.dir apuntando al SDK
./gradlew :app:assembleRelease
# APK firmado: app/build/outputs/apk/release/app-release.apk
```

Firma: `keystore/qrscreen.jks` (alias `qrscreen`, contraseñas en `app/build.gradle`).
Es un keystore propio para poder instalar actualizaciones encima sin desinstalar.

## Estructura

```
app/src/main/java/com/drako/qrscreen/
  MainActivity.java        UI principal, permisos, galería, historial
  ScreenScanService.java   servicio en primer plano + bucle de escaneo
  ScreenCapture.java       MediaProjection, VirtualDisplay, ImageReader, recorte de píxeles
  QrDecoder.java           ZXing: QR tryHarder, binarizador global, invertido, 1D
  ScanOverlay.java         píldora, mira, modo ajuste y tarjeta de resultado
  QrImage.java             decodificar desde galería/imagen compartida
  HistoryStore.java        historial en SharedPreferences
  Actions.java             copiar/abrir/compartir/vibrar
  Ui.java / HeroArtView.java  estilos y dibujo de la ilustración
```

## Límites conocidos

- El sistema incluye el propio visor en la captura: por eso el marco se dibuja fuera del área de
  lectura y el escaneo se pausa mientras se muestra la tarjeta.
- Apps con `FLAG_SECURE` (banca, DRM) aparecen en negro y no se pueden leer; es una protección
  del sistema, no de la app.
- La pantalla de bloqueo también queda negra por seguridad del sistema.
