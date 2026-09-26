package dji.sampleV5.aircraft.pro.cartography.ui

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.pro.cartography.ControlPointRole
import dji.sampleV5.aircraft.pro.cartography.ControlPointTarget
import dji.sampleV5.aircraft.pro.cartography.GroundControlPoint
import java.util.Locale
import java.util.UUID

/**
 * Editor for a single ground control point.
 *
 * The accuracy and the source are not decoration: they are what lets a
 * delivered product state how good it is, so they are first-class fields next
 * to the coordinate.
 */
object ControlPointEditorDialog {

    fun show(context: Context, existing: GroundControlPoint?, onSave: (GroundControlPoint) -> Unit) {
        val view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_control_point, null)
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Guardar", null)
            .create()

        val codeInput = view.findViewById<EditText>(R.id.edit_gcp_code)
        val latitudeInput = view.findViewById<EditText>(R.id.edit_gcp_latitude)
        val longitudeInput = view.findViewById<EditText>(R.id.edit_gcp_longitude)
        val heightInput = view.findViewById<EditText>(R.id.edit_gcp_height)
        val accuracyInput = view.findViewById<EditText>(R.id.edit_gcp_accuracy)
        val sourceInput = view.findViewById<EditText>(R.id.edit_gcp_source)
        val targetSpinner = view.findViewById<Spinner>(R.id.spinner_gcp_target)
        val roleSpinner = view.findViewById<Spinner>(R.id.spinner_gcp_role)

        targetSpinner.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            ControlPointTarget.entries.map { "${it.displayName} (±${it.typicalAccuracyMillimeters.toInt()} mm)" }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        roleSpinner.adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            ControlPointRole.entries.map { it.displayName }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        if (existing == null) {
            codeInput.setText(nextCode())
        } else {
            codeInput.setText(existing.code)
            latitudeInput.setText(String.format(Locale.US, "%.7f", existing.latitude))
            longitudeInput.setText(String.format(Locale.US, "%.7f", existing.longitude))
            existing.heightMeters?.let { heightInput.setText(String.format(Locale.US, "%.2f", it)) }
            existing.horizontalAccuracyMillimeters?.let {
                accuracyInput.setText(String.format(Locale.US, "%.0f", it))
            }
            sourceInput.setText(existing.source)
            targetSpinner.setSelection(ControlPointTarget.entries.indexOf(existing.target).coerceAtLeast(0))
            roleSpinner.setSelection(ControlPointRole.entries.indexOf(existing.role).coerceAtLeast(0))
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val point = runCatching { read(view, existing, targetSpinner, roleSpinner) }.getOrElse { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "Revisa los datos del punto",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                val validation = point.validate()
                if (!validation.isValid) {
                    Toast.makeText(
                        context,
                        validation.errors.joinToString("\n"),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                onSave(point)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun read(
        view: android.view.View,
        existing: GroundControlPoint?,
        targetSpinner: Spinner,
        roleSpinner: Spinner
    ): GroundControlPoint = GroundControlPoint(
        id = existing?.id ?: "gcp-${UUID.randomUUID()}",
        code = view.findViewById<EditText>(R.id.edit_gcp_code).text.toString().trim(),
        latitude = view.findViewById<EditText>(R.id.edit_gcp_latitude).text.toString()
            .trim().replace(',', '.').toDoubleOrNull() ?: error("La latitud debe ser un número"),
        longitude = view.findViewById<EditText>(R.id.edit_gcp_longitude).text.toString()
            .trim().replace(',', '.').toDoubleOrNull() ?: error("La longitud debe ser un número"),
        heightMeters = view.findViewById<EditText>(R.id.edit_gcp_height).text.toString()
            .trim().replace(',', '.').toDoubleOrNull(),
        role = ControlPointRole.entries[
            roleSpinner.selectedItemPosition.coerceIn(0, ControlPointRole.entries.lastIndex)
        ],
        target = ControlPointTarget.entries[
            targetSpinner.selectedItemPosition.coerceIn(0, ControlPointTarget.entries.lastIndex)
        ],
        horizontalAccuracyMillimeters = view.findViewById<EditText>(R.id.edit_gcp_accuracy).text.toString()
            .trim().replace(',', '.').toDoubleOrNull(),
        source = view.findViewById<EditText>(R.id.edit_gcp_source).text.toString().trim()
    )

    private fun nextCode(): String = "CP%02d".format(Locale.US, (1..99).random())
}
