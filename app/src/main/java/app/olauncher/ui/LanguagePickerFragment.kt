package app.olauncher.ui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentLanguagePickerBinding
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr

class LanguagePickerFragment : Fragment() {

    private var _binding: FragmentLanguagePickerBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs

    private var originalLanguageCode = ""
    private var selectedLanguageCode = ""

    private data class LanguageOption(
        val code: String,
        val flag: String,
        val nativeName: String,
        val englishName: String,
        val nameRes: Int = 0
    )

    private val languageOptions = listOf(
        LanguageOption("", "🌐", "Automatic", "Automatic", R.string.language_automatic),
        LanguageOption("ar", "🇸🇦", "العربية", "Arabic"),
        LanguageOption("bn", "🇧🇩", "বাংলা", "Bengali"),
        LanguageOption("zh", "🇨🇳", "中文", "Chinese"),
        LanguageOption("hr", "🇭🇷", "Hrvatski", "Croatian"),
        LanguageOption("nl", "🇳🇱", "Nederlands", "Dutch"),
        LanguageOption("en", "🇬🇧", "English", "English"),
        LanguageOption("fr", "🇫🇷", "Français", "French"),
        LanguageOption("de", "🇩🇪", "Deutsch", "German"),
        LanguageOption("he", "🇮🇱", "עברית", "Hebrew"),
        LanguageOption("hi", "🇮🇳", "हिन्दी", "Hindi"),
        LanguageOption("hu", "🇭🇺", "Magyar", "Hungarian"),
        LanguageOption("in", "🇮🇩", "Bahasa Indonesia", "Indonesian"),
        LanguageOption("it", "🇮🇹", "Italiano", "Italian"),
        LanguageOption("ja", "🇯🇵", "日本語", "Japanese"),
        LanguageOption("ko", "🇰🇷", "한국어", "Korean"),
        LanguageOption("pl", "🇵🇱", "Polski", "Polish"),
        LanguageOption("pt-BR", "🇧🇷", "Português", "Portuguese"),
        LanguageOption("ru-RU", "🇷🇺", "Русский", "Russian"),
        LanguageOption("es-ES", "🇪🇸", "Español", "Spanish"),
        LanguageOption("sv", "🇸🇪", "Svenska", "Swedish"),
        LanguageOption("tr", "🇹🇷", "Türkçe", "Turkish"),
        LanguageOption("uk", "🇺🇦", "Українська", "Ukrainian")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLanguagePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        originalLanguageCode = if (currentLocales.isEmpty) "" else currentLocales.get(0)?.toLanguageTag() ?: ""
        selectedLanguageCode = originalLanguageCode

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

        binding.btnApplyChanges.setOnClickListener {
            applyLanguageChanges()
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
        val list = binding.languagePickerList
        list.removeAllViews()

        val padV = 14.dpToPx()
        val padH = 10.dpToPx()
        val rippleBg = TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        fun addRow(opt: LanguageOption, selected: Boolean, onClick: () -> Unit) {
            val rowLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, padV, padH, padV)
                setBackgroundResource(if (selected) R.drawable.rounded_rect_selected_language else rippleBg)
                setOnClickListener { onClick() }
            }

            // Flag TextView
            val flagView = TextView(ctx).apply {
                text = opt.flag
                textSize = 18f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    32.dpToPx(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 12.dpToPx()
                }
            }
            rowLayout.addView(flagView)

            if (opt.code.isEmpty()) {
                val nameView = TextView(ctx).apply {
                    text = getString(opt.nameRes)
                    textSize = 18f
                    setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                    typeface = if (selected) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                rowLayout.addView(nameView)
            } else {
                val nativeView = TextView(ctx).apply {
                    text = opt.nativeName
                    textSize = 18f
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                    typeface = if (selected) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                val separatorView = TextView(ctx).apply {
                    text = "-"
                    textSize = 18f
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                    typeface = if (selected) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 8.dpToPx()
                        marginEnd = 8.dpToPx()
                    }
                }

                val englishView = TextView(ctx).apply {
                    text = opt.englishName
                    textSize = 18f
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTextColor(ctx.getColorFromAttr(R.attr.primaryColor))
                    typeface = if (selected) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                rowLayout.addView(nativeView)
                rowLayout.addView(separatorView)
                rowLayout.addView(englishView)
            }

            list.addView(
                rowLayout,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 2.dpToPx() }
            )
        }

        // Add rows
        for (opt in languageOptions) {
            val isSelectedExact = if (opt.code.isEmpty()) {
                selectedLanguageCode.isEmpty()
            } else {
                selectedLanguageCode.equals(opt.code, ignoreCase = true) ||
                        (opt.code.length == 2 && selectedLanguageCode.startsWith(opt.code + "-", ignoreCase = true))
            }
            addRow(opt, isSelectedExact) {
                selectedLanguageCode = opt.code
                updateSelectionState()
            }
        }

        // Update apply changes button state
        val hasChanges = selectedLanguageCode != originalLanguageCode
        binding.btnApplyChanges.isClickable = hasChanges
        binding.btnApplyChanges.isFocusable = hasChanges
        binding.btnApplyChanges.alpha = if (hasChanges) 1.0f else 0.4f
    }

    private fun applyLanguageChanges() {
        prefs.settingsScrollY = 0
        prefs.reopenSettingsAfterRestart = true

        val localeList = if (selectedLanguageCode.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(selectedLanguageCode)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun handleBackPress() {
        val hasChanges = selectedLanguageCode != originalLanguageCode
        if (hasChanges) {
            showDiscardChangesDialog()
        } else {
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
        negativeButton.setText(R.string.dialog_back)

        val cancelIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)?.apply {
            val size = (16 * resources.displayMetrics.density).toInt()
            setBounds(0, 0, size, size)
            setTint(requireContext().getColorFromAttr(R.attr.primaryColorTrans80))
        }
        negativeButton.setCompoundDrawablesRelative(cancelIcon, null, null, null)
        negativeButton.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()

        val discardIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)?.apply {
            val size = (16 * resources.displayMetrics.density).toInt()
            setBounds(0, 0, size, size)
            setTint(requireContext().getColorFromAttr(R.attr.primaryColor))
        }
        positiveButton.setCompoundDrawablesRelative(discardIcon, null, null, null)
        positiveButton.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        positiveButton.setOnClickListener {
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
}
