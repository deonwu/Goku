package org.goku.socket;

import static org.goku.http.BaseRouteServlet.SESSION_ID;
import static org.goku.http.BaseRouteServlet.SESSION_USER;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.goku.core.model.User;

public class DummyHttpRequest implements HttpServletRequest {
	private Map<String, String> param = null;
	private  SocketClient client = null;
	//private Map<String, Object> attrs = new HashMap<String, Object>(); 
	
	public DummyHttpRequest(Map<String, String> param, SocketClient client){
		this.param = param;
		this.client = client;
	}

	public Object getAttribute(String arg0) {
		return null;
	}

	@SuppressWarnings("rawtypes")
	public Enumeration getAttributeNames() {
		return null;
	}

	public String getCharacterEncoding() {
		return "utf8";
	}

	public int getContentLength() {
		return 0;
	}

	public String getContentType() {
		return null;
	}

	public ServletInputStream getInputStream() throws IOException {
		return null;
	}

	public String getLocalAddr() {
		InetSocketAddress addr = (InetSocketAddress)client.socket.socket().getLocalSocketAddress();
		if(addr != null){
			return addr.getAddress().getHostAddress();
		}		
		return null;
	}

	public String getLocalName() {
		return null;
	}

	public int getLocalPort() {
		return 0;
	}

	public Locale getLocale() {
		return null;
	}

	@SuppressWarnings("rawtypes")
	public Enumeration getLocales() {
		return null;
	}

	public String getParameter(String arg0) {
		return this.param.get(arg0);
	}

	@SuppressWarnings("rawtypes")
	public Map getParameterMap() {
		return this.param;
	}

	@SuppressWarnings("rawtypes")
	public Enumeration getParameterNames() {
		final Iterator iter = this.param.keySet().iterator();
		return new Enumeration(){
			@Override
			public boolean hasMoreElements() {
				return iter.hasNext();
			}

			@Override
			public Object nextElement() {
				return iter.next();
			}			
		};
	}

	public String[] getParameterValues(String arg0) {
		return null;
	}

	public String getProtocol() {
		return null;
	}

	public BufferedReader getReader() throws IOException {
		return null;
	}

	/**
	 * @deprecated
	 */	
	public String getRealPath(String arg0) {
		return null;
	}

	public String getRemoteAddr() {
		return this.client.socket.socket().getInetAddress().getHostAddress();
	}

	public String getRemoteHost() {
		return null;
	}

	public int getRemotePort() {
		return 0;
	}

	public RequestDispatcher getRequestDispatcher(String arg0) {
		return null;
	}

	public String getScheme() {
		return null;
	}

	public String getServerName() {
		return null;
	}

	public int getServerPort() {
		return 0;
	}

	public boolean isSecure() {
		return false;
	}

	public void removeAttribute(String arg0) {

	}

	public void setAttribute(String name, Object value) {
		if(name.equals(SESSION_ID)){
			if(value != null){
				this.client.sessionId = (String)value;
			}else {
				this.client.sessionId = null;
			}
		}else if(name.equals(SESSION_USER)){
			if(value != null){
				this.client.loginUser = (User)value;
			}else {
				this.client.loginUser = null;
			}
		}
	}

	public void setCharacterEncoding(String encoding)
			throws UnsupportedEncodingException {
		//validate the encoding.
		new OutputStreamWriter(new ByteArrayOutputStream(0), 
				encoding);
		this.client.encoding = encoding;
	}

	public String getAuthType() {
		return null;
	}

	public String getContextPath() {
		return null;
	}

	public Cookie[] getCookies() {
		return null;
	}

	public long getDateHeader(String arg0) {
		return 0;
	}

	public String getHeader(String arg0) {
		return null;
	}

	@SuppressWarnings("rawtypes")
	public Enumeration getHeaderNames() {
		return null;
	}

	@SuppressWarnings("rawtypes")
	public Enumeration getHeaders(String arg0) {
		return null;
	}

	public int getIntHeader(String arg0) {
		return 0;
	}

	public String getMethod() {
		return "POST";
	}

	public String getPathInfo() {
		return "/";
	}

	public String getPathTranslated() {
		return null;
	}

	public String getQueryString() {
		return null;
	}

	public String getRemoteUser() {
		return null;
	}

	public String getRequestURI() {
		return null;
	}

	public StringBuffer getRequestURL() {
		return null;
	}

	public String getRequestedSessionId() {
		return null;
	}

	public String getServletPath() {
		return "/";
	}

	public HttpSession getSession() {
		return null;
	}

	public HttpSession getSession(boolean arg0) {
		return null;
	}

	public Principal getUserPrincipal() {
		return null;
	}

	public boolean isRequestedSessionIdFromCookie() {
		return false;
	}

	public boolean isRequestedSessionIdFromURL() {
		return false;
	}
	
	/**
	 * @deprecated
	 */
	public boolean isRequestedSessionIdFromUrl() {
		return false;
	}

	public boolean isRequestedSessionIdValid() {
		return false;
	}

	public boolean isUserInRole(String arg0) {
		return false;
	}

}
