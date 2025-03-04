package org.oppia.android.domain.topic

import android.graphics.Color
import org.json.JSONObject
import org.oppia.android.app.model.ChapterPlayState
import org.oppia.android.app.model.ChapterProgress
import org.oppia.android.app.model.ChapterSummary
import org.oppia.android.app.model.ComingSoonTopicList
import org.oppia.android.app.model.LessonThumbnail
import org.oppia.android.app.model.LessonThumbnailGraphic
import org.oppia.android.app.model.ProfileId
import org.oppia.android.app.model.PromotedActivityList
import org.oppia.android.app.model.PromotedStory
import org.oppia.android.app.model.PromotedStoryList
import org.oppia.android.app.model.StoryProgress
import org.oppia.android.app.model.StoryRecord
import org.oppia.android.app.model.StorySummary
import org.oppia.android.app.model.Topic
import org.oppia.android.app.model.TopicIdList
import org.oppia.android.app.model.TopicList
import org.oppia.android.app.model.TopicPlayAvailability
import org.oppia.android.app.model.TopicPlayAvailability.AvailabilityCase.AVAILABLE_TO_PLAY_IN_FUTURE
import org.oppia.android.app.model.TopicPlayAvailability.AvailabilityCase.AVAILABLE_TO_PLAY_NOW
import org.oppia.android.app.model.TopicProgress
import org.oppia.android.app.model.TopicRecord
import org.oppia.android.app.model.TopicSummary
import org.oppia.android.app.model.UpcomingTopic
import org.oppia.android.domain.util.JsonAssetRetriever
import org.oppia.android.domain.util.getStringFromObject
import org.oppia.android.util.caching.AssetRepository
import org.oppia.android.util.caching.LoadLessonProtosFromAssets
import org.oppia.android.util.data.AsyncResult
import org.oppia.android.util.data.DataProvider
import org.oppia.android.util.data.DataProviders
import org.oppia.android.util.data.DataProviders.Companion.transformAsync
import org.oppia.android.util.system.OppiaClock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val ONE_WEEK_IN_DAYS = 7

private const val TOPIC_BG_COLOR = "#C6DCDA"

private const val CHAPTER_BG_COLOR_1 = "#F8BF74"
private const val CHAPTER_BG_COLOR_2 = "#D68F78"
private const val CHAPTER_BG_COLOR_3 = "#8EBBB6"
private const val CHAPTER_BG_COLOR_4 = "#B3D8F1"

const val TEST_TOPIC_ID_0 = "test_topic_id_0"
const val TEST_TOPIC_ID_1 = "test_topic_id_1"
const val TEST_TOPIC_ID_2 = "test_topic_id_2"
const val FRACTIONS_TOPIC_ID = "GJ2rLXRKD5hw"
const val SUBTOPIC_TOPIC_ID = 1
const val SUBTOPIC_TOPIC_ID_2 = 2
const val RATIOS_TOPIC_ID = "omzF4oqgeTXd"
val TOPIC_THUMBNAILS = mapOf(
  FRACTIONS_TOPIC_ID to createTopicThumbnail0(),
  RATIOS_TOPIC_ID to createTopicThumbnail1(),
  TEST_TOPIC_ID_0 to createTopicThumbnail2(),
  TEST_TOPIC_ID_1 to createTopicThumbnail3()
)
val STORY_THUMBNAILS = mapOf(
  FRACTIONS_STORY_ID_0 to createStoryThumbnail0(),
  RATIOS_STORY_ID_0 to createStoryThumbnail1(),
  RATIOS_STORY_ID_1 to createStoryThumbnail2(),
  TEST_STORY_ID_0 to createStoryThumbnail3(),
  TEST_STORY_ID_2 to createStoryThumbnail5()
)
val EXPLORATION_THUMBNAILS = mapOf(
  FRACTIONS_EXPLORATION_ID_0 to createChapterThumbnail0(),
  FRACTIONS_EXPLORATION_ID_1 to createChapterThumbnail1(),
  RATIOS_EXPLORATION_ID_0 to createChapterThumbnail2(),
  RATIOS_EXPLORATION_ID_1 to createChapterThumbnail3(),
  RATIOS_EXPLORATION_ID_2 to createChapterThumbnail4(),
  RATIOS_EXPLORATION_ID_3 to createChapterThumbnail5(),
  TEST_EXPLORATION_ID_2 to createChapterThumbnail8(),
  TEST_EXPLORATION_ID_4 to createChapterThumbnail0(),
  TEST_EXPLORATION_ID_13 to createChapterThumbnail0(),
)

private const val GET_TOPIC_LIST_PROVIDER_ID = "get_topic_list_provider_id"
private const val GET_PROMOTED_ACTIVITY_LIST_PROVIDER_ID =
  "get_recommended_actvity_list_provider_id"

private val EVICTION_TIME_MILLIS = TimeUnit.DAYS.toMillis(1)

/** Controller for retrieving the list of topics available to the learner to play. */
@Singleton
class TopicListController @Inject constructor(
  private val jsonAssetRetriever: JsonAssetRetriever,
  private val topicController: TopicController,
  private val storyProgressController: StoryProgressController,
  private val dataProviders: DataProviders,
  private val oppiaClock: OppiaClock,
  private val assetRepository: AssetRepository,
  @LoadLessonProtosFromAssets private val loadLessonProtosFromAssets: Boolean
) {

  /**
   * Returns the list of [TopicSummary]s currently tracked by the app, possibly up to
   * [EVICTION_TIME_MILLIS] old.
   */
  fun getTopicList(): DataProvider<TopicList> {
    return dataProviders.createInMemoryDataProvider(
      GET_TOPIC_LIST_PROVIDER_ID,
      this::createTopicList
    )
  }

  /**
   * Returns the list of ongoing [PromotedStory]s that can be viewed via a link on the homescreen.
   * The total number of promoted stories should correspond to the ongoing story count within the
   * [TopicList] returned by [getTopicList].
   *
   * @param profileId the ID corresponding to the profile for which [PromotedStory] needs to be
   *    fetched.
   * @return a [DataProvider] for an [PromotedActivityList].
   */
  fun getPromotedActivityList(profileId: ProfileId): DataProvider<PromotedActivityList> {
    return storyProgressController.retrieveTopicProgressListDataProvider(profileId)
      .transformAsync(GET_PROMOTED_ACTIVITY_LIST_PROVIDER_ID) {
        val promotedActivityList = computePromotedActivityList(it)
        AsyncResult.Success(promotedActivityList)
      }
  }

  private fun createTopicList(): TopicList {
    return if (loadLessonProtosFromAssets) {
      val topicIdList =
        assetRepository.loadProtoFromLocalAssets(
          assetName = "topics",
          baseMessage = TopicIdList.getDefaultInstance()
        )
      return TopicList.newBuilder().apply {
        // Only include topics currently playable in the topic list.
        addAllTopicSummary(
          topicIdList.topicIdsList.map { createTopicSummary(it) }
            .filter { it.topicPlayAvailability.availabilityCase == AVAILABLE_TO_PLAY_NOW }
        )
      }.build()
    } else loadTopicListFromJson()
  }

  private fun loadTopicListFromJson(): TopicList {
    val topicIdJsonArray = jsonAssetRetriever
      .loadJsonFromAsset("topics.json")!!
      .getJSONArray("topic_id_list")
    val topicListBuilder = TopicList.newBuilder()
    for (i in 0 until topicIdJsonArray.length()) {
      val topicSummary = createTopicSummary(topicIdJsonArray.optString(i)!!)
      // Only include topics currently playable in the topic list.
      if (topicSummary.topicPlayAvailability.availabilityCase == AVAILABLE_TO_PLAY_NOW) {
        topicListBuilder.addTopicSummary(topicSummary)
      }
    }
    return topicListBuilder.build()
  }

  private fun computeComingSoonTopicList(): ComingSoonTopicList {
    val topicIdJsonArray = jsonAssetRetriever
      .loadJsonFromAsset("topics.json")!!
      .getJSONArray("topic_id_list")
    val comingSoonTopicListBuilder = ComingSoonTopicList.newBuilder()
    for (i in 0 until topicIdJsonArray.length()) {
      val upcomingTopicSummary = createUpcomingTopicSummary(topicIdJsonArray.optString(i)!!)
      // Only include topics currently not playable in the upcoming topic list.
      if (upcomingTopicSummary.topicPlayAvailability.availabilityCase
        == AVAILABLE_TO_PLAY_IN_FUTURE
      ) {
        comingSoonTopicListBuilder.addUpcomingTopic(upcomingTopicSummary)
      }
    }
    return comingSoonTopicListBuilder.build()
  }

  private fun createTopicSummary(topicId: String): TopicSummary {
    return if (loadLessonProtosFromAssets) {
      val topicRecord =
        assetRepository.loadProtoFromLocalAssets(
          assetName = topicId,
          baseMessage = TopicRecord.getDefaultInstance()
        )
      val storyRecords = topicRecord.canonicalStoryIdsList.map {
        assetRepository.loadProtoFromLocalAssets(
          assetName = it,
          baseMessage = StoryRecord.getDefaultInstance()
        )
      }
      TopicSummary.newBuilder().apply {
        this.topicId = topicId
        name = topicRecord.name
        totalChapterCount = storyRecords.map { it.chaptersList.size }.sum()
        topicThumbnail = topicRecord.topicThumbnail
        topicPlayAvailability = if (topicRecord.isPublished) {
          TopicPlayAvailability.newBuilder().setAvailableToPlayNow(true).build()
        } else {
          TopicPlayAvailability.newBuilder().setAvailableToPlayInFuture(true).build()
        }
      }.build()
    } else {
      createTopicSummaryFromJson(topicId, jsonAssetRetriever.loadJsonFromAsset("$topicId.json")!!)
    }
  }

  private fun createUpcomingTopicSummary(topicId: String): UpcomingTopic {
    val topicJson =
      jsonAssetRetriever.loadJsonFromAsset("$topicId.json")!!
    return createUpcomingTopicSummaryFromJson(topicId, topicJson)
  }

  private fun createTopicSummaryFromJson(topicId: String, jsonObject: JSONObject): TopicSummary {
    var totalChapterCount = 0
    val storyData = jsonObject.getJSONArray("canonical_story_dicts")
    for (i in 0 until storyData.length()) {
      totalChapterCount += storyData
        .getJSONObject(i)
        .getJSONArray("node_titles")
        .length()
    }
    val topicPlayAvailability = if (jsonObject.getBoolean("published")) {
      TopicPlayAvailability.newBuilder().setAvailableToPlayNow(true).build()
    } else {
      TopicPlayAvailability.newBuilder().setAvailableToPlayInFuture(true).build()
    }
    return TopicSummary.newBuilder()
      .setTopicId(topicId)
      .setName(jsonObject.getStringFromObject("topic_name"))
      .setVersion(jsonObject.optInt("version"))
      .setTotalChapterCount(totalChapterCount)
      .setTopicThumbnail(createTopicThumbnailFromJson(jsonObject))
      .setTopicPlayAvailability(topicPlayAvailability)
      .build()
  }

  private fun createUpcomingTopicSummaryFromJson(
    topicId: String,
    jsonObject: JSONObject
  ): UpcomingTopic {
    var totalChapterCount = 0
    val storyData = jsonObject.getJSONArray("canonical_story_dicts")
    for (i in 0 until storyData.length()) {
      totalChapterCount += storyData
        .getJSONObject(i)
        .getJSONArray("node_titles")
        .length()
    }
    val topicPlayAvailability = if (jsonObject.getBoolean("published")) {
      TopicPlayAvailability.newBuilder().setAvailableToPlayNow(true).build()
    } else {
      TopicPlayAvailability.newBuilder().setAvailableToPlayInFuture(true).build()
    }

    return UpcomingTopic.newBuilder().setTopicId(topicId)
      .setName(jsonObject.getStringFromObject("topic_name"))
      .setVersion(jsonObject.optInt("version"))
      .setTopicPlayAvailability(topicPlayAvailability)
      .setLessonThumbnail(createTopicThumbnailFromJson(jsonObject))
      .build()
  }

  private fun computePromotedActivityList(
    topicProgressList: List<TopicProgress>
  ): PromotedActivityList {
    val promotedActivityListBuilder = PromotedActivityList.newBuilder()
    promotedActivityListBuilder.promotedStoryList = computePromotedStoryList(topicProgressList)
    if (promotedActivityListBuilder.promotedStoryList.getTotalPromotedStoryCount() == 0) {
      promotedActivityListBuilder.comingSoonTopicList = computeComingSoonTopicList()
    }
    return promotedActivityListBuilder.build()
  }

  private fun computePromotedStoryList(topicProgressList: List<TopicProgress>): PromotedStoryList {
    return PromotedStoryList.newBuilder()
      .addAllRecentlyPlayedStory(computePlayedStories(topicProgressList) { it < ONE_WEEK_IN_DAYS })
      .addAllOlderPlayedStory(computePlayedStories(topicProgressList) { it > ONE_WEEK_IN_DAYS })
      .addAllSuggestedStory(computeSuggestedStories(topicProgressList))
      .build()
  }

  private fun PromotedStoryList.getTotalPromotedStoryCount(): Int {
    return recentlyPlayedStoryList.size + olderPlayedStoryList.size + suggestedStoryList.size
  }

  private fun computePlayedStories(
    topicProgressList: List<TopicProgress>,
    completionTimeFilter: (Long) -> Boolean
  ): List<PromotedStory> {
    val playedPromotedStoryList = mutableListOf<PromotedStory>()
    val sortedTopicProgressList =
      topicProgressList.sortedByDescending { topicProgress ->
        val topicProgressStories = topicProgress.storyProgressMap.values
        val topicProgressChapters = topicProgressStories.flatMap { storyProgress ->
          storyProgress.chapterProgressMap.values
        }
        val topicProgressLastPlayedTimes =
          topicProgressChapters.map(ChapterProgress::getLastPlayedTimestamp)
        topicProgressLastPlayedTimes.maxOrNull()
      }

    sortedTopicProgressList.forEach { topicProgress ->
      val topic = topicController.retrieveTopic(topicProgress.topicId)

      val isTopicConsideredCompleted = topicHasAtLeastOneStoryCompleted(topicProgress)

      topicProgress.storyProgressMap.values.forEach { storyProgress ->
        val storyId = storyProgress.storyId
        val story = topicController.retrieveStory(topic.topicId, storyId)

        val completedChapterProgressList = getCompletedChapterProgressList(storyProgress)
        val latestCompletedChapterProgress: ChapterProgress? =
          completedChapterProgressList.firstOrNull()

        val startedChapterProgressList = getStartedChapterProgressList(storyProgress)
        val latestStartedChapterProgress: ChapterProgress? =
          startedChapterProgressList.firstOrNull()

        when {
          latestStartedChapterProgress != null -> {
            val numberOfDaysPassed = latestStartedChapterProgress.getNumberOfDaysPassed()
            if (completionTimeFilter(numberOfDaysPassed)) {
              createOngoingStoryListBasedOnRecentlyPlayed(
                storyId,
                story,
                latestStartedChapterProgress,
                completedChapterProgressList,
                topic,
                isTopicConsideredCompleted,
                storyProgress.chapterProgressMap
              )?.let { promotedStory ->
                playedPromotedStoryList.add(promotedStory)
              }
            }
          }
          // Compute the ongoing story list for stories that are not fully completed yet.
          latestCompletedChapterProgress != null &&
            latestCompletedChapterProgress.explorationId !=
            story.chapterList.last().explorationId -> {
            val numberOfDaysPassed = latestCompletedChapterProgress.getNumberOfDaysPassed()
            if (completionTimeFilter(numberOfDaysPassed)) {
              createOngoingStoryListBasedOnMostRecentlyCompleted(
                storyId,
                story,
                latestCompletedChapterProgress,
                completedChapterProgressList,
                topic,
                isTopicConsideredCompleted,
                storyProgress.chapterProgressMap
              )?.let { promotedStory ->
                playedPromotedStoryList.add(promotedStory)
              }
            }
          }
        }
      }
    }
    return playedPromotedStoryList
  }

  private fun checkIfStoryIsCompleted(
    storyProgress: StoryProgress,
    storySummary: StorySummary
  ): Boolean {
    val completedChapterProgressList = getCompletedChapterProgressList(storyProgress)
    val lastChapterSummary = storySummary.chapterList.lastOrNull()
    return completedChapterProgressList.find { chapterProgress ->
      chapterProgress.explorationId == lastChapterSummary?.explorationId
    } != null
  }

  private fun getStartedChapterProgressList(storyProgress: StoryProgress): List<ChapterProgress> {
    val startedNotCompletedChapterList =
      getSortedChapterProgressListByPlayState(
        storyProgress, playState = ChapterPlayState.STARTED_NOT_COMPLETED
      )
    val inProgressSavedChapterList =
      getSortedChapterProgressListByPlayState(
        storyProgress, playState = ChapterPlayState.IN_PROGRESS_SAVED
      )
    val inProgressNotSavedChapterList =
      getSortedChapterProgressListByPlayState(
        storyProgress, playState = ChapterPlayState.IN_PROGRESS_NOT_SAVED
      )
    return (
      startedNotCompletedChapterList +
        inProgressSavedChapterList +
        inProgressNotSavedChapterList
      )
      .sortedByDescending { chapterProgress -> chapterProgress.lastPlayedTimestamp }
  }

  private fun getCompletedChapterProgressList(storyProgress: StoryProgress): List<ChapterProgress> =
    getSortedChapterProgressListByPlayState(
      storyProgress, playState = ChapterPlayState.COMPLETED
    )

  private fun getSortedChapterProgressListByPlayState(
    storyProgress: StoryProgress,
    playState: ChapterPlayState
  ): List<ChapterProgress> {
    return storyProgress.chapterProgressMap.values
      .filter { chapterProgress -> chapterProgress.chapterPlayState == playState }
      .sortedByDescending { chapterProgress -> chapterProgress.lastPlayedTimestamp }
  }

  private fun createOngoingStoryListBasedOnRecentlyPlayed(
    storyId: String,
    story: StorySummary,
    latestStartedChapterProgress: ChapterProgress,
    completedChapterProgressList: List<ChapterProgress>,
    topic: Topic,
    isTopicConsideredCompleted: Boolean,
    chapterProgressMap: Map<String, ChapterProgress>
  ): PromotedStory? {
    val recentlyPlayerChapterSummary: ChapterSummary? =
      story.chapterList.find { chapterSummary ->
        latestStartedChapterProgress.explorationId == chapterSummary.explorationId
      }
    if (recentlyPlayerChapterSummary != null) {
      return createPromotedStory(
        storyId,
        topic,
        completedChapterProgressList.size,
        story.chapterCount,
        recentlyPlayerChapterSummary.name,
        recentlyPlayerChapterSummary.explorationId,
        isTopicConsideredCompleted,
        chapterProgressMap[recentlyPlayerChapterSummary.explorationId]
      )
    }
    return null
  }

  private fun createOngoingStoryListBasedOnMostRecentlyCompleted(
    storyId: String,
    story: StorySummary,
    latestCompletedChapterProgress: ChapterProgress,
    completedChapterProgressList: List<ChapterProgress>,
    topic: Topic,
    isTopicConsideredCompleted: Boolean,
    chapterProgressMap: Map<String, ChapterProgress>
  ): PromotedStory? {
    val lastChapterSummary: ChapterSummary? =
      story.chapterList.find { chapterSummary ->
        latestCompletedChapterProgress.explorationId == chapterSummary.explorationId
      }
    val nextChapterIndex = story.chapterList.indexOf(lastChapterSummary) + 1
    if (story.chapterList.size > nextChapterIndex) {
      val nextChapterSummary: ChapterSummary? = story.chapterList[nextChapterIndex]
      if (nextChapterSummary != null) {
        return createPromotedStory(
          storyId,
          topic,
          completedChapterProgressList.size,
          story.chapterCount,
          nextChapterSummary.name,
          nextChapterSummary.explorationId,
          isTopicConsideredCompleted,
          chapterProgressMap[nextChapterSummary.explorationId]
        )
      }
    }
    return null
  }

  private fun ChapterProgress.getNumberOfDaysPassed(): Long {
    return TimeUnit.MILLISECONDS.toDays(oppiaClock.getCurrentTimeMs() - this.lastPlayedTimestamp)
  }

  // TODO(#2550): Remove hardcoded order of topics. Compute list of suggested stories from backend structures
  /**
   * Returns a list of topic IDs for which the specified topic ID expects to be completed before
   * being suggested.
   */
  private fun retrieveTopicDependencies(topicId: String): List<String> {
    // The comments describe the correct dependencies, but those might not be available until the
    // topic is introduced into the app.
    return when (topicId) {
      // TEST_TOPIC_ID_0 (depends on Fractions)
      TEST_TOPIC_ID_0 -> listOf(FRACTIONS_TOPIC_ID)
      // TEST_TOPIC_ID_1 (depends on TEST_TOPIC_ID_0,Ratios)
      TEST_TOPIC_ID_1 -> listOf(TEST_TOPIC_ID_0, RATIOS_TOPIC_ID)
      // Fractions (depends on A+S, Multiplication, Division)
      FRACTIONS_TOPIC_ID -> listOf()
      // Ratios (depends on A+S, Multiplication, Division)
      RATIOS_TOPIC_ID -> listOf()
      // Addition and Subtraction (depends on Place Values)
      // Multiplication (depends on Addition and Subtraction)
      // Division (depends on Multiplication)
      // Expressions and Equations (depends on A+S, Multiplication, Division)
      // Decimals (depends on A+S, Multiplication, Division)
      else -> listOf()
    }
  }

  /*
  * Explanation for logic:
  * We always recommend the next topic that all dependencies are completed for. If a topic with
  * prerequisites is completed out-of-order (e.g. test topic 1 below) then we assume fractions is already done.
  * In the same way, finishing test topic 2 means there's nothing else to recommend.
  *
  * Here's an example topic graph to illustrate:
  *
  *      Fractions
  *       |
  *       |
  *       V
  * Test topic 0                     Ratios
  *    \                              /
  *     \                           /
  *       -----> Test topic 1 <----
  *
  * In this example, when topic Fractions is finished, Test topic 0 will be recommended and so on.
  */
  private fun computeSuggestedStories(
    topicProgressList: List<TopicProgress>
  ): List<PromotedStory> {
    return if (loadLessonProtosFromAssets) {
      val topicIdList =
        assetRepository.loadProtoFromLocalAssets(
          assetName = "topics",
          baseMessage = TopicIdList.getDefaultInstance()
        )
      return computeSuggestedStoriesForTopicIds(topicProgressList, topicIdList.topicIdsList)
    } else computeSuggestedStoriesFromJson(topicProgressList)
  }

  private fun computeSuggestedStoriesFromJson(
    topicProgressList: List<TopicProgress>
  ): List<PromotedStory> {
    val topicIdJsonArray = jsonAssetRetriever
      .loadJsonFromAsset("topics.json")!!
      .getJSONArray("topic_id_list")
    // All topics that could potentially be recommended.
    val topicIdList =
      (0 until topicIdJsonArray.length()).map { topicIdJsonArray[it].toString() }
    return computeSuggestedStoriesForTopicIds(topicProgressList, topicIdList)
  }

  private fun computeSuggestedStoriesForTopicIds(
    topicProgressList: List<TopicProgress>,
    topicIdList: List<String>
  ): List<PromotedStory> {
    val recommendedStories = mutableListOf<PromotedStory>()
    // The list of started or completed topic IDs.
    val startedTopicIds = topicProgressList.map(TopicProgress::getTopicId)
    // The list of topic IDs that qualify for being recommended.
    val unstartedTopicIdList = topicIdList.filterNot { startedTopicIds.contains(it) }

    // A map of topic IDs to their dependencies.
    val topicDependencyMap = topicIdList.associateWith {
      retrieveTopicDependencies(it).toSet()
    }.withDefault { setOf<String>() }
    // The list of topic IDs that are considered "finished" from a recommendation perspective.
    val fullyCompletedTopicIds = topicProgressList.filter {
      topicHasAtLeastOneStoryCompleted(it)
    }.map(TopicProgress::getTopicId)
    // A set of topic IDs that can be considered topics that should not be recommended.
    val impliedFinishedTopicIds = computeImpliedCompletedDependencies(
      fullyCompletedTopicIds, topicDependencyMap
    )
    // Suggest prerequisite topic user needs to learn after completing any of the topics.
    // The order in which the topic IDs are enumerated matters, and that it should be in the order
    // of the list itself.
    for (topicId in unstartedTopicIdList) {
      // All of the topic's prerequisites can be suggested if the topic is ongoing.
      val dependentTopicIds = topicDependencyMap[topicId] ?: listOf()
      if (topicId !in impliedFinishedTopicIds &&
        impliedFinishedTopicIds.containsAll(dependentTopicIds)
      ) {
        loadRecommendedStory(topicId)?.let(recommendedStories::add)
      }
    }
    return recommendedStories
  }

  private fun topicHasAtLeastOneStoryCompleted(it: TopicProgress): Boolean {
    val topic = topicController.retrieveTopic(it.topicId)
    return it.storyProgressMap.values.any { storyProgress ->
      val story = topicController.retrieveStory(topic.topicId, storyProgress.storyId)
      return@any checkIfStoryIsCompleted(storyProgress, story)
    }
  }

  /**
   * Return the list of topic IDs that are completed or can be implied completed based on actually
   * completed topics.
   */
  private fun computeImpliedCompletedDependencies(
    fullyCompletedTopicIds: List<String>,
    topicDependencyMap: Map<String, Set<String>>
  ): Set<String> {
    // For each completed topic ID, compute the transitive closure of all of its dependencies &
    // then combine them into a single list with the actual completed topic IDs. The returned list
    // is a list of either completed or assumed completed topics which will eliminate potential
    // recommendations.
    val completedTopicIds =
      fullyCompletedTopicIds.flatMap { topicId ->
        computeTransitiveDependencyClosure(topicId, topicDependencyMap)
      } + fullyCompletedTopicIds
    return completedTopicIds.toSet()
  }

  private fun computeTransitiveDependencyClosure(
    topicId: String,
    topicDependencyMap: Map<String, Set<String>>
  ): Set<String> {
    // Compute the total list of dependent topics that must be completed before the specified topic
    // can be recommended. Note that this will cause a stack overflow if the graph has cycles.
    val directDependencies = topicDependencyMap[topicId] ?: listOf()
    val transitiveDependencies = directDependencies.flatMap { dependentId ->
      computeTransitiveDependencyClosure(
        dependentId,
        topicDependencyMap
      )
    }
    return (transitiveDependencies + directDependencies).toSet()
  }

  private fun loadRecommendedStory(topicId: String): PromotedStory? {
    return if (loadLessonProtosFromAssets) {
      val topicRecord =
        assetRepository.loadProtoFromLocalAssets(
          assetName = topicId,
          baseMessage = TopicRecord.getDefaultInstance()
        )
      if (!topicRecord.isPublished || topicRecord.canonicalStoryIdsCount == 0) {
        // Do not recommend unpublished topics, or topics without stories (which shouldn't happen).
        return null
      }
      // Only recommend the first story of unstarted topics.
      val firstStoryId = topicRecord.canonicalStoryIdsList.first()
      val storyRecord =
        assetRepository.loadProtoFromLocalAssets(
          assetName = firstStoryId,
          baseMessage = StoryRecord.getDefaultInstance()
        )
      return PromotedStory.newBuilder().apply {
        storyId = firstStoryId
        storyName = storyRecord.storyName
        this.topicId = topicId
        topicName = topicRecord.name
        completedChapterCount = 0
        totalChapterCount = storyRecord.chaptersCount
        lessonThumbnail = storyRecord.storyThumbnail
        isTopicLearned = false
        // Only populate next chapter information if there is a next chapter.
        storyRecord.chaptersList.firstOrNull()?.let {
          nextChapterName = it.title
          explorationId = it.explorationId
        }
        // ChapterPlayState will be NOT_STARTED because this function only recommends the first
        // story of un-started topics.
        chapterPlayState = ChapterPlayState.NOT_STARTED
      }.build()
    } else loadRecommendedStoryFromJson(topicId)
  }

  private fun loadRecommendedStoryFromJson(topicId: String): PromotedStory? {
    val topicJson = jsonAssetRetriever.loadJsonFromAsset("$topicId.json")
    if (topicJson!!.optString("topic_name").isNullOrEmpty()) {
      return null
    } else {
      if (!topicJson.getBoolean("published")) {
        // Do not recommend unpublished topics.
        return null
      }

      val storyData = topicJson.getJSONArray("canonical_story_dicts")
      if (storyData.length() == 0) {
        return PromotedStory.getDefaultInstance()
      }
      val totalChapterCount = storyData
        .getJSONObject(0)
        .getJSONArray("node_titles")
        .length()
      val storyId = storyData.optJSONObject(0).optString("id")
      val storySummary = topicController.retrieveStory(topicId, storyId)

      val promotedStoryBuilder = PromotedStory.newBuilder()
        .setStoryId(storyId)
        .setStoryName(storySummary.storyName)
        .setLessonThumbnail(storySummary.storyThumbnail)
        .setTopicId(topicId)
        .setTopicName(topicJson.optString("topic_name"))
        .setCompletedChapterCount(0)
        .setTotalChapterCount(totalChapterCount)
      if (storySummary.chapterList.isNotEmpty()) {
        promotedStoryBuilder.nextChapterName = storySummary.chapterList[0].name
        promotedStoryBuilder.explorationId = storySummary.chapterList[0].explorationId
        promotedStoryBuilder.chapterPlayState = ChapterPlayState.NOT_STARTED
      }
      return promotedStoryBuilder.build()
    }
  }

  private fun createPromotedStory(
    storyId: String,
    topic: Topic,
    completedChapterCount: Int,
    totalChapterCount: Int,
    nextChapterName: String?,
    explorationId: String?,
    isTopicConsideredCompleted: Boolean,
    nextChapterProgress: ChapterProgress?
  ): PromotedStory {
    val storySummary = topic.storyList.find { summary -> summary.storyId == storyId }!!
    val promotedStoryBuilder = PromotedStory.newBuilder()
      .setStoryId(storyId)
      .setStoryName(storySummary.storyName)
      .setLessonThumbnail(storySummary.storyThumbnail)
      .setTopicId(topic.topicId)
      .setTopicName(topic.name)
      .setCompletedChapterCount(completedChapterCount)
      .setTotalChapterCount(totalChapterCount)
      .setIsTopicLearned(isTopicConsideredCompleted)
    if (nextChapterName != null && explorationId != null) {
      promotedStoryBuilder.nextChapterName = nextChapterName
      promotedStoryBuilder.explorationId = explorationId
      // If the chapterProgress equals null that means the chapter has no progress associated with
      // it because it is not yet started.
      promotedStoryBuilder.chapterPlayState =
        nextChapterProgress?.chapterPlayState ?: ChapterPlayState.NOT_STARTED
    }
    return promotedStoryBuilder.build()
  }
}

internal fun createTopicThumbnailFromJson(topicJsonObject: JSONObject): LessonThumbnail {
  val topicId = topicJsonObject.optString("topic_id")
  val thumbnailBgColor = topicJsonObject.optString("thumbnail_bg_color")
  val thumbnailFilename = topicJsonObject.optString("thumbnail_filename")
  return if (thumbnailFilename.isNotNullOrEmpty() && thumbnailBgColor.isNotNullOrEmpty()) {
    LessonThumbnail.newBuilder()
      .setThumbnailFilename(thumbnailFilename)
      .setBackgroundColorRgb(Color.parseColor(thumbnailBgColor))
      .build()
  } else if (TOPIC_THUMBNAILS.containsKey(topicId)) {
    TOPIC_THUMBNAILS.getValue(topicId)
  } else {
    createDefaultTopicThumbnail()
  }
}

internal fun createTopicThumbnailFromProto(
  topicId: String,
  lessonThumbnail: LessonThumbnail
): LessonThumbnail {
  val thumbnailFilename = lessonThumbnail.thumbnailFilename
  return when {
    thumbnailFilename.isNotNullOrEmpty() -> lessonThumbnail
    TOPIC_THUMBNAILS.containsKey(topicId) -> TOPIC_THUMBNAILS.getValue(topicId)
    else -> createDefaultTopicThumbnail()
  }
}

internal fun createDefaultTopicThumbnail(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.CHILD_WITH_FRACTIONS_HOMEWORK)
    .setBackgroundColorRgb(Color.parseColor(TOPIC_BG_COLOR))
    .build()
}

internal fun createTopicThumbnail0(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.CHILD_WITH_FRACTIONS_HOMEWORK)
    .setBackgroundColorRgb(Color.parseColor(TOPIC_BG_COLOR))
    .build()
}

internal fun createTopicThumbnail1(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.DUCK_AND_CHICKEN)
    .setBackgroundColorRgb(Color.parseColor(TOPIC_BG_COLOR))
    .build()
}

internal fun createTopicThumbnail2(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.ADDING_AND_SUBTRACTING_FRACTIONS)
    .setBackgroundColorRgb(Color.parseColor(TOPIC_BG_COLOR))
    .build()
}

internal fun createTopicThumbnail3(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.BAKER)
    .setBackgroundColorRgb(Color.parseColor(TOPIC_BG_COLOR))
    .build()
}

internal fun createDefaultStoryThumbnail(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.CHILD_WITH_FRACTIONS_HOMEWORK)
    .setBackgroundColorRgb(0xa5d3ec)
    .build()
}

internal fun createStoryThumbnail0(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.DUCK_AND_CHICKEN)
    .setBackgroundColorRgb(0xa5d3ec)
    .build()
}

internal fun createStoryThumbnail1(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.CHILD_WITH_FRACTIONS_HOMEWORK)
    .setBackgroundColorRgb(0xd3a5ec)
    .build()
}

internal fun createStoryThumbnail2(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.CHILD_WITH_CUPCAKES)
    .setBackgroundColorRgb(0xa5ecd3)
    .build()
}

internal fun createStoryThumbnail3(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.BAKER)
    .setBackgroundColorRgb(0xa5a2d3)
    .build()
}

internal fun createStoryThumbnail4(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.COMPARING_FRACTIONS)
    .setBackgroundColorRgb(0xf2ecd3)
    .build()
}

internal fun createStoryThumbnail5(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.DERIVE_A_RATIO)
    .setBackgroundColorRgb(0xf2ec63)
    .build()
}

internal fun createChapterThumbnail0(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.CHILD_WITH_FRACTIONS_HOMEWORK)
    .setBackgroundColorRgb(Color.parseColor(CHAPTER_BG_COLOR_1))
    .build()
}

internal fun createChapterThumbnail1(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.DUCK_AND_CHICKEN)
    .setBackgroundColorRgb(Color.parseColor(CHAPTER_BG_COLOR_2))
    .build()
}

internal fun createChapterThumbnail2(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.PERSON_WITH_PIE_CHART)
    .setBackgroundColorRgb(Color.parseColor(CHAPTER_BG_COLOR_3))
    .build()
}

internal fun createChapterThumbnail3(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.CHILD_WITH_CUPCAKES)
    .setBackgroundColorRgb(Color.parseColor(CHAPTER_BG_COLOR_4))
    .build()
}

internal fun createChapterThumbnail4(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.BAKER)
    .setBackgroundColorRgb(Color.parseColor(CHAPTER_BG_COLOR_1))
    .build()
}

internal fun createChapterThumbnail5(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.DUCK_AND_CHICKEN)
    .setBackgroundColorRgb(Color.parseColor(CHAPTER_BG_COLOR_2))
    .build()
}

internal fun createChapterThumbnail6(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.BAKER)
    .setBackgroundColorRgb(Color.parseColor(CHAPTER_BG_COLOR_3))
    .build()
}

internal fun createChapterThumbnail7(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.PERSON_WITH_PIE_CHART)
    .setBackgroundColorRgb(Color.parseColor(CHAPTER_BG_COLOR_4))
    .build()
}

internal fun createChapterThumbnail8(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.DUCK_AND_CHICKEN)
    .setBackgroundColorRgb(Color.parseColor(CHAPTER_BG_COLOR_1))
    .build()
}

internal fun createChapterThumbnail9(): LessonThumbnail {
  return LessonThumbnail.newBuilder()
    .setThumbnailGraphic(LessonThumbnailGraphic.CHILD_WITH_FRACTIONS_HOMEWORK)
    .setBackgroundColorRgb(Color.parseColor(CHAPTER_BG_COLOR_2))
    .build()
}

private fun String?.isNullOrEmpty(): Boolean = this == null || this.isEmpty() || this == "null"

private fun String?.isNotNullOrEmpty(): Boolean = !this.isNullOrEmpty()
