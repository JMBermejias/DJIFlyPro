package dji.sampleV5.aircraft.pro

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.pages.ControlCenterFragment

class ControlCenterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control_center)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.control_center_container, ControlCenterFragment())
                .commit()
        }
    }
}
