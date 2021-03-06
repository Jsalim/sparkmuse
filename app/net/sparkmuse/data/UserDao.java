package net.sparkmuse.data;

import net.sparkmuse.data.entity.*;
import net.sparkmuse.user.Votable;
import net.sparkmuse.user.UserLogin;

import java.util.Set;
import java.util.Map;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author neteller
 * @created: Jul 5, 2010
 */
public interface UserDao extends CrudDao {
  UserVO findOrCreateUserBy(UserLogin login);

  /**
   * Finds a user in the cache.  If not present, the db is queried and the cache is updated.
   *
   * @param id
   * @return
   */
  UserVO findUserBy(Long id);

  UserProfile findUserProfileBy(String userName);
  UserProfile findUserProfileBy(Long userId);

  UserApplication findUserApplicationBy(String userName);

  List<UserProfile> getAllProfiles();

  List<UserProfile> getPeopleProfiles();

  UserProfile createUser(String userName);

  Map<Long, UserVO> findUsersBy(Set<Long> ids);
  void saveApplication(UserApplication userApplication);

  /**
   * Stores a record of the vote for the given user, upvotes the votable, stores
   * it to the datastore, and adjusts the author's reputation.
   *
   * @param votable
   * @param voter
   */
  void vote(Votable votable, UserVO voter);
  <T extends Entity<T>> void vote(Class<T> entityClass, Long id, UserVO voter);
  Set<UserVote> findVotesFor(Set<Votable> votables, UserVO user);
  Invitation findInvitation(String code);
  Invitation findInvitationByGroup(String groupName);

  <T extends Entity<T>> T update(T entity);

  void deleteVotesFor(Votable votable);
}
