package com.biostar.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var deviceRef: DatabaseReference
    private lateinit var commandsRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        auth = FirebaseAuth.getInstance()

        // Root references
        deviceRef = FirebaseDatabase.getInstance()
            .getReference("biostar/devices/device_001")

        commandsRef = deviceRef.child("commands")

        // UI references
        val co2Text = findViewById<TextView>(R.id.co2Value)
        val tempText = findViewById<TextView>(R.id.tempValue)
        val humidityText = findViewById<TextView>(R.id.humidityValue)
        val pm25Text = findViewById<TextView>(R.id.pm25Value)
        val mq135Text = findViewById<TextView>(R.id.mq135Value)

        val fanSeekBar = findViewById<SeekBar>(R.id.fanSeekBar)
        val fanValue = findViewById<TextView>(R.id.fanValue)
        val logoutBtn = findViewById<Button>(R.id.logoutBtn)

        // ================= SENSOR LISTENER =================
        deviceRef.child("sensors")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    val co2 = snapshot.child("air/co2").getValue(Int::class.java)
                    val temp = snapshot.child("air/temperature").getValue(Double::class.java)
                    val humidity = snapshot.child("air/humidity").getValue(Int::class.java)
                    val pm25 = snapshot.child("dust/pm25").getValue(Int::class.java)
                    val mq135 = snapshot.child("gas/mq135").getValue(Int::class.java)

                    co2Text.text = "CO₂: ${co2 ?: "--"} ppm"
                    tempText.text = "Temperature: ${temp ?: "--"} °C"
                    humidityText.text = "Humidity: ${humidity ?: "--"} %"
                    pm25Text.text = "PM2.5: ${pm25 ?: "--"} µg/m³"
                    mq135Text.text = "MQ135: ${mq135 ?: "--"}"
                }

                override fun onCancelled(error: DatabaseError) {
                    co2Text.text = "Sensor read error"
                }
            })

        // ================= FAN CONTROL =================
        fanSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                fanValue.text = "Fan Speed: $progress %"

                if (fromUser) {
                    // SAFE WRITE — DOES NOT TOUCH SENSORS
                    commandsRef.child("fanSpeed").setValue(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // ================= LOGOUT =================
        logoutBtn.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
