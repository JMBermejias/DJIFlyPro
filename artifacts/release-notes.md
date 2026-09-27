# DJIFlyPro v1.1.0-alpha.8

`versionName 1.1.0-alpha.8`, `versionCode 9`.

## Instala esta. Las dos anteriores no conectaban con el aircraft

Ninguna de las versiones hasta ahora ha llega a registrarse con DJI. La
alpha.7 arregló una cosa y dejó otra, y las dos daban el mismo error. Esta es la
primera en la que la App Key llega a la APK como DJI la emitió.

Había dos fallos, ambos en el camino entre el fichero de claves y el manifiesto:

1. **Package name equivocado en la consola de DJI.** El alta era con
   `DJIFlyPro.apk` y la app se identifica como `com.djiflypro.app`. Sin encontrar
   a quién pertenecía la clave, el servidor rechazaba la identidad.

2. **Comillas dentro de la clave.** Las claves viven en
   `.local/api-key.properties`, escrito con la sintaxis de `.properties` de Java,
   donde `CLAVE="valor"` es legal y significa `valor`. Pero el script de build lo
   lee con un bucle de `bash`, no con un parser de Java, así que las comillas
   sobrevivían: DJI recibía una clave de **26 caracteres con dos comillas
   pegadas** en vez de los 24 reales.

El segundo fallo es el que hace que la alpha.7 no sirviera, y es el más traicionero
de los dos: la clave de DJI era correcta todo el rato, así que el error
`INVALID_METADATA` senala a la consola de DJI y no al build. Cambiar el package name
parecía haberlo arreglado, porque había arreglado la mitad.

Por eso esta vez el build falla si la App Key trae comillas o espacios, y avisa si
no mide 24 caracteres. Un preflight que solo comprueba "no está vacía" deja pasar
una clave corrupta sin decir nada.

**Hay que desinstalar la alpha.7 antes de instalar esta.** No es opcional: la App
Key va incrustada en la APK y Android no admite dos APKs con la misma
`applicationId` sin borrar la otra.

## Novedad: el mapa ya carga teselas

Las versiones anteriores salían con `com.dji.mapkit.maplibre.apikey` vacío, así
que todos los mapas se veían en blanco: la app registraba, la telemetría
funcionaba, y el plan se dibujaba sobre un fondo liso.

Ahora lleva un token de Mapbox. Conviene tener claro de dónde sale cada clave,
porque no vienen todas del mismo sitio:

| Clave | De dónde sale | Sin ella |
|---|---|---|
| `AIRCRAFT_API_KEY` | DJI Developer | La app no conecta con nada. Bloquea el build. |
| `MAPLIBRE_TOKEN` | Mapbox o MapTiler | Los mapas salen en blanco. Avisa el build. |
| `GMAP_API_KEY` | Google | Irrelevante. Se queda vacía para siempre. |

`MAPLIBRE_TOKEN` **no es una clave de DJI**, aunque el hueco del manifiesto se
llame `com.dji.mapkit.maplibre.apikey`. DJI solo pone el hueco; el valor es un
token de Mapbox. Y no hace falta clave de Google: MSDK V5 dejó de mantener Google
Maps y Autonavi, solo queda el proveedor MapLibre.

Ver `docs/MAPA.md`, que explica cómo pedirlo y cómo restringirlo.

### El token va sin restringir: conviene restringirlo

Un token público de Mapbox (`pk.`) sin restricciones se puede leer descompilando
la APK y cualquiera puede usarlo a tu cargo. No es una fuga de la app, es la
naturaleza de un token de cliente, pero se arregla en un minuto: en Mapbox,
edita el token y ponle restricción **Android** con

- Package name: `com.djiflypro.app`
- SHA-1: `8437d47734c8fa198ab1c05bb819517ccbaaaea7`

La huella es la de la clave de firma de release. Si algún día cambia la firma,
hay que volver a restringir el token con la nueva.

## Actualización desde la propia app

Al abrirse comprueba si hay una versión nueva y, si la hay, avisa con un botón
para instalarla. También está **Buscar actualizaciones** en el panel principal.

Antes de llegar al instalador se comprueban **dos** cosas:

1. El hash SHA-256 que publica la release, por HTTPS desde GitHub.
2. El certificado del firmante, que tiene que ser el de la app instalada.

La segunda es la que importa: si alguien publica una release manipulada controla
el manifiesto, y con solo el hash podría hacer que la app instalara cualquier
APK. Comparar el firmante impide que un APK de otra aplicación pase por uno de
DJIFlyPro. Si cualquiera de las dos falla, el fichero se borra y no se instala
nada.

**La app nunca instala por su cuenta:** el flujo termina en la pantalla de
instalación del sistema.

- Android 8 y posteriores piden conceder a DJIFlyPro el permiso de "instalar apps
  desconocidas". Se pide la primera vez, con un diálogo que lleva a Ajustes.
- La comprobación se hace como mucho **una vez cada seis horas**.
- Se descarga el APK entero, unos 205 MB. No hay actualización delta.
- Cada release publica un `update.json`. Si se sube sin él, la app lo dice en vez
  de fingir que está al día.

## Lo que sigue sin funcionar: misiones WPML en el Mini 3

El Mini 3 y el Mini 3 Pro **no pueden ejecutar misiones de waypoint** (WPML), por
una limitación de su firmware que DJI ha confirmado: sus mandos no tienen función
de rutas y la app oficial DJI Fly tampoco. El síntoma es
`errorType=WAYPOINT`, `errorCode=REQUEST_HANDLER_NOT_FOUND` al subir el KMZ.

No se arregla registrando la app, ni con un perfil de allowlist, ni cambiando de
firmware dentro de la misma familia. Con un Mini 3 **sí** funcionan la conexión,
la telemetría, la cámara, el vídeo y el control manual.

## Binarios

| Archivo | Tamaño | SHA-256 |
|---|---:|---|
| `DJIFlyPro-1.1.0-alpha.8.apk` | 205,138,570 bytes | `ec4cac991e563f6553b42b30893957dd984863734fbfa53bbbf4c41b52e630b4` |
| `DJIFlyPro-1.1.0-alpha.8.aab` | 198,945,965 bytes | `f174e2d95ff11d626ec872931b5d4fc5aa3090f5b96dd58b3542e2c37481b3e7` |
| `update.json` | 615 bytes | `f574b211d03f84f581a4cccafff222cb459f1390d5274d6592d318864151469a` |

El `.aab` es para subir a Google Play, no para instalar a mano.

Firmada con el mismo certificado que las anteriores
(`CN=DJIFlyPro Local Test`), que es lo que permite que la app acepte actualizar
desde ellas al comprobar el firmante:

```
SHA-256: 8a3aac2e456093f76dc0a2ccd717538b1732da3cecf470231feb48de187def43
SHA-1:   8437d47734c8fa198ab1c05bb819517ccbaaaea7
MD5:     0bea2157164ef298389284423a21aaa5
```

## Verificación

319 pruebas unitarias, 0 fallos. Lint con 0 errores.

`scripts/verify-published-release.sh 8` recorre el camino completo contra esta
release: busca el manifiesto por la misma API, lo valida, decide que hay versión
nueva, descarga los 205 MB desde la URL que anuncia el manifiesto y comprueba el
hash y el firmante.

Lo que **no** está probado: el registro real contra los servidores de DJI, el
enlace con el control remoto, el diálogo del permiso de instalación y si las
teselas pintan de verdad en el dispositivo. Eso solo se prueba en un móvil.
