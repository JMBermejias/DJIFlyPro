package dji.sampleV5.aircraft.pro

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import dji.sampleV5.aircraft.R

class DocumentationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documentation)
        findViewById<Button>(R.id.button_close_documentation).setOnClickListener { finish() }
    }
}
