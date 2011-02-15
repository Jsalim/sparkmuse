package net.sparkmuse.data.twig;

import net.sparkmuse.data.UserDao;
import net.sparkmuse.user.Votable;
import net.sparkmuse.user.Votables;
import net.sparkmuse.user.UserLogin;
import net.sparkmuse.data.entity.*;
import com.google.inject.Inject;
import static com.google.appengine.api.datastore.Query.FilterOperator.*;
import com.google.common.collect.Sets;
import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;

import java.util.Set;
import java.util.Map;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

/**
 * Created by IntelliJ IDEA.
 *
 * @author neteller
 * @created: Sep 19, 2010
 */
public class TwigUserDao extends TwigDao implements UserDao {

  @Inject
  public TwigUserDao(DatastoreService service) {
    super(service);
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

  public UserApplication findUserApplicationBy(String userName) {
    return helper.only(datastore.find()
        .type(UserApplication.class)
        .addFilter("userName", EQUAL, userName.toLowerCase()));
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
      datastore.store().instance(UserVote.newUpVote(votable, voter)).parent(voter).later();

      //record aggregate vote count on entity
      if (votable instanceof Entity) {
        votable.upVote();
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
      public boolean apply(UserVote voteModel) {
        return null != voteModel;
      }
    });

    return Sets.newHashSet(votes);
  }

}
