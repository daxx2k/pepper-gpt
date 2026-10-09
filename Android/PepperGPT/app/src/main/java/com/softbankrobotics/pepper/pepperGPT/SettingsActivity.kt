package com.softbankrobotics.pepper.pepperGPT

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.softbankrobotics.pepper.pepperGPT.databinding.ActivitySettingsBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val PREFS = "PepperGPT_Prefs"
    private val KEY_API = "openai_api_key"
    private val KEY_WEATHER = "weather_api_key"
    private val KEY_PROMPT = "system_personality"
    private val KEY_CITY = "default_city"

    // Loaded persona presets from personas.json
    private data class Persona(val name: String, val emoji: String, val description: String, val prompt: String)
    private val personas = mutableListOf<Persona>()

    object Defaults {
        fun defaultPersona(context: android.content.Context): String {
            return try {
                val json = context.resources.openRawResource(R.raw.personas)
                    .bufferedReader().use { it.readText() }
                val arr = JSONArray(json)
                if (arr.length() > 0) arr.getJSONObject(0).getString("prompt")
                else "You are Pepper, a friendly humanoid robot."
            } catch (e: Exception) {
                "You are Pepper, a friendly humanoid robot."
            }
        }
    }

    private val REQ_EDIT_PERSONA = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ModelSettings.attach(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Settings"

        // Load persona presets from JSON
        loadPersonas()

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        binding.spinnerVoice.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Pepper native voice"))
        binding.spinnerVoice.setSelection(0)


        // Load existing User Keys (if any)
        val userOpenAi = prefs.getString(KEY_API, "") ?: ""
        val userWeather = prefs.getString(KEY_WEATHER, "") ?: ""
        val userCity = prefs.getString(KEY_CITY, "London") ?: "London"
        val defaultPersonaText = Defaults.defaultPersona(this)
        val currentPersona = prefs.getString(KEY_PROMPT, defaultPersonaText) ?: defaultPersonaText

        binding.editSshKnownHosts.setText(prefs.getString("ssh_known_hosts", ""))
        binding.editSshHost.setText(prefs.getString("ssh_host", ""))
        binding.editSshUser.setText(prefs.getString("ssh_user", ""))
        binding.editSshPassword.setText(prefs.getString("ssh_password", ""))
        binding.editOpenAIKey.setText(userOpenAi)
        binding.editWeatherKey.setText(userWeather)
        binding.editDefaultCity.setText(userCity)
        binding.editPersona.setText(currentPersona)

        // Show hints if using defaults
        if (userOpenAi.isEmpty()) {
            binding.editOpenAIKey.hint = "Enter your API key"
        }
        if (userWeather.isEmpty()) {
            binding.editWeatherKey.hint = "Enter your API key"
        }

        // Setup personality Spinner
        setupPersonaSpinner(currentPersona)

        // Masking
        setKeyMasking(masked = true)
        binding.checkboxShowKeys.setOnCheckedChangeListener { _, isChecked ->
            setKeyMasking(masked = !isChecked)
        }

        binding.btnSave.setOnClickListener {
            val newOpenAi = binding.editOpenAIKey.text.toString().trim()
            val newWeather = binding.editWeatherKey.text.toString().trim()
            val newCity = binding.editDefaultCity.text.toString().trim().ifEmpty { "London" }
            val newPersona = binding.editPersona.text.toString().trim()
            if (newOpenAi.isNotEmpty() && newOpenAi != userOpenAi) {
                verifyAndSave(prefs, newOpenAi, newWeather, newCity, newPersona)
            } else {
                // Saving empty keys disables API features until configured.
                savePrefs(prefs, newOpenAi, newWeather, newCity, newPersona)
            }
        }

        binding.btnResetPersona.setOnClickListener {
            binding.editPersona.setText(Defaults.defaultPersona(this))
            binding.spinnerPersona.setSelection(0)
        }

        binding.btnClearKeys.setOnClickListener {
            binding.editOpenAIKey.setText("")
            binding.editWeatherKey.setText("")
            binding.editOpenAIKey.hint = "Enter your API key"
            binding.editWeatherKey.hint = "Enter your API key"
            
            // Immediately clear from prefs
            prefs.edit()
                .remove(KEY_API)
                .remove(KEY_WEATHER)
                .apply()
            Toast.makeText(this, "API keys cleared", Toast.LENGTH_SHORT).show()
        }

        binding.btnPersonaFullscreen.setOnClickListener {
            val intent = Intent(this, FullscreenEditorActivity::class.java)
            intent.putExtra(FullscreenEditorActivity.EXTRA_INITIAL, binding.editPersona.text.toString())
            startActivityForResult(intent, REQ_EDIT_PERSONA)
        }
    }

    private fun loadPersonas() {
        try {
            val json = resources.openRawResource(R.raw.personas)
                .bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                personas.add(Persona(
                    name = obj.getString("name"),
                    emoji = obj.optString("emoji", ""),
                    description = obj.optString("description", ""),
                    prompt = obj.getString("prompt")
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("Settings", "Failed to load personas", e)
        }
    }

    private fun setupPersonaSpinner(currentPrompt: String) {
        if (personas.isEmpty()) return

        // Build display list: "emoji Name — description"  +  a "Custom" option at end
        val displayNames = personas.map { "${it.emoji} ${it.name}" }.toMutableList()
        displayNames.add("\u270F\uFE0F Custom")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPersona.adapter = adapter

        // Find which preset matches the current prompt (if any)
        val matchIndex = personas.indexOfFirst { it.prompt.trim() == currentPrompt.trim() }
        if (matchIndex >= 0) {
            binding.spinnerPersona.setSelection(matchIndex)
        } else {
            // Custom (last item)
            binding.spinnerPersona.setSelection(displayNames.size - 1)
        }

        binding.spinnerPersona.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (position < personas.size) {
                    // A preset was selected — load its prompt
                    binding.editPersona.setText(personas[position].prompt)
                }
                // If "Custom" selected, leave the text as-is (user edits freely)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun verifyAndSave(prefs: android.content.SharedPreferences, key: String, weather: String, city: String, persona: String) {
        val btnText = binding.btnSave.text
        binding.btnSave.text = "Verifying..."
        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            val isValid = OpenAIQuickTester.testKey(key)
            if (isValid) {
                savePrefs(prefs, key, weather, city, persona)
            } else {
                Toast.makeText(this@SettingsActivity, "Could not verify the key. Check your connection and credentials.", Toast.LENGTH_LONG).show()
            }
            binding.btnSave.text = btnText
            binding.btnSave.isEnabled = true
        }
    }

    private fun savePrefs(prefs: android.content.SharedPreferences, key: String, weather: String, city: String, persona: String) {
        prefs.edit()
            .putString("voice_mode", "pepper")
            .putString("ssh_known_hosts", binding.editSshKnownHosts.text.toString().trim())
            .putString("ssh_host", binding.editSshHost.text.toString().trim())
            .putString("ssh_user", binding.editSshUser.text.toString().trim())
            .putString("ssh_password", binding.editSshPassword.text.toString())
            .putString(KEY_API, key)
            .putString(KEY_WEATHER, weather)
            .putString(KEY_CITY, city)
            .putString(KEY_PROMPT, persona)
            .apply()
        Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun setKeyMasking(masked: Boolean) {
        val type = if (masked)
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

        binding.editOpenAIKey.inputType = type
        binding.editWeatherKey.inputType = type
        binding.editSshPassword.inputType = type
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_EDIT_PERSONA && resultCode == Activity.RESULT_OK) {
            val newText = data?.getStringExtra(FullscreenEditorActivity.EXTRA_RESULT) ?: return
            binding.editPersona.setText(newText)
            // Switch spinner to "Custom" since user edited in fullscreen
            if (personas.isNotEmpty()) {
                binding.spinnerPersona.setSelection(personas.size) // last item = Custom
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
