# Idioma

DJIFlyPro se entrega **en español**. Este documento explica por qué, dónde vive
cada cadena y qué queda fuera, para que nadie lo descubra buscando en el código.

## Español como idioma por defecto, no como traducción

El español está en `res/values/`, no en `res/values-es/`. Eso no es un descuido:
significa que **la aplicación muestra español aunque el dispositivo esté en
otro idioma**, porque no hay ningún otro idioma al que recurrir.

Si estuviera en `values-es/`, un dispositivo en inglés caería en `values/` y
vería lo que hubiera ahí. Con el español como valor por defecto no hay
ambigüedad.

`res/xml/locales_config.xml` declara el idioma para que Android 13 y
posteriores ofrezcan el ajuste de idioma por aplicación, y para que el sistema
sepa que no hay más traducciones que aplicar.

## Dónde vive cada cadena

| Origen | Dónde | Cómo se traduce |
|---|---|---|
| Texto de los layouts de DJIFlyPro | `res/values/strings_pro.xml` |Ya estaba en español; extraído a recursos para que sea localizable |
| 23 cadenas de la interfaz de control | `res/values/strings_pro.xml` | Traducidas de inglés a español |
| Textos de la capa de dominio | En el propio código, en español | Ver el apartado siguiente |
| Textos de los diálogos y avisos | `res/values/strings_pro.xml` + código | definidas como recursos para los reutilizables |
| Recetas de ejemplo | `assets/algorithms/*.json` | Los `name` y `description` están en español |
| Fichas de entrega (GeoJSON, KML, ficha de vuelo, informe de validación) | Se generan con textos en español desde el código | Los identificadores JSON y las claves WPML siguen en inglés, porque son formato |
| `res/values/strings.xml` | Del sample de DJI, 406 cadenas | **No se traducen.** Solo 3 las usa una pantalla alcanzable, y se cubren en sus propios layouts |
| UX SDK y SDK de DJI | Dentro de los AAR | **No se pueden traducir.** El AAR sí trae `values-es/`, pero el único widget alcanzable es el mapa, que no tiene texto |

### Por qué los mensajes del dominio están en el código y no en `strings.xml`

`MissionValidator`, `DroneCapabilities`, `MissionGeometry`,
`GroundSampleDistance`, `CartographySolution`, `ControlNetwork`,
`CartographicSummary` y `SurveyCamera` escriben sus mensajes en español dentro
del propio Kotlin, no como recursos.

Es una decisión deliberada:

- Son invariantes del dominio, no adornos de interfaz. Si el mensaje dice
  "El control solo llega al 30 % del radio del bloque", eso es una afirmación
  sobre la geometría y va con ella.
- Las pruebas unitarias comprueban el texto real que ve el operador. Con
  códigos de recurso habría que resolverlos en cada aserción, y una prueba
  que pasa porque el código cambió en vez de porque el mensaje es correcto no
  vale nada.
- Una capa de indirección de unos 130 mensajes tocaría código de seguridad
  (la barrera de ejecución WPML) a cambio de poder traducirlos. Ese intercambio
  no sale a cuenta mientras la aplicación sea solo en español.

El precio es que un día querer inglés exigiría refactorizar esa capa. Está
anotado aquí para que la decisión sea visible y no se descubra por sorpresa.

## Qué se retiró

`res/values-zh-rCN/strings.xml` traducía 23 claves de DJIFlyPro al chino, entre
ellas `app_name_aircraft`, que llegaba a decir "MSDK" (funciones de
aeronave del MSDK) en vez de "DJIFlyPro". Un usuario con el dispositivo en
chino habría visto el nombre de la aplicación cambiado por el del SDK.

Se han retirado las claves propias de DJIFlyPro de ese fichero, de modo que
caen en el español por defecto. Las cadenas que quedan son del sample de DJI y
se dejan intactas. Tres de ellas sí las usa una pantalla alcanzable
—el diálogo de descarga YUV de `CameraStreamDetailFragment`—, pero sus
botones están en el layout, no en el recurso, y ya están en español allí.

## Qué queda en inglés, y por qué

No todo se traduce, y en algunos casos traducir sería un error:

- **Los mensajes de error del SDK de DJI.** Se traducen los 52 códigos que esta
  app puede llegar a mostrar, más los 21 tipos de error. Un código desconocido cae
  en un texto en español que nombra el área del fallo, y **el texto original de
  DJI se conserva siempre**. Traducir solo una parte es lo correcto: DJI no da
  texto ni en inglés para 48 de sus 61 códigos de misión, y un mensaje inventado
  podría decirle al piloto lo contrario de la verdad. Ver `DjiErrorText.kt`.
- **Los nombres de enum** (`CameraAutomationState`, `FlightSafetyReason`) están en
  inglés porque son identificadores. Lo que el usuario lee sale de
  `strings_pro.xml`, elegido por `FlightFailureText`.
- **Las etiquetas de enum del SDK** en los layouts del centro de control
  (`MS_G_CAMERA`, `WIDE`, `CenterCrop`): son los códigos con los que aparecen en la
  documentación de DJI, y traducirlos rompería la correspondencia.
- **Los nombres propios**: OpenStreetMap, Mapbox, M350_RTK, Zenmuse P1.
- **Los identificadores de código** en general.
- **Las citas textuales de DJI** en esta documentación, que van entrecomilladas y
  atribuidas.

## Comprobación

- `lintDebug` avisa de `HardcodedText` en los layouts de DJIFlyPro: no queda
  ninguno, y el informe da 0 errores. Las 112 sustituciones están en
  `activity_djiflypro_main.xml`, `activity_mission_planner.xml`,
  `activity_cartography.xml`, `activity_mission_map.xml`,
  `activity_manual_guidance.xml`, `activity_algorithm_library.xml`,
  `activity_documentation.xml`, `dialog_control_point.xml`,
  `pro_control_point_card.xml` y `pro_algorithm_card.xml`.
- Los identificadores técnicos no se traducen: `EPSG:32630`, `WGS 84`,
  `GeoJSON`, `KML`, `WPML`, `RTK`, `mm`, `cm/px`, `ha`, `m`. Son parte del
  vocabulario de la especialidad y traducirlos daría información menos precisa.
- El formato numérico es independiente del idioma: las cifras se emiten con
  `Locale.US`, con punto decimal, porque van a un archivo de intercambio y a
  un parser. Un operador español ve "0.75" en pantalla, no "0,75", y eso es
  intencionado para que lo que lee sea exactamente lo que se exporta.
- `res/values/strings.xml` conserva 406 cadenas del sample de DJI en inglés. No
  se traducen porque no se usan, y borrarlas rompería el código de ejemplo que
  se conserva como referencia del SDK.
- Las 328 pruebas unitarias pasan. Las que fijaban texto en inglés ahora fijan
  el texto español, y al revés de lo que parecía: si alguien cambia un mensaje
  de cara al usuario sin querer, la prueba falla.
