package org.oppia.android.app.options

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Component
import dagger.Module
import dagger.Provides
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.oppia.android.R
import org.oppia.android.app.activity.ActivityComponent
import org.oppia.android.app.activity.ActivityComponentFactory
import org.oppia.android.app.application.ApplicationComponent
import org.oppia.android.app.application.ApplicationInjector
import org.oppia.android.app.application.ApplicationInjectorProvider
import org.oppia.android.app.application.ApplicationModule
import org.oppia.android.app.application.ApplicationStartupListenerModule
import org.oppia.android.app.devoptions.DeveloperOptionsModule
import org.oppia.android.app.devoptions.DeveloperOptionsStarterModule
import org.oppia.android.app.player.state.itemviewmodel.SplitScreenInteractionModule
import org.oppia.android.app.recyclerview.RecyclerViewMatcher.Companion.atPositionOnView
import org.oppia.android.app.shim.ViewBindingShimModule
import org.oppia.android.app.topic.PracticeTabModule
import org.oppia.android.app.translation.testing.ActivityRecreatorTestModule
import org.oppia.android.app.utility.OrientationChangeAction.Companion.orientationLandscape
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
import org.oppia.android.domain.question.QuestionModule
import org.oppia.android.domain.topic.PrimeTopicAssetsControllerModule
import org.oppia.android.domain.workmanager.WorkManagerConfigurationModule
import org.oppia.android.testing.OppiaTestRule
import org.oppia.android.testing.TestLogReportingModule
import org.oppia.android.testing.junit.InitializeDefaultLocaleRule
import org.oppia.android.testing.profile.ProfileTestHelper
import org.oppia.android.testing.robolectric.RobolectricModule
import org.oppia.android.testing.threading.TestCoroutineDispatchers
import org.oppia.android.testing.threading.TestDispatcherModule
import org.oppia.android.testing.time.FakeOppiaClockModule
import org.oppia.android.util.accessibility.AccessibilityTestModule
import org.oppia.android.util.caching.AssetModule
import org.oppia.android.util.caching.CacheAssetsLocally
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

private const val ENGLISH = 0
private const val FRENCH = 1

/** Tests for [AppLanguageFragment]. */
@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = AppLanguageFragmentTest.TestApplication::class)
class AppLanguageFragmentTest {
  @get:Rule
  val initializeDefaultLocaleRule = InitializeDefaultLocaleRule()

  @get:Rule
  val oppiaTestRule = OppiaTestRule()

  @Inject
  lateinit var context: Context

  @Inject
  lateinit var profileTestHelper: ProfileTestHelper

  @Inject
  lateinit var testCoroutineDispatchers: TestCoroutineDispatchers

  @Before
  fun setUp() {
    setUpTestApplicationComponent()
    profileTestHelper.initializeProfiles()
    testCoroutineDispatchers.registerIdlingResource()
  }

  @After
  fun tearDown() {
    testCoroutineDispatchers.unregisterIdlingResource()
  }

  @Test
  fun testAppLanguage_selectedLanguageIsEnglish() {
    launch<AppLanguageActivity>(createAppLanguageActivityIntent("English")).use {
      checkSelectedLanguage(ENGLISH)
    }
  }

  @Test
  fun testAppLanguage_configChange_selectedLanguageIsEnglish() {
    launch<AppLanguageActivity>(createAppLanguageActivityIntent("English")).use {
      rotateToLandscape()
      checkSelectedLanguage(ENGLISH)
    }
  }

  @Test
  @Config(qualifiers = "sw600dp")
  fun testAppLanguage_tabletConfig_selectedLanguageIsEnglish() {
    launch<AppLanguageActivity>(createAppLanguageActivityIntent("English")).use {
      testCoroutineDispatchers.runCurrent()
      checkSelectedLanguage(ENGLISH)
    }
  }

  @Test
  fun testAppLanguage_changeLanguageToFrench_selectedLanguageIsFrench() {
    launch<AppLanguageActivity>(createAppLanguageActivityIntent("English")).use {
      checkSelectedLanguage(ENGLISH)
      selectLanguage(FRENCH)
      checkSelectedLanguage(FRENCH)
    }
  }

  @Test
  fun testAppLanguage_changeLanguageToFrench_configChange_selectedLanguageIsFrench() {
    launch<AppLanguageActivity>(createAppLanguageActivityIntent("English")).use {
      checkSelectedLanguage(ENGLISH)
      selectLanguage(FRENCH)
      rotateToLandscape()
      checkSelectedLanguage(FRENCH)
    }
  }

  @Test
  @Config(qualifiers = "sw600dp")
  fun testAppLanguage_tabletConfig_changeLanguageToFrench_selectedLanguageIsFrench() {
    launch<AppLanguageActivity>(createAppLanguageActivityIntent("English")).use {
      testCoroutineDispatchers.runCurrent()
      checkSelectedLanguage(ENGLISH)
      selectLanguage(FRENCH)
      checkSelectedLanguage(FRENCH)
    }
  }

  private fun checkSelectedLanguage(index: Int) {
    onView(
      atPositionOnView(
        R.id.language_recycler_view,
        index,
        R.id.language_radio_button
      )
    ).check(matches(isChecked()))
    testCoroutineDispatchers.runCurrent()
  }

  private fun rotateToLandscape() {
    onView(isRoot()).perform(orientationLandscape())
    testCoroutineDispatchers.runCurrent()
  }

  private fun selectLanguage(index: Int) {
    onView(
      atPositionOnView(
        recyclerViewId = R.id.language_recycler_view,
        position = index,
        targetViewId = R.id.language_radio_button
      )
    ).perform(
      click()
    )
    testCoroutineDispatchers.runCurrent()
  }

  private fun createAppLanguageActivityIntent(summaryValue: String): Intent {
    return AppLanguageActivity.createAppLanguageActivityIntent(
      ApplicationProvider.getApplicationContext(),
      APP_LANGUAGE,
      summaryValue
    )
  }

  private fun setUpTestApplicationComponent() {
    ApplicationProvider.getApplicationContext<TestApplication>().inject(this)
  }

  @Module
  class TestModule {
    // Do not use caching to ensure URLs are always used as the main data source when loading audio.
    @Provides
    @CacheAssetsLocally
    fun provideCacheAssetsLocally(): Boolean = false
  }

  // TODO(#59): Figure out a way to reuse modules instead of needing to re-declare them.
  @Singleton
  @Component(
    modules = [
      TestDispatcherModule::class, PlatformParameterModule::class, ApplicationModule::class,
      RobolectricModule::class, LoggerModule::class, ContinueModule::class,
      FractionInputModule::class, ItemSelectionInputModule::class, MultipleChoiceInputModule::class,
      NumberWithUnitsRuleModule::class, NumericInputRuleModule::class, TextInputRuleModule::class,
      DragDropSortInputModule::class, InteractionsModule::class, GcsResourceModule::class,
      GlideImageLoaderModule::class, ImageParsingModule::class, HtmlParserEntityTypeModule::class,
      QuestionModule::class, TestLogReportingModule::class, AccessibilityTestModule::class,
      ImageClickInputModule::class, LogStorageModule::class, CachingTestModule::class,
      PrimeTopicAssetsControllerModule::class, ExpirationMetaDataRetrieverModule::class,
      ViewBindingShimModule::class, ApplicationStartupListenerModule::class,
      RatioInputModule::class, HintsAndSolutionConfigModule::class,
      WorkManagerConfigurationModule::class, FirebaseLogUploaderModule::class,
      LogUploadWorkerModule::class, FakeOppiaClockModule::class, PracticeTabModule::class,
      DeveloperOptionsStarterModule::class, DeveloperOptionsModule::class,
      ExplorationStorageModule::class, NetworkModule::class, HintsAndSolutionProdModule::class,
      NetworkConnectionUtilDebugModule::class, NetworkConnectionDebugUtilModule::class,
      AssetModule::class, LocaleProdModule::class, ActivityRecreatorTestModule::class,
      NetworkConfigProdModule::class, PlatformParameterSingletonModule::class,
      NumericExpressionInputModule::class, AlgebraicExpressionInputModule::class,
      MathEquationInputModule::class, SplitScreenInteractionModule::class
    ]
  )
  interface TestApplicationComponent : ApplicationComponent {
    @Component.Builder
    interface Builder : ApplicationComponent.Builder

    fun inject(appLanguageFragmentTest: AppLanguageFragmentTest)
  }

  class TestApplication : Application(), ActivityComponentFactory, ApplicationInjectorProvider {
    private val component: TestApplicationComponent by lazy {
      DaggerAppLanguageFragmentTest_TestApplicationComponent.builder()
        .setApplication(this)
        .build() as TestApplicationComponent
    }

    fun inject(appLanguageFragmentTest: AppLanguageFragmentTest) {
      component.inject(appLanguageFragmentTest)
    }

    override fun createActivityComponent(activity: AppCompatActivity): ActivityComponent {
      return component.getActivityComponentBuilderProvider().get().setActivity(activity).build()
    }

    override fun getApplicationInjector(): ApplicationInjector = component
  }
}
