package org.goku.master;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.BaseStation;
import org.goku.core.model.RouteServer;
import org.goku.db.DataStorage;

/**
 * 转发服务管理器，实现动态的调度监控资源。
 * @author deon
 */
public class RouteServerManager implements Runnable {
	private Log log = LogFactory.getLog("route.manager");
	//private Collection<RouteServer> servers = Collections.synchronizedCollection(new ArrayList<RouteServer>());
	
	private Map<String, RouteServer> servers = Collections.synchronizedMap(new HashMap<String, RouteServer>());	
	
	private ThreadPoolExecutor executor = null;
	private DataStorage storage = null;
	private Timer timer = new Timer();
	private long expiredTime = 1000 * 60;
	
	private File statisticsDir = null;
	//private boolean supportStatisticsRunningStatus = false;
	
	public RouteServerManager(ThreadPoolExecutor executor, DataStorage storage){
		this.executor = executor;
		this.storage = storage;
	}
	
	@Override
	public void run() {
		this.restoreRoute();

		timer.scheduleAtFixedRate(new TimerTask(){
			@Override
			public void run() {
				try{
					checkAllRouteServer();
				}catch(Throwable e){
					log.error(e);
				}
			}
		}, 100, expiredTime);		
	}
	
	public RouteServer getRouteReserver(String ipaddr){
		return servers.get(ipaddr);
	}
	
	public RouteServer addRouteServer(final String ipaddr, final String group){
		final RouteServer route = new RouteServer(ipaddr, group);
		servers.put(ipaddr.trim(), route);
		
		route.setBaseStationList(new ArrayList<BaseStation>());
		executor.execute(new Runnable(){
			@Override
			public void run() {
				log.info("Recovering route:" + route.toString());
				route.balanceBaseStation(Integer.MAX_VALUE, 
						storage.listStation(route), 
						storage);
				balanceGroup(group);
		}});
		
		return route;
	}
	
	public RouteServer removeRouteServer(String ipaddr){
		final RouteServer route = this.servers.remove(ipaddr);
		
		if(route != null){
			executor.execute(new Runnable(){
				@Override
				public void run() {
					storage.removeRouteServer(route);
					balanceGroup(route.groupName);
			}});
		}
		
		return route;
	}
	
	public void enableRouteStatistics(File rootPath){
		if(!rootPath.isDirectory()){
			if(!rootPath.mkdirs()){
				log.warn("Failed to create route statistics path:" + rootPath.getAbsolutePath());
			}
		}
		if(rootPath.isDirectory()){
			this.statisticsDir = rootPath;
			log.warn("init route statistics path:" + rootPath.getAbsolutePath());
		}
	}
	
	/**
	 * 重新调度组内Route的服务器负载。
	 * @param groupName
	 */
	protected synchronized void balanceGroup(String groupName){
		Collection<RouteServer> routeList = new ArrayList<RouteServer>();
		
		log.info("balance route group, name:" + groupName);
		int count = 0;
		
		//更新组内所有服务的状态。
		RouteServer route = null;
		for(String key: servers.keySet()){
			route = servers.get(key);
			if(route != null && route.groupName.equals(groupName)){
				log.info("Route:" + route.toString() + ", key:" + key);
				route.setBaseStationList(storage.listStation(route));
				count += route.listBaseStation().size();
				routeList.add(route);
			}
		}
		
		if(routeList.size() > 0){
			log.info("active base station count:" + count);
			Collection<BaseStation> bsPool = new Vector<BaseStation>();		
			bsPool.addAll(storage.listDeadStation(groupName));
			log.warn("dead station count:" + bsPool.size());
			
			count += bsPool.size();
			int average = count / routeList.size() + 1;			
			log.info(String.format("balance route server, station count:%s, route count:%s, average:%s",
						count, routeList.size(), average));
			for(RouteServer s: routeList){
				s.balanceBaseStation(count, bsPool, storage);
			}
			/**
			 * 如果最后还存在没有被监控的终端，全部放到最后一个路由。
			 */			
			routeList.iterator().next().balanceBaseStation(Integer.MAX_VALUE, bsPool, storage);
		}else {
			log.info("Not found any route server for group " + groupName);
		}
	}
	
	protected void checkAllRouteServer(){
		//log.info("check All RouteServer...");
		for(RouteServer s: servers.values()){
			//log.info("xxx.......");
			if(s.ping()){
				s.lastActive = System.currentTimeMillis();
				log.info("Route group:" + s.groupName + ", ipaddr:" + s.ipAddress + ", Status OK!");
				s.updating();
				if(statisticsDir != null){
					this.updateRouteServerStatistics(s);
				}
				
				/**
				 * 如果出现超时或有没有分配的基站，做一次调度操作。
				 */
				if(storage.listDeadStation(s.groupName).size() > 0){
					balanceGroup(s.groupName);
				}
			}else {
				log.info("Route group:" + s.groupName + ", ipaddr:" + s.ipAddress + ", Status ERR!");
				this.removeRouteServer(s.ipAddress);
			}
		}
	}
	
	protected void updateRouteServerStatistics(RouteServer s){
		DateFormat format= new SimpleDateFormat("_MM_dd");
		String name = s.ipAddress.replace(":", "_") + format.format(new Date()) + ".st";
		String status = s.statisticsStatus();
		FileWriter out = null;
		try{
			out = new FileWriter(new File(this.statisticsDir, name), true);
			out.write("#---------------------------------\n");
			out.write(status);
		}catch(IOException e){
			log.error(e.toString(), e);
		}finally{
			if(out != null){
				try {
					out.close();
				} catch (IOException e) {
					log.error(e.toString(), e);
				}
			}
		}
	}
	
	/**
	 * 恢复数据库中，存在的路由服务器。
	 */
	protected void restoreRoute(){
		String routeList = "select routeServer, groupName from base_station " +
						   "group by routeServer, groupName";
		Collection<Map<String, Object>> xx = storage.query(routeList, new Object[]{});
		
		for(Map<String, Object> info: xx){
			RouteServer route = new RouteServer((String)info.get("routeServer"),
												(String)info.get("groupName"));
			if(route.ipAddress != null && !"".equals(route.ipAddress.trim())){
				servers.put(route.ipAddress.trim(), route);
			}
		}
	}
}
