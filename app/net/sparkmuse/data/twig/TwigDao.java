package net.sparkmuse.data.twig;

import net.sparkmuse.data.mapper.ObjectMapper;
import net.sparkmuse.data.entity.Entity;
import com.google.common.base.Function;
import com.google.appengine.api.datastore.Cursor;
import com.google.code.twig.ObjectDatastore;
import com.google.code.twig.FindCommand;
import models.SparkModel;
import org.apache.commons.lang.StringUtils;


/**
 * Created by IntelliJ IDEA.
 *
 * @author neteller
 * @created: Sep 19, 2010
 */
public class TwigDao {

  protected final ObjectMapper map;
  protected final ObjectDatastore datastore;
  protected final DatastoreService helper;
  
  public TwigDao(DatastoreService service) {
    this.map = service.getMapper();
    this.helper = service;
    this.datastore = service.getDatastore();
  }

  /**
   *
   * @param entityClass
   * @param transformation
   * @param cursor          serialized, websafe cursor; null if none
   * @param <U>
   * @return
   */
  public <U extends Entity<U>> String transformAll(Class<U> entityClass, final Function<U, U> transformation, final String cursor) {
    final FindCommand.RootFindCommand<SparkModel> find = datastore.find()
        .type(Entity.modelClassFor(entityClass));
    if (StringUtils.isNotBlank(cursor)) find.continueFrom(Cursor.fromWebSafeString(cursor));
    return helper.execute(entityClass, transformation, find);
  }

}
