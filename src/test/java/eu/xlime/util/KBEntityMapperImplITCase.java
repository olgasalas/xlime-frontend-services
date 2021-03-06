package eu.xlime.util;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;

import com.google.common.base.Optional;

import eu.xlime.util.KBEntityMapperImpl;

public class KBEntityMapperImplITCase {

	@Test
	public void testToCanonicalEntity() {
		KBEntityMapper testObj = new KBEntityMapperImpl();
		Optional<String> optResult = testObj.toCanonicalEntityUrl("http://fr.wikipedia.org/wiki/Lünen");
		assertTrue(optResult.isPresent());
		assertEquals("http://dbpedia.org/resource/Lünen", optResult.get());
	}

	@Test
	public void testToCanonicalEntity2() {
		KBEntityMapper testObj = new KBEntityMapperImpl();
		Optional<String> optResult = testObj.toCanonicalEntityUrl("http://es.dbpedia.org/resource/Alemania");
		assertTrue(optResult.isPresent());
		assertEquals("http://dbpedia.org/resource/Germany", optResult.get());
	}

	@Test
	public void testToCanonicalEntity3() {
		KBEntityMapper testObj = new KBEntityMapperImpl();
		Optional<String> optResult = testObj.toCanonicalEntityUrl("http://dbpedia.org/resource/L%C3%BAcia_Santos");
		assertTrue(optResult.isPresent());
		assertEquals("http://dbpedia.org/resource/Lúcia_Santos", optResult.get());
	}

	@Test
	public void testToCanonicalEntity4() {
		KBEntityMapper testObj = new KBEntityMapperImpl();
		Optional<String> optResult = testObj.toCanonicalEntityUrl("http://dbpedia.org/resource/Silver%28band%29");
		assertTrue(optResult.isPresent());
		assertEquals("http://dbpedia.org/resource/Silver(band)", optResult.get());
	}
	
	@Test
	public void testExpandSameAs() {
		KBEntityMapper testObj = new KBEntityMapperImpl();
		String entUrl = "http://fr.wikipedia.org/wiki/Lünen";
		Set<String> sameAsUrls = testObj.expandSameAs(entUrl);
		System.out.println("SameAs " + entUrl + ": \n\t" + sameAsUrls);
		assertTrue(!sameAsUrls.isEmpty());
		assertTrue(sameAsUrls.size() > 20);
	}

	@Test
	public void testExpandSameAs2() {
		KBEntityMapper testObj = new KBEntityMapperImpl();
		String entUrl = "http://es.dbpedia.org/resource/Alemania";
		Set<String> sameAsUrls = testObj.expandSameAs(entUrl);
		System.out.println("SameAs " + entUrl + ": \n\t" + sameAsUrls);
		assertTrue(!sameAsUrls.isEmpty());
//		assertEquals(42, sameAsUrls.size());
		assertEquals(3, sameAsUrls.size());
	}
	
	@Test
	public void testGetDBpediaSameAsSet() throws Exception {
		SparqlKBEntityMapper testObj = new SparqlKBEntityMapper();
		String entUrl = "http://fr.dbpedia.org/resource/Lünen";
		Set<String> sameAsUrls = testObj.getDBpediaSameAsSet(entUrl);
		System.out.println("SameAs " + entUrl + ": \n\t" + sameAsUrls);
		assertTrue(!sameAsUrls.isEmpty());
		assertTrue(sameAsUrls.size() > 10);
	}

	@Test
	public void testGetDBpediaSameAsSet2() throws Exception {
		SparqlKBEntityMapper testObj = new SparqlKBEntityMapper();
		String entUrl = "http://dbpedia.org/resource/Lünen";
		Set<String> sameAsUrls = testObj.getDBpediaSameAsSet(entUrl);
		System.out.println("SameAs " + entUrl + ": \n\t" + sameAsUrls);
		assertTrue(!sameAsUrls.isEmpty());
		assertTrue(sameAsUrls.size() > 10);
	}
	
}
