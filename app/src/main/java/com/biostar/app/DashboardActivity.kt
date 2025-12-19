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

    // UI
    private lateinit var fanSeekBar: SeekBar
    private lateinit var co2Chart: LineChart
    private lateinit var pm25Chart: LineChart
    private lateinit var airQualityStatus: TextView

    // Chart history
    private val co2History = LinkedList<Entry>()
    private val pm25History = LinkedList<Entry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_dashboard)

        deviceRef = FirebaseDatabase
            .getInstance()
            .getReference("biostar/devices/device_001")

        commandsRef = deviceRef.child("commands")

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
        initChart(co2Chart, "CO₂ (ppm)")
        initChart(pm25Chart, "PM2.5 (µg/m³)")
    }

    // =====================================================
    // 🔥 FIREBASE LISTENERS (SEPARATED PROPERLY)
    // =====================================================
    private fun setupFirebaseListeners() {

        // -------- SENSOR LISTENER (ONLY sensors + graphs) --------
        deviceRef.child("sensors")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    val co2 = snapshot.child("air/co2").getValue(Int::class.java)
                    val temp = snapshot.child("air/temperature").getValue(Double::class.java)
                    val humidity = snapshot.child("air/humidity").getValue(Int::class.java)
                    val pm25 = snapshot.child("dust/pm25").getValue(Int::class.java)
                    val mq135 = snapshot.child("gas/mq135").getValue(Int::class.java)

                    findViewById<TextView>(R.id.co2Value).text = "CO₂: ${co2 ?: "--"} ppm"
                    findViewById<TextView>(R.id.tempValue).text = "Temperature: ${temp ?: "--"} °C"
                    findViewById<TextView>(R.id.humidityValue).text = "Humidity: ${humidity ?: "--"} %"
                    findViewById<TextView>(R.id.pm25Value).text = "PM2.5: ${pm25 ?: "--"} µg/m³"
                    findViewById<TextView>(R.id.mq135Value).text = "MQ135: ${mq135 ?: "--"}"

                    val overall = updateAirQualityStatus(co2, pm25)
                    if (isAutoMode) activateAutoFan(overall)

                    updateChart(co2Chart, co2History, co2?.toFloat())
                    updateChart(pm25Chart, pm25History, pm25?.toFloat())
                }

                override fun onCancelled(error: DatabaseError) {}
            })

        // -------- STATUS LISTENER (NO graph here) --------
        deviceRef.child("status")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    val online = snapshot.child("online").getValue(String::class.java)
                    val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java)

                    findViewById<TextView>(R.id.deviceStatus).apply {
                        text = "Device Status: ${online ?: "Unknown"}"
                        setTextColor(
                            if (online == "ONLINE") Color.GREEN else Color.RED
                        )
                    }

                    findViewById<TextView>(R.id.lastSeen).text =
                        "Last Seen: ${formatTimestamp(lastSeen)}"
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // =====================================================
    // 🔧 USER INTERACTIONS
    // =====================================================
    private fun setupInteractionListeners() {

        findViewById<Button>(R.id.logoutBtn).setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        val fanModeSwitch = findViewById<Switch>(R.id.fanModeSwitch)
        fanModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            isAutoMode = isChecked
            fanSeekBar.isEnabled = !isChecked
            commandsRef.child("fanMode")
                .setValue(if (isChecked) "AUTO" else "MANUAL")
            fanModeSwitch.text =
                if (isChecked) "Auto Mode" else "Manual Mode"
        }

        fanSeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                findViewById<TextView>(R.id.fanValue).text =
                    "Fan Speed: $progress %"
                if (fromUser && !isAutoMode) {
                    commandsRef.child("fanSpeed").setValue(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // =====================================================
    // 🧠 HELPERS
    // =====================================================
    private fun formatTimestamp(ts: Long?): String {
        if (ts == null) return "--"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    private fun updateAirQualityStatus(co2: Int?, pm25: Int?): String {
        val status = when {
            co2 == null || pm25 == null -> "Unknown"
            co2 > 2000 || pm25 > 35 -> "Poor"
            co2 > 1000 || pm25 > 12 -> "Moderate"
            else -> "Good"
        }

        airQualityStatus.text = "Air Quality: $status"
        airQualityStatus.setTextColor(
            when (status) {
                "Good" -> Color.GREEN
                "Moderate" -> Color.parseColor("#FFA500")
                "Poor" -> Color.RED
                else -> Color.GRAY
            }
        )
        return status
    }

    private fun activateAutoFan(status: String) {
        val speed = when (status) {
            "Good" -> 0
            "Moderate" -> 50
            "Poor" -> 100
            else -> 0
        }
        fanSeekBar.progress = speed
        commandsRef.child("fanSpeed").setValue(speed)
    }

    private fun initChart(chart: LineChart, label: String) {
        chart.description.text = label
        chart.setNoDataText("Waiting for data...")
    }

    private fun updateChart(
        chart: LineChart,
        history: LinkedList<Entry>,
        value: Float?
    ) {
        if (value == null) return

        if (history.size >= 30) history.removeFirst()
        history.add(Entry(history.size.toFloat(), value))

        val dataSet = LineDataSet(history, chart.description.text)
        dataSet.color = Color.CYAN
        dataSet.setDrawValues(false)
        dataSet.setDrawCircles(true)

        chart.data = LineData(dataSet)
        chart.invalidate()
    }
}
