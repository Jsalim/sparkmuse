package controllers;

import play.mvc.Router;
import play.mvc.With;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.data.validation.Valid;
import play.templates.Template;
import play.templates.TemplateLoader;

import java.util.HashMap;

import net.sparkmuse.ajax.ValidationErrorAjaxResponse;
import net.sparkmuse.ajax.RedirectAjaxResponse;
import net.sparkmuse.ajax.FragmentAjaxResponse;
import net.sparkmuse.discussion.SparkFacade;
import net.sparkmuse.discussion.LinkMetadata;
import net.sparkmuse.discussion.Posts;
import net.sparkmuse.data.entity.SparkVO;
import net.sparkmuse.data.entity.Post;
import net.sparkmuse.data.entity.UserVO;
import net.sparkmuse.data.entity.Offer;
import net.sparkmuse.user.UserVotes;
import net.sparkmuse.user.Votables;
import com.google.common.collect.Maps;
import java.util.Map;

import javax.inject.Inject;

import filters.AuthorizationFilter;

/**
 * Created by IntelliJ IDEA.
 *
 * @author neteller
 * @created: Jul 5, 2010
 */
@With(AuthorizationFilter.class)
public class Spark extends SparkmuseController {

  @Inject static SparkFacade sparkFacade;

  public static void create() {
    boolean isEditMode = false;
    render(isEditMode);
  }

  public static void edit(final Long sparkId) {
    boolean isEditMode = true;
    SparkVO spark = sparkFacade.findSparkBy(sparkId);
    renderTemplate("Spark/create.html", spark, isEditMode);
  }

  public static void submit(@Valid @Required(message="Input is required.") SparkVO spark) {
    if (Validation.hasErrors()) {
      renderJSON(new ValidationErrorAjaxResponse(validation.errorsMap()));
    }
    else {
      spark.setAuthor(Authorization.getUserFromSessionOrAuthenticate(true));
      final SparkVO savedSpark = sparkFacade.storeSpark(overlay(spark));
      final HashMap<String, Object> parameters = Maps.newHashMap();
      parameters.put("sparkId", savedSpark.getId());
      renderJSON(new RedirectAjaxResponse(Router.reverse("Spark.view", parameters).url));
    }
  }

  public static void view(final Long sparkId) {
    if (null == sparkId) Home.index();
    final SparkVO spark = sparkFacade.findSparkBy(sparkId);
    final UserVotes userVotes = userFacade.findUserVotesFor(Votables.collect(spark), Authorization.getUserFromSession());
    final Posts posts = sparkFacade.findPostsFor(spark);
    render(spark, posts, userVotes);
  }

  public static void lookupLinkMetadata(String url) {
    renderJSON(LinkMetadata.lookup(url));
  }

  public static void reply(@Valid Post post) {
    if (Validation.hasErrors()) {
      renderJSON(new ValidationErrorAjaxResponse(validation.errorsMap()));
    }

    post.setAuthor(Authorization.getUserFromSessionOrAuthenticate(true));
    sparkFacade.createPost(post);

    final Template template = TemplateLoader.load("tags/post.html");
    final Map<String, Object> args = Maps.newHashMap();
    args.put("_post", post);
    final String content = template.render(args);

    renderJSON(new FragmentAjaxResponse(content));
  }

  /**
   * Overlays an edited spark with an existing one to capture unsubmitted properties.
   *
   * @param spark
   * @return
   */
  private static SparkVO overlay(SparkVO spark) {
    if (null != spark.getId()) {
      SparkVO existingSpark = sparkFacade.findSparkBy(spark.getId());
      existingSpark.setTitle(spark.getTitle());
      existingSpark.setStage(spark.getStage());
      existingSpark.setProblem(spark.getProblem());
      existingSpark.setDisplayProblem(spark.getDisplayProblem());
      existingSpark.setSolution(spark.getSolution());
      existingSpark.setDisplaySolution(spark.getDisplaySolution());
      existingSpark.setTags(spark.getTags());
      return existingSpark;
    }
    else {
      return spark;
    }
  }
  
}
