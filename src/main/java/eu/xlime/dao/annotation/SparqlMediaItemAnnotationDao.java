package eu.xlime.dao.annotation;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import eu.xlime.Config;
import eu.xlime.Config.Opt;
import eu.xlime.bean.ASRAnnotation;
import eu.xlime.bean.EntityAnnotation;
import eu.xlime.bean.OCRAnnotation;
import eu.xlime.bean.SubtitleSegment;
import eu.xlime.bean.TVProgramBean;
import eu.xlime.bean.VideoSegment;
import eu.xlime.bean.XLiMeResource;
import eu.xlime.bean.ZattooStreamPosition;
import eu.xlime.bean.annpos.SpanInTextPosition;
import eu.xlime.dao.QueryDao;
import eu.xlime.sparql.SparqlClient;
import eu.xlime.sparql.SparqlQueryFactory;
import eu.xlime.summa.bean.UIEntity;
import eu.xlime.util.KBEntityMapper;
import eu.xlime.util.ResourceTypeResolver;
import eu.xlime.util.SparqlToBeanConverter;
import eu.xlime.util.score.ScoreFactory;
import eu.xlime.util.score.ScoredSet;
import eu.xlime.util.score.ScoredSetImpl;

public abstract class SparqlMediaItemAnnotationDao extends
		AbstractMediaItemAnnotationDao {

	private static final Logger log = LoggerFactory.getLogger(SparqlMediaItemAnnotationDao.class);
	
	private static final ResourceTypeResolver typeResolver = new ResourceTypeResolver();
	
	private static final SparqlQueryFactory qFactory = new SparqlQueryFactory();
	private static final SparqlToBeanConverter s2b = new SparqlToBeanConverter();
	private static final ScoreFactory scoref = ScoreFactory.instance;
	
	protected abstract SparqlClient getXliMeSparqlClient();

	protected abstract KBEntityMapper getKBEntityMapper(); 
	
	@Override
	public List<XLiMeResource> findRecentAnnotations(int minutes, int limit) {
		log.warn("This method is not supported in sparql (yet? or ever?)");
		return ImmutableList.of();
	}

	/* (non-Javadoc)
	 * @see eu.xlime.dao.MediaItemAnnotationDao#findMicroPostEntityAnnotations(java.lang.String)
	 */
	@Override
	public List<EntityAnnotation> findMicroPostEntityAnnotations(final String url) {
		log.trace("Finding entity annotations for micropost " + url);
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		String query = qFactory.microPostEntityAnnotations(url);
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(query, cfg.getLong(Opt.SparqlTimeout));
		
		return toEntityAnnotations(result, url);
	}

	/* (non-Javadoc)
	 * @see eu.xlime.dao.MediaItemAnnotationDao#findNewsArticleEntityAnnotations(java.lang.String)
	 */
	@Override
	public List<EntityAnnotation> findNewsArticleEntityAnnotations(final String url) {
		log.trace("Finding entity annotations for newsarticle " + url);
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		String query = qFactory.newsArticleEntityAnnotations(url);
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(query, cfg.getLong(Opt.SparqlTimeout));
		
		return toEntityAnnotations(result, url);
	}
	
	@Override
	public List<EntityAnnotation> findSubtitleTrackEntityAnnotations(
			String url) {
		log.trace("Finding entity annotations for subtitleSegment " + url);
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		String query = qFactory.subtitleTrackEntityAnnotations(url);
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(query, cfg.getLong(Opt.SparqlTimeout));
		
		return toEntityAnnotations(result, url);
	}

	@Override
	public List<EntityAnnotation> findAudioTrackEntityAnnotations(
			String url) {
		log.trace("Finding entity annotations for audioTrack " + url);
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		String query = qFactory.audioTrackEntityAnnotations(url);
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(query, cfg.getLong(Opt.SparqlTimeout));
		
		return toEntityAnnotations(result, url);
	}

	@Override
	public List<EntityAnnotation> findVideoTrackEntityAnnotations(
			String url) {
		log.trace("Finding entity annotations for videoTrack " + url);
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		String query = qFactory.videoTrackEntityAnnotations(url);
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(query, cfg.getLong(Opt.SparqlTimeout));
		
		return toEntityAnnotations(result, url);
	}

	@Override
	public List<EntityAnnotation> findEntityAnnotationsFor(UIEntity kbEntity) {
		if (kbEntity == null) return ImmutableList.of();
		String entUrl = kbEntity.getUrl();
		log.trace("Finding entity annotations for subtitleSegment " + entUrl);
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		String query = qFactory.entityAnnotation(ImmutableList.of(entUrl));
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(query, cfg.getLong(Opt.SparqlTimeout));
		
		return toEntityAnnotations(result);
	}

	@Override
	public ScoredSet<String> findMediaItemUrlsByKBEntity(String entityUrl) {
		/* TODO: we may need to
		 *  1. add a timer to avoid having to wait for long queries
		 *  2. have multiple queries to ensure different media-item types (as order of standard query is uncertain, so only the same media types may be returned)
		 *  3. try multiple confidence values (if default 0.98 does not return values)
		 *  4. provide an option for doing cross-lingual or mono-lingual search?  
		 */
		final SparqlClient sparqler = getXliMeSparqlClient();
		Config cfg = new Config();
//		Set<String> expandedEntities = ImmutableSet.of(entity_url); 
		Set<String> expandedEntities = getKBEntityMapper().expandSameAs(entityUrl);
		String query = qFactory.entityAnnotationInMediaItem(filterExpandedEntities(expandedEntities), 0.98);
		log.debug("Retrieving media items URIs using: " + query);
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(query, cfg.getLong(Opt.SparqlTimeout));
		log.debug(String.format("Found %s media items for entity %s", result.size(), entityUrl));
		
		ScoredSet<String> urls = toUrlScoredSet(result);
		log.trace("Media Items: " + urls.toString());
		return urls;
	}

	@Override
	public ScoredSet<ASRAnnotation> findASRAnnotationsForKBEntity(
			String entityUrl) {
		log.warn("findASRAnnotationsForKBEntity not implemented in sparql");
		return ScoredSetImpl.empty();
	}

	@Override
	public ScoredSet<OCRAnnotation> findOCRAnnotationForKBEntity(
			String entityUrl) {
		log.warn("findOCRAnnotationForKBEntity not implemented in sparql");
		return ScoredSetImpl.empty();
	}

	@Override
	public ScoredSet<SubtitleSegment> findSubtitleSegmentsForKBEntity(
			String entityUrl) {
		log.warn("findSubtitleSegmentsForKBEntity not implemented in sparql");
		return ScoredSetImpl.empty();
	}

	private ScoredSet<String> toUrlScoredSet(Map<String, Map<String, String>> resultSet) {
		if (resultSet == null || resultSet.keySet().isEmpty()) {
			log.debug("Empty resultset ");
			return ScoredSetImpl.empty();
		}
		ScoredSet.Builder<String> builder = ScoredSetImpl.builder();
		for (String id: resultSet.keySet()) {
			Map<String, String> tuple = resultSet.get(id);
			builder.add(tuple.get("s"), scoref.newScore(readDouble(tuple, "c", 1.0)));
		}
		return builder.build();
	}
	
	private double readDouble(Map<String, String> tuple, String key, double d) {
		if (tuple.containsKey(key)) {
			String value = tuple.get(key);
			try {
				return Double.parseDouble(value);
			} catch (Exception e) {
				log.warn("Error parsing " + value, e.getLocalizedMessage());
				return d;
			}
		} else return d;
	}
	
	private Set<String> filterExpandedEntities(Set<String> inEntUrls) {
		Set<String> result = new HashSet<String>();
		Set<String> supported = getSupportedEntUriFilters(); 
		for (String url: inEntUrls) {
			for (String supFilter : supported) {
				if (url.contains(supFilter)) result.add(url);
			}
		}
		
		return result;
	}

	private Set<String> getSupportedEntUriFilters() {
		//TODO: make this configurable
		Set<String> supportedLangs = ImmutableSet.of("", "nl", "fr", "de", "it", "es");
		Set<String> supportedDomains = ImmutableSet.of("dbpedia.org", "wikipedia.org");
		ImmutableSet.Builder<String> builder = ImmutableSet.builder();
		for (String lang: supportedLangs) {
			for (String dom: supportedDomains) {
				if ("".equals(lang)) builder.add("http://" + dom);
				else builder.add("http://" + lang + "." + dom);
			}
		}
		Set<String> supported = builder.build();
		return supported;
	}
	
	/* (non-Javadoc)
	 * @see eu.xlime.dao.MediaItemAnnotationDao#findASRAnnotation(java.lang.String)
	 */
	@Override
	public Optional<ASRAnnotation> findASRAnnotation(String uri) {
		log.trace("Finding the ASRAnnotation " + uri);
		// TODO implement
		return Optional.absent();
	}

	/* (non-Javadoc)
	 * @see eu.xlime.dao.MediaItemAnnotationDao#findOCRAnnotationsFor(eu.xlime.bean.TVProgramBean)
	 */
	@Override
	public List<OCRAnnotation> findOCRAnnotationsFor(final TVProgramBean mediaResource) {
		log.trace("Finding the OCRAnnotations for " + mediaResource);
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		String query = qFactory.mediaResourceOCRAnnotations(mediaResource.getUrl());
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(query, cfg.getLong(Opt.SparqlTimeout));
		
		return toMediaResourceOCRAnnotations(result, mediaResource);
	}
	
	/* (non-Javadoc)
	 * @see eu.xlime.dao.MediaItemAnnotationDao#findOCRAnnotation(java.lang.String)
	 */
	@Override
	public Optional<OCRAnnotation> findOCRAnnotation(String ocrAnnotUri) {
		log.trace("Finding the OCRAnnotation " + ocrAnnotUri);
		/* TODO: since OCR annotations do not have uris in sparql endpoint:
		 * 1. map to mediaResource uri via naming convention, 
		 * 2. find mediaResource via uri (needs MediaItemDao), 
		 * 3. find all OCR annotations via #findOCRAnnotationsFor and 
		 * 4. return one that matches, if any... 
		 */
		return Optional.absent();
	}

	
	@Override
	public Optional<SubtitleSegment> findSubtitleSegment(
			String subtitleSegmentUri) {
		log.trace("Finding the SubtitleSegment " + subtitleSegmentUri);
		/* TODO: since OCR annotations do not have uris in sparql endpoint:
		 * 1. map to mediaResource uri via naming convention, 
		 * 2. find mediaResource via uri (needs MediaItemDao), 
		 * 3. find all OCR annotations via #findOCRAnnotationsFor and 
		 * 4. return one that matches, if any... 
		 */
		return Optional.absent();
	}

	@Override
	public List<SubtitleSegment> findSubtitleSegmentsForTVProg(String tvProgUri) {
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		String q = qFactory.subtitleSegmentsFromSubtitleTrackUri(tvProgUrlToSubtitleTrackUrl(tvProgUri));
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(q, cfg.getLong(Opt.SparqlTimeout));
		return toSubtitleSegments(result);
	}
	
	@Override
	public List<ASRAnnotation> findASRAnnotationsForTVProg(String tvProgUri) {
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		String q = qFactory.asrAnnotationsFromAudioTrackUri(tvProgUrlToAudioTrackUrl(tvProgUri));
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(q, cfg.getLong(Opt.SparqlTimeout));
		return toASRAnnotations(result);
	}

	@Override
	public List<SubtitleSegment> findSubtitleSegmentsByText(QueryDao query) {
		throw new UnsupportedOperationException("Finding subtitles from text is not supported agains Sparql endpoints since they do not have a standard way to index and retrieve text in an efficient way");
	}

	@Override
	public List<OCRAnnotation> findOCRAnnotationsByText(QueryDao query) {
		throw new UnsupportedOperationException("Finding OCR from text is not supported agains Sparql endpoints since they do not have a standard way to index and retrieve text in an efficient way");
	}

	@Override
	public List<ASRAnnotation> findASRAnnotationsByText(QueryDao query) {
		throw new UnsupportedOperationException("Finding ASR from text is not supported agains Sparql endpoints since they do not have a standard way to index and retrieve text in an efficient way");
	}

	@Override
	public List<ASRAnnotation> findAllASRAnnotations(int limit) {
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		String q = qFactory.allASRAnnotations(limit);
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(q, cfg.getLong(Opt.SparqlTimeout));
		return toASRAnnotations(result);
	}

	
	@Override
	public List<OCRAnnotation> findAllOCRAnnotations(int limit) {
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		String q = qFactory.allOCRAnnotations(limit);
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(q, cfg.getLong(Opt.SparqlTimeout));
		return toOCRAnnotations(result);
	}

	@Override
	public List<SubtitleSegment> findAllSubtitleSegments(int limit) {
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		String q = qFactory.allSubtitleSegments(limit);
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(q, cfg.getLong(Opt.SparqlTimeout));
		return toSubtitleSegments(result);
	}


	@Override
	public List<SubtitleSegment> findAllSubtitleSegmentsByDate(long dateFrom,
			long dateTo, int limit) {
		Config cfg = new Config();
		final SparqlClient sparqler = getXliMeSparqlClient();
		DateTimeFormatter formatter1 = ISODateTimeFormat.dateTimeNoMillis().withZoneUTC();
		DateTimeFormatter formatter2 = DateTimeFormat.forPattern("YYYY-MM-dd'T'HH:mm:ss");
		String q = qFactory.subtitleSegmentsByDate(dateFrom, dateTo, limit, formatter1, formatter2);
		Map<String, Map<String, String>> result = sparqler.executeSPARQLOrEmpty(q, cfg.getLong(Opt.SparqlTimeout));
		return toSubtitleSegments(result);
	}

	

	private List<ASRAnnotation> toASRAnnotations(Map<String, Map<String, String>> resultSet) {
		if (resultSet == null || resultSet.keySet().isEmpty()) {
			log.debug("No ASR results found");
			return ImmutableList.of();
		}
		log.debug("Creating ASRAnnotations from resultset with " + resultSet.size() + " tuples.");
		List<ASRAnnotation> result = new ArrayList<>();
		for (String id: resultSet.keySet()) {
			Map<String, String> tuple = resultSet.get(id);
			Optional<ASRAnnotation> optAnn = toASRAnnotation(tuple);
			if (optAnn.isPresent()) result.add(optAnn.get());
		}		
		return result;
	}
	
	private Optional<ASRAnnotation> toASRAnnotation(Map<String, String> tuple) {
		ASRAnnotation result = new ASRAnnotation();
		Optional<VideoSegment> partOf = videoSegmentFromASRResult(tuple); 
		if (partOf.isPresent()) {
			result.setInSegment(partOf.get());
		} else {
			log.debug("Failed to extract a video segment for " + tuple);
			return Optional.absent();
		}
		if (tuple.containsKey("asrText")) {
			result.setRecognizedText(tuple.get("asrText"));
		}
		if (tuple.containsKey("langLabel")) {
			result.setLang(tuple.get("langLabel"));
		}
		if (tuple.containsKey("asrEngUri")) {
			result.setAsrEngine(tuple.get("asrEngUri"));
		}
		result.setUrl(calcUrl(result, tuple.get("audTrack")));
		return Optional.of(result);
		
	}
	private List<SubtitleSegment> toSubtitleSegments(
			Map<String, Map<String, String>> resultSet) {
		if (resultSet == null || resultSet.keySet().isEmpty()) {
			log.debug("No subtitle segments found");
			return ImmutableList.of();
		}
		log.debug("Creating SubtitleSegments from resultset with " + resultSet.size() + " tuples.");
		List<SubtitleSegment> result = new ArrayList<>();
		for (String id: resultSet.keySet()) {
			Map<String, String> tuple = resultSet.get(id);
			Optional<SubtitleSegment> optAnn = toSubtitleSegment(tuple);
			if (optAnn.isPresent()) result.add(optAnn.get());
		}		
		return result;
	}


	private Optional<SubtitleSegment> toSubtitleSegment(
			Map<String, String> tuple) {
		SubtitleSegment result = new SubtitleSegment();
		Optional<VideoSegment> partOf = videoSegmentFromSubtitleResult(tuple); 
		if (partOf.isPresent()) {
			result.setPartOf(partOf.get());
		} else {
			log.debug("Failed to extract a video segment for " + tuple);
			return Optional.absent();
		}
		if (tuple.containsKey("text")) {
			result.setText(tuple.get("text"));
		}
		if (tuple.containsKey("lang")) {
			result.setLang(tuple.get("lang"));
		}
		result.setUrl(calcUrl(result, tuple.get("s")));
		return Optional.of(result);
	}


	private String calcUrl(SubtitleSegment stSeg, String subtitleTrackUrl) {
		return typeResolver.calcUrl(stSeg, subtitleTrackUrl);
	}
	
	private String calcUrl(ASRAnnotation asrAnn, String audioTrackUrl) {
		return typeResolver.calcUrl(asrAnn, audioTrackUrl);
	}

	private String calcUrl(OCRAnnotation ocrAnn, String videoTrackUrl) {
		return typeResolver.calcUrl(ocrAnn, videoTrackUrl);
	}

	private Optional<VideoSegment> videoSegmentFromASRResult(Map<String, String> tuple) {
		if (!tuple.containsKey("audTrack")) return Optional.absent();
		String audTrackUrl = tuple.get("audTrack");
		String tvProgUrl = tvProgUrlFromAudioTrackUrl(audTrackUrl);
		VideoSegment result = newVideoSegment(tvProgUrl);
		if (tuple.containsKey("startTime")) {
			String startTime = tuple.get("startTime");
			result.setStartTime(s2b.asUIDate(s2b.extractISODate(startTime)));
		}
		if (tuple.containsKey("streamPos")) {
			String streamPos = tuple.get("streamPos");
			Double d = Double.parseDouble(streamPos);
			ZattooStreamPosition pos = new ZattooStreamPosition();
			pos.setValue(d.longValue());
			result.setPosition(pos);
		}
		return Optional.of(cleanVideoSegment(result));
	}
	
	private Optional<VideoSegment> videoSegmentFromSubtitleResult(
			Map<String, String> tuple) {
		if (!tuple.containsKey("s")) return Optional.absent();
		String subtitleTrackUrl = tuple.get("s");
		String tvProgUrl = tvProgUrlFromsubtitleTrackUrl(subtitleTrackUrl);
		VideoSegment result = newVideoSegment(tvProgUrl);
		if (tuple.containsKey("startTime")) {
			String startTime = tuple.get("startTime");
			result.setStartTime(s2b.asUIDate(s2b.extractISODate(startTime)));
		}
		if (tuple.containsKey("streamPos")) {
			String streamPos = tuple.get("streamPos");
			Double d = Double.parseDouble(streamPos);
			ZattooStreamPosition pos = new ZattooStreamPosition();
			pos.setValue(d.longValue());
			result.setPosition(pos);
		}
		return Optional.of(cleanVideoSegment(result));
	}
	

	private String tvProgUrlFromsubtitleTrackUrl(String subtitleTrackUrl) {
		//TODO: move methods for converting zattoo uris back and from other urls and beans to their own class..
		String suffix = "/subtitles";
		if (subtitleTrackUrl.endsWith(suffix))
			return subtitleTrackUrl.substring(0, subtitleTrackUrl.length() - suffix.length());
		else throw new IllegalArgumentException("Not a subtitle track url: " + subtitleTrackUrl);
	}
	
	private String tvProgUrlFromAudioTrackUrl(String audioTrackUrl) {
		//TODO: move methods for converting zattoo uris back and from other urls and beans to their own class..
		String suffix = "/audio";
		if (audioTrackUrl.endsWith(suffix))
			return audioTrackUrl.substring(0, audioTrackUrl.length() - suffix.length());
		else throw new IllegalArgumentException("Not a audio track url: " + audioTrackUrl);
	}
	
	private String tvProgUrlToSubtitleTrackUrl(String tvProgUrl) {
		return tvProgUrl + "/subtitles";
	}

	private String tvProgUrlToAudioTrackUrl(String tvProgUrl) {
		return tvProgUrl + "/audio";
	}
	
	private List<OCRAnnotation> toMediaResourceOCRAnnotations(
			Map<String, Map<String, String>> resultSet, TVProgramBean tvProg) {
		if (resultSet == null || resultSet.keySet().isEmpty()) {
			log.debug("No ocr annotations for " + tvProg.getUrl());
			return ImmutableList.of();
		}		
		log.debug("Creating OCRAnnotation from resultset with " + resultSet.size() + " tuples.");
		List<OCRAnnotation> result = new ArrayList<>();
		for (String id: resultSet.keySet()) {
			Map<String, String> tuple = resultSet.get(id);
			Optional<OCRAnnotation> optAnn = toOCRAnnotation(tuple, tvProg);
			if (optAnn.isPresent()) result.add(optAnn.get());
		}		
		return result;
	}

	final Optional<OCRAnnotation> toOCRAnnotation(Map<String, String> tuple) {
		OCRAnnotation result = new OCRAnnotation();
		if (tuple.containsKey("url")) {
			TVProgramBean basicTVProg = new TVProgramBean();
			basicTVProg.setUrl(tuple.get("url"));
			return toOCRAnnotation(tuple, basicTVProg);
		} else {
			log.warn("No URL found for a tvProgram " + tuple);
			return Optional.absent();
		}
	}
	
	final Optional<OCRAnnotation> toOCRAnnotation(Map<String, String> tuple,
			TVProgramBean tvProg) {
		OCRAnnotation result = new OCRAnnotation();
		if (tuple.containsKey("ocr") && tuple.containsKey("vidTrack")) {
			OCRContent ocrContent = new OCRContent(tuple.get("ocr"));
			
			result.setInSegment(toVideoSegment(tvProg, ocrContent.streamPosition));
			result.setRecognizedText(ocrContent.recognizedText);
			result.setUrl(calcUrl(result, tuple.get("vidTrack")));
		} else {
			log.warn("No OCR content found for " + tvProg.getUrl());
			return Optional.absent();
		}
		return Optional.of(result);
	}
	
	private List<OCRAnnotation> toOCRAnnotations(
			Map<String, Map<String, String>> resultSet) {
		if (resultSet == null || resultSet.keySet().isEmpty()) {
			log.debug("No ocr annotations in resultSet");
			return ImmutableList.of();
		}		
		log.debug("Creating OCRAnnotation from resultset with " + resultSet.size() + " tuples.");
		List<OCRAnnotation> result = new ArrayList<>();
		for (String id: resultSet.keySet()) {
			Map<String, String> tuple = resultSet.get(id);
			Optional<OCRAnnotation> optAnn = toOCRAnnotation(tuple);
			if (optAnn.isPresent()) result.add(optAnn.get());
		}		
		return result;
	}
	
	private VideoSegment toVideoSegment(TVProgramBean tvProg, double streamPosition) {
		VideoSegment result = new VideoSegment();
		result.setPartOf(tvProg);
		ZattooStreamPosition pos = new ZattooStreamPosition();
		pos.setValue((long)streamPosition);
		result.setPosition(pos);
		return cleanVideoSegment(result);
	}

	public static class OCRContent {
		final String literal;
		final double streamPosition;
		final String recognizedText;
		
		public OCRContent(String literalOCRContent) {
			literal = literalOCRContent;
			streamPosition = extractTimeStamp(literalOCRContent);
			recognizedText = extractRecognizedText(literalOCRContent);
		}
		
		private String extractRecognizedText(String literalOCRContent) {
			int splitIndex = literalOCRContent.indexOf(',');
			return literalOCRContent.substring(splitIndex + 1);
		}

		private double extractTimeStamp(String literalOCRContent) {
			int splitIndex = literalOCRContent.indexOf(',');
			if (splitIndex <= 0) throw new RuntimeException("Illegal format of OCR content. "
					+ "Expecting :\n\t<timestamp> ', ' <recognizedText>\n\t but found: \n\t " + literalOCRContent);
			return Double.parseDouble(literalOCRContent.substring(0, splitIndex));
		}

	}

	private List<EntityAnnotation> toEntityAnnotations(Map<String, Map<String, String>> resultSet) {
		return toEntityAnnotations(resultSet, Optional.<String>absent());
	}
	
	private List<EntityAnnotation> toEntityAnnotations(Map<String, Map<String, String>> resultSet,
			String annotatedResourceUrl) {
		return toEntityAnnotations(resultSet, Optional.fromNullable(annotatedResourceUrl));
	}

	private List<EntityAnnotation> toEntityAnnotations(Map<String, Map<String, String>> resultSet,
			Optional<String> annotatedResourceUrl) {
		if (resultSet == null || resultSet.keySet().isEmpty()) {
			log.debug("No entities for " + annotatedResourceUrl);
			return ImmutableList.of();
		}
		log.debug("Creating EntityAnnotation from resultset with " + resultSet.size() + " tuples.");
		List<EntityAnnotation> result = new ArrayList<>();
		for (String id: resultSet.keySet()) {
			Map<String, String> tuple = resultSet.get(id);
			Optional<EntityAnnotation> optEntAnn = toEntityAnnotation(tuple);
			if (optEntAnn.isPresent()) {
				EntityAnnotation entAnn = optEntAnn.get();
				if (annotatedResourceUrl.isPresent()) {
					entAnn.setResourceUrl(annotatedResourceUrl.get());
				} else if (entAnn.getResourceUrl() == null){
					log.warn("Extracted EntityAnnotation from Sparql does not have a resourceUrl" + resultSet);
				}
				entAnn.setUrl(EntityAnnotation.coinUri(entAnn));
				result.add(entAnn);
			}
		}
		return mergeEntAnns(result);
	}

	private Optional<EntityAnnotation> toEntityAnnotation(Map<String, String> tuple) {
		EntityAnnotation ea = new EntityAnnotation();
		try {
			ea.setInsertionDate(new Date());
			if (tuple.containsKey("ent")) {
				String entUrl = tuple.get("ent");
				Optional<String> canonEntUrl = getKBEntityMapper().toCanonicalEntityUrl(entUrl);
				if (!canonEntUrl.isPresent()) {
					log.debug("No canonical entity found for " + entUrl + " not converting to EntityAnnotation");
					return Optional.absent();
				} else {
					ea.setEntity(emptyUIEnt(canonEntUrl.get()));
				}
			} else return Optional.absent(); //having an entity is mandatory
			if (tuple.containsKey("confidence")) {
				ea.setConfidence(Double.parseDouble(tuple.get("confidence")));
			}
			if (tuple.containsKey("s")) {
				ea.setResourceUrl(tuple.get("s"));
			}
			
			Optional<SpanInTextPosition> optPos = toAnnPosition(tuple);
			ea.setPosition(optPos.orNull());
			
			if (tuple.containsKey("g")) {
				ea.setActivityUrl(tuple.get("g"));
			}
		} catch (Exception e) {
			log.debug("Error reading EntityAnnotation from tuple" + e);
			return Optional.absent();
		}
		return Optional.of(ea);
	}

	private UIEntity emptyUIEnt(String entUrl) {
		UIEntity result = new UIEntity();
		result.setUrl(entUrl);
		return result;
	}


	private Optional<SpanInTextPosition> toAnnPosition(Map<String, String> tuple) {
		if (!tuple.containsKey("start") && !tuple.containsKey("end")) return Optional.absent();
		SpanInTextPosition pos = new SpanInTextPosition();
		try {
			if (tuple.containsKey("start")) {
				pos.setStart(Long.parseLong(tuple.get("start")));
			}
			if (tuple.containsKey("end")) {
				pos.setStart(Long.parseLong(tuple.get("end")));
			}
		} catch (NumberFormatException ne) {
			log.debug("Error parsing annotation position", ne);
			return Optional.absent();
		}
		return Optional.of(pos);
	}
	
	
}
