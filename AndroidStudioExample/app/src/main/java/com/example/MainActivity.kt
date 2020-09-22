package com.example

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*

const val EXTRA_KEY = "com.example.MainActivity.name_field_text"


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun openAnotherWindow(view: View) {
        // Link MainActivity with GreetingScreen
        val intent = Intent(this, GreetingScreen::class.java).apply {
            // Key-value pair
            putExtra(EXTRA_KEY, nameField.text.toString())
        }
        if (nameField.text.toString().isNotEmpty()) {
            startActivity(intent)
        }
    }
}