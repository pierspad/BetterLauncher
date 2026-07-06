package app.olauncher.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentBackupRestoreBinding
import app.olauncher.helper.showToast

class BackupRestoreFragment : Fragment() {

    private var _binding: FragmentBackupRestoreBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private var originalJson: String = ""
    private var isModified = false

    private val createBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(binding.etSettingsJson.text.toString().toByteArray())
                }
                requireContext().showToast(getString(R.string.backup_saved))
            } catch (e: Exception) {
                e.printStackTrace()
                requireContext().showToast(getString(R.string.backup_failed))
            }
        }

    private val openRestoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val json = requireContext().contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().decodeToString() }
                if (json != null) {
                    binding.etSettingsJson.setText(json)
                    requireContext().showToast("JSON loaded from file. Click 'Apply changes' to restore.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                requireContext().showToast(getString(R.string.restore_failed))
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackupRestoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        // Load current settings
        originalJson = prefs.exportToJson()
        binding.etSettingsJson.setText(originalJson)

        // Back action
        binding.btnBack.setOnClickListener {
            handleBackPress()
        }

        // Copy button
        binding.btnCopy.setOnClickListener {
            val textToCopy = binding.etSettingsJson.text.toString()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("BetterLauncher Settings", textToCopy)
            clipboard.setPrimaryClip(clip)
            requireContext().showToast(getString(R.string.copied))
        }

        // Clear button
        binding.btnClear.setOnClickListener {
            binding.etSettingsJson.setText("")
        }

        // Save File button
        binding.btnExportFile.setOnClickListener {
            createBackupLauncher.launch("betterlauncher-backup.json")
        }

        // Import File button
        binding.btnImportFile.setOnClickListener {
            openRestoreLauncher.launch(arrayOf("*/*"))
        }

        // Apply changes button
        binding.btnApplyChanges.setOnClickListener {
            if (isModified) {
                val jsonToApply = binding.etSettingsJson.text.toString()
                if (prefs.importFromJson(jsonToApply)) {
                    requireContext().showToast(getString(R.string.restore_done))
                    isModified = false
                    originalJson = jsonToApply
                    updateApplyButtonState()
                    restartLauncher()
                } else {
                    requireContext().showToast(getString(R.string.restore_failed))
                }
            }
        }

        // TextWatcher to monitor modifications
        binding.etSettingsJson.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val currentText = s?.toString() ?: ""
                isModified = currentText != originalJson
                updateApplyButtonState()
            }
        })

        // Handle System Back Button
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)

        updateApplyButtonState()
    }

    private fun updateApplyButtonState() {
        if (isModified) {
            binding.btnApplyChanges.isClickable = true
            binding.btnApplyChanges.isFocusable = true
            binding.btnApplyChanges.alpha = 1.0f
        } else {
            binding.btnApplyChanges.isClickable = false
            binding.btnApplyChanges.isFocusable = false
            binding.btnApplyChanges.alpha = 0.4f
        }
    }

    private fun handleBackPress() {
        if (isModified) {
            showDiscardChangesDialog()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun showDiscardChangesDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.discard_changes_title)
            .setMessage(R.string.discard_changes_message)
            .setPositiveButton(R.string.discard) { _, _ ->
                isModified = false
                findNavController().navigateUp()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun restartLauncher() {
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        requireActivity().recreate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
