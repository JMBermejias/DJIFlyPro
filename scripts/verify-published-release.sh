#!/usr/bin/env bash
#
# Comprobacion de extremo a extremo contra la release publicada en GitHub.
#
# Reproduce el camino que sigue la app instalada, con la misma logica que
# UpdateManifest, UpdateClient y ApkVerifier: encontrar la release con
# manifiesto, validarlo, decidir si hay version nueva, descargar el APK y
# comprobar el hash y el firmante.
#
# No sustituye a las pruebas unitarias, que son la red permanente. Esto
# comprueba que la release publicada y la app hablan el mismo idioma, que es
# lo que no se puede ver en un `gradlew test`.
#
# Uso: scripts/verify-published-release.sh [versionCodeInstalado]

set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="JMBermejias/DJIFlyPro"
INSTALLED="${1:-0}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

export JAVA_HOME="${JAVA_HOME:-$ROOT_DIR/tools/jdk/17}"
export PATH="$JAVA_HOME/bin:$PATH"
BT="$(ls -d "$ROOT_DIR/tools/android-sdk"/build-tools/* 2>/dev/null | sort -V | tail -1)"

say() { printf '%s\n' "$*"; }
fail() { say "FALLO: $*" >&2; exit 1; }

say "1. Buscando la release con manifiesto, como hace la app"
python3 - "$REPO" "$WORK" <<'PY'
import json, sys, urllib.request
repo, work = sys.argv[1], sys.argv[2]
def get(url):
    return urllib.request.urlopen(urllib.request.Request(
        url, headers={"Accept": "application/vnd.github+json",
                      "User-Agent": "DJIFlyPro-Android"}), timeout=40)
releases = json.load(get(f"https://api.github.com/repos/{repo}/releases?per_page=10"))
# Sorted by timestamp, never by position. This script had the same bug as the
# app it verifies: it took the first release with a manifest, and the API
# returned v1.1.0-alpha.10 last despite it being the newest by created_at. So it
# kept reporting an old release as current, and the verification passed against
# the wrong files for several releases in a row. A checker that shares the bug it
# is meant to catch is worse than no checker, because it gives a green light.
with_manifest = sorted(
    (r for r in releases if any(a["name"] == "update.json" for a in r.get("assets", []))),
    key=lambda r: r.get("published_at") or r.get("created_at") or "",
    reverse=True)
if not with_manifest:
    sys.exit("Ninguna release publica update.json")
newest = with_manifest[0]
asset = next(a for a in newest["assets"] if a["name"] == "update.json")
print(f"   release {newest['tag_name']} (prerelease={newest['prerelease']})")
raw = get(asset["browser_download_url"]).read()
open(f"{work}/update.json", "wb").write(raw)
open(f"{work}/tag", "w").write(newest["tag_name"])
PY

say "1b. Que la URL de descarga del manifiesto sirva el APK y no la web de la release"
# This is the check that was missing. An earlier version of this script took the
# APK URL from the API's browser_download_url, which is always correct, so the
# manifest's own apkUrl was never exercised. The manifest carried
# /releases/tag/<tag>/download/<asset>, which answers with the release's HTML
# page: 206 on a range request, and ~218 KB of HTML where 205 MB of APK should
# be. Status-only checks pass on it. So the URL under test is the one in the
# manifest, which is the one the app actually downloads from.
python3 - "$WORK/update.json" <<'PY'
import json, sys, urllib.request
m = json.load(open(sys.argv[1], encoding="utf-8"))
url = m["apkUrl"]
r = urllib.request.urlopen(urllib.request.Request(
    url, headers={"User-Agent": "DJIFlyPro-Android", "Range": "bytes=0-3"}), timeout=40)
head = r.read(4)
print(f"   {url}")
if head[:2] != b"PK":
    sys.exit(f"la URL no sirve un APK (empieza por {head[:8]!r}, no por 'PK'). "
             f"Status {r.status}, content-type {r.headers.get('Content-Type')}")
print(f"   Status {r.status}, content-type {r.headers.get('Content-Type')}, magic PK: correcto")
PY

say "2. Validando el manifiesto con las mismas reglas que UpdateManifest.validate()"
python3 - "$WORK/update.json" <<'PY'
import json, re, sys
m = json.load(open(sys.argv[1], encoding="utf-8"))
errors = []
if m.get("schema") != "djiflypro.update/v1": errors.append("schema desconocido")
if not isinstance(m.get("versionCode"), int) or m["versionCode"] <= 0: errors.append("versionCode")
if not m.get("versionName"): errors.append("versionName")
if not str(m.get("tag", "")).startswith("v"): errors.append("tag")
for key in ("apkUrl", "releaseUrl"):
    if not str(m.get(key, "")).startswith("https://"): errors.append(f"{key} no es HTTPS")
if not isinstance(m.get("apkSize"), int) or m["apkSize"] <= 0: errors.append("apkSize")
for key in ("sha256", "signingCertificateSha256"):
    if not re.fullmatch(r"[a-fA-F0-9]{64}", str(m.get(key, ""))): errors.append(key)
if errors:
    sys.exit("manifiesto invalido: " + ", ".join(errors))
print(f"   manifest ok: {m['versionName']} (versionCode {m['versionCode']}, {m['apkSize']:,} bytes)")
PY

say "3. Decidiendo si hay version nueva frente a la instalada ($INSTALLED)"
python3 - "$WORK/update.json" "$INSTALLED" <<'PY'
import json, sys
m = json.load(open(sys.argv[1], encoding="utf-8"))
installed = int(sys.argv[2])
newer = m["versionCode"] > installed
print(f"   remota={m['versionCode']} instalada={installed} -> "
      + ("hay actualizacion" if newer else "ya esta al dia"))
sys.exit(0 if newer else 3)
PY

say "4. Descargando el APK desde la URL que anuncia el manifiesto"
APK_URL="$(python3 -c "import json,sys;print(json.load(open(sys.argv[1]))['apkUrl'])" "$WORK/update.json")"
curl -sL --fail --retry 3 -o "$WORK/app.apk" "$APK_URL"
say "   $(stat -c %s "$WORK/app.apk") bytes descargados"

say "5. Comprobando el hash, como hace ApkVerifier"
python3 - "$WORK/update.json" "$WORK/app.apk" <<'PY'
import hashlib, json, os, sys
m = json.load(open(sys.argv[1], encoding="utf-8"))
apk = sys.argv[2]
size = os.path.getsize(apk)
if size != m["apkSize"]:
    sys.exit(f"el tamano no coincide: manifiesto {m['apkSize']}, fichero {size}")
h = hashlib.sha256(open(apk, "rb").read()).hexdigest()
if h.lower() != m["sha256"].lower():
    sys.exit(f"el hash no coincide: manifiesto {m['sha256']}, fichero {h}")
print(f"   hash ok: {h}")
PY

say "6. Comprobando el firmante, como hace UpdateActivity"
CERT="$("$BT/apksigner" verify --print-certs "$WORK/app.apk" 2>/dev/null \
  | sed -n 's/.*certificate SHA-256 digest: //p' | tr -d '[:space:]' | tr 'A-Z' 'a-z' | head -1)"
EXPECTED="$(python3 -c "import json;print(json.load(open('$WORK/update.json'))['signingCertificateSha256'].lower())")"
[ -n "$CERT" ] || fail "no se ha podido leer el certificado del APK"
[ "$CERT" = "$EXPECTED" ] || fail "el firmante no coincide: manifiesto $EXPECTED, APK $CERT"
say "   firmante ok: $CERT"

say ""
say "OK: la release publicada supera las mismas comprobaciones que la app hace."
