package eu.xlime.dao;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

import eu.xlime.Config;
import eu.xlime.bean.MediaItem;
import eu.xlime.bean.MicroPostBean;
import eu.xlime.bean.NewsArticleBean;
import eu.xlime.bean.TVProgramBean;
import eu.xlime.bean.VideoSegment;
import eu.xlime.dao.mediaitem.AbstractMediaItemDao;
import eu.xlime.dao.mediaitem.MongoMediaItemDao;
import eu.xlime.util.score.ScoredSet;

/**
 * Default implementation for retrieving {@link MediaItem} beans.
 * 
 * Its implementation aims to use the best combination of caching and querying available 
 * back-end data stores to provide "fresh" {@link MediaItem}s while providing quick responses.    
 * 
 * @author RDENAUX
 *
 */
public class MediaItemDaoImpl extends AbstractMediaItemDao {

	private static final Logger log = LoggerFactory.getLogger(MediaItemDaoImpl.class);
	
	private final MediaItemDao delegate;
	
	public MediaItemDaoImpl() {
//		delegate = new CachingMediaItemDao(new XLiMeSparqlMediaItemDao());
		delegate = new MongoMediaItemDao(new Config().getCfgProps()); //TODO: maybe use a combination of Mongo and Sparql?
	}

	/* (non-Javadoc)
	 * @see eu.xlime.dao.MediaItemDao#findNewsArticles(java.util.List)
	 */
	@Override
	public List<NewsArticleBean> findNewsArticles(List<String> uris) {
		return delegate.findNewsArticles(uris);
	}

	/* (non-Javadoc)
	 * @see eu.xlime.dao.MediaItemDao#findMicroPosts(java.util.List)
	 */
	@Override
	public List<MicroPostBean> findMicroPosts(List<String> uris) {
		return delegate.findMicroPosts(uris);
	}
		

	/* (non-Javadoc)
	 * @see eu.xlime.dao.MediaItemDao#findTVPrograms(java.util.List)
	 */
	@Override
	public List<TVProgramBean> findTVPrograms(List<String> uris) {
		return delegate.findTVPrograms(uris);
	}

	@Override
	public ScoredSet<String> findMediaItemUrlsByText(QueryDao query) {
		return delegate.findMediaItemUrlsByText(query);
	}

	/* (non-Javadoc)
	 * @see eu.xlime.dao.MediaItemDao#findVideoSegment(java.lang.String)
	 */
	@Override
	public Optional<VideoSegment> findVideoSegment(String uri) {
		return delegate.findVideoSegment(uri);
	}

	@Override
	public List<MicroPostBean> findMicroPostsByKeywordsFilter(
			List<String> keywordFilters) {
		return delegate.findMicroPostsByKeywordsFilter(keywordFilters);
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
		return delegate.findMediaItemsBefore(date, limit);
	}

	@Override
	public boolean hasMediaItemsAfter(long timestampFrom) {
		return delegate.hasMediaItemsAfter(timestampFrom);
	}
	
}
