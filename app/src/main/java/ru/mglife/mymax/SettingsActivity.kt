package ru.mglife.mymax

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ru.mglife.mymax.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val crypto = CryptoManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        displayCurrentKey()

        binding.buttonRotateKey.setOnClickListener {
            crypto.rotateBackupKey()
            displayCurrentKey()
            Toast.makeText(this, "Новый ключ сгенерирован и сохранен в KeyStore", Toast.LENGTH_LONG).show()
        }
    }

    private fun displayCurrentKey() {
        binding.editTextBackupKey.setText(crypto.getBackupPassword())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
