package org.oppia.android.app.player.audio

import androidx.databinding.ObservableField
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import org.oppia.android.app.fragment.FragmentScope
import org.oppia.android.app.model.State
import org.oppia.android.app.model.Voiceover
import org.oppia.android.app.model.VoiceoverMapping
import org.oppia.android.app.viewmodel.ObservableViewModel
import org.oppia.android.domain.audio.AudioPlayerController
import org.oppia.android.domain.audio.AudioPlayerController.PlayProgress
import org.oppia.android.domain.audio.AudioPlayerController.PlayStatus
import org.oppia.android.util.data.AsyncResult
import org.oppia.android.util.gcsresource.DefaultResourceBucketName
import org.oppia.android.util.locale.OppiaLocale
import javax.inject.Inject

/** [ObservableViewModel] for audio-player state. */
@FragmentScope
class AudioViewModel @Inject constructor(
  private val audioPlayerController: AudioPlayerController,
  @DefaultResourceBucketName private val gcsResource: String,
  private val machineLocale: OppiaLocale.MachineLocale
) : ObservableViewModel() {

  private lateinit var state: State
  private lateinit var explorationId: String
  private var voiceoverMap = mapOf<String, Voiceover>()
  private val defaultLanguage = "en"
  private var languageSelectionShown = false
  private var autoPlay = false
  private var hasFeedback = false

  var selectedLanguageCode: String = ""
  var languages = listOf<String>()

  /** Mirrors PlayStatus in AudioPlayerController except adds LOADING state */
  enum class UiAudioPlayStatus {
    FAILED,
    LOADING,
    PREPARED,
    PLAYING,
    PAUSED,
    COMPLETED
  }

  val currentLanguageCode = ObservableField<String>()

  val durationLiveData: LiveData<Int> by lazy {
    processDurationLiveData()
  }
  val positionLiveData: LiveData<Int> by lazy {
    processPositionLiveData()
  }
  val playStatusLiveData: LiveData<UiAudioPlayStatus> by lazy {
    processPlayStatusLiveData()
  }

  fun setStateAndExplorationId(newState: State, id: String) {
    state = newState
    explorationId = id
  }

  fun loadMainContentAudio(allowAutoPlay: Boolean) {
    hasFeedback = false
    loadAudio(null, allowAutoPlay)
  }

  fun loadFeedbackAudio(contentId: String, allowAutoPlay: Boolean) {
    hasFeedback = true
    loadAudio(contentId, allowAutoPlay)
  }

  /**
   * Load audio based on the contentId.
   *
   * @param contentId If contentId is null, then state.content.contentId is used as default.
   * @param allowAutoPlay If false, audio is guaranteed not to be autoPlayed.
   */
  private fun loadAudio(contentId: String?, allowAutoPlay: Boolean) {
    autoPlay = allowAutoPlay
    voiceoverMap = (
      state.recordedVoiceoversMap[contentId ?: state.content.contentId]
        ?: VoiceoverMapping.getDefaultInstance()
      ).voiceoverMappingMap
    languages = voiceoverMap.keys.toList().map { machineLocale.run { it.toMachineLowerCase() } }
    when {
      selectedLanguageCode.isEmpty() && languages.any {
        it == defaultLanguage
      } -> setAudioLanguageCode(
        defaultLanguage
      )
      languages.any { it == selectedLanguageCode } -> setAudioLanguageCode(selectedLanguageCode)
      languages.isNotEmpty() -> {
        autoPlay = false
        languageSelectionShown = true
        val languageCode = if (languages.contains("en")) {
          "en"
        } else {
          languages.first()
        }
        setAudioLanguageCode(languageCode)
      }
    }
  }

  /** Sets language code for data binding and changes data source to correct audio */
  fun setAudioLanguageCode(languageCode: String) {
    selectedLanguageCode = languageCode
    currentLanguageCode.set(languageCode)
    audioPlayerController.changeDataSource(voiceOverToUri(voiceoverMap[languageCode]))
  }

  /** Plays or pauses AudioController depending on passed in state */
  fun togglePlayPause(type: UiAudioPlayStatus?) {
    if (type == UiAudioPlayStatus.PLAYING) {
      audioPlayerController.pause()
    } else {
      audioPlayerController.play()
    }
  }

  fun pauseAudio() = audioPlayerController.pause()
  fun handleSeekTo(position: Int) = audioPlayerController.seekTo(position)
  fun handleRelease() = audioPlayerController.releaseMediaPlayer()

  private val playProgressResultLiveData: LiveData<AsyncResult<PlayProgress>> by lazy {
    audioPlayerController.initializeMediaPlayer()
  }

  private fun processDurationLiveData(): LiveData<Int> {
    return Transformations.map(playProgressResultLiveData, ::processDurationResultLiveData)
  }

  private fun processPositionLiveData(): LiveData<Int> {
    return Transformations.map(playProgressResultLiveData, ::processPositionResultLiveData)
  }

  private fun processPlayStatusLiveData(): LiveData<UiAudioPlayStatus> {
    return Transformations.map(playProgressResultLiveData, ::processPlayStatusResultLiveData)
  }

  private fun processDurationResultLiveData(playProgressResult: AsyncResult<PlayProgress>): Int {
    if (playProgressResult !is AsyncResult.Success) {
      return 0
    }
    return playProgressResult.value.duration
  }

  private fun processPositionResultLiveData(playProgressResult: AsyncResult<PlayProgress>): Int {
    if (playProgressResult !is AsyncResult.Success) {
      return 0
    }
    return playProgressResult.value.position
  }

  private fun processPlayStatusResultLiveData(
    playProgressResult: AsyncResult<PlayProgress>
  ): UiAudioPlayStatus {
    return when (playProgressResult) {
      is AsyncResult.Pending -> UiAudioPlayStatus.LOADING
      is AsyncResult.Failure -> UiAudioPlayStatus.FAILED
      is AsyncResult.Success -> when (playProgressResult.value.type) {
        PlayStatus.PREPARED -> {
          if (autoPlay) audioPlayerController.play()
          autoPlay = false
          UiAudioPlayStatus.PREPARED
        }
        PlayStatus.PLAYING -> UiAudioPlayStatus.PLAYING
        PlayStatus.PAUSED -> UiAudioPlayStatus.PAUSED
        PlayStatus.COMPLETED -> {
          if (hasFeedback) loadAudio(null, false)
          hasFeedback = false
          UiAudioPlayStatus.COMPLETED
        }
      }
    }
  }

  private fun voiceOverToUri(voiceover: Voiceover?): String {
    return "https://storage.googleapis.com/$gcsResource/exploration/$explorationId/" +
      "assets/audio/${voiceover?.fileName}"
  }
}
