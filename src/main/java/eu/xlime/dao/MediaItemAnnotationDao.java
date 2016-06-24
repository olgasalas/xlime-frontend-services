package eu.xlime.dao;

import java.util.List;

import com.google.common.base.Optional;

import eu.xlime.bean.ASRAnnotation;
import eu.xlime.bean.EntityAnnotation;
import eu.xlime.bean.OCRAnnotation;
import eu.xlime.bean.SubtitleSegment;
import eu.xlime.bean.TVProgramBean;
import eu.xlime.summa.bean.UIEntity;
import eu.xlime.util.score.ScoredSet;

public interface MediaItemAnnotationDao {

	/**
	 * Returns {@link EntityAnnotation}s for a given media item, specified by its xLiMe Url.
	 * 
	 * The Dao implementation is free to return a subset of all the {@link EntityAnnotation}s
	 * and to return them in any order; however, we suggest returning them sorted by descending 
	 * score (i.e. entity annotations with a higher score should be at the beginning of the list). 
	 *  
	 * @param url
	 * @return
	 */
	List<EntityAnnotation> findMediaItemEntityAnnotations(String mediaItemUrl);

	List<EntityAnnotation> findMicroPostEntityAnnotations(String microPostUrl);

	List<EntityAnnotation> findNewsArticleEntityAnnotations(String newsArticleUrl);
	
	/**
	 * 
	 * @param subtitleTrackUrl
	 * @return
	 */
	List<EntityAnnotation> findSubtitleTrackEntityAnnotations(String subtitleTrackUrl);
	
	/**
	 * 
	 * @param kbEntity
	 * @return
	 */
	List<EntityAnnotation> findEntityAnnotationsFor(UIEntity kbEntity);
	
	/**
	 * Find URLs of {@link MediaItem}s which have been annotated with a entity, given by 
	 * its entityUrl.
	 * 
	 * @param entityUrl
	 * @return
	 */
	ScoredSet<String> findMediaItemUrlsByKBEntity(final String entityUrl);

	Optional<ASRAnnotation> findASRAnnotation(String mediaItemUri);

	List<OCRAnnotation> findOCRAnnotationsFor(TVProgramBean mediaResource);

	Optional<OCRAnnotation> findOCRAnnotation(String ocrAnnotUri);
	
	/**
	 * Finds the subtitle segments for a given tvProgram 
	 * @param tvProgUri
	 * @return
	 */
	List<SubtitleSegment> findSubtitleSegmentsForTVProg(String tvProgUri);
	
	/**
	 * Finds {@link SubtitleSegment}s for a given keyword text query
	 * @param textQuery
	 * @return
	 */
	List<SubtitleSegment> findSubtitleSegmentsByText(String textQuery);	
	
	/**
	 * Finds a number of available {@link SubtitleSegment}s.
	 * 
	 * @return
	 */
	List<SubtitleSegment> findAllSubtitleSegments(int limit);

	/**
	 * Finds a number of available {@link SubtitleSegment}s which were broadcast between 
	 * two timestamps.
	 * 
	 * @param dateFrom
	 * @param dateTo
	 * @param limit
	 * @return
	 */
	List<SubtitleSegment> findAllSubtitleSegmentsByDate(long dateFrom, long dateTo, int limit);
	
}