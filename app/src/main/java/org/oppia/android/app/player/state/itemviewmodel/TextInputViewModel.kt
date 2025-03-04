package org.oppia.android.app.player.state.itemviewmodel

import android.text.Editable
import android.text.TextWatcher
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import org.oppia.android.app.model.Interaction
import org.oppia.android.app.model.InteractionObject
import org.oppia.android.app.model.UserAnswer
import org.oppia.android.app.model.WrittenTranslationContext
import org.oppia.android.app.player.state.answerhandling.InteractionAnswerErrorOrAvailabilityCheckReceiver
import org.oppia.android.app.player.state.answerhandling.InteractionAnswerHandler
import org.oppia.android.app.player.state.answerhandling.InteractionAnswerReceiver
import org.oppia.android.domain.translation.TranslationController
import javax.inject.Inject

/** [StateItemViewModel] for the text input interaction. */
class TextInputViewModel private constructor(
  interaction: Interaction,
  val hasConversationView: Boolean,
  private val interactionAnswerErrorOrAvailabilityCheckReceiver: InteractionAnswerErrorOrAvailabilityCheckReceiver, // ktlint-disable max-line-length
  val isSplitView: Boolean,
  private val writtenTranslationContext: WrittenTranslationContext,
  private val translationController: TranslationController
) : StateItemViewModel(ViewType.TEXT_INPUT_INTERACTION), InteractionAnswerHandler {
  var answerText: CharSequence = ""
  val hintText: CharSequence = deriveHintText(interaction)

  var isAnswerAvailable = ObservableField<Boolean>(false)

  init {
    val callback: Observable.OnPropertyChangedCallback =
      object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
          interactionAnswerErrorOrAvailabilityCheckReceiver.onPendingAnswerErrorOrAvailabilityCheck(
            /* pendingAnswerError= */ null,
            answerText.isNotEmpty()
          )
        }
      }
    isAnswerAvailable.addOnPropertyChangedCallback(callback)
  }

  fun getAnswerTextWatcher(): TextWatcher {
    return object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
      }

      override fun onTextChanged(answer: CharSequence, start: Int, before: Int, count: Int) {
        answerText = answer.toString().trim()
        val isAnswerTextAvailable = answerText.isNotEmpty()
        if (isAnswerTextAvailable != isAnswerAvailable.get()) {
          isAnswerAvailable.set(isAnswerTextAvailable)
        }
      }

      override fun afterTextChanged(s: Editable) {
      }
    }
  }

  override fun getPendingAnswer(): UserAnswer = UserAnswer.newBuilder().apply {
    if (answerText.isNotEmpty()) {
      val answerTextString = answerText.toString()
      answer = InteractionObject.newBuilder().apply {
        normalizedString = answerTextString
      }.build()
      plainAnswer = answerTextString
      writtenTranslationContext = this@TextInputViewModel.writtenTranslationContext
    }
  }.build()

  private fun deriveHintText(interaction: Interaction): CharSequence {
    // The subtitled unicode can apparently exist in the structure in two different formats.
    val placeholderUnicodeOption1 =
      interaction.customizationArgsMap["placeholder"]?.subtitledUnicode
    val placeholderUnicodeOption2 =
      interaction.customizationArgsMap["placeholder"]?.customSchemaValue?.subtitledUnicode
    val placeholder1 =
      placeholderUnicodeOption1?.let { unicode ->
        translationController.extractString(unicode, writtenTranslationContext)
      } ?: ""
    val placeholder2 =
      placeholderUnicodeOption2?.let { unicode ->
        translationController.extractString(unicode, writtenTranslationContext)
      } ?: "" // The default placeholder for text input is empty.
    return if (placeholder1.isNotEmpty()) placeholder1 else placeholder2
  }

  /** Implementation of [StateItemViewModel.InteractionItemFactory] for this view model. */
  class FactoryImpl @Inject constructor(
    private val translationController: TranslationController
  ) : InteractionItemFactory {
    override fun create(
      entityId: String,
      hasConversationView: Boolean,
      interaction: Interaction,
      interactionAnswerReceiver: InteractionAnswerReceiver,
      answerErrorReceiver: InteractionAnswerErrorOrAvailabilityCheckReceiver,
      hasPreviousButton: Boolean,
      isSplitView: Boolean,
      writtenTranslationContext: WrittenTranslationContext
    ): StateItemViewModel {
      return TextInputViewModel(
        interaction,
        hasConversationView,
        answerErrorReceiver,
        isSplitView,
        writtenTranslationContext,
        translationController
      )
    }
  }
}
