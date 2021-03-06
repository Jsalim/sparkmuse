package net.sparkmuse.mail;

import play.test.UnitTest;
import org.junit.Test;
import static org.hamcrest.Matchers.*;
import org.hamcrest.MatcherAssert;
import org.apache.commons.mail.Email;
import com.google.inject.internal.Maps;
import com.google.common.base.Functions;
import com.google.common.collect.Iterables;

import java.util.Map;

/**
 * @author neteller
 * @created: Feb 5, 2011
 */
public class PlayMailServiceTest extends UnitTest {

  @Test
  public void shouldPrepareEmail() {
    PlaySendMailService service = new PlaySendMailService(null);
    final EmailTemplate template = new EmailTemplate() {
      public String getToEmail() {
        return "peter.pan@xyz.com";
      }

      public String getSubject() {
        return "A Subject Worth Talking About";
      }

      public String getTemplate() {
        return "Mail/TestTemplate.html";
      }

      public Map<String, Object> getTemplateArguments() {
        final Map<String, Object> args = Maps.newHashMap();
        return args;
      }

      public String getUpdateeName() {
        return "Peter Pan";
      }
    };
    Email email = service.prepareEmail(template);

    MatcherAssert.assertThat(
        (Iterable<String>) Iterables.transform(email.getToAddresses(), Functions.toStringFunction()),
        contains(containsString(template.getToEmail()))
    );
//    MatcherAssert.assertThat(
//        (Iterable<String>) Iterables.transform(email.getBccAddresses(), Functions.toStringFunction()),
//        contains(containsString("dave@sparkmuse.com"))
//    );
    MatcherAssert.assertThat(email.getFromAddress().getAddress(), equalTo("noreply@sparkmuse.com"));
    MatcherAssert.assertThat(email.getSubject(), equalTo(template.getSubject()));
  }



}
