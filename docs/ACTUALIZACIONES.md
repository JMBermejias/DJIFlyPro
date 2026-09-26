# Actualizaciones dentro de la app

DJIFlyPro comprueba si hay una versión nueva al abrirse y ofrece un botón para
instalarla. Este documento explica cómo funciona, qué comprueba antes de
instalar nada, y qué tiene que hacer quien publica una release.

## Cómo se entera de que hay una versión nueva

Al abrir la app, y cada vez que se pulsa **Buscar actualizaciones**, se consulta
la API pública de releases de GitHub:

```
https://api.github.com/repos/JMBermejias/DJIFlyPro/releases
```

Se busca la release más reciente que publique un asset llamado `update.json`, se
descarga y se compara su `versionCode` con el de la app instalada. Es una
comparación de enteros: si el número remoto es mayor, hay actualización.

**No se usa `releases/latest`.** Ese endpoint excluye las prereleases, y todas
las releases de este proyecto lo son, así que siempre respondería "no hay nada".
El endpoint de lista sí las incluye y viene de más reciente a más antigua.

La comprobación se hace como mucho **una vez cada seis horas** y se guarda en
`SharedPreferences`. Es una petición a un tercero cada vez que se abre la app, y
esa es una coste que se paga de vez en cuando y no siempre. Un fallo de red
sale como una línea de texto y nada más: "no hay actualizaciones" no puede
confundirse con "no funciona la red", y la red caída no puede parecer un error
que el operador tenga que resolver.

## El manifiesto de actualización

Cada release publica un `update.json` junto a los binarios:

```json
{
  "schema": "djiflypro.update/v1",
  "versionCode": 6,
  "versionName": "1.1.0-alpha.5",
  "tag": "v1.1.0-alpha.5",
  "apkUrl": "https://github.com/.../DJIFlyPro-1.1.0-alpha.5.apk",
  "apkSize": 205135390,
  "sha256": "b0e8b9af...",
  "signingCertificateSha256": "8a3aac2e...",
  "releaseUrl": "https://github.com/.../releases/tag/v1.1.0-alpha.5",
  "notes": "Novedad de esta versión: ...",
  "publishedAt": "2026-09-26T15:44:26Z"
}
```

Existe porque la API de GitHub no dice nada sobre la versión de un APK, y el
nombre de un tag es una cadena que dos compilaciones distintas podrían
compartir. Aquí el `versionCode` sale del propio build y los dos digests, de los
ficheros recién construidos, así que nada del manifiesto puede desviarse de lo
que se publica.

Lo genera `scripts/build-update-manifest.sh`, al que `build-release.sh` llama
sola después de copiar los binarios. **No hay que acordarse:** si alguien
construye una release sin el manifiesto, `sha256sum -c` falla porque el manifiesto
de hashes lo incluye.

## Qué se comprueba antes de instalar

Una app que puede instalar una versión nueva de sí misma tiene la misma
capacidad que usa el malware para propagarse, así que la descarga se trata como
entrada no confiable hasta que **dos** comprobaciones independientes salen bien:

1. **El hash.** Los bytes descargados tienen que coincidir con el `sha256` que
   publica la release, descargado por HTTPS desde GitHub.
2. **El firmante.** El certificado del APK tiene que ser el mismo que el de la
   app instalada.

La segunda es la que importa. Si alguien-secuestra la cuenta o publica una
release manipulada, el atacante controla el manifiesto, y con solo el hash
podría hacer que la app instalara cualquier APK que él hubiera subido. La
comparación de firmantes impide justo eso: un APK de otra aplicación no puede
pasar por uno de DJIFlyPro.

Si **cualquiera** de las dos falla, el fichero se borra y no se abre el
instalador. La app lo dice y no instala nada:

| Situación | Qué hace |
|---|---|
| Hash distinto | «La descarga no coincide con la release» y no instala |
| Firmante distinto | «Firma distinta» y no instala |
| Certificado de la app ilegible | No instala, y lo dice |
| Descarga más grande de la anunciada | Corta la descarga, para no llenar el almacenamiento |
| Descarga incompleta | Borra el fichero parcial |

El digest se compara **sin distinguir mayúsculas**: un manifiesto escrito a mano
y otro escrito por un script se diferencian en las mayúsculas de los hex, y eso
no es un evento de seguridad. Bloquear una actualización legítima por eso sería
peor que el propio riesgo que se está cubriendo.

## La app nunca instala por su cuenta

El flujo termina siempre en la pantalla de instalación del sistema, que es la
que muestra qué va a cambiar y pide la confirmación. La app nunca llama a
`PackageInstaller` con confirmación automática ni hace nada por la espalda.

## El permiso de instalación

`android.permission.REQUEST_INSTALL_PACKAGES` está declarado. En Android 8 y
posteriores no basta: el usuario tiene que conceder a DJIFlyPro el permiso de
"instalar apps desconocidas", y eso se pide **en el momento**, con un diálogo
que lleva a Ajustes. Si el usuario prefiere no concederlo, el diálogo se lo dice
y puede descargar el APK a mano desde la página de la release.

Es un permiso con implicaciones: le da a la app la capacidad de instalar
paquetes. Está aquí porque el usuario pidió poder actualizar desde la app sin
salir de ella, y porque un APK auto-firmado e instalado a mano no puede usar el
mecanismo de actualizaciones en línea de Google Play.

## Qué tiene que hacer quien publica

```bash
sed -i 's/versionCode 6/versionCode 7/; s/1.1.0-alpha.5/1.1.0-alpha.6/' \
  android-sdk-v5-sample/build.gradle
./scripts/build-release.sh --tag v1.1.0-alpha.6
git push && git tag v1.1.0-alpha.6
gh release create v1.1.0-alpha.6 --prerelease \
  --notes-file artifacts/release-notes.md \
  artifacts/DJIFlyPro-*.apk artifacts/DJIFlyPro-*.aab artifacts/update.json
```

Lo único imprescindible es **subir `update.json` como asset**. Si no se sube,
la app no encuentra ninguna release con manifiesto y lo dice, en vez de
inventarse que está al día.

El orden importa: `update.json` describe un APK concreto por su hash, así que si
el APK se reconstruye después hay que regenerar el manifiesto.

## Comprobarlo sin dispositivo

`scripts/verify-published-release.sh [versionCodeInstalado]` reproduce el
camino entero contra la release publicada: busca el manifiesto por la misma
API, lo valida con las mismas reglas, decide si hay versión nueva, descarga el
APK y comprueba el hash y el firmante.

```bash
./scripts/verify-published-release.sh 5
```

No sustituye a las pruebas unitarias, que son la red permanente. Comprueba lo
que un `gradlew test` no puede ver: que la release publicada y la app hablan el
mismo idioma.

## Lo que no cubre

- **No hay actualización porWi-Fi ni en segundo plano.** La comprobación ocurre
  al abrir la app. Es lo que Android permite sin servicios de Google en un
  dispositivo sin cuenta.
- **No hay actualización delta.** Se descarga el APK entero, unos 205 MB.
- **Un fallo de red no se reintenta solo.** Espera a las seis horas o a que se
  pulse el botón.
- **No se comprueba nada por OTA.** Cada release es manual y con etiqueta.
