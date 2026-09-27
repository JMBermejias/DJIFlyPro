# Claves y mapa

La app necesita tres claves de tres sitios distintos. No son intercambiables y no
vienen todas de DJI, que es la confusión más habitual con este SDK.

| Clave | De dónde sale | Para qué | Sin ella |
|---|---|---|---|
| `AIRCRAFT_API_KEY` | DJI Developer | Que el SDK registre y enlace con el aircraft | **La app no conecta con nada.** Es bloqueante: el build de release falla. |
| `MAPLIBRE_TOKEN` | Mapbox o MapTiler | Que carguen las teselas del mapa | La app funciona, pero **todos los mapas salen en blanco**. |
| `GMAP_API_KEY` | Google | Nada | Irrelevante. Se puede dejar vacía para siempre. |

## `MAPLIBRE_TOKEN` no es una clave de DJI

Esto es lo que más confunde, porque el nombre del hueco en el manifiesto parece de
DJI:

```xml
<meta-data android:name="com.dji.mapkit.maplibre.apikey"
           android:value="${MAPLIBRE_TOKEN}" />
```

DJI solo pone el hueco. El valor es un **token de Mapbox** o una **clave de
MapTiler**, y se pide en la web de ese proveedor, no en la de DJI. La documentacion
de DJI lo dice de forma literal: *"The token should be obtained from the Mapbox
official website, and make sure the package name matches your application."*

## Google Maps ya no está soportado

MSDK V5 dejó de mantener los mapas de Google y de Autonavi. Solo queda el
proveedor MapLibre. En el soporte de DJI: *"currently only maptiler is supported"* y
*"google, Autonavi are no longer maintained"*.

Por eso `com.google.android.geo.API_KEY` va vacío en el manifiesto y
`GMAP_API_KEY` no se usa. Además, Google Maps no funciona sobre el control remoto
de DJI, que es donde se instala la app en los aircraft con mando con pantalla.

## Cómo conseguir el token

### Opción A: Mapbox

1. Cuenta en https://account.mapbox.com.
2. **Create access token** → **Default public token**.
3. En las restricciones, **Android**:
   - Package name: `com.djiflypro.app`
   - SHA-1 certificate fingerprint:
     `8437d47734c8fa198ab1c05bb819517ccbaaaea7`

Ojo: Mapbox pide tarjeta para las cuentas nuevas. Si es un problema, la opción B.

### Opción B: MapTiler

1. Cuenta en https://cloud.maptiler.com.
2. Copia la **API key** del proyecto.
3. Restríngela por paquete Android si quieres: `com.djiflypro.app` y el mismo
   SHA-1 de arriba.

MapTiler tiene plan gratuito más Generoso que Mapbox, y el soporte de DJI lo
menciona como el único proveedor mantenido. Si el estilo por defecto de MSDK se
ve gris o no carga con Mapbox, esta es la vía que recommends.

## Cómo probarlo

El token está **restringido por paquete y firma**, así que un token válido en el
PC puede fallar en el móvil si el SHA-1 no es el de esta APK:

```
SHA-1:   8437d47734c8fa198ab1c05bb819517ccbaaaea7
SHA-256: 8a3aac2e456093f76dc0a2ccd717538b1732da3cecf470231feb48de187def43
MD5:     0bea2157164ef298389284423a21aaa5
```

Esas son las huellas de la clave de firma de release. Si algún día se cambia la
clave de firma, hay que volver a restringir el token con las huellas nuevas.

Para ver las de cualquier APK:

```bash
build-tools/*/apksigner verify --print-certs app.apk
```

Y para comprobar que el token llegó de verdad a la APK, que es el paso que se
puede dar por hecho y no lo está:

```bash
build-tools/*/aapt2 dump xmltree --file AndroidManifest.xml app.apk \
  | grep -A2 com.dji.mapkit.maplibre.apikey
```

Si el valor sale vacío, el token no llegó al build. Suele ser que está en
`gradle.properties` —que está versionado— en vez de en
`.local/api-key.properties`, que está ignorado por git. Ver `docs/BUILD.md`.
