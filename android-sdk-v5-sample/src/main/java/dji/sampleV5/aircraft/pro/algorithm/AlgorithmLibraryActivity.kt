package dji.sampleV5.aircraft.pro.algorithm

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.pro.mission.MissionPlannerActivity

class AlgorithmLibraryActivity : AppCompatActivity() {
    private lateinit var repository: AlgorithmRepository
    private lateinit var list: LinearLayout
    private lateinit var status: TextView

    private val openJson = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching { repository.import(uri) }
            .onSuccess { recipe ->
                status.text = "Receta importada: ${recipe.name}"
                render()
            }
            .onFailure { error ->
                AlertDialog.Builder(this)
                    .setTitle("No se pudo importar")
                    .setMessage(error.message ?: "Receta inválida")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_algorithm_library)
        repository = AlgorithmRepository(this)
        list = findViewById(R.id.algorithm_list)
        status = findViewById(R.id.text_algorithm_status)
        findViewById<Button>(R.id.button_import_algorithm).setOnClickListener {
            openJson.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        render()
    }

    private fun render() {
        list.removeAllViews()
        val inflater = LayoutInflater.from(this)
        repository.list().forEach { recipe ->
            val card = inflater.inflate(R.layout.pro_algorithm_card, list, false)
            card.findViewById<TextView>(R.id.algorithm_name).text = recipe.name
            card.findViewById<TextView>(R.id.algorithm_description).text = recipe.description
            card.findViewById<TextView>(R.id.algorithm_metadata).text =
                "${recipe.id} · v${recipe.version} · ${recipe.template.displayName} · ${recipe.routePattern.displayName}"
            card.findViewById<Button>(R.id.algorithm_use).setOnClickListener {
                startActivity(
                    Intent(this, MissionPlannerActivity::class.java)
                        .putExtra(MissionPlannerActivity.EXTRA_ALGORITHM_ID, recipe.id)
                )
            }
            list.addView(card)
        }
        status.text = "${repository.list().size} recetas disponibles. Solo se admiten JSON declarativos validados."
    }
}
