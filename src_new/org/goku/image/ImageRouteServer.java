package org.goku.image;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.BaseStation;
import org.goku.core.model.SystemLog;
import org.goku.db.DataStorage;
import org.goku.http.HTTPRemoteClient;
import org.goku.http.SimpleHTTPServer;
import org.goku.http.StartupListener;
import org.goku.settings.Settings;
import org.goku.socket.SimpleSocketServer;
import org.goku.socket.SocketManager;

public class ImageRouteServer {
	private Log log = LogFactory.getLog("main");
	private static ImageRouteServer ins = null;
	public Map<String, ASC100Client> clients = Collections.synchronizedMap(new HashMap<String, ASC100Client>());
	
	public Settings settings = null;
	public DataStorage storage = null;	
	public HTTPRemoteClient master = null;
	public AlarmMonitorCenter manager = null;
	public ASC100MX mx = null;
	public SimpleHTTPServer httpServer = null;
	public SocketManager socketManager = null;
	public SimpleSocketServer socketServer = null;	
	private ThreadPoolExecutor threadPool = null; 
	private boolean running = true;
	private static final String servelt = "org.goku.video.DefaultRouteServerServlet";
	
	private int remotePort = 0;	
	private int localPort = 0;	
	
	public static ImageRouteServer getInstance(){
		return ins;
	}
	
	public ImageRouteServer(Settings settings){
		ins = this;
		this.settings = settings;
	}
	
	public void startUp() throws Exception{
		log.info("Starting image routing server...");	
		
		log.info("init DB connection...");
		this.storage = DataStorage.create(settings);
		this.storage.checkConnect();
		SystemLog.dataStorage = storage;
		
		int core_thread_count = settings.getInt(Settings.CORE_ROUTE_THREAD_COUNT, 50);
		
		log.info("init thread pool, core thread count " + core_thread_count);
		threadPool = new ThreadPoolExecutor(
				core_thread_count,
				settings.getInt(Settings.MAX_ROUTE_THREAD_COUNT, 500),
				60, 
				TimeUnit.SECONDS, 
				new LinkedBlockingDeque<Runnable>(core_thread_count * 2)
				);
		
		socketManager = new SocketManager(threadPool);
		threadPool.execute(socketManager);		
				
		String masterUrl = settings.getString(Settings.MASTER_SERVER_URL, "http://127.0.0.1:8080");
		log.info("Check master server in running, url:" + masterUrl);
		master = new HTTPRemoteClient(masterUrl);
		if(master.checkConnection()){
			log.info("Connected master server.");
		}else {
			log.warn("Failed to connect master server.");
		}
		
		localPort = settings.getInt(Settings.UDP_LOCAL_PORT, 0);
		remotePort = settings.getInt(Settings.UDP_REMOTE_PORT, 0);		
		log.info(String.format("MX remote UDP port:%s, local UDP port", remotePort, localPort));
		
		mx = new ASC100MX(remotePort, localPort);
		
		log.info("Starting alarm manager server...");
		manager = new AlarmMonitorCenter(threadPool);
		threadPool.execute(manager);
		
		int port = settings.getInt(Settings.LISTEN_PORT, 8000);
		socketServer = new SimpleSocketServer(socketManager, port);
		socketServer.setServlet(servelt);
		threadPool.execute(socketServer);
		
		final int httpPort = settings.getInt(Settings.HTTP_PORT, 8083);
		log.info("Start http server at port " + httpPort);
		
		httpServer = new SimpleHTTPServer("", httpPort);
		httpServer.setServlet(servelt);
		httpServer.addStartupListener(new StartupListener(){
			@Override
			public void started() {
				master.registerRoute("", httpPort, "image", socketServer.listenPort + "");
				log.info("started http...");	
			}
		});
		
		threadPool.execute(httpServer);
		while(this.running){
			synchronized(this){
				this.wait();
			}
		}
		log.info("halt");			
	}
	
	public boolean addMonitorClient(String uuid){
		BaseStation station = (BaseStation)storage.load(BaseStation.class, uuid);
		if(station != null && station.devType == 2){
			ASC100Client client = new ASC100Client(station);
			this.clients.put(uuid, client);
			this.manager.addClient(client);
			mx.register(client);
			
			return true;
		}else if(station == null){
			log.warn("Not found base station by uuid '" + uuid + "'");			
		}else {
			log.warn("Not a vedio base station '" + uuid + "'");
		}
		return false;		
		//client.connect(selector);
	}

}
