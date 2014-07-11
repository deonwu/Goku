package org.goku.http;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.webapp.WebAppContext;

public class SimpleHTTPServer implements Runnable{
	//private Settings settings = null;
	private Log log = LogFactory.getLog("http");
	
	private int httpPort = 0;
	private String servlet = "";
	private StartupListener lisener = null;
	private Server server = null;
	private Collection<War> webApps = new ArrayList<War>();
	
	public SimpleHTTPServer(String bindHost,  int port){
		this.httpPort = port;
	}
	
	public void setServlet(String servlet){
		this.servlet = servlet;
	}
	
	public void addStartupListener(StartupListener l){
		this.lisener = l;
	}	
	
	public void addWar(String context, File war){
		War app = new War();
		app.context = context;
		app.war = war;
		this.webApps.add(app);
	}


	@Override
	public void run() {
		server = new Server(httpPort);
		/*
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(httpPort);
        connector.setStatsOn(false);
        connector.setAcceptors(1);
        //connector.setSoLingerTime(soLingerTime)
        
        //server.addConnector(connector);
         */
		
        Collection<Handler> handlers = new ArrayList<Handler>();
        
        for(War app: webApps){
        	WebAppContext context = new WebAppContext();
        	context.setServer(server);
        	context.setContextPath(app.context);
        	try {
				context.setWar(app.war.toURI().toURL().toExternalForm());
				handlers.add(context);
				//server.addHandler(context);
			} catch (MalformedURLException e) {
				log.error(e.toString(), e);
			}
        }
                
        ContextHandlerCollection contexts = new ContextHandlerCollection();

        Context root = new Context(contexts,"/", Context.NO_SECURITY|Context.NO_SESSIONS);
        ServletHolder holder = new ServletHolder();
        try {
			holder.setHeldClass(Class.forName(servlet));
		} catch (ClassNotFoundException e1) {
			log.error(e1.toString(), e1);
		}
        root.addServlet(holder, "/*");        
        
        handlers.add(root);

        contexts.setHandlers(handlers.toArray(new Handler[]{}));
        server.setHandler(contexts);
        
        try {
        	log.info("Start http server at " + httpPort);
			server.start();
			this.lisener.started();
		} catch (Exception e) {
			log.error(e.toString(), e);
		}
		
	}
	
	class War{
		String context;
		File war;
	}

}
