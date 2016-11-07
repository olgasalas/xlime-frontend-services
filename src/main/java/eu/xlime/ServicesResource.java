package eu.xlime;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import eu.xlime.bean.EntityAnnotation;
import eu.xlime.bean.MediaItem;
import eu.xlime.bean.MediaItemListBean;
import eu.xlime.bean.SearchResultBean;
import eu.xlime.bean.UrlLabel;
import eu.xlime.bean.XLiMeResource;
import eu.xlime.dao.MediaItemAnnotationDao;
import eu.xlime.dao.MediaItemDao;
import eu.xlime.dao.MediaItemDaoImpl;
import eu.xlime.dao.UIEntityDao;
import eu.xlime.dao.Filter;
import eu.xlime.dao.QueryDao;
import eu.xlime.dao.annotation.MediaItemAnnotationDaoImpl;
import eu.xlime.dao.entity.UIEntityDaoImpl;
import eu.xlime.datasum.CachedDatasetSummaryFactory;
import eu.xlime.datasum.DatasetSummaryFactory;
import eu.xlime.datasum.bean.DatasetSummary;
import eu.xlime.sphere.SpheresFactory;
import eu.xlime.sphere.bean.Spheres;
import eu.xlime.summa.SummaClient;
import eu.xlime.summa.bean.EntitySummary;
import eu.xlime.summa.bean.UIEntity;
import eu.xlime.util.ListUtil;
import eu.xlime.util.score.ScoredSet;

/**
 * Provides the REST services for this web application.
 * 
 * @author RDENAUX
 *
 */
@Path("/services")
public class ServicesResource {

	private static final Logger log = LoggerFactory.getLogger(ServicesResource.class);
	
	private static final MediaItemDao mediaItemDao = new MediaItemDaoImpl();
	private static final MediaItemAnnotationDao mediaItemAnnotationDao = new MediaItemAnnotationDaoImpl();
	private static final SummaClient summaClient = new SummaClient();
	private static final SpheresFactory spheresFactory = new SpheresFactory();	
	private static final UIEntityDao uiEntityDao = UIEntityDaoImpl.instance;
	private static final DatasetSummaryFactory dsSummaFactory = CachedDatasetSummaryFactory.instance;

	public ServicesResource() {
		log.info(String.format("Created %s with %s, %s, %s, %s and %s", this.getClass().getSimpleName(), 
				mediaItemDao, mediaItemAnnotationDao, summaClient, spheresFactory, uiEntityDao));
	}

	@GET
	@Path("/dataset-summary")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response datasetSummary(@QueryParam("ds") String datasetId) {
		log.info("Received /dataset-summary?ds=" + datasetId);
		DatasetSummary summa = new DatasetSummary();
		if ("sparql".equalsIgnoreCase(datasetId)) 
			summa = dsSummaFactory.createXLiMeSparqlSummary();
		else if ("mongo".equalsIgnoreCase(datasetId)) 
			summa = dsSummaFactory.createXLiMeMongoSummary();
		else return Response.serverError().entity("Unsupported datasetId " + datasetId).build();
		return Response.ok(summa).build();
	}

	
	@GET
	@Path("/mediaItem")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response mediaItem(@QueryParam("url") List<String> urls) {
		log.info("Received /mediaItems?url=" + urls);
		MediaItemListBean milb = lookupMediaItems(urls);
		return asResponse(urls, milb);
	}

	private Response asResponse(List<String> urls, MediaItemListBean milb) {
		if (milb.getMediaItems().isEmpty() && !milb.getErrors().isEmpty()) {
			log.info("returning errors" + milb.getErrors());
			Response errorResponse = Response.serverError().entity(milb.getErrors()).build();
			return errorResponse;
		}
		if (log.isDebugEnabled()) {
			String msg = String.format("Returning response for %s with %s media-items and %s errors.", urls, milb.getMediaItems().size(), milb.getErrors().size()); 
			log.debug(msg);
		}

		return Response.ok(milb).build();
	}

	private MediaItemListBean lookupMediaItems(List<String> urls) {
		MediaItemListBean milb = new MediaItemListBean();
		if (urls == null || urls.isEmpty()) milb.addError("No requested urls");
		for (String url: urls){
			try {
				Optional<? extends MediaItem> optMedItem = findMediaItem(url); 
				if (optMedItem.isPresent()) {
					milb.addMediaItem(optMedItem.get());
				} else {
					milb.addMessage("Failed to find media-item for " + url);
				}
			} catch (Exception e1) {
				log.error("Error retrieving media items for " + url, e1);
				milb.addError(e1.getLocalizedMessage());
			} 
		}
		return milb;
	}
	
	@GET
	@Path("/spheres")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response spheres(@QueryParam("context") List<String> context) {
		log.info("Received spheres?context=" + context);
		Spheres spheres = spheresFactory.buildSpheres(context);
		spheres.setUri("http://showcase.xlime.eu/spheres?context=" + context);
		spheres.setName("Showcase Spheres");
		spheres.setType("http://showcase.xlime.eu/sphere");
		return Response.ok(spheres).build();
	}
	
	@GET
	@Path("/latestMediaItems")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response latestMediaItems(@QueryParam("minutes") int minutes) {
		log.info("Received latestMediaItems?minutes=" + minutes);
		int limit = 50;
		if (minutes <= 0) minutes = 5;
		List<String> urls = mediaItemDao.findLatestMediaItemUrls(minutes, limit);
		log.info(String.format("Found %s recent media items", urls.size()));
		if (log.isTraceEnabled()) {
			log.trace("Found latest media items " + urls);
		}
		MediaItemListBean milb = lookupMediaItems(urls);
		return asResponse(urls, milb);
	}
	
	final int default_recent_minutes = 5; 
	final int default_recent_limit = 50;
	
	@GET
	@Path("/latestAnnotations")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response latestAnnotations(@QueryParam("minutes") int minutes) {
		log.info("Received latestAnnotations?minutes=" + minutes);
		int limit = default_recent_limit;
		if (minutes <= 0) minutes = default_recent_minutes;
		List<XLiMeResource> recentAnns = mediaItemAnnotationDao.findRecentAnnotations(minutes, limit);
		log.info(String.format("Found %s recent media items", recentAnns.size()));
		if (log.isTraceEnabled()) {
			log.trace("Found latest annotations " + recentAnns);
		}
		
		SearchResultBean bean = new SearchResultBean();
		bean.getErrors();
		bean.getMediaItems();
		bean.getAnnotations().addAll(recentAnns);
		bean.getEntities();
		
		return Response.ok(bean).build();
	}
	
	
	@GET
	@Path("/entities")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response entities(@QueryParam("url") List<String> urls) {
		log.info("Received /entities?url= " + urls);
		List<EntitySummary> list = new ArrayList<EntitySummary>();
		if (urls == null || urls.isEmpty()) 
			return Response.serverError().entity("No requested urls").build();
		int limit = 10;
		if (urls.size() > limit) {
			urls = urls.subList(0, limit);
		}
		for (String url: urls){
			try {
				Optional<? extends EntitySummary> optEntitySummary = findUIEntitySummary(url); 
				if (!optEntitySummary.isPresent()) throw new RuntimeException("Failed to find a KB entity for " + url);
				list.add(optEntitySummary.get());
			} catch (Exception e1) {
				e1.printStackTrace(); 
//				milb.addError(e1.getLocalizedMessage()); //gets ignored
				throw e1;
			} 
		}
		String msg = "Returning response for " + list; 
		log.info(msg);
//		System.out.println(msg);
		return Response.ok(list).build();
	}
	
	/**
	 * Returns a list of annotations for the input media-items.
	 * 
	 * @param urls
	 * @return
	 */
	@GET
	@Path("/annotationsFor")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response annotationsFor(@QueryParam("url") List<String> urls) {
		log.info("Received annotatationsFor with urls " + urls);
		MediaItemListBean milb = new MediaItemListBean();
		if (urls == null || urls.isEmpty()) milb.addError("No requested urls");
		List<EntityAnnotation> list = new ArrayList<>();
		for (String url: urls){
			try {
				list.addAll(findAnnotations(url));
			} catch (Exception e1) {
				e1.printStackTrace();
				throw e1;
			}
		}
		return Response.ok(list).build();
	}	

	/**
	 * Returns a list of media items and/or known KBentities from an input query (input: text or KB entity)
	 * 
	 * @param query
	 * @return
	 */
	@GET
	@Path("/search")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response search(@QueryParam("q") String query, @QueryParam("dateBefore") String dateBefore,
			@QueryParam("dateAfter") String dateAfter, @QueryParam("language") String language) throws ParseException {
		log.info("Received /search?q=" + query);
		List<String> foundMedItemUrls = ImmutableList.of();
		List<UIEntity> ents = ImmutableList.of();
		List<XLiMeResource> annotations = ImmutableList.of();
		if (query == null || query.isEmpty()) {
			foundMedItemUrls = mediaItemDao.findLatestMediaItemUrls(default_recent_minutes, default_recent_limit);
			annotations = mediaItemAnnotationDao.findRecentAnnotations(default_recent_minutes, default_recent_limit);
		} else {
			Filter filter = new Filter();
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

			if (dateBefore != null) filter.setDateBefore(df.parse(dateBefore));
			if (dateAfter != null) filter.setDateAfter(df.parse(dateAfter));
			if (language != null) filter.setLanguage(language);
			
			QueryDao q = new QueryDao();
			q.setQuery(query);
			q.setFilter(filter);
			
			foundMedItemUrls = findMediaItemUrls(q).asList();
			ents = findEntities(query);
			annotations = findAnnotationsByText(q);
		}
		
		MediaItemListBean milb = lookupMediaItems(foundMedItemUrls);
		
		SearchResultBean bean = new SearchResultBean();
		bean.getErrors().addAll(milb.getErrors());
		bean.getMediaItems().addAll(milb.getMediaItems());
		bean.getAnnotations().addAll(annotations);
		bean.getEntities().addAll(ents);
		
		return Response.ok(bean).build();
	}

	private List<XLiMeResource> findAnnotationsByText(QueryDao query) {
		@SuppressWarnings("unchecked")
		List<XLiMeResource> result = new ListUtil().weave(
				mediaItemAnnotationDao.findASRAnnotationsByText(query),
				mediaItemAnnotationDao.findOCRAnnotationsByText(query),
				mediaItemAnnotationDao.findSubtitleSegmentsByText(query));

		return ImmutableList.copyOf(result);
	}

	private List<UIEntity> findEntities(String query) {
		List<UrlLabel> urlLabels = uiEntityDao.autoCompleteEntities(query);
		List<UIEntity> ents = new ArrayList<>();
		if (!urlLabels.isEmpty()) 
			for (UIEntity ent: UIEntityDaoImpl.instance.retrieveFromUris(mapUris(urlLabels.subList(0, Math.min(5, urlLabels.size()))))) {
				ents.add(ent);
		}
		return ents;
	}

	private List<String> mapUris(List<UrlLabel> urlLabels) {
		List<String> result = new ArrayList<String>();
		for (UrlLabel ul: urlLabels) {
			result.add(ul.getUrl());
		}
		return result;
	}

	private ScoredSet<String> findMediaItemUrls(QueryDao query) {
		ScoredSet<String> foundMedItemUrls;
		if(query.getQuery().startsWith("http://"))			
			foundMedItemUrls = mediaItemAnnotationDao.findMediaItemUrlsByKBEntity(query.getQuery());
		else
			foundMedItemUrls = mediaItemDao.findMediaItemUrlsByText(query);
		return foundMedItemUrls;
	}

	private Optional<? extends EntitySummary> findUIEntitySummary(String url) {
		Optional<EntitySummary> result = Optional.absent();
		try {
			result = summaClient.retrieveSummary(url, 2000);
		} catch (Throwable e) {
			log.warn("Failed to use summaClient", e);
		}
		if (result.isPresent()) return result;
		Optional<UIEntity> optUiEnt = uiEntityDao.retrieveFromUri(url);
		if (optUiEnt.isPresent()) {
			return Optional.of(EntitySummary.emptyFrom(optUiEnt.get()));
		} else return Optional.absent();
	}

	final Optional<? extends MediaItem> findMediaItem(String url) {
		return mediaItemDao.findMediaItem(url);
	}
	
	final List<EntityAnnotation> findAnnotations(String url) {
		return mediaItemAnnotationDao.findMediaItemEntityAnnotations(url);
	}
	
}
