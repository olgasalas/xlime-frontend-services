package eu.xlime.dao;

import java.util.Date;
import java.util.List;

import com.google.common.base.Optional;

import eu.xlime.bean.MediaItem;
import eu.xlime.bean.MicroPostBean;
import eu.xlime.bean.NewsArticleBean;
import eu.xlime.bean.TVProgramBean;
import eu.xlime.bean.VideoSegment;
import eu.xlime.util.score.ScoredSet;

/**
 * Interface for retrieving {@link MediaItem}s from some back-end storage 
 * (e.g. a triple-store or document store).
 * 
 * @author rdenaux
 *
 */
public interface MediaItemDao {

	/**
	 * Finds a {@link MediaItem} based on its identifying url
	 * @param url
	 * @return
	 */
	Optional<? extends MediaItem> findMediaItem(String url);

	/**
	 * Find media items by their URIs
	 * @param urls
	 * @return
	 */
	List<MediaItem> findMediaItems(List<String> urls);

	/**
	 * Perform a text search to find {@link MediaItem}s (but only return their urls).
	 * 
	 * @param text
	 * @return
	 */
	ScoredSet<String> findMediaItemUrlsByText(final QueryDao query);
	
	<T extends MediaItem> List<T> findMediaItems(Class<T> clazz, List<String> urls);

	List<NewsArticleBean> findNewsArticles(List<String> uris);

	Optional<NewsArticleBean> findNewsArticle(String url);

	List<MicroPostBean> findMicroPosts(List<String> uris);
	
	List<MicroPostBean> findMicroPostsByKeywordsFilter(List<String> keywordFilters);

	Optional<MicroPostBean> findMicroPost(String url);

	List<TVProgramBean> findTVPrograms(List<String> uris);

	Optional<TVProgramBean> findTVProgram(String url);

	Optional<VideoSegment> findVideoSegment(String uri);

	/**
	 * Returns a list of recent media item urls relative to the current time. 
	 * I.e. in this case recent means in the last <code>nMinutes</code>.
	 * 
	 * Note that if the underlying dataset has not been updated recently, this method may return an empty 
	 * list.
	 *  
	 * @param nMinutes a positive number of minutes. Used to retrieve the list of 'recent' mediaItems.
	 * @param limit a positive number which imposes a hard-coded limit on the number of results to return.
	 *  
	 * @return
	 */
	List<String> findLatestMediaItemUrls(int nMinutes, int limit);

	/**
	 * Returns a list of "recent" media items urls, where recent is relative to the newest media items of
	 * each type. I.e. if the most recent tv program is from yesterday at 18h, it finds tv programs between 18h - nMinutes.
	 * If the most recent {@link MicroPostBean} is from a week ago at 12h, it finds {@link MicroPostBean}s between 
	 * a week ago at 12h - nMinutes, etc.
	 * 
	 * Note that, as long as the underlying dataset contains {@link MediaItem}s (even if they are from a long while ago),
	 * this method should return a list of {@link MediaItem}s.
	 * 
	 * @param nMinutes
	 * @param limit
	 * @return
	 */
	List<String> findMostRecentMediaItemUrls(int nMinutes, int limit);
	
	/**
	 * Returns <code>true</code> if the underlying dataset contains {@link MediaItem}s after the 
	 * specified timestampFrom
	 *  
	 * @param timestampFrom
	 * @return
	 */
	boolean hasMediaItemsAfter(long timestampFrom);
	
	/**
	 * Returns a list of all the media item urls available to this {@link MediaItemDao}. 
	 * Note that this may be a huge number, depending on the back-end being used. So,
	 * you should be careful when calling this method.
	 * 
	 * @param limit
	 * @return
	 */
	List<String> findAllMediaItemUrls(int limit);
	
	List<String> findMediaItemsByDate(long dateFrom, long dateTo,
			int limit);

	List<String> findMediaItemsBefore(Date date, int limit);
	
	
}