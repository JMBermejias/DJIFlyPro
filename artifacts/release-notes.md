# DJIFlyPro v1.1.0-alpha.1

Prerelease de **cartografía**. Esta versión deja de ser un planificador de rutas
y pasa a planificar por resolución: se introduce la cámara y la resolución
objetivo, y la aplicación deduce la altura de vuelo y las separaciones.

`applicationId com.djiflypro.app`, DJI Mobile SDK V5.18.0, `versionCode 2`.

## Aviso: estos artefactos aún no pueden volar

Este release **no es funcional para control de vuelos**. Los APK se compilaron
usando el flag `--allow-missing-api-key` porque `AIRCRAFT_API_KEY` está vacía:

> Sin la App Key de DJI registrada para `com.djiflypro.app`, la aplicación no
> puede registrarse con el SDK ni conectarse a ningún aircraft. La interfaz, la
> cartografía, la planificación y la exportación funcionan; todo lo que toque el
> aircraft real fallará.

Y aunque hubiera App Key, la ejecución automática de misiones WPML sigue
bloqueada: la allowlist de validación física se publica vacía.

Para que sirvan de algo hace falta, en este orden:

1. Registrar `com.djiflypro.app` en el DJI Developer Console y obtener su App Key.
2. Compilar de nuevo con `AIRCRAFT_API_KEY` definida. El build release **aborta**
   si falta, así que no es posible publicar por accidente otro APK sin clave.
3. Firmar con una clave privada de producción fuera del repositorio.
4. Hacer la validación física y rellenar la allowlist antes de habilitar
   misiones automáticas.

Publicamos esta prerelease como evidencia de compilación, R8, lint y firma, y
como base sobre la que iterar.

## Novedad de esta versión: cartografía

### Motor de resolución

La aplicación trabaja en la dirección en que trabaja un encargo de cartografía:
la resolución y el solape se declaran, la altura se deduce. Un P1 a 2 cm/px son
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
  comprueba otra vez justo antes de subir y justo antes de iniciar.
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
ensanchaciones de Noruega y Svalbard. La ficha de vuelo y el GeoJSON entregan
las dos referencias con su código EPSG, más la superficie en hectáreas.

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
nada, y lo dice en pantalla. Sin llave de mapa la aplicación sigue
funcionando; el resto de la cartografía no depende de él.

## Otras correcciones de esta versión

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
- Proguard protege los nuevos modelos serializados.

## Verificaciones

- 292 pruebas unitarias, 0 fallos.
- `lintDebug`: 0 errores.
- R8: 0 advertencias de clase ausente y ninguna regla `-dontwarn` aplicada a
  código propio.
- `apksigner verify`: correcto, APK Signature Scheme v2.
- `jarsigner -verify` sobre el AAB: código 0.
- El asset `validated_wpml_profiles.json` va empaquetado y vacío, así que la
  ejecución automática está bloqueada.

## Barrera de ejecución WPML

Sin cambios respecto a `v0.1.0-alpha.1`. La subida y la ejecución automática
siguen **bloqueadas**: hacen falta conexión, registro, firmware versionado,
modelo de control remoto, firmware del control remoto y un perfil de validación
física con coincidencia exacta. La allowlist se publica vacía, un perfil no
válido deja la allowlist vacía, y el build falla si el asset contiene marcadores
sin rellenar. Ver `docs/COMPATIBILITY.md` y `docs/SAFETY.md`.

## Límites conocidos

- Un GSD planificado se cumple sobre terreno de altura conocida. En relieve se
  desvía, y el desvio crece con la altura y con la posición del sensor.
- La aplicación no comprueba espacio aéreo, obstáculos, líneas eléctricas,
  permisos ni zonas reguladas.
- No genera el orto, el DSM ni la nube de puntos. Genera la planificación y la
  documentación necesarias para generarlos.
- El mapa necesita `MAPLIBRE_TOKEN` o `GMAP_API_KEY` para mostrar teselas.
- La validación física de vuelo no está realizada.

## Binarios

| Archivo | Tamaño | SHA-256 |
|---|---:|---|
| `DJIFlyPro-1.1.0.apk` | 205,002,138 bytes | `c93f36f8bf5e276924a829d5858d989d4616cb6c7f148a61419aedf494a35727` |
| `DJIFlyPro-1.1.0.aab` | 198,917,491 bytes | `83deb28a9b1408f5337e8a6a4a57e3bdc0e61f5a5cfb137165851cf9d7171686` |
| `DJIFlyPro-1.1.0-debug.apk` | 238,557,908 bytes | `fef20561703da7d1a46961251c29785cc162e734b44ae06e380821ddd74af24d` |

Los nombres llevan la `versionName` que declara el propio build. Verifica con:

```bash
sha256sum -c SHA256SUMS.txt
```

## Documentación

- `docs/CARTOGRAFIA.md`: el modelo cartográfico completo, las matemáticas y las
  decisiones que tiene que tomar el operador.
- `docs/SAFETY.md`: límites y lo que la aplicación no comprueba.
- `docs/COMPATIBILITY.md`: aircraft, firmware y control remoto.
- `docs/BUILD.md`: compilación, firma y publicación.
- `docs/PRUEBAS.md`: protocolo de pruebas en dispositivo.
- `docs/ALGORITHMS.md`: recetas declarativas.
