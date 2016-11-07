package eu.xlime.dao.annotation;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;

import eu.xlime.bean.ASRAnnotation;
import eu.xlime.bean.EntityAnnotation;
import eu.xlime.bean.OCRAnnotation;
import eu.xlime.bean.SubtitleSegment;
import eu.xlime.bean.TVProgramBean;
import eu.xlime.bean.XLiMeResource;
import eu.xlime.dao.MediaItemAnnotationDao;
import eu.xlime.dao.QueryDao;
import eu.xlime.summa.bean.UIEntity;
import eu.xlime.util.CacheFactory;
import eu.xlime.util.score.ScoredSet;
import eu.xlime.util.score.ScoredSetImpl;

public class CachedMediaItemAnnotationDao extends AbstractMediaItemAnnotationDao implements MediaItemAnnotationDao {

	private static final Logger log = LoggerFactory.getLogger(CachedMediaItemAnnotationDao.class);
	
	private MediaItemAnnotationDao delegate;

	private static Cache<String, ScoredSet<String>> searchEntityCache = CacheFactory.instance.buildCache("searchEntityCache");
	private static Cache<String, ScoredSet<String>> searchStringCache = CacheFactory.instance.buildCache("searchStringCache");

	public CachedMediaItemAnnotationDao(MediaItemAnnotationDao delegate) {
		super();
		this.delegate = delegate;
	}

	@Override
	public List<XLiMeResource> findRecentAnnotations(int minutes, int limit) {
		return delegate.findRecentAnnotations(minutes, limit);
	}

	@Override
	public List<EntityAnnotation> findMicroPostEntityAnnotations(
			String microPostUrl) {
		return delegate.findMicroPostEntityAnnotations(microPostUrl);
	}

	@Override
	public List<EntityAnnotation> findNewsArticleEntityAnnotations(
			String newsArticleUrl) {
		return delegate.findNewsArticleEntityAnnotations(newsArticleUrl);
	}

	@Override
	public List<EntityAnnotation> findSubtitleTrackEntityAnnotations(
			String subtitleTrackUrl) {
		return delegate.findSubtitleTrackEntityAnnotations(subtitleTrackUrl);
	}

	@Override
	public List<EntityAnnotation> findAudioTrackEntityAnnotations(
			String audioTrackUrl) {
		return delegate.findAudioTrackEntityAnnotations(audioTrackUrl);
	}

	@Override
	public List<EntityAnnotation> findVideoTrackEntityAnnotations(
			String videoTrackUrl) {
		return delegate.findVideoTrackEntityAnnotations(videoTrackUrl);
	}

	@Override
	public List<EntityAnnotation> findEntityAnnotationsFor(UIEntity kbEntity) {
		return delegate.findEntityAnnotationsFor(kbEntity);
	}

	@Override
	public ScoredSet<String> findMediaItemUrlsByKBEntity(final String entityUrl) {
		Callable<? extends ScoredSet<String>> valueLoader = new Callable<ScoredSet<String>>() {
			@Override
			public ScoredSet<String> call() throws Exception {
				return delegate.findMediaItemUrlsByKBEntity(entityUrl);
			}
		};

		try {
			return searchEntityCache.get(entityUrl, valueLoader);
		} catch (ExecutionException e) {
			log.warn("Error loading searchEntity result for " + entityUrl, e);
			return ScoredSetImpl.empty();
		}
	}

	@Override
	public ScoredSet<ASRAnnotation> findASRAnnotationsForKBEntity(
			String entityUrl) {
		return delegate.findASRAnnotationsForKBEntity(entityUrl);
	}

	@Override
	public ScoredSet<OCRAnnotation> findOCRAnnotationForKBEntity(
			String entityUrl) {
		return delegate.findOCRAnnotationForKBEntity(entityUrl);
	}

	@Override
	public ScoredSet<SubtitleSegment> findSubtitleSegmentsForKBEntity(
			String entityUrl) {
		return delegate.findSubtitleSegmentsForKBEntity(entityUrl);
	}

	@Override
	public Optional<ASRAnnotation> findASRAnnotation(String mediaItemUri) {
		return delegate.findASRAnnotation(mediaItemUri);
	}

	@Override
	public List<ASRAnnotation> findAllASRAnnotations(int limit) {
		return delegate.findAllASRAnnotations(limit);
	}

	@Override
	public List<ASRAnnotation> findASRAnnotationsByText(QueryDao query) {
		return delegate.findASRAnnotationsByText(query);
	}

	@Override
	public List<ASRAnnotation> findASRAnnotationsForTVProg(String tvProgUri) {
		return delegate.findASRAnnotationsForTVProg(tvProgUri);
	}

	@Override
	public List<OCRAnnotation> findOCRAnnotationsFor(TVProgramBean mediaResource) {
		return delegate.findOCRAnnotationsFor(mediaResource);
	}

	@Override
	public Optional<OCRAnnotation> findOCRAnnotation(String ocrAnnotUri) {
		return delegate.findOCRAnnotation(ocrAnnotUri);
	}

	@Override
	public List<OCRAnnotation> findAllOCRAnnotations(int limit) {
		return delegate.findAllOCRAnnotations(limit);
	}

	@Override
	public List<OCRAnnotation> findOCRAnnotationsByText(QueryDao query) {
		return delegate.findOCRAnnotationsByText(query);
	}
	
	@Override
	public Optional<SubtitleSegment> findSubtitleSegment(
			String subtitleSegmentUri) {
		return delegate.findSubtitleSegment(subtitleSegmentUri);
	}

	@Override
	public List<SubtitleSegment> findSubtitleSegmentsForTVProg(String tvProgUri) {
		return delegate.findSubtitleSegmentsForTVProg(tvProgUri);
	}

	@Override
	public List<SubtitleSegment> findSubtitleSegmentsByText(QueryDao query) {
		return delegate.findSubtitleSegmentsByText(query);
	}

	@Override
	public List<SubtitleSegment> findAllSubtitleSegments(int limit) {
		return delegate.findAllSubtitleSegments(limit);
	}

	@Override
	public List<SubtitleSegment> findAllSubtitleSegmentsByDate(long dateFrom,
			long dateTo, int limit) {
		return delegate.findAllSubtitleSegmentsByDate(dateFrom, dateTo, limit);
	}

}
