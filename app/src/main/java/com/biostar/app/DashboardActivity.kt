package com.biostar.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var deviceRef: DatabaseReference
    private lateinit var commandsRef: DatabaseReference

    private var isAutoMode = false

    // UI Components
    private lateinit var fanSeekBar: SeekBar
    private lateinit var co2Chart: LineChart
    private lateinit var pm25Chart: LineChart
    private lateinit var airQualityStatus: TextView

    // Chart data history
    private val co2History = LinkedList<Entry>()
    private val pm25History = LinkedList<Entry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Firebase setup
        auth = FirebaseAuth.getInstance()
        deviceRef = FirebaseDatabase.getInstance().getReference("biostar/devices/device_001")
        commandsRef = deviceRef.child("commands")

        // Initialize UI, Charts, and Listeners
        setupUI()
        setupCharts()
        setupFirebaseListeners()
        setupInteractionListeners()
    }

    private fun setupUI() {
        fanSeekBar = findViewById(R.id.fanSeekBar)
        co2Chart = findViewById(R.id.co2Chart)
        pm25Chart = findViewById(R.id.pm25Chart)
        airQualityStatus = findViewById(R.id.airQualityStatus)
    }

    private fun setupCharts() {
        initChart(co2Chart, "CO2 Readings")
        initChart(pm25Chart, "PM2.5 Readings")
    }

    private fun setupFirebaseListeners() {
        // Device Status & Last Seen Listener
        deviceRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.child("status").getValue(String::class.java)
                val lastSeenTimestamp = snapshot.child("lastSeen").getValue(Long::class.java)

                findViewById<TextView>(R.id.deviceStatus).apply {
                    text = "Device Status: ${status ?: "Unknown"}"
                    setTextColor(if (status == "ONLINE") Color.GREEN else Color.RED)
                }
                findViewById<TextView>(R.id.lastSeen).text = "Last Seen: ${formatTimestamp(lastSeenTimestamp)}"
            }
            override fun onCancelled(error: DatabaseError) {
                findViewById<TextView>(R.id.deviceStatus).apply {
                    text = "Device Status: Error"
                    setTextColor(Color.RED)
                }
            }
        })

        // Sensor Data Listener
        deviceRef.child("sensors").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val co2 = snapshot.child("air/co2").getValue(Int::class.java)
                val temp = snapshot.child("air/temperature").getValue(Double::class.java)
                val humidity = snapshot.child("air/humidity").getValue(Int::class.java)
                val pm25 = snapshot.child("dust/pm25").getValue(Int::class.java)
                val mq135 = snapshot.child("gas/mq135").getValue(Int::class.java)

                // Update text views
                findViewById<TextView>(R.id.co2Value).text = "CO₂: ${co2 ?: "--"} ppm"
                findViewById<TextView>(R.id.tempValue).text = "Temperature: ${temp ?: "--"} °C"
                findViewById<TextView>(R.id.humidityValue).text = "Humidity: ${humidity ?: "--"} %"
                findViewById<TextView>(R.id.pm25Value).text = "PM2.5: ${pm25 ?: "--"} µg/m³"
                findViewById<TextView>(R.id.mq135Value).text = "MQ135: ${mq135 ?: "--"}"

                val overallStatus = updateAirQualityStatus(co2, pm25)
                if (isAutoMode) {
                    activateAutoFan(overallStatus)
                }

                // Update charts with new, stable method
                updateChart(co2Chart, co2History, co2?.toFloat())
                updateChart(pm25Chart, pm25History, pm25?.toFloat())
            }
            override fun onCancelled(error: DatabaseError) { /* Handle error */ }
        })
    }

    private fun setupInteractionListeners() {
        // Logout Button
        findViewById<Button>(R.id.logoutBtn).setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java) // Corrected from MainActivity
            startActivity(intent)
            finish()
        }

        // Fan Mode Switch
        val fanModeSwitch = findViewById<Switch>(R.id.fanModeSwitch)
        fanModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            isAutoMode = isChecked
            fanSeekBar.isEnabled = !isChecked
            commandsRef.child("fanMode").setValue(if (isChecked) "AUTO" else "MANUAL")
            fanModeSwitch.text = if (isChecked) "Auto Mode" else "Manual Mode"
        }

        // Fan Speed SeekBar
        fanSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                findViewById<TextView>(R.id.fanValue).text = "Fan Speed: $progress %"
                if (fromUser && !isAutoMode) {
                    commandsRef.child("fanSpeed").setValue(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // --- Helper Functions ---

    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null) return "--"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun updateAirQualityStatus(co2: Int?, pm25: Int?): String {
        val co2Status = if (co2 == null) "Unknown" else if (co2 > 2000) "Poor" else if (co2 > 1000) "Moderate" else "Good"
        val pm25Status = if (pm25 == null) "Unknown" else if (pm25 > 35) "Poor" else if (pm25 > 12) "Moderate" else "Good"

        val overallStatus = when {
            co2Status == "Poor" || pm25Status == "Poor" -> "Poor"
            co2Status == "Moderate" || pm25Status == "Moderate" -> "Moderate"
            else -> "Good"
        }

        airQualityStatus.text = "Air Quality: $overallStatus"
        airQualityStatus.setTextColor(
            when (overallStatus) {
                "Good" -> Color.GREEN
                "Moderate" -> Color.parseColor("#FFA500") // Orange
                "Poor" -> Color.RED
                else -> Color.GRAY
            }
        )
        return overallStatus
    }

    private fun activateAutoFan(overallStatus: String) {
        val fanSpeed = when (overallStatus) {
            "Good" -> 0
            "Moderate" -> 50
            "Poor" -> 100
            else -> 0
        }
        commandsRef.child("fanSpeed").setValue(fanSpeed)
        fanSeekBar.progress = fanSpeed
    }

    private fun initChart(chart: LineChart, label: String) {
        chart.description.text = label
        chart.setNoDataText("Waiting for data...")
        chart.invalidate()
    }

    private fun updateChart(chart: LineChart, history: LinkedList<Entry>, newValue: Float?) {
        if (newValue == null) return

        // 1. Manage the history list
        if (history.size >= 30) {
            history.removeFirst() // Remove the oldest entry
        }
        history.addLast(Entry(0f, newValue)) // Add the new entry

        // 2. Re-index X values from 0 to history.size - 1
        for ((index, entry) in history.withIndex()) {
            entry.x = index.toFloat()
        }

        // 3. Create a fresh DataSet from the updated history
        val dataSet = LineDataSet(history, chart.description.text)
        dataSet.color = Color.CYAN
        dataSet.setDrawValues(false)
        dataSet.setDrawCircles(true)

        // 4. Set the fresh data to the chart
        chart.data = LineData(dataSet)
        chart.invalidate() // Refresh the chart
    }
}
