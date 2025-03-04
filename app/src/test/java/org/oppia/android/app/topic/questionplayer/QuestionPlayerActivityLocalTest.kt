package org.oppia.android.app.topic.questionplayer

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToHolder
import androidx.test.espresso.matcher.ViewMatchers.hasChildCount
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.Module
import dagger.Provides
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.oppia.android.R
import org.oppia.android.app.activity.ActivityComponent
import org.oppia.android.app.activity.ActivityComponentFactory
import org.oppia.android.app.application.ApplicationComponent
import org.oppia.android.app.application.ApplicationContext
import org.oppia.android.app.application.ApplicationInjector
import org.oppia.android.app.application.ApplicationInjectorProvider
import org.oppia.android.app.application.ApplicationModule
import org.oppia.android.app.application.ApplicationStartupListenerModule
import org.oppia.android.app.devoptions.DeveloperOptionsModule
import org.oppia.android.app.devoptions.DeveloperOptionsStarterModule
import org.oppia.android.app.model.ProfileId
import org.oppia.android.app.player.state.itemviewmodel.SplitScreenInteractionModule
import org.oppia.android.app.player.state.itemviewmodel.StateItemViewModel
import org.oppia.android.app.shim.ViewBindingShimModule
import org.oppia.android.app.topic.PracticeTabModule
import org.oppia.android.app.translation.testing.ActivityRecreatorTestModule
import org.oppia.android.data.backends.gae.NetworkConfigProdModule
import org.oppia.android.data.backends.gae.NetworkModule
import org.oppia.android.domain.classify.InteractionsModule
import org.oppia.android.domain.classify.rules.algebraicexpressioninput.AlgebraicExpressionInputModule
import org.oppia.android.domain.classify.rules.continueinteraction.ContinueModule
import org.oppia.android.domain.classify.rules.dragAndDropSortInput.DragDropSortInputModule
import org.oppia.android.domain.classify.rules.fractioninput.FractionInputModule
import org.oppia.android.domain.classify.rules.imageClickInput.ImageClickInputModule
import org.oppia.android.domain.classify.rules.itemselectioninput.ItemSelectionInputModule
import org.oppia.android.domain.classify.rules.mathequationinput.MathEquationInputModule
import org.oppia.android.domain.classify.rules.multiplechoiceinput.MultipleChoiceInputModule
import org.oppia.android.domain.classify.rules.numberwithunits.NumberWithUnitsRuleModule
import org.oppia.android.domain.classify.rules.numericexpressioninput.NumericExpressionInputModule
import org.oppia.android.domain.classify.rules.numericinput.NumericInputRuleModule
import org.oppia.android.domain.classify.rules.ratioinput.RatioInputModule
import org.oppia.android.domain.classify.rules.textinput.TextInputRuleModule
import org.oppia.android.domain.exploration.lightweightcheckpointing.ExplorationStorageModule
import org.oppia.android.domain.hintsandsolution.HintsAndSolutionConfigModule
import org.oppia.android.domain.hintsandsolution.HintsAndSolutionProdModule
import org.oppia.android.domain.onboarding.ExpirationMetaDataRetrieverModule
import org.oppia.android.domain.oppialogger.LogStorageModule
import org.oppia.android.domain.oppialogger.loguploader.LogUploadWorkerModule
import org.oppia.android.domain.platformparameter.PlatformParameterModule
import org.oppia.android.domain.platformparameter.PlatformParameterSingletonModule
import org.oppia.android.domain.question.InternalMasteryMultiplyFactor
import org.oppia.android.domain.question.InternalScoreMultiplyFactor
import org.oppia.android.domain.question.MaxMasteryGainPerQuestion
import org.oppia.android.domain.question.MaxMasteryLossPerQuestion
import org.oppia.android.domain.question.MaxScorePerQuestion
import org.oppia.android.domain.question.QuestionCountPerTrainingSession
import org.oppia.android.domain.question.QuestionTrainingSeed
import org.oppia.android.domain.question.ViewHintMasteryPenalty
import org.oppia.android.domain.question.ViewHintScorePenalty
import org.oppia.android.domain.question.WrongAnswerMasteryPenalty
import org.oppia.android.domain.question.WrongAnswerScorePenalty
import org.oppia.android.domain.topic.PrimeTopicAssetsControllerModule
import org.oppia.android.domain.topic.TEST_SKILL_ID_1
import org.oppia.android.domain.workmanager.WorkManagerConfigurationModule
import org.oppia.android.testing.TestLogReportingModule
import org.oppia.android.testing.espresso.EditTextInputAction
import org.oppia.android.testing.espresso.KonfettiViewMatcher.Companion.hasActiveConfetti
import org.oppia.android.testing.junit.InitializeDefaultLocaleRule
import org.oppia.android.testing.profile.ProfileTestHelper
import org.oppia.android.testing.robolectric.RobolectricModule
import org.oppia.android.testing.threading.TestCoroutineDispatchers
import org.oppia.android.testing.threading.TestDispatcherModule
import org.oppia.android.testing.time.FakeOppiaClockModule
import org.oppia.android.util.accessibility.AccessibilityTestModule
import org.oppia.android.util.accessibility.FakeAccessibilityService
import org.oppia.android.util.caching.AssetModule
import org.oppia.android.util.caching.testing.CachingTestModule
import org.oppia.android.util.gcsresource.GcsResourceModule
import org.oppia.android.util.locale.LocaleProdModule
import org.oppia.android.util.logging.LoggerModule
import org.oppia.android.util.logging.firebase.FirebaseLogUploaderModule
import org.oppia.android.util.networking.NetworkConnectionDebugUtilModule
import org.oppia.android.util.networking.NetworkConnectionUtilDebugModule
import org.oppia.android.util.parser.html.HtmlParserEntityTypeModule
import org.oppia.android.util.parser.image.GlideImageLoaderModule
import org.oppia.android.util.parser.image.ImageParsingModule
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tests for [QuestionPlayerActivity] that can only be run locally, e.g. using Robolectric, and not on an
 * emulator.
 */
@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(
  application = QuestionPlayerActivityLocalTest.TestApplication::class,
  qualifiers = "port-xxhdpi"
)
class QuestionPlayerActivityLocalTest {
  @get:Rule
  val initializeDefaultLocaleRule = InitializeDefaultLocaleRule()

  @Inject
  lateinit var accessibilityManager: FakeAccessibilityService

  @Inject
  lateinit var profileTestHelper: ProfileTestHelper

  @Inject
  lateinit var testCoroutineDispatchers: TestCoroutineDispatchers

  @Inject
  @field:ApplicationContext
  lateinit var context: Context

  @Inject
  lateinit var editTextInputAction: EditTextInputAction

  private val SKILL_ID_LIST = arrayListOf(TEST_SKILL_ID_1)

  @Before
  fun setUp() {
    setUpTestApplicationComponent()
    profileTestHelper.initializeProfiles()
  }

  @Test
  @Config(qualifiers = "port")
  fun testQuestionPlayer_portrait_submitCorrectAnswer_correctTextBannerIsDisplayed() {
    launchForQuestionPlayer(SKILL_ID_LIST).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.question_recycler_view)).check(matches(isDisplayed()))

      submitCorrectAnswerToQuestionPlayerFractionInput()

      onView(withId(R.id.congratulations_text_view))
        .check(matches(isCompletelyDisplayed()))
    }
  }

  @Test
  @Config(qualifiers = "land")
  fun testQuestionPlayer_landscape_submitCorrectAnswer_correctTextBannerIsDisplayed() {
    launchForQuestionPlayer(SKILL_ID_LIST).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.question_recycler_view)).check(matches(isDisplayed()))

      submitCorrectAnswerToQuestionPlayerFractionInput()

      onView(withId(R.id.congratulations_text_view))
        .check(matches(isCompletelyDisplayed()))
    }
  }

  @Test
  @Config(qualifiers = "+port")
  fun testQuestionPlayer_portrait_submitCorrectAnswerWithFeedback_correctIsNotAnnounced() {
    launchForQuestionPlayer(SKILL_ID_LIST).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.question_recycler_view)).check(matches(isDisplayed()))

      submitCorrectAnswerToQuestionPlayerFractionInput()
      clickContinueNavigationButton()
      accessibilityManager.resetLatestAnnouncement()
      submitCorrectAnswerToQuestion2PlayerFractionInput()

      assertThat(accessibilityManager.getLatestAnnouncement()).isNull()
    }
  }

  @Test
  @Config(qualifiers = "+land")
  fun testQuestionPlayer_landscape_submitCorrectAnswerWithFeedback_correctIsNotAnnounced() {
    launchForQuestionPlayer(SKILL_ID_LIST).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.question_recycler_view)).check(matches(isDisplayed()))

      submitCorrectAnswerToQuestionPlayerFractionInput()
      clickContinueNavigationButton()
      accessibilityManager.resetLatestAnnouncement()
      submitCorrectAnswerToQuestion2PlayerFractionInput()

      assertThat(accessibilityManager.getLatestAnnouncement()).isNull()
    }
  }

  @Test
  @Config(qualifiers = "port")
  fun testQuestionPlayer_portrait_submitCorrectAnswer_correctIsAnnounced() {
    launchForQuestionPlayer(SKILL_ID_LIST).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.question_recycler_view)).check(matches(isDisplayed()))

      submitCorrectAnswerToQuestionPlayerFractionInput()

      assertThat(accessibilityManager.getLatestAnnouncement()).isEqualTo("Correct!")
    }
  }

  @Test
  @Config(qualifiers = "land")
  fun testQuestionPlayer_landscape_submitCorrectAnswer_correctIsAnnounced() {
    launchForQuestionPlayer(SKILL_ID_LIST).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.question_recycler_view)).check(matches(isDisplayed()))

      submitCorrectAnswerToQuestionPlayerFractionInput()

      assertThat(accessibilityManager.getLatestAnnouncement()).isEqualTo("Correct!")
    }
  }

  @Test
  @Config(qualifiers = "port")
  fun testQuestionPlayer_portrait_submitCorrectAnswer_confettiIsActive() {
    launchForQuestionPlayer(SKILL_ID_LIST).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.question_recycler_view)).check(matches(isDisplayed()))

      submitCorrectAnswerToQuestionPlayerFractionInput()

      onView(withId(R.id.congratulations_text_confetti_view)).check(matches(hasActiveConfetti()))
    }
  }

  @Test
  @Config(qualifiers = "land")
  fun testQuestionPlayer_landscape_submitCorrectAnswer_confettiIsActive() {
    launchForQuestionPlayer(SKILL_ID_LIST).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.question_recycler_view)).check(matches(isDisplayed()))

      submitCorrectAnswerToQuestionPlayerFractionInput()

      onView(withId(R.id.congratulations_text_confetti_view)).check(matches(hasActiveConfetti()))
    }
  }

  @Test
  fun testQuestionPlayer_submitTwoWrongAnswers_checkPreviousHeaderVisible() {
    launchForQuestionPlayer(SKILL_ID_LIST).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.question_recycler_view)).check(matches(isDisplayed()))

      submitTwoWrongAnswersToQuestionPlayer()

      onView(withId(R.id.previous_response_header)).check(matches(isDisplayed()))
    }
  }

  @Test
  fun testQuestionPlayer_submitTwoWrongAnswers_checkPreviousHeaderCollapsed() {
    launchForQuestionPlayer(SKILL_ID_LIST).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.question_recycler_view)).check(matches(isDisplayed()))

      submitTwoWrongAnswersToQuestionPlayer()

      onView(withId(R.id.previous_response_header)).check(matches(isDisplayed()))
      onView(withId(R.id.question_recycler_view)).check(
        matches(
          hasChildCount(/* childCount= */ 5)
        )
      )
    }
  }

  @Test
  fun testQuestionPlayer_submitTwoWrongAnswers_expandResponse_checkPreviousHeaderExpanded() {
    launchForQuestionPlayer(SKILL_ID_LIST).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.question_recycler_view)).check(matches(isDisplayed()))

      submitTwoWrongAnswersToQuestionPlayer()

      onView(withId(R.id.previous_response_header)).check(matches(isDisplayed()))
      onView(withId(R.id.question_recycler_view))
        .perform(scrollToViewType(StateItemViewModel.ViewType.PREVIOUS_RESPONSES_HEADER))
      onView(withId(R.id.previous_response_header)).perform(click())
      onView(withId(R.id.question_recycler_view)).check(
        matches(
          hasChildCount(/* childCount= */ 6)
        )
      )
    }
  }

  @Test
  fun testQuestionPlayer_expandCollapseResponse_checkPreviousHeaderCollapsed() {
    launchForQuestionPlayer(SKILL_ID_LIST).use {
      testCoroutineDispatchers.runCurrent()
      onView(withId(R.id.question_recycler_view)).check(matches(isDisplayed()))

      submitTwoWrongAnswersToQuestionPlayer()

      onView(withId(R.id.previous_response_header)).check(matches(isDisplayed()))
      onView(withId(R.id.question_recycler_view)).check(
        matches(
          hasChildCount(/* childCount= */ 5)
        )
      )

      onView(withId(R.id.question_recycler_view))
        .perform(scrollToViewType(StateItemViewModel.ViewType.PREVIOUS_RESPONSES_HEADER))
      onView(withId(R.id.previous_response_header)).perform(click())
      onView(withId(R.id.question_recycler_view)).check(
        matches(
          hasChildCount(/* childCount= */ 6)
        )
      )

      onView(withId(R.id.previous_response_header)).perform(click())
      onView(withId(R.id.question_recycler_view)).check(
        matches(
          hasChildCount(/* childCount= */ 5)
        )
      )
    }
  }

  private fun launchForQuestionPlayer(
    skillIdList: ArrayList<String>
  ): ActivityScenario<QuestionPlayerActivity> {
    return ActivityScenario.launch(
      QuestionPlayerActivity.createQuestionPlayerActivityIntent(
        context, skillIdList, ProfileId.getDefaultInstance()
      )
    )
  }

  private fun submitCorrectAnswerToQuestionPlayerFractionInput() {
    onView(withId(R.id.question_recycler_view))
      .perform(scrollToViewType(StateItemViewModel.ViewType.TEXT_INPUT_INTERACTION))
    onView(withId(R.id.text_input_interaction_view)).perform(
      editTextInputAction.appendText("1/2"),
      closeSoftKeyboard()
    )
    testCoroutineDispatchers.runCurrent()

    onView(withId(R.id.question_recycler_view))
      .perform(scrollToViewType(StateItemViewModel.ViewType.SUBMIT_ANSWER_BUTTON))
    onView(withId(R.id.submit_answer_button)).perform(click())
    testCoroutineDispatchers.runCurrent()
  }

  private fun submitCorrectAnswerToQuestion2PlayerFractionInput() {
    onView(withId(R.id.question_recycler_view))
      .perform(scrollToViewType(StateItemViewModel.ViewType.TEXT_INPUT_INTERACTION))
    onView(withId(R.id.text_input_interaction_view)).perform(
      editTextInputAction.appendText("1/4"),
      closeSoftKeyboard()
    )
    testCoroutineDispatchers.runCurrent()

    onView(withId(R.id.question_recycler_view))
      .perform(scrollToViewType(StateItemViewModel.ViewType.SUBMIT_ANSWER_BUTTON))
    onView(withId(R.id.submit_answer_button)).perform(click())
    testCoroutineDispatchers.runCurrent()
  }

  private fun clickContinueNavigationButton() {
    onView(withId(R.id.question_recycler_view))
      .perform(scrollToViewType(StateItemViewModel.ViewType.CONTINUE_NAVIGATION_BUTTON))
    testCoroutineDispatchers.runCurrent()
    onView(withId(R.id.continue_navigation_button)).perform(click())
    testCoroutineDispatchers.runCurrent()
  }

  private fun submitTwoWrongAnswersToQuestionPlayer() {
    submitWrongAnswerToQuestionPlayerFractionInput()
    submitWrongAnswerToQuestionPlayerFractionInput()
  }

  private fun submitWrongAnswerToQuestionPlayerFractionInput() {
    onView(withId(R.id.question_recycler_view))
      .perform(scrollToViewType(StateItemViewModel.ViewType.TEXT_INPUT_INTERACTION))
    onView(withId(R.id.text_input_interaction_view)).perform(editTextInputAction.appendText("1"))
    testCoroutineDispatchers.runCurrent()

    onView(withId(R.id.question_recycler_view))
      .perform(scrollToViewType(StateItemViewModel.ViewType.SUBMIT_ANSWER_BUTTON))
    onView(withId(R.id.submit_answer_button)).perform(click())
    testCoroutineDispatchers.runCurrent()
  }

  private fun scrollToViewType(viewType: StateItemViewModel.ViewType): ViewAction {
    return scrollToHolder(StateViewHolderTypeMatcher(viewType))
  }

  /**
   * [BaseMatcher] that matches against the first occurrence of the specified view holder type in
   * StateFragment's RecyclerView.
   */
  private class StateViewHolderTypeMatcher(
    private val viewType: StateItemViewModel.ViewType
  ) : BaseMatcher<RecyclerView.ViewHolder>() {
    override fun describeTo(description: Description?) {
      description?.appendText("item view type of $viewType")
    }

    override fun matches(item: Any?): Boolean {
      return (item as? RecyclerView.ViewHolder)?.itemViewType == viewType.ordinal
    }
  }

  private fun setUpTestApplicationComponent() {
    ApplicationProvider.getApplicationContext<TestApplication>().inject(this)
  }

  @Module
  class QuestionPlayerActivityLocalTestModule {
    @Provides
    @QuestionCountPerTrainingSession
    fun provideQuestionCountPerTrainingSession(): Int = 3

    // Ensure that the question seed is consistent for all runs of the tests to keep question order
    // predictable.
    @Provides
    @QuestionTrainingSeed
    fun provideQuestionTrainingSeed(): Long = 1

    @Provides
    @ViewHintScorePenalty
    fun provideViewHintScorePenalty(): Int = 1

    @Provides
    @WrongAnswerScorePenalty
    fun provideWrongAnswerScorePenalty(): Int = 1

    @Provides
    @MaxScorePerQuestion
    fun provideMaxScorePerQuestion(): Int = 10

    @Provides
    @InternalScoreMultiplyFactor
    fun provideInternalScoreMultiplyFactor(): Int = 10

    @Provides
    @MaxMasteryGainPerQuestion
    fun provideMaxMasteryGainPerQuestion(): Int = 10

    @Provides
    @MaxMasteryLossPerQuestion
    fun provideMaxMasteryLossPerQuestion(): Int = -10

    @Provides
    @ViewHintMasteryPenalty
    fun provideViewHintMasteryPenalty(): Int = 2

    @Provides
    @WrongAnswerMasteryPenalty
    fun provideWrongAnswerMasteryPenalty(): Int = 5

    @Provides
    @InternalMasteryMultiplyFactor
    fun provideInternalMasteryMultiplyFactor(): Int = 100
  }

  // TODO(#59): Figure out a way to reuse modules instead of needing to re-declare them.
  @Singleton
  @Component(
    modules = [
      QuestionPlayerActivityLocalTestModule::class,
      PlatformParameterModule::class, PlatformParameterSingletonModule::class,
      TestDispatcherModule::class, ApplicationModule::class, RobolectricModule::class,
      LoggerModule::class, ContinueModule::class, FractionInputModule::class,
      ItemSelectionInputModule::class, MultipleChoiceInputModule::class,
      NumberWithUnitsRuleModule::class, NumericInputRuleModule::class, TextInputRuleModule::class,
      DragDropSortInputModule::class, ImageClickInputModule::class, InteractionsModule::class,
      GcsResourceModule::class, GlideImageLoaderModule::class, ImageParsingModule::class,
      HtmlParserEntityTypeModule::class, TestLogReportingModule::class,
      AccessibilityTestModule::class, LogStorageModule::class, CachingTestModule::class,
      PrimeTopicAssetsControllerModule::class, ExpirationMetaDataRetrieverModule::class,
      ViewBindingShimModule::class, ApplicationStartupListenerModule::class,
      RatioInputModule::class, HintsAndSolutionConfigModule::class, NetworkConfigProdModule::class,
      LogUploadWorkerModule::class, WorkManagerConfigurationModule::class,
      FirebaseLogUploaderModule::class, FakeOppiaClockModule::class, PracticeTabModule::class,
      DeveloperOptionsStarterModule::class, DeveloperOptionsModule::class,
      ExplorationStorageModule::class, NetworkModule::class, HintsAndSolutionProdModule::class,
      NetworkConnectionUtilDebugModule::class, NetworkConnectionDebugUtilModule::class,
      AssetModule::class, LocaleProdModule::class, ActivityRecreatorTestModule::class,
      NumericExpressionInputModule::class, AlgebraicExpressionInputModule::class,
      MathEquationInputModule::class, SplitScreenInteractionModule::class
    ]
  )
  interface TestApplicationComponent : ApplicationComponent {
    @Component.Builder
    interface Builder : ApplicationComponent.Builder

    fun inject(questionPlayerActivityLocalTest: QuestionPlayerActivityLocalTest)
  }

  class TestApplication : Application(), ActivityComponentFactory, ApplicationInjectorProvider {
    private val component: TestApplicationComponent by lazy {
      DaggerQuestionPlayerActivityLocalTest_TestApplicationComponent.builder()
        .setApplication(this)
        .build() as TestApplicationComponent
    }

    fun inject(questionPlayerActivityLocalTest: QuestionPlayerActivityLocalTest) {
      component.inject(questionPlayerActivityLocalTest)
    }

    override fun createActivityComponent(activity: AppCompatActivity): ActivityComponent {
      return component.getActivityComponentBuilderProvider().get().setActivity(activity).build()
    }

    override fun getApplicationInjector(): ApplicationInjector = component
  }
}
