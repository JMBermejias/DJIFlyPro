package dji.sampleV5.aircraft.pro.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

/**
 * The notice the dashboard shows when a newer build exists, and the whole
 * download-verify-install flow behind its button.
 *
 * The flow is deliberately dull. It shows what will happen, downloads over
 * HTTPS, checks the file against the digest and the signer the release
 * published, and only then hands the file to the system installer. It never
 * installs anything by itself: the user always sees and accepts the system's
 * own install screen, which is the step that shows what is about to change.
 */
class UpdateActivity : AppCompatActivity() {

    private val client = UpdateClient()
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var manifest: UpdateManifest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val offered = intent.getBooleanExtra(EXTRA_AUTOMATIC, false)
        val manifest = intent.getStringExtra(EXTRA_MANIFEST)

        if (manifest == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val parsed = runCatching { UpdateManifest.parse(manifest) }.getOrNull()
        if (parsed == null || !parsed.validate().isValid) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        this.manifest = parsed
        render(parsed, automatic = offered)
    }

    private fun render(manifest: UpdateManifest, automatic: Boolean) {
        val installed = installedVersionCode()
        val title = if (automatic) "Hay una versión nueva" else "Sin actualizaciones nuevas"
        val current = installed.takeIf { it > 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(
                buildString {
                    appendLine("Instalada: ${installedVersionName()} ($current)")
                    appendLine("Disponible: ${manifest.versionName} (${manifest.versionCode})")
                    if (manifest.notes.isNotBlank()) {
                        appendLine()
                        appendLine(manifest.notes)
                    }
                    appendLine()
                    append("La actualización se descarga de la release oficial de GitHub y se ")
                    append("comprueba contra la firma de esta misma aplicación antes de abrir el ")
                    append("instalador del sistema.")
                }
            )
            .setPositiveButton("Actualizar") { _, _ -> startUpdate(manifest) }
            .setNegativeButton("Ahora no") { _, _ -> setResult(RESULT_CANCELED); finish() }
            .setOnCancelListener { finish() }
            .setOnDismissListener { if (!isFinishing) finish() }
            .show()
    }

    private fun startUpdate(manifest: UpdateManifest) {
        if (!canRequestInstallPackages()) {
            askForInstallPermission(manifest)
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Descargando ${manifest.versionName}")
            .setMessage("Conectando con la release…")
            .setNegativeButton("Cancelar") { _, _ -> executor.shutdownNow() }
            .setCancelable(false)
            .create()
        dialog.show()

        val target = File(getExternalFilesDir("apk") ?: filesDir, "DJIFlyPro-${manifest.versionName}.apk")
        executor.execute {
            val result = client.download(manifest, target) { done, total ->
                runOnUiThread {
                    val percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                    dialog.setMessage("Descargando… $percent%")
                }
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                when (result) {
                    is ApkVerifier.Result.Valid -> {
                        dialog.dismiss()
                        offerInstall(target, manifest)
                    }
                    is ApkVerifier.Result.HashMismatch -> {
                        dialog.dismiss()
                        refuse("La descarga no coincide con la release", "El fichero descargado no es el publicado. No se va a instalar nada.")
                    }
                    is ApkVerifier.Result.WrongSigner -> {
                        dialog.dismiss()
                        refuse("Firma distinta", "El APK está firmado con un certificado distinto del de esta app. No se va a instalar nada.")
                    }
                    is ApkVerifier.Result.Unreadable -> {
                        dialog.dismiss()
                        refuse("No se ha podido descargar", result.reason)
                    }
                }
            }
        }
    }

    /**
     * The certificate check needs the running app's own signer, so it happens
     * here and not in [ApkVerifier]. If it cannot be read, the update stops:
     * a hash match without a signer match is not enough to install over yourself.
     */
    private fun offerInstall(file: File, manifest: UpdateManifest) {
        val installedCertificate = installedCertificateSha256()
        if (installedCertificate == null) {
            refuse(
                "No se ha podido comprobar la firma",
                "No se ha podido leer el certificado de esta aplicación, así que no se puede comprobar " +
                    "que el APK venga de la misma firma. Descárgalo a mano desde la release."
            )
            return
        }
        if (!installedCertificate.equals(manifest.signingCertificateSha256, ignoreCase = true)) {
            refuse(
                "Firma distinta",
                "El APK publicado está firmado con un certificado distinto del de esta aplicación. " +
                    "O el firmante de la app ha cambiado, o el APK no es de este proyecto. No se va a instalar nada."
            )
            return
        }
        val verification = ApkVerifier.verify(file, manifest, installedCertificate)
        if (verification !is ApkVerifier.Result.Valid) {
            refuse("El APK descargado no es válido", verification.reasonOrMismatch())
            return
        }
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileProvider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { startActivity(intent) }.isSuccess
        if (!launched) {
            askForInstallPermission(manifest, file)
            return
        }
        setResult(RESULT_OK)
        finish()
    }

    private fun askForInstallPermission(manifest: UpdateManifest, file: File? = null) {
        if (canRequestInstallPackages()) {
            if (file != null) offerInstall(file, manifest) else startUpdate(manifest)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Permiso necesario")
            .setMessage(
                "Para instalar la actualización, Android necesita que autorices a DJIFlyPro a instalar " +
                    "paquetes. Se abre Ajustes; al volver, pulsa Actualizar otra vez.\n\n" +
                    "Si prefieres no concederlo, puedes descargar el APK a mano desde la página de la release."
            )
            .setPositiveButton("Abrir Ajustes") { _, _ ->
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
            .setNegativeButton("Ahora no") { _, _ -> setResult(RESULT_CANCELED); finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun refuse(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Entendido") { _, _ -> setResult(RESULT_CANCELED); finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun canRequestInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private fun installedVersionCode(): Int = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionCode
    }.getOrDefault(0)

    private fun installedVersionName(): String = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    /** SHA-256 of the certificate the running app is signed with, in hex. */
    private fun installedCertificateSha256(): String? = runCatching {
        val info = packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        @Suppress("DEPRECATION")
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return null
            if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        signatures?.firstOrNull()?.let { ApkVerifier.toHex(it.toByteArray()) }
    }.onFailure { Log.w(TAG, "Could not read the installed signing certificate", it) }.getOrNull()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun ApkVerifier.Result.reasonOrMismatch(): String = when (this) {
        is ApkVerifier.Result.HashMismatch -> "El hash no coincide: se esperaba $expected y se obtuvo $actual."
        is ApkVerifier.Result.WrongSigner -> "El firmante no coincide: se esperaba $expected y se obtuvo $actual."
        is ApkVerifier.Result.Unreadable -> reason
        is ApkVerifier.Result.Valid -> ""
    }

    companion object {
        private const val TAG = "DJIFlyProUpdate"
        const val EXTRA_MANIFEST = "com.djiflypro.update.MANIFEST"
        const val EXTRA_AUTOMATIC = "com.djiflypro.update.AUTOMATIC"

        fun launchIntent(context: Context, manifest: UpdateManifest, automatic: Boolean): Intent =
            Intent(context, UpdateActivity::class.java)
                .putExtra(EXTRA_MANIFEST, manifest.toJson())
                .putExtra(EXTRA_AUTOMATIC, automatic)
    }
}
