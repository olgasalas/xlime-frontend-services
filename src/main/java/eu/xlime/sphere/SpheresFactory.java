package eu.xlime.sphere;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.collect.Ordering;

import eu.xlime.bean.ASRAnnotation;
import eu.xlime.bean.EntityAnnotation;
import eu.xlime.bean.MediaItem;
import eu.xlime.bean.MicroPostBean;
import eu.xlime.bean.NewsArticleBean;
import eu.xlime.bean.OCRAnnotation;
import eu.xlime.bean.SearchString;
import eu.xlime.bean.SubtitleSegment;
import eu.xlime.bean.TVProgramBean;
import eu.xlime.bean.UrlLabel;
import eu.xlime.bean.XLiMeResource;
import eu.xlime.dao.MediaItemAnnotationDao;
import eu.xlime.dao.MediaItemDao;
import eu.xlime.dao.MediaItemDaoImpl;
import eu.xlime.dao.QueryDao;
import eu.xlime.dao.UIEntityDao;
import eu.xlime.dao.xLiMeResourceDao;
import eu.xlime.dao.annotation.MediaItemAnnotationDaoImpl;
import eu.xlime.dao.entity.UIEntityDaoImpl;
import eu.xlime.sphere.bean.Recommendation;
import eu.xlime.sphere.bean.Spheres;
import eu.xlime.summa.bean.UIEntity;
import eu.xlime.util.ResourceTypeResolver;
import eu.xlime.util.score.Score;
import eu.xlime.util.score.ScoreFactory;
import eu.xlime.util.score.ScoredSet;
import eu.xlime.util.score.ScoredSetImpl;

/**
 * Implements the building of {@link Spheres} based on an initial context.
 * 
 * @author RDENAUX
 *
 */
public class SpheresFactory {

	private static final Logger log = LoggerFactory.getLogger(SpheresFactory.class);
	private static final ResourceTypeResolver typeResolver = new ResourceTypeResolver();
	private static final xLiMeResourceDao resourceDao = new xLiMeResourceDao();
	private static final MediaItemDao mediaItemDao = new MediaItemDaoImpl();
	private static final MediaItemAnnotationDao mediaItemAnnotationDao = new MediaItemAnnotationDaoImpl();
	private static final UIEntityDao uiEntityFactory = UIEntityDaoImpl.instance;
	private static final ScoreFactory scoref = ScoreFactory.instance;
		
	private final int default_recent_minutes = 5;
	private final int default_recent_limit = 20;
	
	public Spheres buildSpheres(List<String> contextUrls) {
		final long start = System.currentTimeMillis();
		Spheres result = new Spheres();
		List<XLiMeResource> context = resolveContextUrls(contextUrls);
		result.setInner(asTopRecommendations(context));
		List<Recommendation> recs = calcRecs(context);
		if (recs.size() == 0) return result;
		if (recs.size() > 20) {
			result.setInter(recs.subList(0, 8));
			result.setOuter(recs.subList(8, 20));
		} else if (recs.size() > 15) {
			result.setInter(recs.subList(0, 5));
			result.setOuter(recs.subList(5, 15));
		} else {
			result.setInter(recs.subList(0, Math.min(5, recs.size())));
			if (recs.size() > 5) {
				result.setOuter(recs.subList(5, Math.min(15, recs.size())));
			} else result.setOuter(ImmutableList.<Recommendation>of());
		}
		log.debug(String.format("Built sphere based on %s contextUrls in %s ms", contextUrls.size(), (System.currentTimeMillis() - start)));
		return result;
	}


	private <T extends XLiMeResource> List<Recommendation> mapToRecommendation(
			ScoredSet<T> recs) {
		return mapToRecommendation(recs, recs.asList());
	}

	private <T extends XLiMeResource> List<Recommendation> mapToRecommendation(
			ScoredSet<T> recs, List<T> ress) {
		List<Recommendation> result = new ArrayList<>();
		for (T res: ress) {
			result.add(asRec(recs, res));
		}
		return result;
	}

	private List<Recommendation> calcRecs(List<XLiMeResource> context) {
		final long startEntRec = System.currentTimeMillis(); 
		List<Recommendation> entities = mapToRecommendation(calcRecEntities(context));
		log.debug(String.format("Found %s ent recs in %sms", entities.size(), (System.currentTimeMillis() - startEntRec)));
		
		final long startMedItRec = System.currentTimeMillis();
		List<Recommendation> mediaItems = mapToRecommendation(calcRecMediaItems(context));
		log.debug(String.format("Found %s mediaItem recs in %sms", mediaItems.size(), (System.currentTimeMillis() - startEntRec)));
		
		List<Recommendation> annots = mapToRecommendation(calcRecAnnots(context));
		log.debug(String.format("Found %s annot recs in %sms", mediaItems.size(), (System.currentTimeMillis() - startEntRec)));
		
		if (mediaItems.isEmpty() && !entities.isEmpty()) {
			log.debug(String.format("We could do a search based on %s entities to find some media items", entities.size()));
		}
		
		log.debug(String.format("Weaving %s entities and %s media-items", entities.size(), mediaItems.size()));
		return weave(entities, mediaItems, annots);
	}


	private List<Recommendation> weave(List<Recommendation>... resLists) {
		List<Recommendation> result = new ArrayList<Recommendation>();
		int maxSize = 0;
		int total = 0;
		for (List<?> list: resLists) {
			maxSize = Math.max(maxSize, list.size());
			total = total + list.size();
		}
		for (int i = 0; i < maxSize; i++) {
			for (List<Recommendation> list: resLists) {
				if (list.size() > i) {
					result.add(list.get(i));
				}
			}
		}
		return result;
	}

	private Recommendation asRec(XLiMeResource xLiMeResource) {
		Recommendation result = new Recommendation();
		result.setConfidence(1.0);
		result.setRecommended(xLiMeResource);
		return result;
	}

	private <T extends XLiMeResource> Recommendation asRec(ScoredSet<T> scored, T xLiMeResource) {
		assert(scored.unscored().contains(xLiMeResource));
		Recommendation result = new Recommendation();
		result.setConfidence(scored.getScore(xLiMeResource).getValue());
		result.setRecommended(xLiMeResource);
		return result;
	}

	private ScoredSet<XLiMeResource> calcRecAnnots(List<XLiMeResource> context) {
		final ScoredSet.Builder<XLiMeResource> builder = ScoredSetImpl.builder();
		if (context == null || context.isEmpty()) {
			List<XLiMeResource> anns = mediaItemAnnotationDao.findRecentAnnotations(default_recent_minutes, default_recent_limit);
			for (XLiMeResource res: anns) {
				if (!typeResolver.isEntityAnnotation(res.getUrl())) {
					builder.add(res, scoref.newScore(1.0, "Recent annotation"));
				}
			}
		}
		for (XLiMeResource res: context) {
			builder.addAll(calcRecAnnots(res));
		}
		return filter(builder.build(), context);
	}
	
	private ScoredSet<XLiMeResource> calcRecAnnots(XLiMeResource res) {
		final ScoredSet.Builder<XLiMeResource> builder = ScoredSetImpl.builder();
		if (res instanceof TVProgramBean) {
			// TODO: use Adityia's recommender for the supported media items
			
			List<? extends XLiMeResource> asr = mediaItemAnnotationDao.findASRAnnotationsForTVProg(res.getUrl());
			List<? extends XLiMeResource> sub = mediaItemAnnotationDao.findSubtitleSegmentsForTVProg(res.getUrl());
			List<? extends XLiMeResource> ocr = mediaItemAnnotationDao.findOCRAnnotationsFor((TVProgramBean)res);
			
			List<XLiMeResource> anns = ImmutableList.<XLiMeResource>builder().addAll(asr).addAll(sub).addAll(ocr).build();
			for (XLiMeResource ann: anns) {
				builder.add(ann, scoref.newScore(1.0, "Part of TV program in context"));
			}
		} else if (res instanceof SearchString) {
			String keywords = ((SearchString)res).getValue();
			QueryDao q = new QueryDao();
			q.setQuery(keywords);
			String justif = String.format("Matches search '%s'", keywords);
			List<XLiMeResource> anns = new ArrayList<>();
			anns.addAll(mediaItemAnnotationDao.findASRAnnotationsByText(q));
			anns.addAll(mediaItemAnnotationDao.findOCRAnnotationsByText(q));
			anns.addAll(mediaItemAnnotationDao.findSubtitleSegmentsByText(q));
			for (XLiMeResource ann: anns) {
				builder.add(ann, scoref.newScore(1.0, "Matches search"));
			}
		} else if (res instanceof UIEntity) {
			String entUrl = ((UIEntity) res).getUrl();
			for (ASRAnnotation asrAnn: mediaItemAnnotationDao.findASRAnnotationsForKBEntity(entUrl)) {
				builder.add(asrAnn, scoref.newScore(1.0, "Has entity annotation for " + entUrl));
			}
			for (OCRAnnotation ocrAnn: mediaItemAnnotationDao.findOCRAnnotationForKBEntity(entUrl)) {
				builder.add(ocrAnn, scoref.newScore(1.0, "Has entity annotation for " + entUrl));
			}
			for (SubtitleSegment subAnn: mediaItemAnnotationDao.findSubtitleSegmentsForKBEntity(entUrl)) {
				builder.add(subAnn, scoref.newScore(1.0, "Has entity annotation for " + entUrl));
			}
		} else {
			// TODO: match other types of
			// ocr, sub, asr could be matched using precalculated vectors...
		}
		return builder.build();
	}


	private ScoredSet<MediaItem> calcRecMediaItems(List<XLiMeResource> context) {
		final ScoredSet.Builder<String> medItUrls = ScoredSetImpl.builder();
		if (context == null || context.isEmpty()) {
			Score score = scoref.newScore(1.0, "Recent media item");
			for (String miUrl: mediaItemDao.findMediaItemsBefore(new Date(), default_recent_limit)) {
				medItUrls.add(miUrl, score);
			}
		}
		for (XLiMeResource res: context) {
			medItUrls.addAll(calcRecMediaItemUrls(res));
		}
		return filter(asMediaItems(medItUrls.build()), context); 
	}

	private ScoredSet<String> calcRecMediaItemUrls(XLiMeResource res) {
		final ScoredSet.Builder<String> builder = ScoredSetImpl.builder();
		if (isMediaItem(res)) {
			// TODO: use Adityia's recommender for the supported media items
		} else if (res instanceof SearchString) {
			String keywords = ((SearchString)res).getValue();
			QueryDao q = new QueryDao();
			q.setQuery(keywords);
			String justif = String.format("Matches search '%s'", keywords);
			ScoredSet<String> miUrls = mediaItemDao.findMediaItemUrlsByText(q);
			builder.addAll(miUrls);
		} else if (res instanceof UIEntity) {
			String entUrl = ((UIEntity) res).getUrl();
			ScoredSet<String> matches = mediaItemAnnotationDao.findMediaItemUrlsByKBEntity(entUrl); 
			builder.addAll(matches);
		} else if (res instanceof ASRAnnotation) {
			try {
				String tvProgUrl = ((ASRAnnotation) res).getInSegment().getPartOf().getUrl();
				builder.add(tvProgUrl, scoref.newScore(1.0, "Program for ASR segment"));
			} catch (Exception e) {
				log.warn("Could not map ASRAnnot to tvProgram", e);
			}
		} else if (res instanceof SubtitleSegment) {
			try {
				String tvProgUrl = ((SubtitleSegment) res).getPartOf().getPartOf().getUrl();
				builder.add(tvProgUrl, scoref.newScore(1.0, "Program for subtitle segment"));
			} catch (Exception e) {
				log.warn("Could not map ASRAnnot to tvProgram", e);
			}
			
		} else if (res instanceof OCRAnnotation) {
			try {
				String tvProgUrl = ((OCRAnnotation) res).getInSegment().getPartOf().getUrl();
				builder.add(tvProgUrl, scoref.newScore(1.0, "Program for ocr segment"));
			} catch (Exception e) {
				log.warn("Could not map OCRAnnot to tvProgram", e);
			}
		} else {
			log.warn("Cannot map resource to media items " + res);
		}
		return builder.build();
	}


	private ScoredSet<MediaItem> asMediaItems(ScoredSet<String> medItUrls) {
		ScoredSet.Builder<MediaItem> builder = ScoredSetImpl.builder();
		List<MediaItem> mis = mediaItemDao.findMediaItems(medItUrls.asList());
		for (MediaItem mit: mis) {
			builder.add(mit, medItUrls.getScore(mit.getUrl()));
		}
		return builder.build();
	}

	private ScoredSet<UIEntity> calcRecEntities(List<XLiMeResource> context) {
		final ScoredSet.Builder<UIEntity> builder = ScoredSetImpl.builder();
		if (context == null || context.isEmpty()) {
			List<XLiMeResource> anns = mediaItemAnnotationDao.findRecentAnnotations(default_recent_minutes, default_recent_limit);
			for (XLiMeResource res: anns) {
				if (typeResolver.isEntityAnnotation(res.getUrl())) {
					EntityAnnotation ea = (EntityAnnotation)res;
					builder.add(ea.getEntity(), scoref.newScore(1.0, "Recent annotation"));
				}
			}
		}
		//TODO: much better to only search for entUris, only convert the topN to UIEntity 
		for (XLiMeResource res: context) {
			builder.addAll(calcRecEntities(res));
		}
		return filter(cleanBad(builder.build()), context);
	}

	private ScoredSet<UIEntity> calcRecEntities(XLiMeResource res) {
		final ScoredSet.Builder<UIEntity> builder = ScoredSetImpl.builder();
		if (isMediaItem(res)) {
			MediaItem medIt = (MediaItem) res;
			List<EntityAnnotation> entAnns = mediaItemAnnotationDao.findMediaItemEntityAnnotations(medIt.getUrl());
			log.debug(String.format("Found %s entity annotations for %s", entAnns.size(), medIt.getUrl()));
			for (EntityAnnotation entAnn: entAnns) {
				builder.add(entAnn.getEntity(),
						scoref.newScore(entAnn.getConfidence(), "Mentioned in media item in context"));
			}
		} else if (res instanceof SearchString){
			//TODO: can we use http://km.aifb.kit.edu/services/xlid-lexica-ui/ instead, this would give us greater control and info on confidence for the scores
			String keywords = ((SearchString) res).getValue();
			String justif = String.format("Matches search '%s'", keywords);
			List<UrlLabel> urlLabels = uiEntityFactory.autoCompleteEntities(keywords);
			for (UIEntity uiEnt : uiEntityFactory.retrieveFromUris(mapUrls(urlLabels))) {
				builder.add(uiEnt,
						scoref.newScore(1.0, justif));
			}
		} else {
			// ASR annotations are bound to audioTrack, so we cannot retrieve this
			// Subtitle (same as ASR)
			// OCR (not enough data)
			// we could to a quick analysis to find relevant words and map them to entities?
			
			// TODO: recommend other entities for KBEntities?
			// other resources -> entities
		}
		return builder.build();
	}
	
	/**
	 * Performs filtering of a {@link ScoredSet} of recommended {@link XLiMeResource}s for a given 
	 * context. For example, this removes recommendations if they are already in the context, or 
	 * it may update the score of a resource if it does not have enough links to other resources.
	 *  
	 * @param original
	 * @param context
	 * @return
	 */
	private <T extends XLiMeResource> ScoredSet<T> filter(ScoredSet<T> original,
			List<XLiMeResource> context) {
		return penaliseResourcesWithoutLinks(removeAlreadyInContext(removeEntityAnns(original), context));
	}

	private <T extends XLiMeResource> ScoredSet<T> removeEntityAnns(
			ScoredSet<T> original) {
		final ScoredSet.Builder<T> builder = ScoredSetImpl.builder();
		for (T res: original) {
			if (res instanceof EntityAnnotation) {
				//won't add
			} else builder.add(res, original.getScore(res));
		}
		return builder.build();
	}
	
	private <T extends XLiMeResource> ScoredSet<T> penaliseResourcesWithoutLinks(
			ScoredSet<T> original) {
		final ScoredSet.Builder<T> builder = ScoredSetImpl.builder();
		for (T res: original.unscored()) {
			if (calcRecEntities(res).isEmpty() 
					&& calcRecAnnots(res).isEmpty() 
					&& calcRecMediaItemUrls(res).isEmpty()) {
				log.debug("Penalising " + res.getUrl() + " because it has no links (would result in empty spheres)");
				builder.add(res, scoref.newScore(0.1, "Not linked to other resources (no annotations, entities, etc."));
			} else {
				builder.add(res, original.getScore(res));
			}
		}
		return builder.build();
	}
	
	private <T extends XLiMeResource> ScoredSet<T> removeAlreadyInContext(
			ScoredSet<T> original, List<XLiMeResource> context) {
		final ScoredSet.Builder<T> builder = ScoredSetImpl.builder();
		Set<String> urls  = toUrls(context); 
		for (T res: original.unscored()) {
			if (!urls.contains(res.getUrl())) 
				builder.add(res, original.getScore(res));
		}
		return builder.build();
	}


	private Set<String> toUrls(List<XLiMeResource> context) {
		Set<String> result = new HashSet<>();
		for (XLiMeResource res: context) {
			result.add(res.getUrl());
		}
		return result;
	}


	private ScoredSet<UIEntity> cleanBad(ScoredSet<UIEntity> uiEnts) {
		log.debug(String.format("Cleaning any bad UIEntities (from %s)", uiEnts.size()));
		long start = System.currentTimeMillis();
		final ScoredSet.Builder<UIEntity> builder = ScoredSetImpl.builder();
		List<UIEntity> needCleaning = filterBad(uiEnts.unscored());
		log.trace(String.format("Found %s UIEntities that need cleaning", needCleaning.size()));
		List<String> uris = mapUIEntUrls(needCleaning);
		List<UIEntity> clean = uiEntityFactory.retrieveFromUris(uris);
		for (UIEntity uiEnt: uiEnts.unscored()) {
			UIEntity cleanVersion = uiEnt.isBadUIEnt() ? findClean(uiEnt.getUrl(), clean) : uiEnt;
			if (cleanVersion == null) {
				log.debug("Couldn't clean " + uiEnt);
				cleanVersion = uiEnt;
			}
			builder.add(cleanVersion, uiEnts.getScore(uiEnt));
		}
		log.debug(String.format("Cleaned bad UIEntities in %s ms.", (System.currentTimeMillis() - start)));
		return builder.build();
	}


	private UIEntity findClean(String url, List<UIEntity> clean) {
		for (UIEntity ent: clean) {
			if (ent.getUrl().equals(url)) return ent;
		}
		return null;
	}


	private List<String> mapUIEntUrls(List<UIEntity> uiEnt) {
		List<String> result = new ArrayList<>();
		for (UIEntity ent: uiEnt) {
			result.add(ent.getUrl());
		}
		return result;
	}


	private List<UIEntity> filterBad(Set<UIEntity> uiEnts) {
		List<UIEntity> result = new ArrayList<>();
		for (UIEntity ent: uiEnts) {
			if (ent.isBadUIEnt()) {
				result.add(ent);
			}
		}
		return result;
	}

	private List<String> mapUrls(List<UrlLabel> urlLabels) {
		List<String> result = new ArrayList<String>();
		for (UrlLabel ul: urlLabels) {
			result.add(ul.getUrl());
		}
		return result;
	}


	/**
	 * Flattens the input collection (which has more information) into an output 
	 * sorted collection. 
	 * @param ents
	 * @return
	 */
	private <T> List<T> flatten(final Multiset<T> ents) {
		List<T> result = new ArrayList<>(ents.elementSet());
		Ordering<T> ordByFreq = Ordering.natural().reverse().onResultOf(new Function<T, Integer>() {

			@Override
			public Integer apply(T input) {
				return ents.count(input);
			}
		}); 
		Collections.sort(result, ordByFreq);
		return result;
	}
	
	private boolean isMediaItem(XLiMeResource res) {
		return (res instanceof TVProgramBean) || (res instanceof NewsArticleBean) || (res instanceof MicroPostBean);
	}

	private List<Recommendation> calcOuterSphere(List<XLiMeResource> context) {
		// TODO implement
		return mockOuterSphere();
	}

	private List<Recommendation> calcInterSphere(List<XLiMeResource> context) {
		// TODO implement
		return mockInterSphere();
	}

	private List<Recommendation> mockInterSphere() {
		List<String> recentMedIts = mediaItemDao.findLatestMediaItemUrls(60, 3);
		ImmutableList<String> uris = ImmutableList.<String>builder()
				.add("http://dbpedia.org/resource/Berlin") //KBEntity 
				.add("http://dbpedia.org/resource/Amsterdam") //TVProg, Soc-Med, News art...
				.addAll(recentMedIts)
				.build();
		return mockSphere(uris, 0.7, 0.9);
	}

	private List<Recommendation> mockSphere(ImmutableList<String> uris, double minConf, double maxConf) {
		List<Recommendation> result = new ArrayList<>();
		Random rand = new Random();
		for (String uri: uris) {
			Recommendation rec = new Recommendation();
			double randConf = minConf + (rand.nextDouble() * (maxConf - minConf));
			rec.setConfidence(randConf);
			Optional<? extends XLiMeResource> res = resourceDao.retrieve(uri);
			if (res.isPresent()) {
				rec.setRecommended(res.get());
				result.add(rec);
			}
		}
		return result;
	}

	private List<Recommendation> mockOuterSphere() {
		List<String> recentMedIts = mediaItemDao.findLatestMediaItemUrls(8, 3);
		ImmutableList<String> uris = ImmutableList.<String>builder()
				.add("http://dbpedia.org/resource/Albert_Einstein") //KBEntity 
				.add("http://dbpedia.org/resource/Nike")
				.add("http://dbpedia.org/resource/Adidas")
				.addAll(recentMedIts) //TVProg, Soc-Med, News art...
				.build(); 
		return mockSphere(uris, 0.4, 0.7);
	}
	
	private List<Recommendation> asTopRecommendations(
			List<XLiMeResource> context) {
		List<Recommendation> result = new ArrayList<>();
		for (XLiMeResource res: context) {
			result.add(asRec(res, 1.0));
		}
		return result;
	}

	private Recommendation asRec(XLiMeResource res, double d) {
		Recommendation result = new Recommendation();
		result.setRecommended(res);
		result.setConfidence(d);
		return result;
	}

	/**
	 * Converts a list of context URIs into a list of corresponding {@link XLiMeResource}s.
	 * 
	 * @param contextUrls
	 * @return
	 */
	private List<XLiMeResource> resolveContextUrls(List<String> contextUrls) {
		List<XLiMeResource> result = new ArrayList<>();
		for (String uri: contextUrls) {
			try {
				Optional<? extends XLiMeResource> optRes = resourceDao.retrieve(uri);
				if (optRes.isPresent()) result.add(optRes.get());
			} catch (Exception e) {
				if (log.isDebugEnabled()) 
					log.error("Error retrieving xLiMeResource from " + uri, e);
			}
		}
		return result;
	}
}
