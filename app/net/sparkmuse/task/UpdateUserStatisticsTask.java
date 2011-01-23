package net.sparkmuse.task;

import net.sparkmuse.common.Cache;
import net.sparkmuse.data.twig.BatchDatastoreService;
import net.sparkmuse.data.entity.UserVO;
import net.sparkmuse.data.entity.Post;
import net.sparkmuse.data.entity.SparkVO;
import com.google.inject.Inject;
import com.google.code.twig.ObjectDatastore;
import com.google.code.twig.FindCommand;
import com.google.appengine.api.datastore.Query;
import play.Logger;

/**
 * @author neteller
 * @created: Jan 22, 2011
 */
public class UpdateUserStatisticsTask extends Task<UserVO> {

  private final ObjectDatastore datastore;

  @Inject
  public UpdateUserStatisticsTask(Cache cache, BatchDatastoreService batchService, ObjectDatastore datastore) {
    super(cache, batchService, datastore);
    this.datastore = datastore;
  }

  protected String getTaskName() {
    return "Update User Statistics Task";
  }

  protected FindCommand.RootFindCommand<UserVO> find(boolean isNew) {
    return datastore.find().type(UserVO.class).fetchNextBy(200);
  }

  protected UserVO transform(UserVO userVO) {
    final Integer posts = datastore.find().type(Post.class)
        .addFilter("authorUserId", Query.FilterOperator.EQUAL, userVO.getId())
        .returnCount()
        .now();
    Logger.debug("Found [" + posts + "] posts for user [" + userVO.getUserName() + "]");
    final Integer sparks = datastore.find().type(SparkVO.class)
        .addFilter("authorUserId", Query.FilterOperator.EQUAL, userVO.getId())
        .returnCount()
        .now();
    
    if (userVO.getSparks() != sparks) {
      Logger.error("User [" + userVO + "] reported a spark count of [" + userVO.getSparks() + "] but had [" + sparks + "] sparks.");
    }
    if (userVO.getPosts() != posts) {
      Logger.error("User [" + userVO + "] reported a post count of [" + userVO.getPosts() + "] but had [" + posts + "] posts.");
    }

    userVO.setSparks(sparks);
    userVO.setPosts(posts);

    return userVO;
  }

}
