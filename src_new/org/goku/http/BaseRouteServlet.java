package org.goku.http;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.RouteRunningStatus;

public abstract class BaseRouteServlet extends HttpServlet{
	
	public static final String SESSION_ID = "session_id";
	public static final String SESSION_USER = "session_user";
	
	public static final String TEXT = "text/plain";
	public static final String HTML = "text/html";
	
	protected static final RouteRunningStatus runningStatus = new RouteRunningStatus();
	
	private static final long serialVersionUID = 1L;

	private final Map<String, Method> handler = new HashMap<String, Method>();
	
	private Log log = LogFactory.getLog("http");

	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
    	doPost(request, response);
    }
    
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
    	if(this.getStringParam(request, "en", null) != null){
    		response.setCharacterEncoding(request.getParameter("en"));
    	}
		response.setContentType(TEXT);
    	String action = request.getParameter("q");
		Method m = handler.get(action);
		log.debug("New http action:" + action);
		if(m == null && action != null){
			try {
				m = this.getClass().getMethod(action, new Class[]{HttpServletRequest.class, HttpServletResponse.class});
				handler.put(action, m);
			} catch (Exception e1) {
				log.error(e1, e1);
			}
		}
		if(m == null){
			index_page(request, response);
		}else {
			try {
				runningStatus.clientRequestCount(1);
				m.invoke(this, new Object[]{request, response});
			} catch (Exception e1) {
				log.error(e1, e1);
			}
		}
		//response.getWriter().flush();
		//response.getOutputStream().flush();
		response.flushBuffer();
    }
    
    protected abstract void index_page(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException;
    
    protected void static_serve(String path, String mimeType, HttpServletResponse response) throws IOException{
	    response.setContentType(mimeType == null ? TEXT : mimeType);
	    response.setCharacterEncoding("utf-8");
	    InputStream ins = this.getClass().getClassLoader().getResourceAsStream(path);
	    byte[] buffer = new byte[64 * 1024];
	    if(ins == null){
	    	//response.setStatus(HttpServletResponse.SC_NOT_FOUND);
	    	response.getWriter().println("Not found:" + path);
	    }else {
	    	if(response.getOutputStream() != null){
		    	for(int len = ins.read(buffer); len > 0; ){
		    		response.getOutputStream().write(buffer, 0, len);
		    		len = ins.read(buffer);
		    	}
	    	}else { //在Socket, 模式不能取到OutputStream.
	    		response.getWriter().println("Can't get OutputStream.");
	    	}
	    }
    }
    
    protected String getStringParam(HttpServletRequest request, String name, String def){
    	String val = request.getParameter(name);
    	if(val == null || val.trim().equals("")){
    		val = def;
    	}
    	return val;
    }
    
    protected int getIntParam(HttpServletRequest request, String name, int def){
    	String val = request.getParameter(name);
    	int intVal = def;
    	if(val != null && !val.trim().equals("")){
    		intVal = Integer.parseInt(val);
    	}
    	return intVal;
    }
    
    public void set_encoding(HttpServletRequest request, HttpServletResponse response) throws IOException{
    	String encode = this.getStringParam(request, "code", "utf8");
    	try{
    		request.setCharacterEncoding(encode);
    		response.getWriter().println("0:set encoding ok$" + encode);
    	}catch(Exception e){
    		response.getWriter().println("1:not supported encode");
    	}
    }
}
