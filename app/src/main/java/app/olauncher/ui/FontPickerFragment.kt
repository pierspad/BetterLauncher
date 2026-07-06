package app.olauncher.ui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentFontPickerBinding
import app.olauncher.helper.FontHelper
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.showToast
import java.io.File

class FontPickerFragment : Fragment() {

    private var _binding: FragmentFontPickerBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs

    private var originalFontFamily = ""
    private var originalCustomFontPath = ""
    private var selectedFontFamily = ""
    private var tempCustomFontPath = ""
    private var isCustomFontDeleted = false

    private data class FontOption(val labelRes: Int, val family: String)
    private val fontOptions = listOf(
        FontOption(R.string.font_system_default, ""),
        FontOption(R.string.font_sans_serif_thin, "sans-serif-thin"),
        FontOption(R.string.font_sans_serif_light, "sans-serif-light"),
        FontOption(R.string.font_sans_serif, "sans-serif"),
        FontOption(R.string.font_sans_serif_medium, "sans-serif-medium"),
        FontOption(R.string.font_sans_serif_black, "sans-serif-black"),
        FontOption(R.string.font_sans_serif_condensed, "sans-serif-condensed"),
        FontOption(R.string.font_sans_serif_smallcaps, "sans-serif-smallcaps"),
        FontOption(R.string.font_serif, "serif"),
        FontOption(R.string.font_monospace, "monospace"),
        FontOption(R.string.font_casual, "casual"),
        FontOption(R.string.font_cursive, "cursive")
    )

    private val pickFontLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val path = importTempFont(uri)
            if (path != null) {
                val fileName = getFileName(uri)
                tempCustomFontPath = path
                selectedFontFamily = FontHelper.CUSTOM_PREFIX + fileName
                isCustomFontDeleted = false
                updateSelectionState()
            } else {
                requireContext().showToast(getString(R.string.font_import_failed))
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFontPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        originalFontFamily = prefs.fontFamily
        originalCustomFontPath = prefs.customFontPath
        selectedFontFamily = originalFontFamily
        tempCustomFontPath = originalCustomFontPath

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

        binding.btnBack.setOnClickListener {
            handleBackPress()
        }

        binding.btnAddCustomFont.setOnClickListener {
            pickFontLauncher.launch(FONT_MIME_TYPES)
        }

        binding.btnRemoveFont.setOnClickListener {
            if (selectedFontFamily.startsWith(FontHelper.CUSTOM_PREFIX)) {
                isCustomFontDeleted = true
                tempCustomFontPath = ""
                selectedFontFamily = "" // select default
                updateSelectionState()
            }
        }

        binding.btnApplyChanges.setOnClickListener {
            applyFontChanges()
        }

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)

        updateSelectionState()
    }

    private fun updateSelectionState() {
        val ctx = requireContext()
        val list = binding.fontPickerList
        list.removeAllViews()

        val padV = 14.dpToPx()
        val padH = 10.dpToPx()
        val rippleBg = TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        fun addRow(label: String, preview: Typeface, selected: Boolean, onClick: () -> Unit) {
            val row = TextView(ctx).apply {
                text = label
                textSize = 18f
                setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                typeface = if (selected) Typeface.create(preview, Typeface.BOLD) else preview
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, padV, padH, padV)
                setBackgroundResource(if (selected) R.drawable.rounded_rect_shade_color_glass else rippleBg)
                setOnClickListener { onClick() }
            }
            list.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 2.dpToPx() }
            )
        }

        // Add standard system fonts
        for (opt in fontOptions) {
            val preview = if (opt.family.isBlank()) Typeface.DEFAULT
            else Typeface.create(opt.family, Typeface.NORMAL)
            addRow(getString(opt.labelRes), preview, selectedFontFamily == opt.family) {
                selectedFontFamily = opt.family
                updateSelectionState()
            }
        }

        // Add custom font if it exists
        val hasCustomFont = tempCustomFontPath.isNotEmpty() && !isCustomFontDeleted
        if (hasCustomFont) {
            val customPreview = runCatching { Typeface.createFromFile(tempCustomFontPath) }.getOrNull() ?: Typeface.DEFAULT
            val label = selectedFontFamily.removePrefix(FontHelper.CUSTOM_PREFIX).ifEmpty {
                originalFontFamily.removePrefix(FontHelper.CUSTOM_PREFIX).ifEmpty { "Custom Font" }
            }
            addRow(label, customPreview, selectedFontFamily.startsWith(FontHelper.CUSTOM_PREFIX)) {
                selectedFontFamily = FontHelper.CUSTOM_PREFIX + label
                updateSelectionState()
            }
        }

        // Resolve typeface for current selection in preview box
        val selectedTypeface = when {
            selectedFontFamily.startsWith(FontHelper.CUSTOM_PREFIX) -> {
                if (hasCustomFont) {
                    runCatching { Typeface.createFromFile(tempCustomFontPath) }.getOrNull() ?: Typeface.DEFAULT
                } else {
                    Typeface.DEFAULT
                }
            }
            selectedFontFamily.isBlank() -> Typeface.DEFAULT
            else -> runCatching { Typeface.create(selectedFontFamily, Typeface.NORMAL) }.getOrNull() ?: Typeface.DEFAULT
        }
        binding.etFontPreview.typeface = selectedTypeface

        // Update buttons state
        val isCustomSelected = selectedFontFamily.startsWith(FontHelper.CUSTOM_PREFIX)
        binding.btnRemoveFont.isClickable = isCustomSelected
        binding.btnRemoveFont.isFocusable = isCustomSelected
        binding.btnRemoveFont.alpha = if (isCustomSelected) 1.0f else 0.4f

        val hasChanges = selectedFontFamily != originalFontFamily ||
                tempCustomFontPath != originalCustomFontPath ||
                isCustomFontDeleted

        binding.btnApplyChanges.isClickable = hasChanges
        binding.btnApplyChanges.isFocusable = hasChanges
        binding.btnApplyChanges.alpha = if (hasChanges) 1.0f else 0.4f
    }

    private fun importTempFont(uri: Uri): String? {
        return runCatching {
            val context = requireContext()
            val tempFile = File(context.filesDir, "temp_custom_font")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            val tf = Typeface.createFromFile(tempFile)
            if (tf == null || tf == Typeface.DEFAULT) {
                tempFile.delete()
                return null
            }
            tempFile.absolutePath
        }.getOrNull()
    }

    private fun getFileName(uri: Uri): String {
        var name = "custom_font"
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    private fun applyFontChanges() {
        val context = requireContext()
        val targetFile = File(context.filesDir, "custom_font")
        val tempFile = File(context.filesDir, "temp_custom_font")

        if (isCustomFontDeleted) {
            if (targetFile.exists()) targetFile.delete()
            if (tempFile.exists()) tempFile.delete()
            prefs.customFontPath = ""
            prefs.fontFamily = ""
        } else if (tempCustomFontPath.isNotEmpty() && tempCustomFontPath.endsWith("temp_custom_font")) {
            if (tempFile.exists()) {
                runCatching {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }
            prefs.customFontPath = targetFile.absolutePath
            prefs.fontFamily = selectedFontFamily
        } else {
            prefs.fontFamily = selectedFontFamily
        }

        FontHelper.reload(context)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        requireActivity().recreate()
    }

    private fun handleBackPress() {
        val hasChanges = selectedFontFamily != originalFontFamily ||
                tempCustomFontPath != originalCustomFontPath ||
                isCustomFontDeleted

        if (hasChanges) {
            showDiscardChangesDialog()
        } else {
            // Clean up temp font if any
            val tempFile = File(requireContext().filesDir, "temp_custom_font")
            if (tempFile.exists()) tempFile.delete()
            findNavController().navigateUp()
        }
    }

    private fun showDiscardChangesDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_message, null)
        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        val messageView = view.findViewById<TextView>(R.id.dialogMessage)
        val positiveButton = view.findViewById<TextView>(R.id.dialogPositiveButton)
        val negativeButton = view.findViewById<TextView>(R.id.dialogNegativeButton)

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
            // Clean up temp font
            val tempFile = File(requireContext().filesDir, "temp_custom_font")
            if (tempFile.exists()) tempFile.delete()
            dialog.dismiss()
            findNavController().navigateUp()
        }

        negativeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        (activity as? app.olauncher.MainActivity)?.updateGlobalOpacityScrim(animate = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val FONT_MIME_TYPES = arrayOf("font/otf", "font/ttf", "application/x-font-ttf", "application/x-font-otf")
    }
}
