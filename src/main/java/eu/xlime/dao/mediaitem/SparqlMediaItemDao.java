package eu.xlime.dao.mediaitem;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.hp.hpl.jena.query.QueryParseException;

import eu.xlime.bean.Content;
import eu.xlime.bean.CustomTVInfo;
import eu.xlime.bean.Duration;
import eu.xlime.bean.GeoLocation;
import eu.xlime.bean.MediaItem;
import eu.xlime.bean.MicroPostBean;
import eu.xlime.bean.NewsArticleBean;
import eu.xlime.bean.TVProgramBean;
import eu.xlime.bean.UrlLabel;
import eu.xlime.bean.ZattooCustomTVInfo;
import eu.xlime.dao.MediaItemDao;
import eu.xlime.dao.QueryDao;
import eu.xlime.sparql.QueryExecutionException;
import eu.xlime.sparql.SparqlClient;
import eu.xlime.sparql.SparqlQueryFactory;
import eu.xlime.util.ListUtil;
import eu.xlime.util.SparqlToBeanConverter;
import eu.xlime.util.score.ScoredSet;
import eu.xlime.util.score.ScoredSetImpl;

/**
 * Base class for {@link MediaItemDao} implementations which use a Sparql 
 * backend to retrieve {@link MediaItem}s. 
 * 
 * Implementations only need to provide a 
 * {@link SparqlClient} to access the sparql endpoint and use the various protected 
 * final methods to retrieve the required beans. 
 * 
 * This class already implements how to query endpoints that adhere to the xLiMe 
 * data-model to construct the various {@link MediaItem} beans.   
 * 
 * @author rdenaux
 *
 */
public abstract class SparqlMediaItemDao extends AbstractMediaItemDao {

	private static final Logger log = LoggerFactory.getLogger(SparqlMediaItemDao.class);
	private static final SparqlQueryFactory qFactory = new SparqlQueryFactory();
	private static final SparqlToBeanConverter s2b = new SparqlToBeanConverter();
	/**
	 * The 'ideal' maximum number of characters to allow in a Content preview.
	 * This limit is 'soft' in the sense that  
	 */
	private static int PreviewSoftMaxLength = 200;
	
	/**
	 * Percentage of the {@link #PreviewSoftMaxLength} by which a content preview 
	 * can exceed the {@link #PreviewSoftMaxLength}. 
	 */
	private static double PreviewMaxLengthAllowance = 0.1;

	
	/**
	 * Returns a {@link SparqlClient} to query the Sparql back-end endpoint. 
	 * @return
	 */
	protected abstract SparqlClient getXLiMeSparqlClient();
	
	@Override
	public List<MicroPostBean> findMicroPostsByKeywordsFilter(
			List<String> keywordFilters) {
		List<String> uris = findMicroPostUrlsByKeyword(keywordFilters);
		return findMicroPosts(uris);
	}

	@Override
	public List<String> findAllMediaItemUrls(int limit) {
		String query = qFactory.mediaItemUrls();
		Map<String, Map<String, String>> result = getXLiMeSparqlClient().executeSPARQLQuery(query);
		return toUrlList(result);
	}

	@Override
	public final List<String> findMediaItemsByDate(long dateFrom, long dateTo,
			int limit) {
		DateTimeFormatter formatter1 = ISODateTimeFormat.dateTimeNoMillis().withZoneUTC();
		DateTimeFormatter formatter2 = DateTimeFormat.forPattern("YYYY-MM-dd'T'HH:mm:ss");
		String query = qFactory.mediaItemUrlsByDate(dateFrom, dateTo, limit, formatter1, formatter2);
		log.trace("Retrieving latest media items using: " + query);
		Map<String, Map<String, String>> result = getXLiMeSparqlClient().executeSPARQLQuery(query);
		return toUrlList(result);
	}
	
	
	@Override
	public List<String> findMostRecentMediaItemUrls(int nMinutes, int limit) {
		throw new RuntimeException("Not implemented");
	}

	
	@Override
	public List<String> findMediaItemsBefore(Date date, int limit) {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public boolean hasMediaItemsAfter(long timestampFrom) {
		return false; //don't know really :)
	}

	@Override
	public ScoredSet<String> findMediaItemUrlsByText(QueryDao query) {
		log.warn("Search by text not supported as this is slow in default SPARQL and not standardised (triplestore dependent).");
		return ScoredSetImpl.empty();
	}

	protected final List<String> findMicroPostUrlsByKeyword(List<String> allowedKeywords) {
		String query = qFactory.microPostsByKeywordFilter(allowedKeywords); 
		return extractValues(getXLiMeSparqlClient().executeSPARQLQuery(query), "s"); 
	}

	protected final Map<String, NewsArticleBean> doFindNewsArticles(List<String> toFind) {
		if (toFind.isEmpty()) return ImmutableMap.of();
		String query = qFactory.newsArticleDetails(toFind);
		log.debug("Retrieving newsArticle details for " + toFind.size() + " items using query: " + query);
		Map<String, Map<String, String>> result = getXLiMeSparqlClient().executeSPARQLQuery(query);
		log.debug("Found " + result.size() + " results ");
		
		return toNewsArticles(result, toFind);
	}
	
	protected final Map<String, TVProgramBean> doFindTVPrograms(List<String> toFind) {
		if (toFind.isEmpty()) return ImmutableMap.of();
		String query = qFactory.mediaResource(toFind);
		log.debug("Retrieving tvProgram details for " + toFind.size() + " items using query: " + query);
		Map<String, Map<String, String>> result = getXLiMeSparqlClient().executeSPARQLQuery(query);
		log.debug("Found " + result.size() + " results ");
		
		return toTVProgramBeans(result, toFind);
	}
	
	protected final Map<String, Long> findZattooProductionPidMappings() {
		String query = qFactory.zattooPidMappings();
		log.debug("Retrieving zattoo sandbox to production mappings using query: " + query);
		Map<String, Map<String, String>> result = getXLiMeSparqlClient().executeSPARQLQuery(query);
		log.debug("Found " + result.size() + " results ");
		
		return toZattooProductionPidMappings(result);
	}

	protected final Map<String, MicroPostBean> doFindMicroPosts(List<String> toFind) {
		int maxListSize = 10;
		if (toFind.size() > maxListSize) {
			List<List<String>> subLists = new ListUtil().splitIntoSubListsWithMaxSize(maxListSize, toFind);
			Map<String, MicroPostBean> result = new HashMap<>(); 
			for (List<String> sl: subLists) {
				result.putAll(doFindMicroPosts(sl));
			}
			return result;
		} else {
			return doFindMicroPosts(toFind, 2);
		}
	}

	private Map<String, MicroPostBean> doFindMicroPosts(List<String> toFind, int triesLeft) {
		if (toFind.isEmpty()) return ImmutableMap.of();
		String query = qFactory.microPostDetails(toFind);
		log.debug("Retrieving microPost details for " + toFind.size() + " items using query: " + query);
		Map<String, Map<String, String>> result = ImmutableMap.of();
		try {
			result = getXLiMeSparqlClient().executeSPARQLQuery(query);
		} catch (QueryExecutionException e) {
			log.error("Error retrieving microPosts", e);
			return ImmutableMap.of();
		}
		log.debug("Found " + result.size() + " results ");
		
		Map<String, MicroPostBean> found = toMicroPosts(result, toFind);
		List<String> missing = new ArrayList<>(toFind);
		missing.removeAll(found.keySet());
		if (!found.isEmpty() && !missing.isEmpty() && triesLeft > 1) {
			//sometimes query fails to return all results, so try again
			return ImmutableMap.<String, MicroPostBean>builder()
					.putAll(found)
					.putAll(doFindMicroPosts(missing, triesLeft - 1))
					.build();
		} else return found;
	}
	
	private Map<String, NewsArticleBean> toNewsArticles(
			Map<String, Map<String, String>> resultSet, List<String> urls) {
		if (resultSet == null || resultSet.keySet().isEmpty()) {
			log.debug("No newsArticles with " + urls);
			return ImmutableMap.of();
		}
		Map<String, NewsArticleBean> result = new HashMap<>();
		for (String id: resultSet.keySet()) {
			Map<String, String> tuple = resultSet.get(id);
			if (tuple.containsKey("url")) {
				String url = tuple.get("url");
				if (result.containsKey(url)) continue;
				NewsArticleBean builder = new NewsArticleBean();
				builder.setUrl(url);
				buildFromTuple(builder, tuple);
				result.put(url, builder);
			} else continue;
		}
		
		return result;
	}

	/**
	 * Helpst to build an incomplete {@link NewsArticleBean} by adding some information from
	 * a (search result) tuple.
	 *  
	 * @param incompleteBean
	 * @param tuple
	 */
	private void buildFromTuple(NewsArticleBean incompleteBean,
			Map<String, String> tuple) {
		if (tuple.containsKey("created")) {
			String created = tuple.get("created");
			incompleteBean.setCreated(s2b.asUIDate(s2b.extractISODate(created)));
		}
		if (tuple.containsKey("lang")) {
			incompleteBean.setLang(tuple.get("lang"));
		}
		if (tuple.containsKey("publisher")) {
			incompleteBean.setPublisher(readPublisher(tuple.get("publisher")));
		} else if (tuple.containsKey("source")) {
			incompleteBean.setPublisher(readPublisherFromSource(tuple.get("source")));
		}
		if (tuple.containsKey("source")) {
			incompleteBean.setSource(tuple.get("source"));
		}
		if (tuple.containsKey("title")) {
			incompleteBean.setTitle(tuple.get("title"));
		}
		if (tuple.containsKey("content")) {
			String content = tuple.get("content");
			if (content != null) incompleteBean.setContent(readContent(content));
		}
		Optional<GeoLocation> optGeoLoc = toGeoLocation(tuple);
		if (optGeoLoc.isPresent()) {
			incompleteBean.setLocation(optGeoLoc.get());
		}
	}

	private void buildFromTuple(TVProgramBean result, Map<String, String> tuple) {
		if (tuple.containsKey("broadcastDate")) {
			String created = tuple.get("broadcastDate");
			result.setBroadcastDate(s2b.asUIDate(s2b.extractISODate(created)));
		}
		if (tuple.containsKey("title")) {
			result.setTitle(tuple.get("title"));
		}
		if (tuple.containsKey("description")) {
			String content = tuple.get("description");
			if (content != null) result.setDescription(readContent(content));
			else result.setDescription(noDescriptionContent());
		} else result.setDescription(noDescriptionContent());
		if (tuple.containsKey("duration")) {
			result.setDuration(readDuration(tuple.get("duration")));
		}
		if (tuple.containsKey("publisher")) {
			result.setPublisher(readTVPublisher(tuple.get("publisher")));
		}
		if (tuple.containsKey("relImage")) {
			result.setRelatedImage(tuple.get("relImage"));
		}
		if (tuple.containsKey("source")) {
			result.setSource(readTVSource(tuple));
		}
		Optional<GeoLocation> optGeoLoc = toTVGeoLocation(tuple);
		if (optGeoLoc.isPresent()) {
			result.setRelatedLocation(optGeoLoc.get());
		}
		if (tuple.containsKey("genre")) {
			result.setGenre(tuple.get("genre"));
		}
		if (tuple.containsKey("prodpid")) {
			String ppidstr = tuple.get("prodpid");
			try {
				long ppid = Long.valueOf(ppidstr);
				CustomTVInfo ctvi = result.getCustomInfo();
				ZattooCustomTVInfo ztvi = (ctvi == null) ? new ZattooCustomTVInfo() :
					asZattooCustomTVInfo(ctvi);
				ztvi.setProductionProgId(ppid);
				result.setCustomInfo(ztvi);
			} catch (Exception e) {
				log.error("Error reading production pid for " + ppidstr, e);
			}
		}
	}

	private ZattooCustomTVInfo asZattooCustomTVInfo(CustomTVInfo ctvi) {
		if (ctvi instanceof ZattooCustomTVInfo) return (ZattooCustomTVInfo)ctvi;
		throw new RuntimeException("Not a ZattooCustomTVInfo " + ctvi);
	}

	private Content noDescriptionContent() {
		Content result = new Content();
		result.setFull("No description available");
		return result;
	}

	private Duration readDuration(String string) {
		Double d = Double.valueOf(string);
		return new Duration(d);
	}

	private Optional<GeoLocation> toTVGeoLocation(Map<String, String> tuple) {
		if (!tuple.containsKey("geoname")) return Optional.absent();
		GeoLocation result = new GeoLocation();
		result.setLabel(tuple.get("geoname"));
		//TODO: can we expand zattoo's location label into a proper geoname?
		return Optional.of(result);
	}

	private UrlLabel readTVSource(Map<String, String> tuple) {
		UrlLabel result = new UrlLabel();
		result.setLabel(tuple.get("publisher"));
		result.setUrl(tuple.get("source"));
		return result;
	}

	
	private UrlLabel readPublisherFromSource(String sourceUrl) {
		try {
			URI uri = URI.create(sourceUrl);
			UrlLabel result = new UrlLabel();
			String host = uri.getHost();
			if (host == null) return null;
			result.setLabel(host);
			result.setUrl(host);
			return result;
		} catch (Exception e) {
			log.warn("Failed to extract publisher from sourceUrl" + sourceUrl);
			return null;
		}
	}

	private Optional<GeoLocation> toGeoLocation(Map<String, String> tuple) {
		if (tuple == null || tuple.keySet().isEmpty()) return Optional.absent();
		if (!tuple.keySet().containsAll(ImmutableSet.of("geolat", "geolon"))) {
			if (log.isDebugEnabled()) {
				log.debug("Cannot extract geolocation from tuple due to mising 'geolat' or 'geolon' properties. Available properties: " + tuple.keySet());
			}
			if (log.isTraceEnabled()) {
				log.trace("Original tuple " + tuple);
			}
			return Optional.absent();
		}
		GeoLocation result = new GeoLocation();
		if (tuple.containsKey("geolat")) {
			String geolat = tuple.get("geolat");
			result.setLat(asFloat(geolat));
		}
		if (tuple.containsKey("geolon")) {
			result.setLon(asFloat(tuple.get("geolon")));
		}
		if (tuple.containsKey("geoname")) {
			result.setLabel(tuple.get("geoname"));
		}
		return Optional.of(result);
	}
	
	private float asFloat(String val) {
		return Float.valueOf(val);
	}
	
	private Map<String, MicroPostBean> toMicroPosts(
			Map<String, Map<String, String>> resultSet, List<String> urls) {
		if (resultSet == null || resultSet.keySet().isEmpty()) {
			log.debug("No microPost with " + urls);
			return ImmutableMap.of();
		}
		Map<String, MicroPostBean> result = new HashMap<>();
		for (String id: resultSet.keySet()) {
			Map<String, String> tuple = resultSet.get(id);
			if (tuple.containsKey("url")) {
				String url = tuple.get("url");
				if (result.containsKey(url)) continue; 
				MicroPostBean bean = new MicroPostBean();
				bean.setUrl(url);
				buildFromTuple(bean, tuple);
				result.put(url, bean);
			} else continue;
		}
		
		return result;
	}

	private void buildFromTuple(MicroPostBean result, Map<String, String> tuple) {
		if (tuple.containsKey("created")) {
			String created = tuple.get("created");
			result.setCreated(s2b.asUIDate(s2b.extractISODate(created)));
		}
		if (tuple.containsKey("lang")) {
			result.setLang(tuple.get("lang"));
		}
		if (tuple.containsKey("publisher")) {
			String pubUrl = tuple.get("publisher");
			result.setPublisher(readPublisher(pubUrl, findMicroPostPublisherLabel(pubUrl)));
		}
		if (tuple.containsKey("source")) {
			result.setSource(tuple.get("source"));
		}
		if (tuple.containsKey("sourceType")) {
			result.setSourceType(tuple.get("sourceType"));
		}
		if (tuple.containsKey("content")) {
			String content = tuple.get("content");
			if (content != null) result.setContent(readContent(content));
		}
		if (tuple.containsKey("creator")) {
			String creatorUrl = tuple.get("creator");
			result.setCreator(readCreator(creatorUrl, findMicroPostCreatorLabel(creatorUrl)));
		}
	}
	
	Optional<String> findMicroPostPublisherLabel(final String pubUrl) {
		String query = qFactory.siocNameOf(pubUrl);
		Map<String, Map<String, String>> result = getXLiMeSparqlClient().executeSPARQLQuery(query);

		return optValue(result, "label", pubUrl).or(Optional.of(pubUrlToLabel(pubUrl)));
	}
	
	Optional<String> findMicroPostCreatorLabel(final String creatorUrl) {
		String query = qFactory.labelOf(creatorUrl);
		try {
			Map<String, Map<String, String>> result = getXLiMeSparqlClient().executeSPARQLQuery(query);

			return optValue(result, "label", creatorUrl).or(Optional.of(creatorUrlToLabel(creatorUrl)));
		} catch (QueryParseException e) {
			return ignoreQueryParseEx(creatorUrl, e);
		} catch (RuntimeException e) {
			if (e.getCause() instanceof QueryParseException) {
				return ignoreQueryParseEx(creatorUrl, (QueryParseException)e.getCause());
			} else throw e;
		}
	}

	private Optional<String> ignoreQueryParseEx(final String creatorUrl,
			QueryParseException e) {
		if (log.isTraceEnabled()) {
			log.error("Error getting label for creatorUrl " + creatorUrl, e);
		}
		//ignore this type of errors as they are relatively frequent
		return Optional.of(creatorUrlToLabel(creatorUrl));
	}
	
	protected Optional<String> optValue(
			Map<String, Map<String, String>> resultSet, String var,
			String key) {
		if (resultSet == null || resultSet.keySet().isEmpty()) {
			log.debug("No " + var + " value for " + key);
			return Optional.absent();
		}
		String result = null;
		for (String id: resultSet.keySet()) {
			Map<String, String> tuple = resultSet.get(id);
			if (tuple.containsKey(var)) {
				result = tuple.get(var);
				break;
			}
		}
		
		return Optional.fromNullable(result);
	}
	
	private Optional<String> getOpt(Map<String, String> tuple, String key) {
		if (tuple.containsKey(key)) {
			return Optional.fromNullable(tuple.get(key));
		} else return Optional.absent();
	}
	
	private List<String> toUrlList(Map<String, Map<String, String>> resultSet) {
		return extractValues(resultSet, "s");
	}

	/**
	 * Extract all the values bound to a given variable in the resultSet.
	 * @param resultSet
	 * @param variable the variable for which to extract the values (do not include the "?" in the variable).
	 * @return
	 */
	private List<String> extractValues(
			Map<String, Map<String, String>> resultSet, String variable) {
		if (resultSet == null || resultSet.keySet().isEmpty()) {
			log.debug("Empty resultset ");
			return ImmutableList.of();
		}
		List<String> result = new ArrayList<>();
		for (String id: resultSet.keySet()) {
			Map<String, String> tuple = resultSet.get(id);
			result.add(tuple.get(variable));
		}
		return result;
	}
	
	private Map<String, TVProgramBean> toTVProgramBeans(
			Map<String, Map<String, String>> resultSet, List<String> urls) {
		if (resultSet == null || resultSet.keySet().isEmpty()) {
			log.debug("No tv programmes with " + urls);
			return ImmutableMap.of();
		}
		Map<String, TVProgramBean> result = new HashMap<>();
		for (String id: resultSet.keySet()) {
			Map<String, String> tuple = resultSet.get(id);
			if (tuple.containsKey("url")) {
				String url = tuple.get("url");
				if (result.containsKey(url)) continue;
				TVProgramBean bean = new TVProgramBean();
				bean.setUrl(url);
				buildFromTuple(bean, tuple);
				result.put(url, clean(bean)); //needs cleaning (relatedImage)
			} else continue;
		}
		
		return result;
	}
	
	private Map<String, Long> toZattooProductionPidMappings(
			Map<String, Map<String, String>> resultSet) {
		if (resultSet == null || resultSet.keySet().isEmpty()) {
			log.debug("No zattoo mappings found");
			return ImmutableMap.of();
		}
		Map<String, Long> result = new HashMap<>();
		for (String id: resultSet.keySet()) {
			final Map<String, String> tuple = resultSet.get(id);
			if (tuple.containsKey("url") && tuple.containsKey("prodpid")) {
				final String url = tuple.get("url");
				if (result.containsKey("url")) continue;
				final String pidStr = tuple.get("prodpid");
				try {
					final long prodPid = Long.valueOf(pidStr);
					result.put(url, prodPid);
				} catch (Exception e) {
					log.warn("Expecting a long value, but found " + pidStr);
				}
			}
		}
		return result;
	}
	
	private UrlLabel readCreator(String creatorUrl, Optional<String> optLabel) {
		UrlLabel result = new UrlLabel();
		result.setUrl(creatorUrl);
		String label = optLabel.isPresent() ? optLabel.get() : creatorUrlToLabel(creatorUrl); 
		result.setLabel(label);
		return result;
	}

	/**
	 * Coins a label for a given creatorUrl. E.g.
	 * <code>
	 * creatorUrlToLabel("http://clubsearay.com/forum/index.php#Chris%20Nowell") // returns "Chris Nowell"
	 * creatorUrlToLabel("http://twitter.com/YummyDestiny") // returns "YummyDestiny"
	 * creatorUrlToLabel("https://plus.google.com/117956042043423124419") //returns ??
	 * creatorUrlToLabel("http://jjjameson65.wordpress.com#jjjameson65") //returns "jjjameson65"
	 * creatorUrlToLabel("https://www.facebook.com#Daniel%20Cosenza") //return "Daniel Cosenza"
	 * </code>
	 * @param creatorUrl
	 * @return
	 */
	final String creatorUrlToLabel(String creatorUrl) {
		if (creatorUrl.startsWith("http://twitter.com/"))
			return tryDecode(creatorUrl.substring("http://twitter.com/".length()));
		if (creatorUrl.startsWith("http://www.facebook.com#"))
			return tryDecode(creatorUrl.substring("http://www.facebook.com#".length()));
		if (creatorUrl.startsWith("https://plus.google.com/"))
			return "Google+ user"; //TODO: use g+ api to retrieve user info?
		String escapedFragment = extractFragment(creatorUrl);
		if (escapedFragment != null) return escapedFragment;
		return "creator"; //the default label
	}

	private String extractFragment(String aUrl) {
		int hashIndex = aUrl.lastIndexOf('#');
		if (hashIndex < 0) return null;
		String fragment = aUrl.substring(hashIndex + 1);
		return tryDecode(fragment);
	}

	private String tryDecode(String str) {
		try {
			return URLDecoder.decode(str, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return str;
		}
	}

	private Content readContent(String content) {
		Content result = new Content();
		result.setFull(content);
		result.setPreview(asPreview(content));
		return result;
	}

	private String asPreview(String fullContent) {
		int hardMax = PreviewSoftMaxLength + (int)(PreviewSoftMaxLength * PreviewMaxLengthAllowance);
		if (fullContent.length() < hardMax)
			return null; // fullContent doesn't need a preview since it's short enough
		String preview = fullContent.substring(0, PreviewSoftMaxLength) + "...";
		return preview;
	}

	private UrlLabel readPublisher(String pubUrl) {
		return readPublisher(pubUrl, Optional.<String>absent());
	}
	
	private UrlLabel readPublisher(String pubUrl, Optional<String> optLabel) {
		UrlLabel result = new UrlLabel();
		result.setUrl(pubUrl);
		if (optLabel.isPresent())
			result.setLabel(optLabel.get());
		else result.setLabel(pubUrlToLabel(pubUrl));
		return result;
	}

	private UrlLabel readTVPublisher(String pubLabel) {
		UrlLabel result = new UrlLabel();
//		result.setUrl(); //unknown? can we map to a page on zattoo for the channel?
		result.setLabel(pubLabel);
		return result;
	}
	
	private String pubUrlToLabel(String pubUrl) {
		if (pubUrl.contains("/www.twitter.com")) return "Twitter";
		if (pubUrl.contains("/plus.google.com")) return "Google+";
		if (pubUrl.contains("/www.facebook.com")) return "Facebook";
		return extractMainDomain(pubUrl);
	}

	/**
	 * Extracts the 'main domain' from a full URL, e.g.:
	 * <code>
	 * extractMainDomain("http://www.twitter.com") //returns "twitter.com"
	 * extractMainDomain("http://jjjameson65.wordpress.com") //returns "wordpress.com"
	 * extractMainDomain("http://clubsearay.com/forum/index.php"); //returns "clubsearay.com"
	 * </code> 
	 * @param url
	 * @return
	 */
	final String extractMainDomain(String url) {
		int endProtocol = url.indexOf("://");
		if (endProtocol > 0) {
			endProtocol = endProtocol + "://".length();
			int endDomain = url.indexOf("/", endProtocol);
			if (endDomain < 0) endDomain = url.length();
			final String host = url.substring(endProtocol, endDomain);
			int topLevelDomSeparator = host.lastIndexOf('.');
			if (topLevelDomSeparator > 0) {
				String hostNoTLD = host.substring(0, topLevelDomSeparator);
				int mainLevelDomSep = hostNoTLD.lastIndexOf('.');
				if (mainLevelDomSep > 0) {
					return host.substring(mainLevelDomSep + 1, host.length());
				} else {
					return host;
				}
			} else { //no top level domain in host?
				return host;
			}
		} else {
			//failed, return whole url?
			return url;
		}
	}
	
	
}
