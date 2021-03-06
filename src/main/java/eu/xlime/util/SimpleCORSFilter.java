package eu.xlime.util;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleCORSFilter implements Filter {

	private static final Logger log = LoggerFactory.getLogger(SimpleCORSFilter.class);
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// Auto-generated method stub
		log.info("Initialised " + this);
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res,
			FilterChain chain) throws IOException, ServletException {
		HttpServletResponse response = (HttpServletResponse) res;
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST,GET,OPTIONS,DELETE");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with");
        chain.doFilter(req, res);
        if (log.isTraceEnabled()) {
        	log.trace("Added CORS headers, now has: " + response.getHeaderNames());
        }
	}

	@Override
	public void destroy() {
		// Auto-generated method stub
	}

}
