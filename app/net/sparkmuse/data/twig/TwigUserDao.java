package net.sparkmuse.data.twig;

import net.sparkmuse.data.UserDao;
import net.sparkmuse.user.Votable;
import net.sparkmuse.user.Votables;
import net.sparkmuse.user.UserLogin;
import net.sparkmuse.data.entity.*;
import net.sparkmuse.task.IssueTaskService;
import com.google.inject.Inject;
import static com.google.appengine.api.datastore.Query.FilterOperator.*;
import com.google.appengine.api.datastore.Query;
import com.google.common.collect.Sets;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.base.Predicate;

import java.util.Set;
import java.util.Map;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Created by IntelliJ IDEA.
 *
 * @author neteller
 * @created: Sep 19, 2010
 */
public class TwigUserDao extends TwigDao implements UserDao {

  private final IssueTaskService issueTaskService;

  @Inject
  public TwigUserDao(DatastoreService service, IssueTaskService issueTaskService) {
    super(service);
    this.issueTaskService = issueTaskService;
  }

  public UserVO findOrCreateUserBy(UserLogin login) {
    final UserVO userVO = helper.only(datastore.find()
        .type(UserVO.class)
        .addFilter("authProviderUserId", EQUAL, login.getAuthProviderUserId()));

    //user never logged in before
    if (null == userVO) {
      //see if we already created one
      final UserVO adminCreatedUser = helper.only(datastore.find()
        .type(UserVO.class)
        .addFilter("userNameLowercase", EQUAL, login.getScreenName().toLowerCase()));

      //otherwise create a new guy
      if (null == adminCreatedUser) {
        final UserVO newUser = UserVO.newUser(login.getScreenName());

        newUser.updateUserDuring(login);
        final UserVO storedNewUser = helper.store(newUser);

        final UserProfile profile = UserProfile.newProfile(storedNewUser);
        helper.store(profile);

        return storedNewUser;
      }
      else {
        adminCreatedUser.updateUserDuring(login);
        return helper.update(adminCreatedUser);
      }
    }
    //user was created or invited but never logged in, same as below, just wanted it doc'd
    //repeat user login
    else {
      userVO.updateUserDuring(login);
      return helper.update(userVO);
    }
  }

  public UserVO findUserBy(Long id) {
    return helper.getUser(id);
  }

  public UserProfile findUserProfileBy(String userName) {
    final UserVO user = helper.only(datastore.find()
        .type(UserVO.class)
        .addFilter("userNameLowercase", EQUAL, userName.toLowerCase()));

    if (null == user) return null;

    return helper.only(datastore.find()
        .type(UserProfile.class)
        .ancestor(user)
    );
  }

  public UserProfile findUserProfileBy(Long userId) {
    final UserVO user = datastore.load(UserVO.class, userId);

    if (null == user) return null;

    return helper.only(datastore.find()
        .type(UserProfile.class)
        .ancestor(user)
    );
  }

  public UserApplication findUserApplicationBy(String userName) {
    List<UserApplication> userApplications = helper.all(datastore.find()
        .type(UserApplication.class)
        .addFilter("userName", EQUAL, userName.toLowerCase()));
    return CollectionUtils.size(userApplications) > 0 ? Iterables.getLast(userApplications) : null;
  }

  public List<UserProfile> getAllProfiles() {
    return helper.all(datastore.find().type(UserProfile.class));
  }

  public List<UserProfile> getPeopleProfiles() {
    return helper.all(datastore.find().type(UserProfile.class)
        .addFilter("peopleElgible", EQUAL, Boolean.TRUE));
  }

  public UserProfile createUser(String userName) {
    UserVO newUser = UserVO.newUser(userName);
    final UserVO storedNewUser = helper.store(newUser);
    final UserProfile profile = UserProfile.newProfile(storedNewUser);
    helper.store(profile);
    return profile;
  }

  public Map<Long, UserVO> findUsersBy(Set<Long> ids) {
    return helper.getUsers(ids);
  }

  public void saveApplication(UserApplication app) {
    //make sure application usernames are stored in lowercase for easy queries
    app.userName = app.userName.toLowerCase();
    datastore.store(app);
  }

  /**
   * Stores a record of the vote for the given user, upvotes the votable, stores
   * it to the datastore, and adjusts the author's reputation.
   *
   * @param votable
   * @param voter
   */
  public void vote(Votable votable, UserVO voter) {
    DatastoreUtils.associate(voter, datastore);

    final UserVote voteModel = datastore.load()
        .type(UserVote.class)
        .id(Votables.newKey(votable))
        .parent(voter)
        .now();

    //check for existing vote
    if (null == voteModel) {
      //store vote later so we can check if user has voted on whatever
      UserVote newUserVote = UserVote.newUpVote(votable, voter);
      datastore.store().instance(newUserVote).parent(voter).later();

      //record aggregate vote count on entity
      if (votable instanceof Entity) {
        votable.upVote(newUserVote, issueTaskService);
        helper.update((Entity) votable);
      }

      //adjust reputation
      final UserVO author = votable.getAuthor();
      author.setReputation(author.getReputation() + 1);
      helper.update(author);
    }
  }

  public <T extends Entity<T>> void vote(Class<T> entityClass, Long id, UserVO voter) {
    T entity = helper.load(entityClass, id);
    if (entity instanceof Votable) {
      vote((Votable) entity, voter);
    }
  }

  public Set<UserVote> findVotesFor(Set<Votable> votables, UserVO user) {
    if (CollectionUtils.size(votables) == 0) return Sets.newHashSet();

    Set<String> ids = Sets.newHashSet();
    for (Votable votable: votables) {
      ids.add(Votables.newKey(votable));
    }

    DatastoreUtils.associate(user, datastore);
    final Map<String, UserVote> voteMap = datastore.load()
        .type(UserVote.class)
        .ids(ids)
        .parent(user)
        .now();

    //filter out nulls
    final Iterable<UserVote> votes = Iterables.filter(voteMap.values(), new Predicate<UserVote>(){
      public boolean apply(UserVote userVote) {
        return null != userVote;
      }
    });

    return Sets.newHashSet(votes);
  }

  public Invitation findInvitation(String code) {
    if (StringUtils.isEmpty(code)) return null;

    return helper.only(datastore.find().type(Invitation.class)
        .addFilter("code", EQUAL, code));
  }

  public Invitation findInvitationByGroup(String groupName) {
    return helper.only(datastore.find().type(Invitation.class)
          .addFilter("groupName", EQUAL, groupName));
  }

  public void deleteVotesFor(Votable votable) {
    List<UserVote> votes = datastore.find().type(UserVote.class)
        .addFilter("entityId", Query.FilterOperator.EQUAL, votable.getId())
        .returnAll()
        .now();
    datastore.deleteAll(Lists.newArrayList(Iterables.filter(votes, UserVote.isType(votable.getClass()))));
  }
}
