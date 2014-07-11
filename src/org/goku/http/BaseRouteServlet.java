package org.goku.http;

import java.io.File;
import java.io.FileInputStream;
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
		String uri = request.getRequestURI();
		log.debug("request uri:" + uri);
		if(uri.startsWith("/video/")){
			videoHandler(request, response);
		}else if(uri.startsWith("/static/")){
			staticHandler(uri.replaceFirst("/static/", ""), request, response);			
		}else {
			doPost(request, response);
		}
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
    
    protected void videoHandler(HttpServletRequest request, HttpServletResponse response) 
	throws ServletException, IOException {
    	response.getWriter().println(request.getRequestURI());
    }
    
	public void staticHandler(String path, HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException{
		File f = null;
		if(path.length() > 0){
			f = new File(System.getProperty("STATIC_ROOT", "."), path);
		}else {
			f = new File(System.getProperty("STATIC_ROOT", "."));
		}
		if(f.isFile()){
		    response.setCharacterEncoding("utf-8");
		    long last = this.getIntParam(request, "last", 0);		    
		    InputStream ins = new FileInputStream(f);
		    if(last > 0){
		    	long start = f.length() - last;
		    	if(start > 0){
		    		ins.skip(start);
		    	}
		    }
		    byte[] buffer = new byte[64 * 1024];
	    	if(response.getOutputStream() != null){
		    	for(int len = ins.read(buffer); len > 0; ){
		    		response.getOutputStream().write(buffer, 0, len);
		    		len = ins.read(buffer);
		    	}
	    	}else { //在Socket, 模式不能取到OutputStream.
	    		response.getWriter().println("Can't get OutputStream.");
	    	}
	    	ins.close();
		}else if(f.isDirectory()){
			response.setCharacterEncoding("utf-8");
			response.setContentType(HTML);
			response.getWriter().println("<html><title>文件列表" +
					path + "</title><body>");
			response.getWriter().println("<h1>目录：" + path + "</h1>");
			response.getWriter().println("<ul>");
			String stRoot = "/static/";
			if(path.length() > 0){
				stRoot += path;
			}
			
			for(File sub: f.listFiles()){
				if(sub.getName().startsWith(".")) continue;
				if(sub.isDirectory()){
					response.getWriter().println(String.format("<li><a href='%s%s/'>%s</a></li>", stRoot, sub.getName(), sub.getName()));
				}else {
					String last = String.format("<a href='%s%s?last=1024'>last</a>", stRoot, sub.getName());
					if(sub.getName().indexOf("log") == -1)last = "";
					response.getWriter().println(String.format("<li><a href='%s%s'>%s</a> -- %s %s</li>", stRoot, sub.getName(), sub.getName(), 
						formateSize(sub.length()), last));
				}
			}
			
			response.getWriter().println("</ul>");
			response.getWriter().println("</body></html>");
			
		}else {
			response.getWriter().println("Not found path");
		}
	}
    
	private String formateSize(long size){
		if(size == 0)return "";
		double b = size / 1024.0;
		String unit = "Kb";
		if(b > 1024){
			b = b / 1024.0;
			unit = "Mb";
		}
		return String.format("%s(%1.2f%s)", size, b, unit);
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
	    	ins.close();
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
