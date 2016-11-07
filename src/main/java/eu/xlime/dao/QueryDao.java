package eu.xlime.dao;

import com.google.common.base.Optional;

public class QueryDao {
	private String query;
	private Filter filter;
	
	public String getQuery() {
		return query;
	}
	public void setQuery(String query) {
		this.query = query;
	}
	public Filter getFilter() {
		return filter;
	}
	public void setFilter(Filter filter) {
		this.filter = filter;
	}
}
