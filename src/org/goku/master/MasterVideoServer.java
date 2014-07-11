package org.goku.master;

import java.io.File;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.AlarmDefine;
import org.goku.core.model.RouteServer;
import org.goku.core.model.SimpleCache;
import org.goku.core.model.SystemLog;
import org.goku.db.DataStorage;
import org.goku.http.SimpleHTTPServer;
import org.goku.http.StartupListener;
import org.goku.settings.Settings;
import org.goku.socket.SimpleSocketServer;
import org.goku.socket.SocketManager;
import org.goku.video.VideoRecorderManager;

/**
 * 监控管理服务器，负责调度不同的转发服务器，实现监控服务。
 * @author deon
 *
 */
public class MasterVideoServer {
	private Log log = LogFactory.getLog("main");
	private static MasterVideoServer ins = null;
	
	public Settings settings = null;
	public DataStorage storage = null;
	public SimpleHTTPServer httpServer = null;
	public SimpleSocketServer socketServer = null;
	public SocketManager socketManager = null;	
	public RouteServerManager routeManager = null;
	public VideoRecorderManager recordManager = null;
	public VideoTaskManager taskManager = null;
	/**
	 * 基站告警屏蔽列表。把需要屏蔽告警的基站放到Cache里面，在查询实时告警时，过滤出
	 * 在Cache里面基站相关的告警。例如Cache的超时机制实现告警的自动过期。
	 */
	public SimpleCache stopAlarm = new SimpleCache();
	private boolean running = true;
	
	private ThreadPoolExecutor threadPool = null;
	private static final String servelt = "org.goku.master.MasterServerServlet";
	
	public static MasterVideoServer getInstance(){
		return ins;
	}
	
	public MasterVideoServer(Settings settings){
		ins = this;
		this.settings = settings;			
	}
	
	public void startUp() throws Exception{
		log.info("Starting master server...");
		
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
		
		routeManager = new RouteServerManager(threadPool, storage);
		
		//开始转发服务器，性能监控功能。
		String statistics = settings.getString("statistics_path", null);
		if(statistics != null){
			routeManager.enableRouteStatistics(new File(statistics));
		}
		
		threadPool.execute(routeManager);
		log.info("Start route server manager...");
		
		log.info("Start video record manager...");
		recordManager = new VideoRecorderManager(settings, storage);
		threadPool.execute(recordManager);		
		
		socketManager = new SocketManager(threadPool);
		threadPool.execute(socketManager);		
		int port = settings.getInt(Settings.LISTEN_PORT, 8000);
		socketServer = new SimpleSocketServer(socketManager, port);
		socketServer.setServlet(servelt);
		socketServer.setRecorderManager(recordManager);
		threadPool.execute(socketServer);
		log.info("Start scoket server at port " + port);	
		
		log.info("Start video task manager... ");
		taskManager = new VideoTaskManager(storage);
		threadPool.execute(taskManager);
		
		int httpPort = settings.getInt(Settings.HTTP_PORT, 8080);
		log.info("Start http server at port " + httpPort);		
		httpServer = new SimpleHTTPServer("", httpPort);
		File admin_war = new File(settings.getString("admin_war", "GokuCtrl.war"));
		if(admin_war.isFile()){
			log.info("Deploy admin application '/GokuCtrl, war:" + admin_war.getAbsolutePath());
			httpServer.addWar("/GokuCtrl", admin_war);
		}else {
			log.warn("Not found admin application, " + admin_war.getAbsolutePath());
		}

		File staticRoot = new File(settings.getString("static_root", ".")).getCanonicalFile();
		if(staticRoot.isDirectory()){
			log.info("Deploy static root:" + staticRoot.getAbsolutePath());
			System.setProperty("STATIC_ROOT", staticRoot.getAbsolutePath());
		}
		System.setProperty("DATA_ROOT", recordManager.rootPath.getAbsolutePath());
		log.info("Export DATA_ROOT:" + staticRoot.getAbsolutePath());
		
		httpServer.setServlet(servelt);
		httpServer.addStartupListener(new StartupListener(){
			@Override
			public void started() {
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
	
	public RouteServer addRouteServer(String ipaddr, String groupName){
		return routeManager.addRouteServer(ipaddr, groupName);
	}
}
