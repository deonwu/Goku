package org.goku.video;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.AlarmDefine;
import org.goku.core.model.BaseStation;
import org.goku.core.model.MonitorChannel;
import org.goku.core.model.RouteRunningStatus;
import org.goku.core.model.SystemLog;
import org.goku.db.DataStorage;
import org.goku.http.HTTPRemoteClient;
import org.goku.http.SimpleHTTPServer;
import org.goku.http.StartupListener;
import org.goku.settings.Settings;
import org.goku.socket.SimpleSocketServer;
import org.goku.socket.SocketManager;
import org.goku.socket.proxy.SocketProxyServer;
import org.goku.video.odip.AbstractMonitorListener;
import org.goku.video.odip.MonitorClient;
import org.goku.video.odip.MonitorClientEvent;
import org.goku.video.odip.MonitorClientListener;
import org.goku.video.odip.VideoRoute;

/**
 * 路由服务器，处理基站的连接，和告警查询。录像保存等操作。如果监控客户端，需要连接到
 * 监控。由此服务器转发。
 * 服务启动时，只是初始化一个HTTP服务，具体需要监控的终端，由控制服务器，通过HTTP接口
 * 调度后，才开始收集终端告警。
 * @author deon
 */
public class VideoRouteServer {
	private Log log = LogFactory.getLog("main");
	private static VideoRouteServer ins = null;
	public Map<String, MonitorClient> clients = Collections.synchronizedMap(new HashMap<String, MonitorClient>());
	
	public Settings settings = null;
	public SocketManager socketManager = null;
	public SimpleSocketServer socketServer = null;
	public DataStorage storage = null;	
	public HTTPRemoteClient master = null;
	public AlarmMonitorCenter alarmManager = null;	
	public VideoRecorderManager recordManager = null;
	public VideoEncodingService liveVideoEncoder = null;
	public SocketProxyServer proxyServer = null;
	
	public SimpleHTTPServer httpServer = null;
	public String groupName = null;
	
	private ThreadPoolExecutor threadPool = null; 
	private boolean running = true;
	
	private static final String servelt = "org.goku.video.DefaultRouteServerServlet";
	
	public static VideoRouteServer getInstance(){
		return ins;
	}
	
	public VideoRouteServer(Settings settings){
		ins = this;
		this.settings = settings;
	}
	
	public void startUp() throws Exception{
		log.info("Starting video routing server...");
		
		groupName = settings.getString(Settings.GROUP_NAME, "");		
		log.info("Routing group name:" + groupName);
				
		log.info("init DB connection...");
		this.storage = DataStorage.create(settings);
		this.storage.checkConnect();
		SystemLog.dataStorage = storage;
		AlarmDefine.initAlarmDefine(storage);
		
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
		
		log.info("Start video record manager..");
		recordManager = new VideoRecorderManager(settings, storage);
		log.info("Record filename format:" + recordManager.pattern);
		threadPool.execute(recordManager);
		
		String masterUrl = settings.getString(Settings.MASTER_SERVER_URL, "http://127.0.0.1:8080");
		log.info("Check master server in running, url:" + masterUrl);
		master = new HTTPRemoteClient(masterUrl);
		if(master.checkConnection()){
			log.info("Connected master server.");
		}else {
			log.warn("Failed to connect master server.");
		}
		
		log.info("Starting alarm manager server...");
		alarmManager = new AlarmMonitorCenter(threadPool, settings);
		threadPool.execute(alarmManager);
		
		int port = settings.getInt(Settings.LISTEN_PORT, 8000);
		socketServer = new SimpleSocketServer(socketManager, port);
		socketServer.setServlet(servelt);
		socketServer.setRecorderManager(recordManager);
		threadPool.execute(socketServer);
		log.info("Start scoket server at port " + port);
		
		String ffmpeg = settings.getString(Settings.FFMPEG_PATH, "ffmpeg");
		liveVideoEncoder = new VideoEncodingService(threadPool, ffmpeg);
		log.info("Start live video encoder...");
		threadPool.execute(liveVideoEncoder);
		
		int startPort = settings.getInt(Settings.PROXY_PORT_START, 9000);
		int endPort = settings.getInt(Settings.PROXY_PORT_END, 9000);
		
		int timeOut = settings.getInt(Settings.PROXY_TIMEOUT, 300);
		proxyServer = new SocketProxyServer(socketManager, startPort, endPort);
		proxyServer.timeOut = timeOut * 1000;
		threadPool.execute(proxyServer);
		log.info(String.format("Start video proxy server, %s->%s, timeout:%ss.", startPort, endPort, timeOut));
		
		final int httpPort = settings.getInt(Settings.HTTP_PORT, 8082);
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
	
	public void shutdown(){
		this.running = false;
		synchronized(this){
			this.notifyAll();
		}
	}
	
	public boolean addMonitorClient(String uuid){
		BaseStation station = (BaseStation)storage.load(BaseStation.class, uuid);
		MonitorClient client = null;
		if(station != null && station.devType == 1){
			if(!this.clients.containsKey(uuid)){
				client = new MonitorClient(station, new VideoRoute(threadPool),
										   socketManager);
				this.clients.put(uuid, client);
				this.alarmManager.addClient(client);
				
				client.addListener(this.connectionListener);
				//开始连接设备, 非阻塞连接，很快就可以返回。
				try {
					client.connect();
				} catch (IOException e) {
					log.warn(e.toString());
				}
			}else {
				log.debug("The Client aready in current route, uuid:" + uuid);
				client = clients.get(uuid);
				//设备可能需要重新连接。
				if(!client.isConnected()){
					try {
						client.connect();
					} catch (IOException e) {
						log.warn(e.toString());
					}
				}
			}			
			return true;
		}else if(station == null){
			log.warn("Not found base station by uuid '" + uuid + "'");			
		}else {
			log.warn("Not a vedio base station '" + uuid + "'");
		}
		return false;
	}
	
	/**
	 * 删除一个监控视频，例如，需要负载调度，或连接错误时。
	 * @param client
	 */
	public void removeMonitorClient(MonitorClient client){
		if(this.clients.containsKey(client.info.uuid)){
			this.alarmManager.removeClient(client);
			this.clients.remove(client.info.uuid);
			client.close();
		}else {
			log.warn("Not found client in current route, uuid:" + client.info.uuid);
		}
	}
	
	/**
	 * 重新加载基站信息，可能被后台管理接口修改了配置。
	 * @param station
	 */
	public void reloadMonitorClient(BaseStation info){
		MonitorClient client = clients.get(info.uuid); 
		BaseStation old = null;
		if(client != null){
			log.info("Reload client configuration from database, client:" + info.toString());
			old = client.info;
			client.info = info;
			//修改配置后，不在同一个组内。
			if(info.groupName == null || !info.groupName.trim().equals(this.groupName)){
				this.removeMonitorClient(client);
			}else {
				client.close();
				//关闭所有过期的视频通道。
				for(MonitorChannel ch : old.getChannels()){
					if(ch.videoChannel != null){
						try {
							ch.videoChannel.reconnectSocketChannel();
						} catch (IOException e) {
							log.error(e);
						}
					}
				}
				try {
					client.connect();
				} catch (IOException e) {
					log.error(e.toString());
				}		
			}
		}
		//if(station.uuid)
		//BaseStation station = (BaseStation)storage.load(BaseStation.class, uuid);
	}
	
	
	/**
	 * 根据UUID取得监控客户端对象。
	 * @param uuid
	 * @return
	 */
	public MonitorClient getMonitorClient(String uuid){
		return clients.get(uuid);
	}
	
	public RouteRunningStatus getStatus(RouteRunningStatus httpStatus, boolean reset){
		RouteRunningStatus status = new RouteRunningStatus();
		for(MonitorClient client: clients.values()){
			status.allVideo += 1;
			if(client.getClientStatus() != null){
				status.connectVideo += 1;
				if(client.getClientStatus().realPlaying){
					status.activeVideo += 1;
				}
			}
			status.receiveData(client.runningStatus.receiveData);
			status.sendData(client.runningStatus.sendData);
			if(reset){
				client.runningStatus.cleanData();
			}
		}
		
		status.clientRequestCount = httpStatus.clientRequestCount;
		if(reset){
			httpStatus.cleanData();
		}
		return status;
	} 
	
	public MonitorClientListener connectionListener = new AbstractMonitorListener(){
		public void loginOK(final MonitorClientEvent event){
			event.client.initMonitorClientStatus();
			event.client.info.connectionStatus = "connected";
			storage.save(event.client.info, new String[]{"connectionStatus"});
		}
		public void loginError(final MonitorClientEvent event){
			event.client.info.connectionStatus = "error";
			storage.save(event.client.info, new String[]{"connectionStatus"});
			//触发一个登录错误的告警.
			event.alarms = new ArrayList<AlarmDefine>();
			event.alarms.add(AlarmDefine.alarm(AlarmDefine.AL_2002));
			event.client.eventProxy.alarm(event);
		}
		
		public void connected(final MonitorClientEvent event){
			event.client.login(false);
			this.updateLastActive(event.client.info);
		}
		public void timeout(final MonitorClientEvent event) {
			//如果设备之前是处于连接状态。
			if(event.client.getClientStatus() != null) {
				log.info("Try to reconnect timeout DVR:" + event.client.info.toString());
				event.client.close();
				try{
					event.client.connect();
				}catch(IOException e){
					log.equals(e.toString());
				}
			}else {
				/*
				 * 属于连接超时.连接超时的设备，会在5分钟后，有中心管理服务器。重新调度连接。
				 */
				log.info("Timeout to connect DVR:" + event.client.info.toString());
				event.client.info.connectionStatus = "timeout";
				event.client.info.lastActive = new Date(System.currentTimeMillis());
				storage.save(event.client.info, new String[]{"connectionStatus", 
														     "lastActive"});
				
				//以前讨论的业务超时，可以用超时时间间隔来实现。
				//触发一个超时的告警.
				event.alarms = new ArrayList<AlarmDefine>();
				event.alarms.add(AlarmDefine.alarm(AlarmDefine.AL_2001));
				event.client.eventProxy.alarm(event);
				
			}
		}
		
		/**
		 * 在ODIPHandler中会周期性的触发alarm事件。避免设备在中心服务器上，状态
		 * 被设置为超时。
		 */
		@Override
		public void alarm(MonitorClientEvent event) {
			this.updateLastActive(event.client.info);
		}			
	
		
		@Override
		public void disconnected(final MonitorClientEvent event) {
			//有可能设备是从服务器，删除后在断开。
			if(clients.get(event.client.info.uuid) != null && event.client.retryError < 5){
				event.client.retryError++;
				log.info("Try to reconnect disconnected DVR:" + event.client.info.toString() + ", retry:" + event.client.retryError);
				try{
					event.client.connect();
				}catch(IOException e){
					log.equals(e.toString());
				}
			}else if(event.client.retryError > 5) {
				removeMonitorClient(event.client);
			}
		}
		
		private void updateLastActive(final BaseStation info){
			threadPool.execute(new Runnable(){
				@Override
				public void run() {
					info.lastActive = new Date(System.currentTimeMillis());
					storage.save(info, new String[]{"lastActive"});
				}
			});
		}
	};
	
	public static void main(String[] a) throws Exception{
		new VideoRouteServer(new Settings("video.conf")).startUp();
	}
}
