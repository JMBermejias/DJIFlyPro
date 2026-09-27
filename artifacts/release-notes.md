# DJIFlyPro v1.1.0-alpha.6

`versionName 1.1.0-alpha.6`, `versionCode 7`.

## Novedad de esta versión: App Key correcta, la app ya registra con DJI

Las versiones anteriores **no conectaban con ningún aircraft**, incluido el Mini 3.
No era la app, ni el dron, ni el firmware: era el alta en la consola de DJI.

DJI vincula cada App Key a un **package name exacto y al certificado con el que
firma la app**. El package name con el que se había dado de alta la app era
`DJIFlyPro.apk`, pero esta app se identifica como `com.djiflypro.app`. Al no
encontrar a quién pertenecía la clave, el servidor de DJI rechazaba la identidad
y el SDK no llegaba a registrarse, así que nunca llegaba a enlazar con el
aircraft. En el panel se veía:

> Registro DJI: The metadata received from server is invalid, please reconnect to
> the server and try

**Hay que reinstalar esta versión.** La clave va incrustada en la APK, así que no
basta con actualizar: hay que desinstalar la anterior e instalar esta. Es
imprescindible porque Android no deja instalar dos APKs con la misma
`applicationId` sin desinstalar antes la otra.

Qué hay que tener en cuenta esta vez:

- El package name correcto es `com.djiflypro.app`, en minúsculas.
- Las huellas de firma de esta APK son las que DJI tiene registradas. Si
  cambias de clave de firma algún día, hay que volver a registrarlas.
- La App Key que lleva dentro es la que emitió DJI para este package name.
  Esta vez son 24 caracteres: es el formato que emite DJI, no 32.

## Actualización desde la propia app

Al abrir el programa comprueba si hay una versión nueva y, si la hay, lo avisa
con un botón para instalarla. También hay **Buscar actualizaciones** en el panel
principal.

La descarga se comprueba contra **dos** cosas antes de llegar al instalador:

1. El hash SHA-256 que publica la release, descargado por HTTPS desde GitHub.
2. El certificado del firmante, que tiene que ser el mismo que el de la app
   instalada.

La segunda es la que importa: si alguien publica una release manipulada controla
el manifiesto, y con solo el hash podría hacer que la app instalara cualquier
APK. Comparar el firmante impide que un APK de otra aplicación pase por uno de
DJIFlyPro. Si cualquiera de las dos falla, el fichero se borra y no se instala
nada.

**La app nunca instala por su cuenta:** el flujo termina en la pantalla de
instalación del sistema, que es la que muestra qué va a cambiar y pide la
confirmación.

Detalles:

- Android 8 y posteriores piden además que concedas a DJIFlyPro el permiso de
  "instalar apps desconocidas". Se pide en el momento, con un diálogo que lleva a
  Ajustes. Si prefieres no concederlo, se puede descargar el APK a mano.
- La comprobación se hace como mucho **una vez cada seis horas**: es una
  petición a GitHub cada vez que se abre la app, y esa es una coste que no
  compensa pagar siempre.
- Se descarga el APK entero, unos 205 MB. No hay actualización delta.
- Cada release publica un `update.json`. **Si se sube sin él, la app no
  encuentra nada que actualizar** y lo dice, en vez de fingir que está al día.

Ver `docs/ACTUALIZACIONES.md`.

## Lo que sigue sin funcionar: misiones WPML en el Mini 3

El Mini 3 y el Mini 3 Pro **no pueden ejecutar misiones de waypoint** (WPML),
por una limitación de su firmware que DJI ha confirmado: sus controles remotos
no tienen función de rutas, y la app oficial DJI Fly tampoco. El síntoma es un
error `errorType=WAYPOINT`, `errorCode=REQUEST_HANDLER_NOT_FOUND` al subir el
KMZ.

No se arregla registrando la app, ni añadiendo un perfil de allowlist, ni
cambiando de firmware dentro de la misma familia. Con un Mini 3 **sí** funcionan
la conexión, la telemetría, la cámara, el vídeo y el control manual.

Ver `docs/COMPATIBILITY.md`.

## Binarios

| Archivo | Tamaño | SHA-256 |
|---|---:|---|
| `DJIFlyPro-1.1.0-alpha.6.apk` | 205,135,398 bytes | `32126b8e21d4aea98493409d7adc9844ebc2573e6963f501ac63361844e026a5` |
| `DJIFlyPro-1.1.0-alpha.6.aab` | 198,943,949 bytes | `882350a620e48085bc38302ee3e3d1e8974d96f824331a8883827e6a8d2becaa` |
| `update.json` | 650 bytes | `105a8aab3b6dc1cce686b25cc55721d89ba4c4a41b377a6882863b9974c8a4b2` |

El `.aab` es para subir a Google Play, no para instalar a mano.

La APK está firmada con un certificado de desarrollo
(`CN=DJIFlyPro Local Test`, `OU=Development`):

```
SHA-256: 8a3aac2e456093f76dc0a2ccd717538b1732da3cecf470231feb48de187def43
SHA-1:   8437d47734c8fa198ab1c05bb819517ccbaaaea7
MD5:     0bea2157164ef298389284423a21aaa5
```

El mismo certificado que las anteriores. Es lo que permite que la app acepte
actualizar desde las alpha anteriores, porque comprueba que el firmante coincide
con el de la app instalada.

## Documentación

- `docs/COMPATIBILITY.md`: qué aircraft soporta cada cosa, y por qué el Mini 3
  no hace waylines.
- `docs/ACTUALIZACIONES.md`: cómo se comprueba y se instala una versión nueva.
- `docs/IDIOMA.md`: por qué la aplicación es solo en español y dónde vive cada
  cadena.
- `docs/PRUEBAS.md`: qué está probado y qué no, sin adornos.

## Verificación

316 pruebas unitarias, 0 fallos. Lint con 0 errores.

`scripts/verify-published-release.sh 6` recorre el camino completo contra esta
release: busca el manifiesto por la misma API, lo valida con las mismas reglas,
decide que hay versión nueva, descarga los 205 MB desde la URL que anuncia el
manifiesto y comprueba el hash y el firmante.
