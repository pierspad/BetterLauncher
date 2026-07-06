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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.olauncher.helper.dpToPx

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

        // Adjust padding dynamically to handle edge-to-edge system bars and keyboard (IME) insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomPadding = if (ime.bottom > 0) {
                ime.bottom + 8.dpToPx()
            } else {
                systemBars.bottom + 16.dpToPx()
            }
            v.setPadding(
                0,
                systemBars.top + 16.dpToPx(),
                0,
                bottomPadding
            )
            insets
        }

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
        val view = layoutInflater.inflate(R.layout.dialog_message, null)
        val titleView = view.findViewById<android.widget.TextView>(R.id.dialogTitle)
        val messageView = view.findViewById<android.widget.TextView>(R.id.dialogMessage)
        val positiveButton = view.findViewById<android.widget.TextView>(R.id.dialogPositiveButton)
        val negativeButton = view.findViewById<android.widget.TextView>(R.id.dialogNegativeButton)

        titleView.setText(R.string.discard_changes_title)
        messageView.setText(R.string.discard_changes_message)
        positiveButton.setText(R.string.discard)
        negativeButton.visibility = View.VISIBLE
        negativeButton.setText(android.R.string.cancel)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        positiveButton.setOnClickListener {
            isModified = false
            dialog.dismiss()
            findNavController().navigateUp()
        }

        negativeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun restartLauncher() {
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        requireActivity().recreate()
    }

    override fun onResume() {
        super.onResume()
        (activity as? app.olauncher.MainActivity)?.updateGlobalOpacityScrim(animate = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
