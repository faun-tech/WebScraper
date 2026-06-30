package com.example.webscraper.ui.viewer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.webscraper.R
import com.example.webscraper.data.model.ViewerBackgroundPreset
import com.example.webscraper.data.model.ViewerSettings
import com.example.webscraper.data.model.ViewerTextColorPreset
import com.example.webscraper.data.model.ViewerTextSizePreset
import com.example.webscraper.databinding.ActivityTextViewerBinding
import com.example.webscraper.databinding.DialogViewerSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 한 폴더 안의 텍스트 파일들을 이름순으로 넘겨가며 보여주는 뷰어 화면.
 * 본문은 위아래로 스크롤할 수 있고, 배경색/글자색/글자크기를 사용자가 고를 수 있다.
 */
@AndroidEntryPoint
class TextViewerActivity : AppCompatActivity() {

    private val viewModel: TextViewerViewModel by viewModels()

    private lateinit var binding: ActivityTextViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val folderUri = IntentCompat.getParcelableExtra(intent, EXTRA_FOLDER_URI, Uri::class.java)
        val startFileUri = IntentCompat.getParcelableExtra(intent, EXTRA_START_FILE_URI, Uri::class.java)

        if (folderUri == null) {
            Toast.makeText(this, getString(R.string.viewer_no_folder_error), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel.initFolder(folderUri, startFileUri)

        setupClickListeners()
        observeViewModel()
    }

    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonPrevious.setOnClickListener {
            viewModel.onPreviousClicked(binding.scrollContent.scrollY)
        }
        binding.buttonNext.setOnClickListener {
            viewModel.onNextClicked(binding.scrollContent.scrollY)
        }
        binding.buttonSettings.setOnClickListener {
            showSettingsDialog(viewModel.uiState.value.settings)
        }
    }

    override fun onPause() {
        super.onPause()
        // 화면을 벗어나는 시점(다른 앱으로 전환, 뒤로가기 등)의 스크롤 위치를 저장해, 같은
        // 폴더를 나중에 다시 열었을 때 이 지점부터 이어볼 수 있게 한다.
        viewModel.saveScrollPosition(binding.scrollContent.scrollY)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is TextViewerUiEvent.ShowToast ->
                            Toast.makeText(this@TextViewerActivity, event.message, Toast.LENGTH_SHORT).show()

                        is TextViewerUiEvent.RestoreScroll -> restoreScroll(event.scrollY)
                    }
                }
            }
        }
    }

    private fun render(state: TextViewerUiState) {
        binding.textFileTitle.text = state.currentFile?.name ?: ""
        binding.textPosition.text = state.positionLabel

        binding.progressLoading.visibility =
            if (state.isLoadingFolder || state.isLoadingFile) View.VISIBLE else View.GONE

        val showEmpty = !state.isLoadingFolder && state.files.isEmpty()
        binding.textEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE
        binding.scrollContent.visibility = if (showEmpty) View.INVISIBLE else View.VISIBLE

        binding.textContent.text = state.currentText

        binding.buttonPrevious.isEnabled = state.hasPrevious
        binding.buttonNext.isEnabled = state.hasNext

        applySettings(state.settings)
    }

    /**
     * 방금 표시한 본문을 [scrollY] 위치까지 스크롤한다. 새 텍스트가 막 설정된 직후라
     * 아직 레이아웃이 끝나지 않았을 수 있으므로 post()로 다음 레이아웃 패스 이후에
     * 실행하고, 본문 길이가 짧아져 저장된 위치를 넘어서는 경우를 대비해 범위를 clamp한다.
     */
    private fun restoreScroll(scrollY: Int) {
        binding.scrollContent.post {
            val maxScroll = (binding.textContent.height - binding.scrollContent.height).coerceAtLeast(0)
            binding.scrollContent.scrollTo(0, scrollY.coerceIn(0, maxScroll))
        }
    }

    private fun applySettings(settings: ViewerSettings) {
        val backgroundColor = Color.parseColor(settings.background.colorHex)
        binding.scrollContent.setBackgroundColor(backgroundColor)
        binding.textContent.setTextColor(Color.parseColor(settings.textColor.colorHex))
        binding.textContent.textSize = settings.textSize.sp
    }

    /** 배경색/글자색/글자크기 설정 다이얼로그를 띄운다. 고를 때마다 바로 화면에 반영된다. */
    private fun showSettingsDialog(initialSettings: ViewerSettings) {
        val dialogBinding = DialogViewerSettingsBinding.inflate(LayoutInflater.from(this))
        var current = initialSettings

        val backgroundSwatches = listOf(
            dialogBinding.swatchBgWhite to ViewerBackgroundPreset.WHITE,
            dialogBinding.swatchBgSepia to ViewerBackgroundPreset.SEPIA,
            dialogBinding.swatchBgMint to ViewerBackgroundPreset.MINT,
            dialogBinding.swatchBgDarkGray to ViewerBackgroundPreset.DARK_GRAY,
            dialogBinding.swatchBgBlack to ViewerBackgroundPreset.BLACK
        )
        val textColorSwatches = listOf(
            dialogBinding.swatchTextBlack to ViewerTextColorPreset.BLACK,
            dialogBinding.swatchTextDarkGray to ViewerTextColorPreset.DARK_GRAY,
            dialogBinding.swatchTextSepiaBrown to ViewerTextColorPreset.SEPIA_BROWN,
            dialogBinding.swatchTextLightGray to ViewerTextColorPreset.LIGHT_GRAY,
            dialogBinding.swatchTextWhite to ViewerTextColorPreset.WHITE
        )
        val sizeRadioButtons = mapOf(
            dialogBinding.radioSizeSmall to ViewerTextSizePreset.SMALL,
            dialogBinding.radioSizeMedium to ViewerTextSizePreset.MEDIUM,
            dialogBinding.radioSizeLarge to ViewerTextSizePreset.LARGE,
            dialogBinding.radioSizeExtraLarge to ViewerTextSizePreset.EXTRA_LARGE
        )

        fun refreshPreview() {
            dialogBinding.textPreview.setBackgroundColor(Color.parseColor(current.background.colorHex))
            dialogBinding.textPreview.setTextColor(Color.parseColor(current.textColor.colorHex))
            dialogBinding.textPreview.textSize = current.textSize.sp
            highlightSwatches(backgroundSwatches, current.background)
            highlightSwatches(textColorSwatches, current.textColor)
        }

        fun applyChange(newSettings: ViewerSettings) {
            current = newSettings
            refreshPreview()
            viewModel.onSettingsChanged(current)
        }

        backgroundSwatches.forEach { (view, preset) ->
            view.setOnClickListener { applyChange(current.copy(background = preset)) }
        }
        textColorSwatches.forEach { (view, preset) ->
            view.setOnClickListener { applyChange(current.copy(textColor = preset)) }
        }
        sizeRadioButtons.entries.forEach { (radioButton, preset) ->
            radioButton.setOnClickListener { applyChange(current.copy(textSize = preset)) }
        }

        sizeRadioButtons.entries.firstOrNull { it.value == current.textSize }?.key?.isChecked = true
        refreshPreview()

        AlertDialog.Builder(this)
            .setTitle(R.string.viewer_settings_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.viewer_settings_close_button, null)
            .show()
    }

    private fun <T> highlightSwatches(swatches: List<Pair<FrameLayout, T>>, selected: T) {
        swatches.forEach { (view, preset) ->
            view.setBackgroundResource(
                if (preset == selected) R.drawable.bg_swatch_border_selected else R.drawable.bg_swatch_border
            )
        }
    }

    companion object {
        private const val EXTRA_FOLDER_URI = "extra_folder_uri"
        private const val EXTRA_START_FILE_URI = "extra_start_file_uri"

        /** [folderUri] 폴더의 텍스트 파일들을 뷰어로 보여주는 Intent를 만든다. */
        fun createIntent(context: Context, folderUri: Uri, startFileUri: Uri? = null): Intent {
            return Intent(context, TextViewerActivity::class.java).apply {
                putExtra(EXTRA_FOLDER_URI, folderUri)
                if (startFileUri != null) {
                    putExtra(EXTRA_START_FILE_URI, startFileUri)
                }
            }
        }
    }
}
