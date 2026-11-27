# QR Screen — escáner de QR por pantalla (sin cámara)

App Android que lee códigos QR **directamente de la pantalla del celular**. No usa la cámara:
captura la pantalla con `MediaProjection` y decodifica los píxeles con ZXing.

**Descargar:** [APK firmado 1.5.1](dist/QR-Screen-1.5.1.apk) · **Página web:** <https://profilebiome.github.io/qr-screen/>

![Icono de QR Screen](web/icon.png)

## Crear y guardar códigos

La pantalla principal tiene dos accesos nuevos: **Crear QR** y **Mis códigos**.

- **Crear QR**: escribe el contenido (una URL, `WIFI:S:…;T:WPA;P:…;;`, un `mailto:`, un teléfono,
  una vCard…) y, si quieres, un nombre. La app detecta el tipo, dibuja la vista previa y guarda el
  código en la galería local.
- **Mis códigos**: rejilla con miniatura, nombre, tipo y fecha. Al tocar uno se abre el detalle:
  copiar el contenido, abrirlo, compartir el PNG generado, guardarlo en la galería del teléfono
  (Android 10 o superior), renombrarlo o eliminarlo.

Los QR se regeneran desde el texto guardado, así que no ocupan espacio como imágenes y siempre
están nítidos al compartirlos.

## Actualizaciones

La app comprueba `https://profilebiome.github.io/qr-screen/version.json` al abrirse (como mucho una
vez cada 6 horas) y también desde **Buscar actualizaciones**, en el pie de la pantalla principal.
Si el `versionCode` publicado es mayor, muestra el aviso **«Actualización disponible»**, descarga el
APK, verifica su SHA-256 y abre el instalador de Android. La confirmación final la pide el sistema.

Para publicar una versión nueva:

1. Sube `versionCode` y `versionName` en `app/build.gradle`.
2. `gradlew.bat :app:assembleRelease` y copia el APK a `dist/`.
3. Calcula el SHA-256 del APK y actualiza `version.json` (`versionCode`, `versionName`, `apk`,
   `sha256`, `notes`).
4. Haz commit y push: Pages sirve el `version.json` nuevo en un minuto.

Las versiones anteriores a la 1.2.0 no tienen el actualizador; hay que instalar la 1.2.0 a mano una
vez.

## App para PC (Windows)

`pc/QR-Screen-Updater.exe` es un actualizador de escritorio: detecta el teléfono por USB con ADB,
lee la versión instalada, consulta la publicada en GitHub y la instala con `adb install -r` sin pasar
por el gestor de archivos. El botón **Descargar APK** guarda la última versión en el PC aunque no
haya teléfono conectado. Requiere Windows 10/11, .NET Framework 4.8 (viene de serie) y las
Platform-Tools de Android (`adb.exe`; el programa lo busca o permite elegirlo con **Buscar adb…**).

Descarga: <https://github.com/profilebiome/qr-screen/raw/main/pc/QR-Screen-Updater.exe>

## Cómo funciona

1. El botón **Escanear pantalla** pide la captura del sistema y arranca un servicio en primer plano.
2. Un `VirtualDisplay` entrega los fotogramas a un `ImageReader`; se recorta el área del visor y,
   si no hay lectura, se prueban la pantalla completa reducida y a resolución nativa.
3. ZXing decodifica el QR (con búsqueda agresiva, binarizador global e intento invertido para modo
   oscuro). Al encontrar un código, vibra, suena y muestra la tarjeta de resultado.
4. El texto queda en el historial local. La única conexión a internet es la comprobación de
   actualizaciones contra GitHub Pages.

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
- **Crear QR**: pantalla «Crear QR» con contenido, nombre e **información**, tipo detectado (enlace,
  Wi-Fi, contacto, correo, teléfono, pago, 2FA…), vista previa en vivo y guardado.
- **Mis códigos**: barra horizontal de códigos guardados en la pantalla principal y galería completa
  con miniatura, nombre, tipo, fecha e información; detalle con copiar, abrir, compartir el PNG,
  guardar en la galería del teléfono (Android 10+), renombrar y eliminar.
- **Herramientas** (perilla en la cabecera): tu información personal (nombre, email, teléfono),
  contactos para los que creas códigos y el buscador de actualizaciones.
- **Web**: sección «Tus códigos» que crea QR en el navegador y los guarda en `localStorage` (nombre,
  enlace e información, copiar, abrir, descargar PNG, eliminar), con el generador incluido en
  `web/qrcode.js` (sin depender de un CDN).
- **Sin cámara**: el manifest no declara `android.permission.CAMERA`.

## Instalar

1. Copia `dist/QR-Screen-1.5.1.apk` al teléfono (cable, Drive, Telegram…).
2. Ábrelo y acepta «Instalar apps desconocidas» para el explorador/gestor de archivos.
3. Al abrir la app:
   - concede **Mostrar sobre otras apps** (visor y tarjeta de resultados),
   - concede **Notificaciones** (Android 13+),
   - al pulsar «Escanear pantalla», acepta la **captura de pantalla** del sistema.

> La aceptación de captura se pide cada vez que inicias el escáner: es un requisito de Android.
> La app nunca pide cámara. Internet se usa solo para buscar e instalar actualizaciones.

### «No se puede verificar la app» / «Play Protect»

Ese aviso **lo pone Android, no la app**, y aparece con cualquier APK instalado fuera de Google
Play. No hay ningún ajuste dentro del APK que lo quite: la única forma de eliminarlo del todo es
publicar en Play Store. Para instalar igualmente:

- Toca **Más detalles → Instalar de todas formas** (o «Instalar sin analizar»).
- Si el teléfono lo bloquea: **Play Store → foto de perfil → Play Protect → ⚙ → desactiva
  «Analizar apps con Play Protect»** durante la instalación y vuelve a activarlo después.
- En Xiaomi/POCO/HyperOS, además: **Ajustes → Privacidad → Protección → desactivar «Analizar apps
  instaladas»**, y activa «Instalar vía USB» en Opciones de desarrollador.
- Si dice **«App no instalada»** suele ser porque ya hay una versión con otra firma:
  desinstala la anterior y vuelve a instalar.

### Instalar por USB (sin avisos del gestor de archivos)

Con depuración USB activada, ejecuta `instalar-adb.bat` en Windows (o a mano:
`adb install -r dist/QR-Screen-1.5.1.apk`). Es la vía más limpia y permite ver el motivo exacto si
la instalación falla.

### ¿Es maliciosa?

No. Auditoría del APK:

- **INTERNET solo para actualizaciones**: comprueba `version.json` en GitHub Pages y descarga el
  APK. No hay analítica, ni cuentas, ni subida de datos.
- **Sin cámara, sin contactos, sin ubicación, sin almacenamiento.**
- Permisos: servicio en primer plano, proyección de pantalla, notificaciones, dibujar sobre otras
  apps, vibración, internet y pedir la instalación de paquetes (para actualizarse).
- Una sola dependencia (ZXing core, decodificador libre de Google), sin analítica ni SDK de
  terceros; un único `classes.dex`, sin librerías nativas.
- Firma propia (`keystore/qrscreen.jks`), verificada con `apksigner` (v1+v2+v3).

El código fuente completo está en este repositorio para que cualquiera lo revise y recompile.

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

La firma se lee de `keystore.properties` (ignorado por git, junto con `keystore/`). Ese archivo no
se publica: el repositorio solo incluye el APK ya firmado. Sin él, `assembleRelease` genera un APK
sin firma y `assembleDebug` sigue funcionando.

```bash
# local.properties con sdk.dir apuntando al SDK
./gradlew :app:assembleRelease
# APK firmado: app/build/outputs/apk/release/app-release.apk
```

Comprobar la firma y los permisos:

```bash
apksigner verify -v --print-certs dist/QR-Screen-1.5.1.apk
aapt2 dump badging dist/QR-Screen-1.5.1.apk | findstr permission
```

Firma: keystore propio (RSA 2048, esquemas v2 y v3). La clave no forma parte del repositorio
público. La 1.1.0 se firmó con una clave nueva, así que si venías de la 1.0.1 hay que desinstalarla
una vez; desde la 1.1.0 las actualizaciones se instalan encima.

## Web

La página de descarga está en `index.html` y se publica con GitHub Pages desde la raíz del repo.
Incluye el botón de descarga del APK, qué hace la app, los pasos de instalación y la ficha técnica.
Copia local: abre `index.html` con doble clic (el APK se sirve desde `dist/`).

## Licencia

MIT. Consulta [LICENSE](LICENSE).

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
  QrStore.java             códigos creados, con nombre, tipo y fecha
  ProfileStore.java        mi información y contactos para los QR
  ToolsActivity.java       menú de herramientas (datos, contactos, actualizaciones)
  QrRenderer.java          QR a Bitmap con ZXing
  CreateQrActivity.java    formulario con vista previa en vivo
  GalleryActivity.java     galería de códigos y detalle con acciones
  QrFileProvider.java      comparte PNG y APK de actualización sin permisos de almacenamiento
  UpdateChecker.java       version.json, descarga y SHA-256 (Java puro)
  Updater.java             aviso de actualización, descarga e instalador
  Actions.java             copiar/abrir/compartir/vibrar
  Ui.java / HeroArtView.java  estilos y dibujo de la ilustración
pc/QR-Screen-Updater.cs    actualizador de escritorio (C#, WinForms)
version.json               versión publicada que leen la app y el PC
web/qrcode.js              generador de QR del navegador (MIT, incluido)
```

## Límites conocidos

- El sistema incluye el propio visor en la captura: por eso el marco se dibuja fuera del área de
  lectura y el escaneo se pausa mientras se muestra la tarjeta.
- Apps con `FLAG_SECURE` (banca, DRM) aparecen en negro y no se pueden leer; es una protección
  del sistema, no de la app.
- La pantalla de bloqueo también queda negra por seguridad del sistema.
