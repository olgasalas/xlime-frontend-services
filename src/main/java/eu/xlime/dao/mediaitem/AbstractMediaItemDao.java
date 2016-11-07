package eu.xlime.dao.mediaitem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import eu.xlime.bean.CustomTVInfo;
import eu.xlime.bean.MediaItem;
import eu.xlime.bean.MicroPostBean;
import eu.xlime.bean.NewsArticleBean;
import eu.xlime.bean.TVProgramBean;
import eu.xlime.bean.UIDate;
import eu.xlime.bean.ZattooCustomTVInfo;
import eu.xlime.dao.MediaItemDao;
import eu.xlime.dao.QueryDao;
import eu.xlime.datasum.CachedDatasetSummaryFactory;
import eu.xlime.datasum.DatasetSummaryFactory;
import eu.xlime.datasum.bean.DatasetSummary;
import eu.xlime.util.ListUtil;
import eu.xlime.util.ResourceTypeResolver;
import eu.xlime.util.score.ScoredSet;

/**
 * Provides common functionality for implementing {@link MediaItemDao}s such as resolving the type of 
 * {@link MediaItem}s and implementing convenience methods which can be implemented by combining other
 * public methods in the interface (e.g. finding single mediaItems by using the variants for finding 
 * lists of mediaItems).
 * 
 * @author rdenaux
 *
 */
public abstract class AbstractMediaItemDao implements MediaItemDao {

	private static final Logger log = LoggerFactory.getLogger(AbstractMediaItemDao.class);
	private static final ResourceTypeResolver typeResolver = new ResourceTypeResolver();
	protected static final DatasetSummaryFactory dsSummaFactory = CachedDatasetSummaryFactory.instance;
	
	
	/* (non-Javadoc)
	 * @see eu.xlime.dao.MediaItemDao#findMediaItem(java.lang.String)
	 */
	@Override
	public Optional<? extends MediaItem> findMediaItem(final String url) {
		if (typeResolver.isNewsArticle(url))
			return findNewsArticle(url);
		else if (typeResolver.isMicroPost(url))
			return findMicroPost(url);
		else if (typeResolver.isTVProgram(url))
			return findTVProgram(url);
		else throw new RuntimeException("Cannot map url to a known xLiMe media-item type " + url);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T extends MediaItem> List<T> findMediaItems(Class<T> clazz,
			List<String> urls) {
		if (NewsArticleBean.class.equals(clazz)) return (List<T>)findNewsArticles(filterNews(urls)); 
		if (MicroPostBean.class.equals(clazz)) return (List<T>)findMicroPosts(filterMicroPosts(urls));
		if (TVProgramBean.class.equals(clazz)) return (List<T>)findTVPrograms(filterTV(urls));
		log.warn("Unsupported mediaItem " + clazz);
		return ImmutableList.of();
	}


	/* (non-Javadoc)
	 * @see eu.xlime.dao.MediaItemDao#findMediaItems(java.util.List)
	 */
	@Override
	public List<MediaItem> findMediaItems(final List<String> urls) {
		/* TODO: remove weaving. Ideally we should output result in the same input order, 
		 * but for now this helps us to shows outputs balanced for the different media types, 
		 * especially when only showing the topN results
		 */
		return new ListUtil().weave(findNewsArticles(filterNews(urls)),
				findMicroPosts(filterMicroPosts(urls)),
				findTVPrograms(filterTV(urls)));
	}

	@Override
	public final Optional<TVProgramBean> findTVProgram(String url) {
		List<TVProgramBean> result = findTVPrograms(ImmutableList.of(url));
		if (result.isEmpty()) return Optional.absent();
		if (result.size() > 1) log.debug("Found multiple beans?");

		return Optional.of(result.get(0));	
	}

	@Override
	public final Optional<NewsArticleBean> findNewsArticle(String url) {
		List<NewsArticleBean> result = findNewsArticles(ImmutableList.of(url));
		if (result.isEmpty()) return Optional.absent();
		if (result.size() > 1) log.debug("Found multiple beans?");

		return Optional.of(result.get(0));
	}
	
	@Override
	public final Optional<MicroPostBean> findMicroPost(String url) {
		List<MicroPostBean> result = findMicroPosts(ImmutableList.of(url));
		if (result.isEmpty()) return Optional.absent();
		if (result.size() > 1) log.debug("Found multiple beans?");

		return Optional.of(result.get(0));
	}

	/* (non-Javadoc)
	 * @see eu.xlime.dao.MediaItemDao#findLatestMediaItemUrls(int, int)
	 */
	@Override
	public List<String> findLatestMediaItemUrls(int nMinutes, int limit) {
		Date now = new Date();
		long dateFrom = now.getTime() - (nMinutes * 60 * 1000);
		long dateTo = now.getTime();
		List<String> result = ImmutableList.of();
		if (hasMediaItemsAfter(dateFrom)) {
			result = findMediaItemsByDate(dateFrom, dateTo, limit);
			log.debug(String.format("Found %s media items between %s and %s", result.size(), now, "" + nMinutes + " minutes ago"));
		}
		if (result.isEmpty()) {
			//instead of returning an empty result, return the latest of each type
			result = findMostRecentMediaItemUrls(nMinutes, limit);
			log.debug(String.format("Found %s most recent media items", result.size()));
		}
		return result;
	}

	private List<String> filterTV(List<String> urls) {
		List<String> result = new ArrayList<>();
		for (String url: urls) {
			if (typeResolver.isTVProgram(url)) result.add(url);
		}
		return result;
	}

	private List<String> filterMicroPosts(List<String> urls) {
		List<String> result = new ArrayList<>();
		for (String url: urls) {
			if (typeResolver.isMicroPost(url)) result.add(url);
		}
		return result;
	}

	private List<String> filterNews(List<String> urls) {
		List<String> result = new ArrayList<>();
		for (String url: urls) {
			if (typeResolver.isNewsArticle(url)) result.add(url);
		}
		return result;
	}
	

	protected final NewsArticleBean clean(NewsArticleBean newsArticleBean) {
		newsArticleBean.getCreated().resetTimeAgo();
		return newsArticleBean;
	}

	protected final MicroPostBean clean(MicroPostBean microPostBean) {
		microPostBean.getCreated().resetTimeAgo();
		return microPostBean;
	}
	
	protected final TVProgramBean clean(TVProgramBean tvProgramBean) {
		tvProgramBean.getBroadcastDate().resetTimeAgo();
		if (tvProgramBean.getRelatedImage() != null &&
				tvProgramBean.getRelatedImage().startsWith("http://cms-staging.zattoo.com")) {
			log.debug("Filter out sandbox zattoo related images as these are forbidden by the server.");
			tvProgramBean.setRelatedImage(null);
		}
		tvProgramBean.setWatchUrl(typeResolver.toWatchUrl(tvProgramBean));
		try {
			ZattooCustomTVInfo ztvi = (tvProgramBean.getCustomInfo() == null) ? new ZattooCustomTVInfo() :
				asZattooCustomTVInfo(tvProgramBean.getCustomInfo());
			String channelId = typeResolver.resolveZattooChannelId(tvProgramBean);
			ztvi.setChannelId(channelId);
			tvProgramBean.setCustomInfo(ztvi);
		} catch (Exception e) {
			log.warn("Failed to set channelId information ", e);
		}
		return tvProgramBean;
	}
	
	private ZattooCustomTVInfo asZattooCustomTVInfo(CustomTVInfo customInfo) {
		if (customInfo instanceof ZattooCustomTVInfo) return (ZattooCustomTVInfo)customInfo;
		throw new IllegalArgumentException("Expecting ZattooCustomTVInfo but found " + customInfo);
	}

	protected final <T extends MediaItem> Iterable<T> cleanMediaItems(
			Collection<T> values) {
		List<T> result = new ArrayList<T>();
		for (T val: values) {
			result.add(cleanMediaItem(val));
		}
		return result;
	}
	
	@SuppressWarnings("unchecked")
	protected final <T extends MediaItem> T cleanMediaItem(T dirty) {
		if (dirty instanceof NewsArticleBean) return (T) clean((NewsArticleBean)dirty);
		if (dirty instanceof TVProgramBean) return (T) clean((TVProgramBean) dirty);
		if (dirty instanceof MicroPostBean) return (T) clean((MicroPostBean) dirty);
		log.warn("Unsupported mediaItem " + dirty);
		return dirty;
	}
	
}
