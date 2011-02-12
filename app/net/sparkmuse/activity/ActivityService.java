package net.sparkmuse.activity;

import net.sparkmuse.data.entity.*;
import net.sparkmuse.data.DaoProvider;
import net.sparkmuse.mail.ActivityUpdate;
import net.sparkmuse.mail.MailService;
import net.sparkmuse.common.CacheKeyFactory;
import net.sparkmuse.common.Cache;
import play.Logger;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import com.google.inject.Inject;

/**
 * Produces activity notifications for new events.
 *
 * @author neteller
 * @created: Feb 11, 2011
 */
public class ActivityService {

  private final DaoProvider daoProvider;
  private final Cache cache;
  private final MailService mailService;

  @Inject
  public ActivityService(Cache cache, MailService mailService, DaoProvider daoProvider) {
    this.cache = cache;
    this.mailService = mailService;
    this.daoProvider = daoProvider;
  }

  public void notify(SparkVO newSpark) {
    Logger.warn("Spark activity updates not implemented");
  }

  public void notify(Post newPost) {
    if (newPost.isNotified()) return;

    final SparkVO spark = getSpark(newPost);

    store(Activity.newPostActivity(spark, newPost), newPost);

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
    daoProvider.getCrudDao().store(newPost);
  }

  private void store(Activity activity, Notifiable notifiable) {
    if (!notifiable.isNotified()) {
      daoProvider.getCrudDao().store(activity);
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

}