package eu.xlime.dao.mediaitem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;

import eu.xlime.bean.MediaItem;
import eu.xlime.bean.MicroPostBean;
import eu.xlime.bean.NewsArticleBean;
import eu.xlime.bean.TVProgramBean;
import eu.xlime.bean.VideoSegment;
import eu.xlime.dao.MediaItemDao;
import eu.xlime.dao.QueryDao;
import eu.xlime.util.CacheFactory;
import eu.xlime.util.score.ScoredSet;
import eu.xlime.util.score.ScoredSetImpl;

/**
 * Wraps a {@link MediaItemDao} and introduces caching of the results.
 * 
 * @author rdenaux
 *
 */
public class CachingMediaItemDao extends AbstractMediaItemDao {

	private static final Logger log = LoggerFactory.getLogger(CachingMediaItemDao.class);

	private static Cache<String, NewsArticleBean> newsCache = CacheFactory.instance.buildCache("newsCache");
	private static Cache<String, MicroPostBean> microPostCache = CacheFactory.instance.buildCache("microPostCache");
	private static Cache<String, TVProgramBean> tvCache = CacheFactory.instance.buildCache("tvCache");
//	private static Cache<String, String> microPostPublisherLabelCache = CacheFactory.instance.buildCache("microPostPublisherLabelCache");	
//	private static Cache<String, String> microPostCreatorLabelCache = CacheFactory.instance.buildCache("microPostCreatorLabelCache");		
	private static Cache<String, ScoredSet<String>> searchStringCache = CacheFactory.instance.buildCache("searchStringCache");

	private final MediaItemDao delegate; 
	
	public CachingMediaItemDao(MediaItemDao delegate) {
		super();
		this.delegate = delegate;
	}

	@Override
	public List<NewsArticleBean> findNewsArticles(List<String> uris) {
		Map<String, NewsArticleBean> cached = newsCache.getAllPresent(uris);
		List<String> toFind = new ArrayList(uris);
		toFind.removeAll(cached.keySet());
		
		Map<String, NewsArticleBean> nonCached = asUrlMap(delegate.findNewsArticles(toFind));
		newsCache.putAll(nonCached);
		
		log.debug(String.format("Found %s cached and %s uncached newsArticles", cached.size(), nonCached.size()));
		
		return ImmutableList.<NewsArticleBean>builder()
				.addAll(cleanMediaItems(cached.values())).addAll(nonCached.values())
				.build();
	}

	@Override
	public ScoredSet<String> findMediaItemUrlsByText(final QueryDao query) {
		Callable<? extends ScoredSet<String>> valueLoader = new Callable<ScoredSet<String>>() {
			@Override
			public ScoredSet<String> call() throws Exception {
				return delegate.findMediaItemUrlsByText(query);
			}
		};

		try {
			return searchStringCache.get(query.getQuery(), valueLoader);
		} catch (ExecutionException e) {
			log.warn("Error loading searchString result for " + query.getQuery(), e);
			return ScoredSetImpl.empty();
		}
	}
	
	private <T extends MediaItem> Map<String, T> asUrlMap(
			List<T> mediaItems) {
		Map<String, T> result = new HashMap<String, T>();
		for (T mi: mediaItems) {
			result.put(mi.getUrl(), mi);
		}
		return result;
	}

	@Override
	public List<MicroPostBean> findMicroPosts(List<String> uris) {
		Map<String, MicroPostBean> cached = microPostCache.getAllPresent(uris);
		List<String> toFind = new ArrayList(uris);
		toFind.removeAll(cached.keySet());
		
		Map<String, MicroPostBean> nonCached = asUrlMap(delegate.findMicroPosts(toFind));
		microPostCache.putAll(nonCached);
		
		log.debug(String.format("Found %s cached and %s uncached microPosts", cached.size(), nonCached.size()));
		
		return ImmutableList.<MicroPostBean>builder()
				.addAll(cleanMediaItems(cached.values())).addAll(nonCached.values())
				.build();
	}

	@Override
	public List<MicroPostBean> findMicroPostsByKeywordsFilter(
			List<String> keywordFilters) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<TVProgramBean> findTVPrograms(List<String> uris) {
		Map<String, TVProgramBean> cached = tvCache.getAllPresent(uris);
		List<String> toFind = new ArrayList(uris);
		toFind.removeAll(cached.keySet());
		
		Map<String, TVProgramBean> nonCached = asUrlMap(delegate.findTVPrograms(toFind));
		tvCache.putAll(nonCached);
		
		log.debug(String.format("Found %s cached and %s uncached tvPrograms", cached.size(), nonCached.size()));
		
		return ImmutableList.<TVProgramBean>builder()
				.addAll(cleanMediaItems(cached.values()))
				.addAll(nonCached.values()) //tv programs also need cleaning 
				.build();
	}

	@Override
	public Optional<VideoSegment> findVideoSegment(String uri) {
		return delegate.findVideoSegment(uri);
	}

	@Override
	public List<String> findAllMediaItemUrls(int limit) {
		return delegate.findAllMediaItemUrls(limit);
	}

	@Override
	public List<String> findMediaItemsByDate(long dateFrom, long dateTo,
			int limit) {
		return delegate.findMediaItemsByDate(dateFrom, dateTo, limit);
	}

	@Override
	public List<String> findMostRecentMediaItemUrls(int nMinutes, int limit) {
		return delegate.findMostRecentMediaItemUrls(nMinutes, limit);
	}

	@Override
	public List<String> findMediaItemsBefore(Date date, int limit) {
		return findMediaItemsBefore(date, limit);
	}

	@Override
	public boolean hasMediaItemsAfter(long timestampFrom) {
		return delegate.hasMediaItemsAfter(timestampFrom);
	}

	
}
