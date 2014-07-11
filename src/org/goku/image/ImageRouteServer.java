package org.goku.image;

import java.util.Collections;
import java.util.Date;
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
	public AlarmMonitorCenter alarmManager = null;
	public ASC100MX mx = null;
	public SimpleHTTPServer httpServer = null;
	public SocketManager socketManager = null;
	public SimpleSocketServer socketServer = null;
	public FileManager fileManager = null;
	public int max_retry_times = 0;
	private ThreadPoolExecutor threadPool = null; 
	public String groupName = null;
	
	private boolean running = true;
	private static final String servelt = "org.goku.image.ImageRouteServerServlet";
	
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
		
		groupName = settings.getString(Settings.GROUP_NAME, "image");
		log.info("Routing group name:" + groupName);		
		
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
				new LinkedBlockingDeque<Runnable>(core_thread_count * 200)
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
		log.info(String.format("MX remote UDP port:%s, local UDP:%s", remotePort, localPort));		
		log.info("Max retry times:" + max_retry_times);
		mx = new ASC100MX(remotePort, localPort, threadPool);
		threadPool.execute(mx);
		
		fileManager = new FileManager(settings, storage);
		
		log.info("Starting alarm manager server...");
		alarmManager = new AlarmMonitorCenter(threadPool);
		threadPool.execute(alarmManager);
		//this.alarmCheckPeriod = s.getInt(Settings.ALARM_CHECK_PERIOD, 3);
		alarmManager.setAlarmCheckTime(settings.getInt(Settings.ALARM_CHECK_PERIOD, 10));
		max_retry_times = settings.getInt("max_retry_times", 5);
		
		int port = settings.getInt(Settings.LISTEN_PORT, 8000);
		socketServer = new SimpleSocketServer(socketManager, port);
		socketServer.imageAdapter = new ImageSocketAdaptor();
		socketServer.setServlet(servelt);
		threadPool.execute(socketServer);
		log.info("Start scoket server at port " + port);
		
		final int httpPort = settings.getInt(Settings.HTTP_PORT, 8083);
		log.info("Start http server at port " + httpPort);
		
		httpServer = new SimpleHTTPServer("", httpPort);
		httpServer.setServlet(servelt);
		httpServer.addStartupListener(new StartupListener(){
			@Override
			public void started() {
				threadPool.execute(new Runnable(){
						public void run(){
							while(true){
								if(master.registerRoute("", httpPort, groupName, socketServer.listenPort + "")){
									log.info("Register to route ok");
									break;
								}
								log.info("Failed to connect master, try again 5 seconds later.");
								try {
									Thread.sleep(5000);
								} catch (InterruptedException e1) {
								}
							}
						}
				});
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
			if(!this.clients.containsKey(uuid)){
				this.clients.put(uuid, client);
				this.alarmManager.addClient(client);
				client.addListener(activeReport);
				if(log.isTraceEnabled()){
					client.addListener(new RawDataLogger(uuid + "_" + client.getClientId().replace('.', '_')));
				}
				mx.register(client);
				client.maxRetryTime = max_retry_times;
			}else {
				log.warn("The client already added before, uuid:" + uuid);
			}
			return true;
		}else if(station == null){
			log.warn("Not found base station by uuid '" + uuid + "'");			
		}else {
			log.warn("Not a image base station '" + uuid + "'");
		}
		return false;		
		//client.connect(selector);
	}
	
	public ASC100Client getMonitorClient(String uuid){
		return clients.get(uuid);
	}	
	
	/**
	 * 删除一个监控视频，例如，需要负载调度，或连接错误时。
	 * @param client
	 */
	public void removeMonitorClient(ASC100Client client){
	}	
	
	
	private ImageClientListener activeReport = new AbstractImageListener(){
		public void active(final ImageClientEvent event) {
			threadPool.execute(new Runnable(){
				@Override
				public void run() {
					try {
						event.source.info.connectionStatus = "connected";
						event.source.info.lastActive = new Date(System.currentTimeMillis());
						storage.save(event.source.info, 
									 new String[]{"connectionStatus", 
												  "lastActive"});
					} catch (Throwable e) {
						log.error("Failed to update client status." + e.toString(), e);
					}
				}});
		};
		
		public void connectionError(final ImageClientEvent event) {
			threadPool.execute(new Runnable(){
				@Override
				public void run() {
					try {
						event.source.info.connectionStatus = "timeout";
						event.source.info.lastActive = new Date(System.currentTimeMillis());
						storage.save(event.source.info, 
									 new String[]{"connectionStatus", 
												  "lastActive"});
					} catch (Throwable e) {
						log.error("Failed to update client status." + e.toString(), e);
					}
				}});			
		}
	};	

}
