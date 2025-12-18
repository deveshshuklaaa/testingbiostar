package com.biostar.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔴 TEMPORARY: INITIALIZE REALTIME DATABASE ROOT (RUN ONCE)
        val db = FirebaseDatabase.getInstance().reference
        db.child("biostar").setValue(
            mapOf(
                "sensors" to mapOf<String, Any>(),
                "status" to mapOf<String, Any>(),
                "commands" to mapOf<String, Any>()
            )
        )
        // 🔴 REMOVE THIS BLOCK AFTER FIRST SUCCESSFUL RUN

        auth = FirebaseAuth.getInstance()

        findViewById<Button>(R.id.loginBtn).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<Button>(R.id.registerBtn).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()

        // AUTO-LOGIN CHECK
        if (auth.currentUser != null) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
    }
}
