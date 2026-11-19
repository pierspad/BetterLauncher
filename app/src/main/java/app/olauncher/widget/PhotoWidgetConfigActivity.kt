package app.olauncher.widget

import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.format.DateFormat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.databinding.ActivityPhotoWidgetConfigBinding
import app.olauncher.helper.FontHelper
import app.olauncher.helper.LocalPhotos
import app.olauncher.helper.WallpaperPeriod
import app.olauncher.helper.showToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

class PhotoWidgetConfigActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoWidgetConfigBinding
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var fixedSource = ""
    private var folderSource = ""
    private var saving = false
    private val sourceNames = mutableMapOf<String, String>()
    private var morningStart = WallpaperPeriod.DEFAULT_MORNING_START
    private var eveningStart = WallpaperPeriod.DEFAULT_EVENING_START

    private val photoPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        fixedSource = result.data?.data?.toString() ?: return@registerForActivityResult
        showSource()
        resolveSourceLabel(fixedSource, fixed = true)
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        folderSource = result.data?.data?.toString() ?: return@registerForActivityResult
        showSource()
        resolveSourceLabel(folderSource, fixed = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        FontHelper.reload(this)
        FontHelper.install(this)
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (!PhotoWidgetProvider.isBound(this, widgetId)) {
            finish()
            return
        }

        binding = ActivityPhotoWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val padding = binding.root.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(padding + safe.left, padding + safe.top, padding + safe.right, padding + safe.bottom)
            insets
        }
        val existing = PhotoWidgetStore.get(this, widgetId)
        if (existing != null) sourceNames[existing.source] = existing.sourceName
        morningStart = savedInstanceState?.getInt("morningStart") ?: existing?.morningStart ?: morningStart
        eveningStart = savedInstanceState?.getInt("eveningStart") ?: existing?.eveningStart ?: eveningStart
        fixedSource = savedInstanceState?.getString("fixed") ?: existing?.takeIf { it.mode == PhotoWidgetSettings.FIXED }?.source.orEmpty()
        folderSource = savedInstanceState?.getString("folder") ?: existing?.takeIf { it.mode != PhotoWidgetSettings.FIXED }?.source.orEmpty()
        val checked = when (savedInstanceState?.getInt("mode") ?: existing?.mode) {
            PhotoWidgetSettings.DAILY -> R.id.photoConfigOnce
            PhotoWidgetSettings.TWICE_DAILY -> R.id.photoConfigTwice
            else -> R.id.photoConfigFixed
        }
        binding.photoConfigFrequency.check(checked)
        binding.photoConfigLayout.check(
            if (savedInstanceState?.getBoolean("crop") ?: (existing?.crop == true)) R.id.photoConfigCrop
            else R.id.photoConfigFit
        )
        binding.photoConfigFrequency.setOnCheckedChangeListener { _, _ -> showSource() }
        binding.photoConfigSource.setOnClickListener { chooseSource() }
        binding.photoConfigMorning.setOnClickListener { chooseTime(evening = false) }
        binding.photoConfigEvening.setOnClickListener { chooseTime(evening = true) }
        binding.photoConfigSave.setOnClickListener { saveWidget() }
        binding.photoConfigCancel.setOnClickListener { finish() }
        showSource()
        if (fixedSource.isNotEmpty()) resolveSourceLabel(fixedSource, fixed = true)
        if (folderSource.isNotEmpty()) resolveSourceLabel(folderSource, fixed = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::binding.isInitialized) return
        outState.putInt("mode", mode())
        outState.putInt("morningStart", morningStart)
        outState.putInt("eveningStart", eveningStart)
        outState.putBoolean("crop", binding.photoConfigCrop.isChecked)
        outState.putString("fixed", fixedSource)
        outState.putString("folder", folderSource)
    }

    private fun mode() = when (binding.photoConfigFrequency.checkedRadioButtonId) {
        R.id.photoConfigOnce -> PhotoWidgetSettings.DAILY
        R.id.photoConfigTwice -> PhotoWidgetSettings.TWICE_DAILY
        else -> PhotoWidgetSettings.FIXED
    }

    private fun source() = if (mode() == PhotoWidgetSettings.FIXED) fixedSource else folderSource

    private fun showSource() {
        val fixed = mode() == PhotoWidgetSettings.FIXED
        val source = source()
        val label = getString(if (fixed) R.string.photo_widget_choose else R.string.wallpaper_choose_folder)
        val document = sourceNames[source].orEmpty()
        binding.photoConfigSource.text = if (document.isBlank()) label
        else getString(R.string.wallpaper_setting_summary, label, document)
        binding.photoConfigSave.isEnabled = source.isNotEmpty() && !saving
        binding.photoConfigTimes.isVisible = mode() == PhotoWidgetSettings.TWICE_DAILY
        if (binding.photoConfigTimes.isVisible) {
            val formatter = DateFormat.getTimeFormat(this)
            fun format(minutes: Int) = formatter.format(Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutes / 60)
                set(Calendar.MINUTE, minutes % 60)
            }.time)
            binding.photoConfigMorning.text = getString(
                R.string.wallpaper_setting_summary, getString(R.string.wallpaper_morning_start), format(morningStart)
            )
            binding.photoConfigEvening.text = getString(
                R.string.wallpaper_setting_summary, getString(R.string.wallpaper_evening_start), format(eveningStart)
            )
        }
    }

    private fun chooseTime(evening: Boolean) {
        if (saving) return
        val selected = if (evening) eveningStart else morningStart
        TimePickerDialog(this, { _, hour, minute ->
            val start = hour * 60 + minute
            if (start == (if (evening) morningStart else eveningStart)) {
                showToast(getString(R.string.wallpaper_distinct_times))
            } else {
                if (evening) eveningStart = start else morningStart = start
                showSource()
            }
        }, selected / 60, selected % 60, DateFormat.is24HourFormat(this)).show()
    }

    private fun resolveSourceLabel(source: String, fixed: Boolean) {
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) { sourceDisplayName(source, fixed) }
            if (name.isNotBlank()) {
                sourceNames[source] = name
                if (source() == source) showSource()
            }
        }
    }

    private fun sourceDisplayName(source: String, fixed: Boolean): String = runCatching {
        val uri = source.toUri()
        val document = if (fixed) uri else
            DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        contentResolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }.orEmpty()
    }.getOrDefault("")

    private fun chooseSource() {
        if (saving) return
        val fixed = mode() == PhotoWidgetSettings.FIXED
        val intent = Intent(if (fixed) Intent.ACTION_OPEN_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        if (fixed) {
            intent.type = "image/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            (if (fixed) photoPicker else folderPicker).launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            showToast(getString(R.string.photo_widget_unavailable))
        }
    }

    private fun saveWidget() {
        val source = source()
        if (saving || source.isBlank()) return
        val selected = PhotoWidgetSettings(
            source, mode(), binding.photoConfigCrop.isChecked, UUID.randomUUID().toString(),
            sourceName = sourceNames[source].orEmpty(), morningStart = morningStart, eveningStart = eveningStart,
        )
        saving = true
        showSource()

        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val context = applicationContext
                val fixed = selected.mode == PhotoWidgetSettings.FIXED
                val uri = source.toUri()
                try {
                    val previous = PhotoWidgetStore.get(context, widgetId)
                    val photo = if (previous?.source == source && previous.mode == selected.mode) {
                        PhotoWidgetStore.read(context, widgetId)?.let { it to previous.lastPhoto }
                    } else null
                    val (bitmap, lastPhoto) = photo ?: (if (fixed) {
                        LocalPhotos.decode(context, uri, PhotoWidgetStore.MAX_IMAGE_SIDE)?.let { it to "" }
                    } else {
                        LocalPhotos.nextInFolder(context, uri, "", PhotoWidgetStore.MAX_IMAGE_SIDE)
                    }) ?: return@withContext false
                    try {
                        if (!fixed) context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val period = WallpaperPeriod.current(
                            Calendar.getInstance(), selected.mode == PhotoWidgetSettings.TWICE_DAILY,
                            selected.morningStart, selected.eveningStart,
                        )
                        val settings = selected.copy(
                            sourceName = selected.sourceName.ifBlank { sourceDisplayName(source, fixed) },
                            lastPhoto = lastPhoto, lastPeriod = period.key
                        )
                        if (!PhotoWidgetStore.save(context, widgetId, settings, bitmap)) {
                            if (!fixed) PhotoWidgetStore.releaseUnusedFolder(context, source)
                            return@withContext false
                        }
                        PhotoWidgetWorker.cancel(context, widgetId)
                        PhotoWidgetWorker.schedule(context, widgetId, settings)
                        true
                    } finally {
                        bitmap.recycle()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (!fixed) PhotoWidgetStore.releaseUnusedFolder(context, source)
                    false
                }
            }
            saving = false
            if (saved) {
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            } else {
                showToast(getString(R.string.photo_widget_unavailable))
                showSource()
            }
        }
    }
}
