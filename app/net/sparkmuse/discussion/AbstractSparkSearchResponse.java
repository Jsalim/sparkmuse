package net.sparkmuse.discussion;

import net.sparkmuse.data.entity.SparkVO;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author neteller
 * @created: Nov 25, 2010
 */
public class AbstractSparkSearchResponse {

  private final List<SparkVO> sparks;
  private SparkSearchRequest.Filter filter;

  public AbstractSparkSearchResponse(final List<SparkVO> sparks, final SparkSearchRequest.Filter filter) {
    this.sparks = sparks;
    this.filter = filter;
  }

  public List<SparkVO> getSparks() {
    return sparks;
  }

  public SparkSearchRequest.Filter getFilter() {
    return filter;
  }
  
}