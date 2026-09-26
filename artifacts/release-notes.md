# DJIFlyPro v1.1.0-alpha.5

**La App Key de DJI ya está en el binario.** Esta prerelease sí puede registrarse
con el SDK. Es un cambio respecto a `v1.1.0-alpha.1`, cuyos binarios se
compilaron sin key y no podían conectarse a ningún aircraft.

`applicationId com.djiflypro.app`, DJI Mobile SDK V5.18.0,
`versionName 1.1.0-alpha.5`, `versionCode 6`.

## Qué sigue sin estar hecho

- **La conexión con un aircraft no se ha probado en un dispositivo.** La key está
  en el manifiesto, pero que DJI la acepte para `com.djiflypro.app` sólo se
  comprueba volando. La interfaz y toda la cartografía se pueden probar sin
  hardware.
- **La ejecución automática de misiones WPML sigue bloqueada.** La allowlist de
  validación física se publica vacía a propósito. Aunque la app se registre, no
  puede subir ni iniciar una misión automática hasta que haya validación
  física documentada.
- La firma es la keystore de desarrollo local. No publicable en Google Play.
- El mapa abre sin teselas: `GMAP_API_KEY` y `MAPLIBRE_TOKEN` siguen vacías.
  El resto de la cartografía no depende del mapa.

## Novedad de esta versión: actualización desde la propia app

Al abrir el programa, DJIFlyPro comprueba si hay una versión nueva y, si la
hay, lo avisa con un botón para instalarla. También hay **Buscar actualizaciones**
en el panel principal.

La descarga se comprueba contra **dos** cosas antes de llegar al instalador:

1. El hash SHA-256 que publica la release, descargado por HTTPS desde GitHub.
2. El certificado con el que está firmado el APK, que tiene que ser el mismo que
   el de la app ya instalada.

La segunda comprobación es la que importa: si alguien publica una release
manipulada controla el manifiesto, y con solo el hash podría hacer que la app
instalara cualquier APK. Comparar el firmante impide que un APK de otra
aplicación pase por uno de DJIFlyPro. Si cualquiera de las dos falla, el
fichero se borra y no se instala nada.

**La app nunca instala por su cuenta:** el flujo termina en la pantalla de
instalación del sistema, que es la que muestra qué va a cambiar y pide la
confirmación.

Detalles a tener en cuenta:

- Android 8 y posteriores piden además que concedas a DJIFlyPro el permiso de
  "instalar apps desconocidas". Se pide en el momento, con un diálogo que lleva
  a Ajustes. Si prefieres no concederlo, se puede descargar el APK a mano.
- La comprobación se hace como mucho **una vez cada seis horas**: es una
  petición a GitHub cada vez que se abre la app, y esa es una coste que no
  compensa pagar siempre.
- Se descarga el APK entero, unos 205 MB. No hay actualización delta.
- Cada release publica un `update.json`. **Si se sube sin él, la app no
  encuentra nada que actualizar** y lo dice, en vez de fingir que está al día.

Ver `docs/ACTUALIZACIONES.md`.

## Novedad de esta versión: toda la aplicación en español

La aplicación se entrega **en español**, y el español es el idioma por defecto,
no una traducción: se ve en español aunque el dispositivo esté en otro idioma.

- **112 textos de los layouts** extraídos a recursos y traducidos. Ya estaban en
  español, pero escritos a mano dentro de los XML, así que no eran localizables.
  Ahora no queda ni un `HardcodedText` en los layouts de DJIFlyPro.
- **Las 23 cadenas de la interfaz de control** estaban en inglés: el centro de
  control era la única pantalla de la aplicación en inglés. Traducidas.
- **La capa de dominio** escribía sus mensajes en inglés y llegaban a pantalla
  por `error.message`: la barrera de ejecución WPML, los validadores de misión,
  los avisos de la geometría de ruta, el motor de GSD, el control terrestre y
  las notas de la solución cartográfica. Todo en español.
- **Las fichas de entrega** —informe de validación, ficha de vuelo— salían con
  los textos en inglés. En español.
- **`res/values-zh-rCN/` eliminado.** Traducía 23 claves de DJIFlyPro al chino
  entre ellas `app_name_aircraft`, que llegaba a decir "MSDK飞机功能" en vez de
  "DJIFlyPro". Y era una traducción a medias: de sus 406 cadenas, 383 eran del
  sample de DJI, lo que producía una aplicación medio china y medio español.
  Ninguna pantalla alcanzable usaba ninguna de ellas.
- **`localeConfig`** declarado, para que Android 13 y posteriores ofrezcan el
  ajuste de idioma por aplicación.
- **Corregido español roto** en la pantalla de documentación: "no se promises en
  esta versión", "la.Return-to-home" y "la Batteries antes de ejecutar". Y
  "Calcular parameters", que estaba en inglés dentro de un layout en español.
- **Las 292 pruebas** fijan ahora el texto español. Si alguien cambia un mensaje
  de cara al usuario sin querer, la prueba falla.

Ver `docs/IDIOMA.md`, que explica por qué los mensajes del dominio están en el
código y no en `strings.xml`.

## Novedad de esta versión: cartografía

### Motor de resolución

La aplicación trabaja en la dirección en que llega un encargo de cartografía: la
resolución y el solape se declaran, la altura se deduce. Un P1 a 2 cm/px son
160 m de altura, con una huella de 164 m: 49 m entre líneas al 70 % de solape
transversal y 33 m entre fotos al 80 % de solape longitudinal.

- Siete payloads de DJI descritos por sensor, objetivo y resolución de salida.
  El GSD se deriva de esos parámetros, no de una tabla, así que un payload
  desconocido se puede describir y el resultado es un número que el operador
  puede ver y corregir.
- Solape longitudinal y transversal por separado.
- Redundancia real calculada sobre la superficie de la parcela.
- La ficha avisa cuando la resolución es demasiado fina o demasiado gruesa para
  ser práctica, y cuando el solape es bajo para el tipo de terreno.

### Techo de altura, sin ocultarlo

Un P1 a 2 cm/px vuela a 160 m, por encima del techo de 120 m de una misión
waypoint automática de DJI. La aplicación no lo sube:

- **120 m** es un error duro para cualquier subida o inicio automático, y se
  comprueba otra vez justo antes de subir y otra vez justo antes de iniciar.
- **500 m** es el techo de planificación. Por encima se puede planificar,
  guardar, exportar y volar con la guía manual, donde la persona está al mando.

La solución indica en cuál de los dos casos está.

### Referencia de altura

Un GSD sólo es cierto sobre terreno de altura conocida, así que el dato se
declara: sobre el terreno, nivel del mar o desde el despegue. Viaja en
`mission.json`, en el GeoJSON, en el KML y en la ficha, y en el KML se traduce
al `altitudeMode` que lo representa.

### Control terrestre

- Puntos de control con tipo de diana, exactitud horizontal medida y origen de
  la coordenada.
- Comprobación de la red: al menos 4 puntos de control y 1 de verificación,
  reparto que llega a los bordes del bloque, no colinealidad, separación mínima
  entre puntos y aviso cuando ningún punto tiene altura.
- Un elemento del terreno no es válido como control, sólo como verificación.

### Sistemas de referencia

WGS84 geográfico ↔ UTM con el desarrollo en serie de Snyder, incluidas las
ensanchaciones de Noruega y Svalbard. La ficha de vuelo y el GeoJSON entregan las
dos referencias con su código EPSG, más la superficie en hectáreas.

### Geometría de bloque corregida

- Las líneas de vuelo se reparten uniformemente sobre el ancho, de modo que la
  última cae exactamente en el borde y la separación real nunca es menor que la
  pedida. Antes la última línea podía dejar una franja sin cubrir.
- Cada línea se alarga una separación de foto a cada extremo. Antes el
  perímetro del bloque solo lo cubría el canto de la primera y la última
  fotografía, y quedaban huecos.
- Nuevo patrón `Cruz oblicua 45°`: dos pasadas sobre las diagonales del bloque.

### Formatos de entrega

| Formato | Para qué |
|---------|----------|
| `mission.json` | Formato interno completo |
| `.geojson` | RFC 7946. QGIS, Pix4D, Agisoft, portales cartográficos |
| `.kml` | Revisión sobre el globo. Google Earth, DJI FlightHub, GIS de escritorio |
| KMZ/WPML | Subida al aircraft |
| Ficha de vuelo (JSON) | Documento que acompaña al producto entregado |

El GeoJSON lleva la huella, una `LineString` por línea de vuelo, un punto por
punto de control y las propiedades que el software receptor no puede deducir.
La ficha de vuelo declara `"status": "planned"` y una lista de limitaciones que
dice, entre otras cosas, que el bloque no se ha volado y que la exactitud del
producto la limita la de su control.

### Revisión en el mapa

Huella, líneas de vuelo y control sobre un mapa MapLibre del UX SDK, y
colocación de un punto de control donde está realmente pintado en vez de
escribirlo de memoria. Es una vista de apoyo: no mueve el aircraft y no verifica
nada, y lo dice en pantalla.

## Correcciones

- El planificador ya no fija la acción de fin a "regreso a casa": hay selector.
- Las recetas algorítmicas se cargan de `assets/algorithms/` en vez de estar
  duplicadas en el código, así que se pueden corregir o ampliar sin recompilar.
  Nueva receta `cartography.cross.v1.json`.
- El registro de auditoría rota por tamaño (2 MiB) en `audit.previous.jsonl`,
  siempre en un límite de línea, en vez de crecer sin límite.
- Corregida una división por 100 en el cálculo de separaciones a partir del
  solape, que producía separaciones casi iguales a la huella de la foto.
- Corregido el conteo de líneas de vuelo, que podía partir una línea en dos
  cuando su posición cruzaba exactamente un límite de la rejilla de agrupación.
- `mission.json` conserva el perfil cartográfico y los puntos de control.
- **El APK debug desaparece del release.** Estaba firmado con un certificado
  distinto al del APK release (`CN=Android Debug` frente a
  `CN=DJIFlyPro Local Test`), así que los dos no se pueden instalar uno sobre
  otro: quien tenga el release instalado no puede instalar el debug sin
  desinstalar antes, y varios instaladores de fabricante informan de ese
  rechazo como "la aplicación no es válida". Además era `android:debuggable`,
  con símbolos sin strippear. Sigue siendo construible en local con
  `./gradlew :sample:assembleDebug`, pero ya no se publica.

## Qué instalar

Solo hay un APK: `DJIFlyPro-1.1.0-alpha.5.apk`. Si ya tienes una versión
anterior de DJIFlyPro instalada, no la desinstales: todas las releases
comparten certificado, así que se actualiza encima. Si en algún momento
tuviste instalada una build depurable, esa sí hay que desinstalarla antes,
porque su certificado es otro.

## Verificaciones

- 292 pruebas unitarias, 0 fallos.
- `unzip -t` sin errores, `zipalign -c 4` correcto, `aapt2 dump badging`
  correcto, `apksigner verify` con v2 y v3.
- `android:debuggable` ausente en el APK publicado.
- `lintDebug`: 0 errores.
- `com.dji.sdk.API_KEY` presente en el manifiesto de los tres binarios.
- `apksigner verify`: correcto, APK Signature Scheme v2.
- `jarsigner -verify` sobre el AAB: código 0.
- `sha256sum -c` sobre los binarios recién construidos, dentro del script.
- El asset `validated_wpml_profiles.json` va empaquetado y vacío, así que la
  ejecución automática está bloqueada.
- La App Key no aparece en ningún fichero versionado: vive en
  `.local/api-key.properties`, que está en `.gitignore`.

## Barrera de ejecución WPML

Sin cambios. La subida y la ejecución automática siguen **bloqueadas**: hacen
falta conexión, registro, firmware versionado, modelo de control remoto, firmware
del control remoto y un perfil de validación física con coincidencia exacta. La
allowlist se publica vacía y el build falla si el asset contiene marcadores sin
rellenar. Ver `docs/COMPATIBILITY.md` y `docs/SAFETY.md`.

## Binarios

| Archivo | Tamaño | SHA-256 |
|---|---:|---|
| `DJIFlyPro-1.1.0-alpha.5.apk` | 205,135,390 bytes | `b0e8b9af8ce3768017f53923d60e66496b59d44951e2072f9715d09c376ee325` |
| `DJIFlyPro-1.1.0-alpha.5.aab` | 198,943,944 bytes | `c09f41dc66ad7a208d054bc175caa3cd0ed692a7407bd2c95bc620ce9d278913` |
| `update.json` | — | manifiesto de actualización, lo publica la propia app |

El `.aab` es para subir a Google Play, no para instalar a mano.

Los nombres llevan la `versionName` que declara el propio build. Verifica con:

```bash
sha256sum -c SHA256SUMS.txt
```

## Documentación

- `docs/ACTUALIZACIONES.md`: cómo se comprueba y se instala una versión nueva.
- `docs/IDIOMA.md`: por qué la aplicación es solo en español y dónde vive cada
  cadena.
- `docs/CARTOGRAFIA.md`: el modelo cartográfico completo, las matemáticas y las
  decisiones que tiene que tomar el operador.
- `docs/SAFETY.md`: límites y lo que la aplicación no comprueba.
- `docs/COMPATIBILITY.md`: aircraft, firmware y control remoto.
- `docs/BUILD.md`: compilación, firma y publicación.
- `docs/PRUEBAS.md`: protocolo de pruebas en dispositivo.
- `docs/ALGORITHMS.md`: recetas declarativas.
