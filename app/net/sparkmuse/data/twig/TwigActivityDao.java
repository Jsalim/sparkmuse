package net.sparkmuse.data.twig;

import net.sparkmuse.data.ActivityDao;
import net.sparkmuse.data.entity.Activity;
import net.sparkmuse.data.entity.UserVO;
import com.google.inject.Inject;
import com.google.appengine.api.datastore.Query;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.code.twig.FindCommand;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;

/**
 * @author neteller
 * @created: Feb 12, 2011
 */
public class TwigActivityDao extends TwigDao implements ActivityDao {

  @Inject
  public TwigActivityDao(DatastoreService service) {
    super(service);
  }

  public List<Activity> findEveryone(DateTime after, int limit) {
    FindCommand.RootFindCommand<Activity> findCommand = datastore.find().type(Activity.class)
        .addFilter("population", Query.FilterOperator.EQUAL, Activity.Population.EVERYONE.toString())
        .addSort("created", Query.SortDirection.DESCENDING)
        .fetchMaximum(limit);
    if (null != after) {
      findCommand.addFilter("created", Query.FilterOperator.GREATER_THAN_OR_EQUAL, after.getMillis());
    }
    return helper.all(findCommand);
  }

  public List<Activity> findUser(UserVO user, DateTime after, int limit) {
    return helper.all(datastore.find().type(Activity.class)
        .addFilter("userId", Query.FilterOperator.EQUAL, user.getId())
        .addSort("created", Query.SortDirection.DESCENDING)
        .addFilter("created", Query.FilterOperator.GREATER_THAN_OR_EQUAL, after.getMillis())
        .fetchMaximum(limit));
  }

  public List<Activity> findUser(UserVO user, Set<Activity.Source> sources, int limit) {
    FindCommand.RootFindCommand<Activity> findCommand = datastore.find().type(Activity.class)
        .addFilter("userId", Query.FilterOperator.EQUAL, user.getId())
        .addSort("created", Query.SortDirection.DESCENDING)
        .fetchMaximum(limit);

    FindCommand.BranchFindCommand branchCommand = findCommand.branch(FindCommand.MergeOperator.OR);

    for (Activity.Source source: sources) {
      branchCommand.addChildCommand().addFilter("sources", Query.FilterOperator.EQUAL, source.toString());
    }

    return helper.all(findCommand);
  }

  public void deleteAll(Activity.Kind kind, Long contentKey) {
    List<Activity> activityList = helper.all(datastore.find().type(Activity.class)
        .addFilter("contentKey", Query.FilterOperator.EQUAL, contentKey)
        .fetchMaximum(1000));
    datastore.deleteAll(Lists.newArrayList(Iterables.filter(activityList, Activity.isKind(kind))));
  }

}
