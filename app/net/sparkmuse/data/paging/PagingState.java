package net.sparkmuse.data.paging;

import net.sparkmuse.data.Cacheable;
import net.sparkmuse.data.entity.UserVO;
import net.sparkmuse.common.CacheKey;
import net.sparkmuse.common.Cache;
import com.google.common.collect.Maps;
import com.google.appengine.api.datastore.Cursor;

import java.util.Map;
import java.io.Serializable;

/**
 * @author neteller
 * @created: Feb 19, 2011
 */
public class PagingState implements Cacheable, Serializable {

  private final String sessionId;
  private final Class type;
  private final String uniqueId;

  private int currentPage = 1; //always start on page one
  private boolean morePages = false;

  //cursors recorded from page transitions; to go to page 2,
  //use the last page's cursor located at index 0 (1st item)
  private Map<Integer, Cursor> cursors;

  PagingState(String sessionId, Class type, String uniqueId) {
    this.sessionId = sessionId;
    this.type = type;
    this.cursors = Maps.newHashMap();
    this.uniqueId = uniqueId;
  }

  public int currentPage() {
    return this.currentPage;
  }

  void transition(int pageModifier, boolean hasMorePages, Cursor lastCursor) {
    currentPage += pageModifier;
    if (null != lastCursor) cursors.put(currentPage, lastCursor);
    morePages = hasMorePages;
  }

  public boolean hasMorePages() {
    return morePages;
  }

  /**
   * Gives the cursor that you would continue from the given
   * page to the next.
   *
   * @param page
   * @return
   */
  public Cursor cursorFromPage(int page) {
    return cursors.get(page);
  }

  public int pageSize() {
    PagingSize pageSize = (PagingSize) this.type.getAnnotation(PagingSize.class);
    return pageSize.value();
  }

  public int cachedPages() {
    CachePages cachePages = (CachePages) this.type.getAnnotation(CachePages.class);
    return cachePages.value();
  }

  /**
   * @param newPage page we are going to
   * @return
   */
  public int calculateOffset(int newPage) {
    //any preceding pages without a cursor should be offset, they are assumed to be cached
//    int pagesWithoutPrecursor = this.currentPage();
//    for (int i = pagesWithoutPrecursor; i > 0; i--) {
//      if (null != this.cursors.get(i)) pagesWithoutPrecursor--;
//    }
//
//    return pagesWithoutPrecursor * this.pageSize();
//
    if (newPage > this.cachedPages()) {
      return this.cachedPages() * this.pageSize();
    }
    else {
      return 0;
    }
  }

  public CacheKey getKey() {
    return newKey(sessionId, type, uniqueId);
  }

  public static CacheKey<PagingState> newKey(String sessionId, Class type, String uniqueId) {
    return new CacheKey(
        PagingState.class,
        sessionId,
        type,
        uniqueId
    );
  }

  /**
   * Retrieves the PagingState from the cache.  If null, creates a new one at page 1.
   *
   * @param cache
   * @param sessionId
   * @param type
   * @param uniqueId      nullable; if there is more than one paging mechanism applied to this object type,
   *                      this is distinguishes (ie, multiple types of entity results to be paged through)
   * @return
   */
  static PagingState retrieve(Cache cache, String sessionId, Class type, String uniqueId) {
    PagingState state = cache.get(newKey(sessionId, type, uniqueId));
    if (null == state) return new PagingState(sessionId, type, uniqueId);
    else return state;
  }

  public Object getInstance() {
    return this;
  }
}
