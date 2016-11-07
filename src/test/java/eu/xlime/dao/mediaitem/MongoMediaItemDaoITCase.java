package eu.xlime.dao.mediaitem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.databind.util.ISO8601Utils;
import com.google.common.base.Optional;

import eu.xlime.bean.MediaItem;
import eu.xlime.bean.MicroPostBean;
import eu.xlime.bean.NewsArticleBean;
import eu.xlime.bean.TVProgramBean;
import eu.xlime.dao.Filter;
import eu.xlime.dao.QueryDao;
import eu.xlime.mongo.ConfigOptions;
import eu.xlime.util.score.ScoredSet;

public class MongoMediaItemDaoITCase {

	@Test
	@Ignore("set up mongodb correctly")
	public void testFindMediaItemByUrl() throws Exception {
		Properties props = new Properties();
		props.put(ConfigOptions.XLIME_MONGO_RESOURCE_DATABASE_NAME.getKey(), "brexit-xlimeress");
		MongoMediaItemDao dao = new MongoMediaItemDao(props);
		
		String url = "http://vico-research.com/social/000ce183-5642-326f-8803-e51069211aac";
		Optional<?> optMi = dao.findMediaItem(url);
		System.out.println(optMi);
		assertTrue(optMi.isPresent());
	}

	@Test
	public void testFindMicroPostsByDate() throws ParseException {
		Properties props = new Properties();
		props.put(ConfigOptions.XLIME_MONGO_RESOURCE_DATABASE_NAME.getKey(), "xlimeres");
		MongoMediaItemDao dao = new MongoMediaItemDao(props);
		
		long dateFrom = ISO8601Utils.parse("2016-06-01T07:00:00Z", new ParsePosition(0)).getTime();
		long dateTo =   ISO8601Utils.parse("2016-06-01T08:00:00Z", new ParsePosition(0)).getTime();
		List<MicroPostBean> mpbs = dao.findMicroPostsByDate(dateFrom, dateTo, 50);
		assertFalse(mpbs.isEmpty());
		System.out.println("Retrieved microposts " + mpbs.size());
		assertEquals(50, mpbs.size()); //microPosts.get(0).getUrl(), optIt.get().getUrl());
	}

	@Test
	public void testFindTVProgramsByDate() throws ParseException {
		Properties props = new Properties();
		props.put(ConfigOptions.XLIME_MONGO_RESOURCE_DATABASE_NAME.getKey(), "xlimeres");
		MongoMediaItemDao dao = new MongoMediaItemDao(props);
		
		long dateFrom = ISO8601Utils.parse("2016-06-10T10:00:00Z", new ParsePosition(0)).getTime();
		long dateTo =   ISO8601Utils.parse("2016-06-10T11:00:00Z", new ParsePosition(0)).getTime();
		long start = System.currentTimeMillis();
		List<TVProgramBean> tvbs = dao.findTVProgramsByDate(dateFrom, dateTo, 50);
		assertFalse(tvbs.isEmpty());
		System.out.println("Retrieved tvprogs " + tvbs.size() + " in " + (System.currentTimeMillis() - start) + "ms.");
		assertEquals(50, tvbs.size()); //microPosts.get(0).getUrl(), optIt.get().getUrl());
	}

	@Test
	public void test_findLatestMediaItemUrls() throws Exception {
		Properties props = new Properties();
		props.put(ConfigOptions.XLIME_MONGO_RESOURCE_DATABASE_NAME.getKey(), "xlimeres");
		MongoMediaItemDao dao = new MongoMediaItemDao(props);

		List<String> result = dao.findMostRecentMediaItemUrls(10, 30);
		
//		List<String> result = dao.findLatestMediaItemUrls(10, 30);
		assertFalse("" + result, result.isEmpty());
	}
	@Test
	public void testRecentMediaItemsBefore() throws ParseException {
		Properties props = new Properties();
		props.put(ConfigOptions.XLIME_MONGO_RESOURCE_DATABASE_NAME.getKey(), "xlimeres");
		MongoMediaItemDao dao = new MongoMediaItemDao(props);
		Date date = new Date();
		List<String> result = dao.findMediaItemsBefore(date, 50);
		assertFalse(result.isEmpty());
		System.out.println("Retrieved " + result.size() + " media items before " + date);
		List<MediaItem> mis = dao.findMediaItems(result);
		for (MediaItem mit: mis) {
			Date creationDate = getCreationDate(mit);
			assertTrue("creation date " + creationDate + " is not before " + date + "\nfor " + mit, date.after(creationDate));
		}
	}
	
	private Date getCreationDate(MediaItem mit) {
		if (mit instanceof MicroPostBean) {
			return ((MicroPostBean) mit).getCreated().timestamp;
		} else if (mit instanceof TVProgramBean) {
			return ((TVProgramBean) mit).getBroadcastDate().timestamp;
		} else if (mit instanceof NewsArticleBean) {
			return ((NewsArticleBean) mit).getCreated().timestamp;
		} throw new IllegalArgumentException();
	}

	@Test
	public void testNewsArticlesByDate() throws ParseException {
		Properties props = new Properties();
		props.put(ConfigOptions.XLIME_MONGO_RESOURCE_DATABASE_NAME.getKey(), "xlimeres");
		MongoMediaItemDao dao = new MongoMediaItemDao(props);
		System.out.println("collection has " + dao.count(NewsArticleBean.class) + " news articles.");
		long dateFrom = ISO8601Utils.parse("2016-06-01T10:00:00Z", new ParsePosition(0)).getTime();
		long dateTo =   ISO8601Utils.parse("2016-06-06T11:00:00Z", new ParsePosition(0)).getTime();
		long start = System.currentTimeMillis();
		List<NewsArticleBean> nabs = dao.findNewsArticlesByDate(dateFrom, dateTo, 50);
		assertFalse(nabs.isEmpty());
		System.out.println("Retrieved news " + nabs.size() + " in " + (System.currentTimeMillis() - start) + "ms.");
		assertEquals(50, nabs.size()); //microPosts.get(0).getUrl(), optIt.get().getUrl());
	}
	
	@Test
	public void testFindMediaItemsByDate() throws Exception {
		Properties props = new Properties();
		props.put(ConfigOptions.XLIME_MONGO_RESOURCE_DATABASE_NAME.getKey(), "xlimeres");
		MongoMediaItemDao dao = new MongoMediaItemDao(props);
		
		long dateFrom = ISO8601Utils.parse("2016-06-02T10:00:00Z", new ParsePosition(0)).getTime();
		long dateTo =   ISO8601Utils.parse("2016-06-05T11:00:00Z", new ParsePosition(0)).getTime();
		long start = System.currentTimeMillis();
		List<String> result = dao.findMediaItemsByDate(dateFrom, dateTo, 50);
		System.out.println(String.format("Retrieved %s mediaItemsUrls in %s ms.", result.size(), (System.currentTimeMillis() - start)));
		System.out.println("First 6: " + result.subList(0,  6));
		assertEquals(50, result.size());
	}
	
	@Test
	public void testFindLatestMediaItems() throws Exception {
		Properties props = new Properties();
		props.put(ConfigOptions.XLIME_MONGO_RESOURCE_DATABASE_NAME.getKey(), "xlimeres");
		MongoMediaItemDao dao = new MongoMediaItemDao(props);
		
		long start = System.currentTimeMillis();
		List<String> result = dao.findLatestMediaItemUrls(100000, 50);
		System.out.println(String.format("Retrieved %s mediaItemsUrls in %s ms.", result.size(), (System.currentTimeMillis() - start)));
		System.out.println("All : " + result);
		assertEquals(50, result.size());
	}
	
	@Test
	public void test_findMediaItemUrlsByText() throws ParseException {
		QueryDao q = new QueryDao();
		Filter f = new Filter();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		//Date d = df.parse("2016-08-08T06:33:24Z");
		//Date d = df.parse("2016-09-21T04:33:24Z");
		Date db = df.parse("2016-09-27T07:10:00Z");
		Date da = df.parse("2016-10-07T01:00:00Z");
		f.setDateBefore(db);
		f.setDateAfter(da);
		//f.setLanguage("en");
		q.setFilter(f);
		q.setQuery("merkel");
		testMediaitemUrlsByText(q);
	}

	private void testMediaitemUrlsByText(QueryDao query) {
		Properties props = new Properties();
		props.put(ConfigOptions.XLIME_MONGO_RESOURCE_DATABASE_NAME.getKey(), "xlimeres");
		MongoMediaItemDao dao = new MongoMediaItemDao(props);
		long start = System.currentTimeMillis();
		ScoredSet<String> result = dao.findMediaItemUrlsByText(query);
		System.out.println(String.format("Retrieved %s mediaItemsUrls in %s ms.", result.size(), (System.currentTimeMillis() - start)));
		System.out.println("All : " + result);
		for(int i = 0; i<result.size();i++){
			System.out.println(result.asList().get(i));
		}
		System.out.println(result.size());
		//assertEquals(30, result.size());
	}
		
}
