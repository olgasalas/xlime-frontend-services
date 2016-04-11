package eu.xlime;

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

import eu.xlime.bean.EntityAnnotation;
import eu.xlime.bean.MediaItem;
import eu.xlime.bean.MediaItemListBean;
import eu.xlime.summa.bean.EntitySummary;

@Path("/services")
public class ServicesResource {

	private static final Logger log = LoggerFactory.getLogger(ServicesResource.class);
	
	private static final MediaItemDao mediaItemDao = new MediaItemDao();
	private static final MediaItemAnnotationDao mediaItemAnnotationDao = new MediaItemAnnotationDao();
	private static final SummaClient summaClient = new SummaClient();
	
	public ServicesResource() {
		log.info("Created " + this.getClass().getSimpleName());
	}
	
	@GET
	@Path("/mediaItem")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response mediaItem(@QueryParam("url") List<String> urls) {
		log.info("Received " + urls);
		MediaItemListBean milb = new MediaItemListBean();
		if (urls == null || urls.isEmpty()) milb.addError("No requested urls");
		for (String url: urls){
			try {
				Optional<? extends MediaItem> optMedItem = findMediaItem(url); 
				if (!optMedItem.isPresent()) throw new RuntimeException("Failed to find a media item for " + url);
				milb.addMediaItem(optMedItem.get());
			} catch (Exception e1) {
				e1.printStackTrace();
				milb.addError(e1.getLocalizedMessage());
			} 
		}
		if (milb.getMediaItems().isEmpty() && !milb.getErrors().isEmpty()) {
			Response errorResponse = Response.serverError().entity(milb.getErrors()).build();
			return errorResponse;
		}
		String msg = "Returning response for " + milb; 
		log.info(msg);
//		System.out.println(msg);
		return Response.ok(milb).build();
	}
	
	@GET
	@Path("/latestMediaItems")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response latestMediaItems(@QueryParam("minutes") int minutes) {
		log.info("Received latestMediaItems?minutes=" + minutes);
		int limit = 50;
		if (minutes <= 0) minutes = 5;
		List<String> urls = mediaItemDao.findLatestMediaItemUrls(minutes, limit);
		return mediaItem(urls);
	}
	
	@GET
	@Path("/entities")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response entities(@QueryParam("url") List<String> urls) {
		log.info("Received " + urls);
		List<EntitySummary> list = new ArrayList<EntitySummary>();
		if (urls == null || urls.isEmpty()) 
			return Response.serverError().entity("No requested urls").build();
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

	private Optional<? extends EntitySummary> findUIEntitySummary(String url) {
		return summaClient.retrieveSummary(url);
	}

	final Optional<? extends MediaItem> findMediaItem(String url) {
		if (url.startsWith("http://ijs.si/article/"))
			return mediaItemDao.findNewsArticle(url);
		else if (url.startsWith("http://vico-research.com/social/"))
			return mediaItemDao.findMicroPost(url);
		else if (url.startsWith("http://zattoo.com/program/"))
			return mediaItemDao.findTVProgram(url);
		else throw new RuntimeException("Cannot map url to a known xLiMe media-item type " + url);
	}
	
	final List<EntityAnnotation> findAnnotations(String url) {
		if (url.startsWith("http://ijs.si/article/"))
			return mediaItemAnnotationDao.findNewsArticleEntityAnnotations(url);
		else if (url.startsWith("http://vico-research.com/social/"))
			return mediaItemAnnotationDao.findMicroPostEntityAnnotations(url);
		else if (url.startsWith("http://zattoo.com/program/"))
//			return mediaItemAnnotationDao.findTVProgram(url);
			throw new RuntimeException("tv programmes not supported yet");
		else throw new RuntimeException("Cannot map url to a known xLiMe media-item type " + url);
	}
	
}