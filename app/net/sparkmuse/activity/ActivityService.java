package net.sparkmuse.activity;

import net.sparkmuse.data.entity.*;
import net.sparkmuse.data.DaoProvider;
import net.sparkmuse.mail.ActivityUpdate;
import net.sparkmuse.mail.SendMailService;
import net.sparkmuse.common.CacheKeyFactory;
import net.sparkmuse.common.Cache;
import net.sparkmuse.discussion.Posts;
import play.Logger;

import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import com.google.inject.Inject;
import com.google.code.twig.ObjectDatastore;
import com.google.common.collect.Maps;
import com.google.common.collect.Lists;
import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;

/**
 * Produces activity notifications for new events.
 *
 * @author neteller
 * @created: Feb 11, 2011
 */
public class ActivityService {

  public static final String GLOBAL_ACTIVITY = "GLOBAL_ACTIVITY";

  private final DaoProvider daoProvider;
  private final Cache cache;
  private final SendMailService mailService;
  private final ObjectDatastore datastore;

  @Inject
  public ActivityService(Cache cache, SendMailService mailService, DaoProvider daoProvider, ObjectDatastore datastore) {
    this.cache = cache;
    this.mailService = mailService;
    this.daoProvider = daoProvider;
    this.datastore = datastore;
  }

  public ActivityStream getActivity(UserVO user) {
    ActivityStream globalActivity = getGlobalActivity();
    ActivityStream userActivity = ActivityStream.builder(daoProvider.getActivityDao())
        .forUser(user)
        .after(globalActivity.getOldestTime())
        .fetch(50)
        .build();
    return globalActivity.overlay(userActivity);
  }

  public ActivityStream getActivity(UserVO user, Activity.Source source) {
    return ActivityStream.builder(daoProvider.getActivityDao())
        .forUser(user)
        .in(source)
        .fetch(20)
        .build();
  }

  public ActivityStream getProfileActivity(UserVO user) {
    return ActivityStream.builder(daoProvider.getActivityDao())
        .forUser(user)
        .in(Activity.Source.LIKE)
        .in(Activity.Source.PERSONAL)
        .fetch(50)
        .build();
  }

  private ActivityStream getGlobalActivity() {
    ActivityStream everyone = cache.get(GLOBAL_ACTIVITY, ActivityStream.class);
    if (null == everyone) {
      everyone = ActivityStream.builder(daoProvider.getActivityDao()).fetch(50).build();
      cache.set(GLOBAL_ACTIVITY, everyone);
    }
    return everyone;
  }

  /**
   * Finds top 8 contributors for the last 7 days.
   *
   * @return
   */
  public List<UserVO> determineTopContributors() {
    ActivityStream stream = ActivityStream.builder(daoProvider.getActivityDao())
        .fetch(500)
        .after(new DateTime().minusDays(7))
        .build();
    List<Activity> activityList = stream.getActivities();

    final Map<UserVO, Integer> activityCount = Maps.newHashMap();
    for (Activity activity: activityList) {
      UserVO author = activity.getSummary().getUpdateAuthor();
      if (activityCount.get(author) == null) activityCount.put(author, 1);
      else activityCount.put(author, activityCount.get(author) + 1);
    }

    List<UserVO> activeUsers = Lists.newArrayList(activityCount.keySet());
    Collections.sort(activeUsers, new Comparator<UserVO>() {
      public int compare(UserVO userVO, UserVO userVO1) {
        return activityCount.get(userVO1) - activityCount.get(userVO);
      }
    });

    //filter admins
    activeUsers = Lists.newArrayList(Iterables.filter(activeUsers, new Predicate<UserVO>(){
      public boolean apply(UserVO userVO) {
        return !StringUtils.equalsIgnoreCase(userVO.getUserName(), "netmau5") &&
            !StringUtils.equalsIgnoreCase(userVO.getUserName(), "sparkmuse");
      }
    }));
    return activeUsers.size() > 8 ? activeUsers.subList(0, 8) : activeUsers;
  }

  public UserVote notify(UserVote userVote) {
    if (userVote.isNotified()) return userVote;

    if (StringUtils.equals(userVote.entityClassName, SparkVO.class.getName())) {
      final SparkVO spark = daoProvider.getSparkDao().load(SparkVO.class, userVote.entityId);
      //dont show personal upvotes

      if (null == spark) {
        Logger.error("Spark [" + userVote.entityId + "] doesn't exist but has a vote [" + userVote.getKey() + "].");
        datastore.delete(userVote); //@todo direct datastore access no goot
        return null;
      }

      UserVO sparkAuthor = spark.getAuthor();
      if (!sparkAuthor.getId().equals(userVote.authorUserId)) {
        store(Activity.newSparkVoteActivity(spark, userVote), userVote);
      }
    }
    else if (StringUtils.equals(userVote.entityClassName, Post.class.getName())) {
      Post post = daoProvider.getPostDao().load(Post.class, userVote.entityId);

      if (null == post) {
        Logger.error("Post [" + userVote.entityId + "] doesn't exist but has a vote [" + userVote.getKey() + "].");
        datastore.delete(userVote); //@todo direct datastore access no goot
        return null;
      }

      //dont show personal upvotes
      if (!post.getAuthor().getId().equals(userVote.authorUserId)) {
        SparkVO spark = getSpark(post);
        store(Activity.newPostVoteActivity(spark, post, userVote), userVote);
      }
    }

    userVote.isNotified = true;
    return userVote;
  }

  public void notify(SparkVO newSpark) {
    if (newSpark.isNotified()) return;

    store(Activity.newSparkActivity(newSpark), newSpark);
    store(Activity.newUserSparkActivity(newSpark), newSpark);
    
    newSpark.setNotified(true);
    daoProvider.getSparkDao().store(newSpark);
  }

  public void notify(Post newPost) {
    if (newPost.isNotified()) return;

    final SparkVO spark = getSpark(newPost);

    store(Activity.newPostActivity(spark, newPost), newPost);
    store(Activity.newUserPostActivity(spark, newPost), newPost);

    //if someone posted to my spark
    final UserProfile sparkAuthorProfile = getUserProfile(spark.getAuthor());
    if (!isSamePerson(newPost.getAuthor(), spark.getAuthor())) {
      store(Activity.newSparkPostActivity(spark, newPost), newPost);

      if (sparkAuthorProfile.hasEmail()) {
        Logger.debug("Sending SparkActivityUpdate to [" + sparkAuthorProfile.getEmail() + "]");
        final ActivityUpdate update = ActivityUpdate.newSparkActivityUpdate(sparkAuthorProfile, spark, newPost);
        mailService.prepareAndSendMessage(update);
      }
    }

    if (null != newPost.getInReplyToId()) {

      //if someone replied to my post
      final Post parentPost = daoProvider.getPostDao().load(Post.class, newPost.getInReplyToId());
      final UserProfile parentPostAuthorProfile = getUserProfile(parentPost.getAuthor());
      if (!isSamePerson(parentPost.getAuthor(), newPost.getAuthor()) && //a reply to my own post
          !isSamePerson(parentPost.getAuthor(), spark.getAuthor())) { //updatee is not Spark author (already got update above)
        store(Activity.newReplyPostActivity(spark, parentPost, newPost), newPost);

        if (parentPostAuthorProfile.hasEmail()) {
          Logger.debug("Sending PostActivityUpdate to [" + parentPostAuthorProfile.getEmail() + "]");
          final ActivityUpdate update = ActivityUpdate.newPostActivityUpdate(parentPostAuthorProfile, spark, newPost);
          mailService.prepareAndSendMessage(update);
        }
      }

      //person has replied to a post you replied to
      final List<Post> siblings = daoProvider.getPostDao().findSiblings(newPost);
      for (Post sibling: siblings) {
        //decide if we want to email a sibling newPost's author
        if (!isSamePerson(sibling.getAuthor(), newPost.getAuthor()) && //my own newPost
            !isSamePerson(sibling.getAuthor(), spark.getAuthor()) && //updatee is not Spark author (already got update above)
            !isSamePerson(sibling.getAuthor(), parentPost.getAuthor())) { //updatee is not root post author (already got update above)
          final UserProfile siblingPostAuthorProfile = getUserProfile(sibling.getAuthor());

          store(Activity.newSiblingReplyPostActivity(spark, sibling, newPost), newPost);

          if (siblingPostAuthorProfile.hasEmail()) {
            Logger.debug("Sending PostActivityUpdate to [" + siblingPostAuthorProfile + "]");
            final ActivityUpdate update = ActivityUpdate.newSiblingPostActivityUpdate(siblingPostAuthorProfile, spark, newPost);
            mailService.prepareAndSendMessage(update);
          }
        }
      }
    }

    newPost.setNotified(true);
    daoProvider.getPostDao().store(newPost);
  }

  private void store(Activity activity, Notifiable notifiable) {
    if (!notifiable.isNotified()) {
      daoProvider.getActivityDao().store(activity);
      cache.delete(GLOBAL_ACTIVITY);
    }
  }

  private static boolean isSamePerson(UserVO u1, UserVO u2) {
    return StringUtils.equals(u1.getUserName(), u2.getUserName());
  }

  private UserProfile getUserProfile(UserVO userVO) {
    String profileKey = CacheKeyFactory.newUserKey(userVO.getId()) + "|Profile";

    UserProfile userProfile = cache.get(profileKey, UserProfile.class);
    if (null != userProfile) return userProfile;

    userProfile = daoProvider.getUserDao().findUserProfileBy(userVO.getUserName());
    cache.set(profileKey, userProfile);

    return userProfile;
  }

  private SparkVO getSpark(Post post) {
    SparkVO spark = cache.get(CacheKeyFactory.newSparkKey(post.getSparkId()));
    if (null != spark) return spark;
    spark = daoProvider.getSparkDao().findById(post.getSparkId());
    cache.set(spark);
    return spark;
  }

  public void deleteActivitiesFor(SparkVO spark) {
    daoProvider.getActivityDao().deleteAll(Activity.Kind.SPARK, spark.getId());
  }

  public void deleteActivitiesFor(Posts posts) {
    for (Post post: posts.getAllComments()) {
      daoProvider.getActivityDao().deleteAll(Activity.Kind.POST, post.getId());
    }
  }
}
