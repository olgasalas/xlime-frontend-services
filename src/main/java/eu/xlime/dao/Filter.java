package eu.xlime.dao;

import java.util.Date;

public class Filter {
	private Date dateBefore;
	private Date dateAfter;
	private String language;

	public Date getDateBefore() {
		return dateBefore;
	}
	public void setDateBefore(Date dateBefore) {
		this.dateBefore = dateBefore;
	}
	public Date getDateAfter() {
		return dateAfter;
	}
	public void setDateAfter(Date dateAfter) {
		this.dateAfter = dateAfter;
	}
	public String getLanguage() {
		return language;
	}
	public void setLanguage(String language) {
		this.language = language;
	}
	
}
