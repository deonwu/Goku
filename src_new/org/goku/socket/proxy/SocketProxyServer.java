package org.goku.socket.proxy;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.socket.SocketManager;
import org.goku.video.odip.MonitorClient;

public class SocketProxyServer implements Runnable {
	public long timeOut = 1000 * 60;

	private Log log = LogFactory.getLog("proxy.server");
	private SocketManager manager = null;	
	private Queue<Integer> portPool = null;
	private Map<String, ListenProxy> proxyMapping = Collections.synchronizedMap(new HashMap<String, ListenProxy>());	
	private Timer timer = new Timer();
	
	public SocketProxyServer(SocketManager manager, int startPort, int endPort){
		this.manager = manager;
		
		portPool = new ArrayDeque<Integer>(endPort - startPort + 1);
		for(int i = startPort; i <= endPort; i++){
			portPool.add(i);
		}
	}
	
	@Override
	public void run() {
		timer.scheduleAtFixedRate(new TimerTask(){
			@Override
			public void run() {
				checkAllClient();				
			}
		}, 100, 1000 * 60);
	}
	
	public void checkAllClient(){
		Collection<String> proxyList = new ArrayList<String>();
		proxyList.addAll(proxyMapping.keySet());
		ListenProxy proxy = null;		
		long st = System.currentTimeMillis() - timeOut;		
		for(String key: proxyList){
			proxy = proxyMapping.get(key);
			if(proxy != null && proxy.lastActive < st){
				log.debug("Close time out proxy, dest:" + key);
				this.releaseProxy(key);
			}
		}
	}
	
	/**
	 * 创建一个新的代理，返回代理的连接端口号。
	 * @param dest
	 * @return
	 */
	public int createProxy(String dest) throws IOException{
		ListenProxy proxy = proxyMapping.get(dest);	
		log.debug("Request to create proxy for " + dest);
		int proxyPort = -1;
		if(proxy == null){
			proxyPort = this.getProxyPort();
			if(proxyPort > 0){
				proxy = new ListenProxy(manager, dest);
				manager.listen("0.0.0.0", proxyPort, proxy);
				this.proxyMapping.put(dest, proxy);
			}
		}else {
			proxyPort = proxy.listenPort();
		}
		
		return proxyPort;
	}
	
	/**
	 * 根据目地地址关闭代理。
	 * @param dest
	 */
	public void releaseProxy(String dest){
		ListenProxy proxy = this.proxyMapping.get(dest);
		if(proxy != null){
			proxy.close();
			proxyMapping.remove(dest);
			portPool.add(proxy.listenPort());			
		}else {
			log.warn("Not found proxy of " + dest);
		}		
	}
	
	/**
	 * 根据端口关闭代理。
	 * @param dest
	 */	
	public void releaseProxy(int port){
		
	}
	
	protected int getProxyPort(){
		int port = -1;
		synchronized(portPool){
			if(portPool.size() > 0){
				port = portPool.poll();
			}
		}
		return port;
	}

}
