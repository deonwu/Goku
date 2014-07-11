package org.goku.core.model;

import java.util.Collection;
import java.util.Iterator;

import org.goku.db.DataStorage;
import org.goku.http.HTTPRemoteClient;
import org.mortbay.log.Log;

/**
 * 转发服务器。
 * @author deon
 */
public class RouteServer {	
	/**
	 * 转发服务器的分组名。
	 * 
	 * 由服务器启动时配置。
	 */
	public String groupName;
	
	public String socketPort;
	
	/**
	 * 中心管理服务器地址。<ip>:<port>
	 * 
	 * 由服务器启动时配置。
	 */	
	public String centerServer;
	
	/**
	 * 转发服务器地址, <ip>:<port>
	 * 
	 * 由服务器启动时配置。
	 */		
	public String ipAddress;
	public String status;	
	/**
	 * 最后活动时间，用来计算心跳时间。
	 */	
	public long lastActive;	
	
	public int alarmCount;
	private HTTPRemoteClient http = null;
	
	public Collection<BaseStation> clients = null;	
	
	
	public RouteServer(String ipaddr, String groupName){
		this.ipAddress = ipaddr;
		this.groupName = groupName;
		http = new HTTPRemoteClient("http://" + ipaddr);
	}
	
	public Collection<BaseStation> listBaseStation(){
		return clients;
	}
	
	public void setBaseStationList(Collection<BaseStation> clients){
		this.clients = clients;
	}
	
	/**
	 * 根据最大终端数量，调整RouteServer需要监控的终端数量，如果大于max,把删除的
	 * 终端放到pool, 如果小于max, 从pool取出相应的终端。
	 * @param max
	 * @param pool
	 */
	public void balanceBaseStation(int max, Collection<BaseStation> pool, DataStorage storage){
		//Log.info("xxx...........");
		if(this.clients.size() > max){
			Iterator<BaseStation> iter = this.clients.iterator();
			for(int i = this.clients.size() - max; i > 0 && iter.hasNext(); i--){
				BaseStation bs = iter.next();
				if(this.http.removeBaseStaion(bs)){
					bs.routeServer = null;
					pool.add(bs);
					storage.save(bs, new String[]{"routeServer"});
					iter.remove();
				}else {
					continue;
				}
			}
		}else if(this.clients.size() < max){
			Iterator<BaseStation> iter = pool.iterator();
			for(int i = max - this.clients.size(); i > 0 && iter.hasNext(); i--){
				BaseStation bs = iter.next();
				//Log.info("xxx..........." + bs.toString());
				if(this.http.addBaseStaion(bs)){
					bs.routeServer = this.ipAddress;
					this.clients.add(bs);
					storage.save(bs, new String[]{"routeServer"});
					iter.remove();
				}else {
					continue;
				}
			}
		}
	}
	
	public boolean ping(){
		return http.checkConnection();
	}
	
	/**
	 * 更新Route信息，
	 * 
	 * <GroupName>$<socketPort>
	 * @return
	 */
	public boolean updating(){
		String info = http.updatingInfo();
		String[] aInfo = info.split("\\$", 2);
		if(aInfo.length == 2){
			this.groupName = aInfo[0].trim();
			this.socketPort = aInfo[1].trim();
		}
		return true;
	}
	
	public String statisticsStatus(){
		return http.statisticsStatus();
	}
	
	public boolean equals(Object o){
		if(o instanceof RouteServer){
			return this.ipAddress.equals(((RouteServer) o).ipAddress);
		}
		return false;
	}
	
	/**
	 * 返回转发服务器的连接地址，更加mode返回HTTP，或Socket连接方式。
	 * @param mode
	 * @return
	 */
	public String getConnectAddr(String mode){
		if(mode == null || mode.equals("http")){
			return this.ipAddress;
		}else if(mode.equals("socket")){
			if(this.socketPort == null || "".equals(this.socketPort.trim())){
				return "";
			}else {
				return this.ipAddress.split(":")[0] + ":" + this.socketPort;
			}
		}
		return "";
	}
	
	public String toString(){
		return this.ipAddress;
	}
}
