package functional;

import static com.google.appengine.api.datastore.Query.FilterOperator.*;
import com.google.common.collect.Lists;
import net.sparkmuse.user.UserFacade;
import net.sparkmuse.data.entity.UserApplication;
import net.sparkmuse.data.entity.UserVO;
import net.sparkmuse.data.entity.UserProfile;
import net.sparkmuse.data.entity.Expertise;
import net.sparkmuse.data.util.AccessLevel;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.hamcrest.Matchers.*;
import org.hamcrest.MatcherAssert;

/**
 * Created by IntelliJ IDEA.
 *
 * @author neteller
 * @created: Jul 5, 2010
 */
public class UserFacadeTest extends PluginFunctionalTest {

  private UserFacade userFacade;

  private UserVO testUser;
  private UserProfile testProfile;

  @Before
  public void setup() {
    super.setup();
    userFacade = FunctionalTestUtils.getInstance(UserFacade.class);

    //create test user
    final UserVO user = UserVO.newUser("FunctionalTest");
    testUser = datastoreService.store(user);

    //create test user's profile
    testProfile = datastoreService.store(UserProfile.newProfile(testUser));
  }

  @After
  public void tearDown() {
    userFacade = null;

    testUser = null;
    testProfile = null;

    super.tearDown();
  }

  @Test
  public void shouldSaveUserApplication() {
    UserApplication app = new UserApplication();
    app.userName = "netmau5";
    app.url = "http://blog.sparkmuse.com";
    userFacade.applyForInvitation(app);

    UserApplication model = datastore.find().type(UserApplication.class)
        .addFilter("userName", EQUAL, "netmau5")
        .now()
        .next();
    assertEquals(model.userName, "netmau5");
    datastore.delete(model);
  }

  @Test
  public void shouldUpdateProfile() {
    testProfile.setInvites(20);
    testProfile.setDisplayBio("sup!");
    testProfile.setExpertises(Lists.newArrayList(Expertise.BIG_DATA));

    userFacade.updateProfile(testProfile);

    MatcherAssert.assertThat(testProfile.getInvites(), equalTo(20));
    MatcherAssert.assertThat(testProfile.getDisplayBio(), equalTo("sup!"));
    MatcherAssert.assertThat(testProfile.getExpertises(), contains(Expertise.BIG_DATA));
  }

  @Test
  public void shouldInviteFriend() {
    //inviter
    UserProfile inviterProfile = userFacade.createUser("Dave");
    userFacade.updateUser(inviterProfile.getUser().getId(), AccessLevel.USER, 10);

    //friend
    UserApplication app = new UserApplication();
    app.userName = "Peter";
    app.email = "djafaricom@gmail.com";
    userFacade.applyForInvitation(app);

    //do invite
    int remainingInvites = userFacade.inviteFriend(inviterProfile.getUser(), app.userName);

    MatcherAssert.assertThat(remainingInvites, equalTo(9));
    UserProfile newUserProfile = userFacade.getUserProfile("Peter");
    MatcherAssert.assertThat(newUserProfile.getUser().getAccessLevel(), equalTo(AccessLevel.USER));
    MatcherAssert.assertThat(newUserProfile.getEmail(), equalTo("djafaricom@gmail.com"));
  }


}
