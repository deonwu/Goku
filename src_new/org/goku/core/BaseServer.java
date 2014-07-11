package org.goku.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.settings.Settings;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.ServletHandler;

import static org.goku.settings.Settings.HTTP_PORT;

public abstract class BaseServer {
	private Settings settings = null;
	private Log log = LogFactory.getLog("main");
	
	public BaseServer(Settings settings){
		
	}	
	public abstract void startUp();
	
	protected void startHttpServer(Object servlet){
		int httpPort = settings.getInt(HTTP_PORT, 8083);		
		Server server = new Server(httpPort);
		System.out.println("Listening HTTP port:" + httpPort);
		
        ServletHandler handler=new ServletHandler();
        server.setHandler(handler);
        handler.addServletWithMapping("org.socialnet.servlet.StatusServlet", "/*");
        try {
			server.start();
	        server.join();
	        System.out.println("Shutdown HTTP service..");
		} catch (Exception e) {
			System.err.println("Stop HTTP service, with error:" + e.toString());
		}
	}
}
