package com.example.bai1.fixture

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Local harness UI only: no application dependency graph, tokens, cloud or location. */
class FixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this).apply { text = "ISOLATED FIXTURE — STRICT ROUTING DISABLED"; tag = "status" }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(Button(this@FixtureActivity).apply {
                text = "Local error screen"; tag = "error"
                setOnClickListener { label.text = "Fixture network unavailable — STRICT ROUTING DISABLED" }
            })
            addView(Button(this@FixtureActivity).apply {
                text = "Local logout screen"; tag = "logout"
                setOnClickListener { label.text = "Fixture session cleared — STRICT ROUTING DISABLED" }
            })
        })
    }
}
