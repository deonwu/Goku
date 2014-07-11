package org.goku.http;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.BaseStation;

/**
 * 中心控制服务器的客户端，通过HTTP和服务端交互。
 * @author deon
 *
 */
public class HTTPRemoteClient {
	private Map<String, String> EMPTY = new HashMap<String, String>();
	private Log log = LogFactory.getLog("master.client");
	private SimpleHttpClient http = null;
	private URL url = null;
	
	public HTTPRemoteClient(String url){
		try {
			http = new SimpleHttpClient(new URL(url));
		} catch (MalformedURLException e1) {
		}
	}
	
	/**
	 * 检查中心服务器的，是否在运行。
	 */	
	public boolean checkConnection(){
		HttpResponse resp = null;		
		try {
			resp = http.get("/?q=ping", new HashMap<String, String>());
			if(resp.getResponseMessage().trim().equals("OK")){
				return true;
			}else {
				log.warn("Check connection, response:" + resp.getResponseMessage());
				return false;
			}
		} catch (IOException e) {
			//log.error("Failed to check master server, ", e);
			return false;
		}
	}
	
	/**
	 * 更新服务器的状态。
	 */	
	public String updatingInfo(){
		HttpResponse resp = null;		
		try {
			resp = http.get("/?q=info", new HashMap<String, String>());
			return resp.getResponseMessage();
		} catch (IOException e) {
			log.error("Failed to master server, ", e);
			return "";
		}
	}	
	
	
	/**
	 * 更新服务器的状态。
	 */	
	public String statisticsStatus(){
		HttpResponse resp = null;		
		try {
			Map<String, String> param = new HashMap<String, String>();
			param.put("reset", "Y");
			resp = http.get("/?q=status", param);
			return resp.getResponseMessage();
		} catch (IOException e) {
			log.error("Failed to get route server status, ", e);
			return "";
		}
	}		
	
	/**
	 * 通知中心服务器，转发服务器已启动。
	 * @param host 转发服务器的名称。
	 * @param port 转发服务器的HTTP接口端口。
	 */
	public boolean registerRoute(String host, int port, String group, String socketPort){
		HttpResponse resp = null;
		Map<String, String> para = new HashMap<String, String>();
		boolean result = false;
		try {
			para.put("q", "add_route");
			para.put("port", port + "");
			para.put("group", group);
			para.put("socketPort", socketPort);
			resp = http.post(para, EMPTY);
			String text = resp.getResponseMessage().trim();
			if(!text.startsWith("0:")){
				log.error("Failed to register route, return:" + text);
			}else {
				log.info("Successfully register to master.");
			}
			result = true;
		}catch(IOException e) {
			log.error("Failed to register route, error:" + e.toString(), e);
		}
		return result;
	}

	/**
	 * 通知中心服务器，转发服务器正在关闭。
	 * @param host 转发服务器的名称。
	 * @param port 转发服务器的HTTP接口端口。
	 */	
	public void shutDownRoute(String host, int port){
		HttpResponse resp = null;
		Map<String, String> para = new HashMap<String, String>();
		try {
			para.put("q", "close_route");
			para.put("port", port + "");
			resp = http.post(para, EMPTY);
			String text = resp.getResponseMessage().trim();
			if(!text.startsWith("0:")){
				log.error("Failed to register route, return:" + text);
			}
		}catch(IOException e) {
			log.error("Failed to register route, error:" + e.toString(), e);
		}
	}
	
	/**
	 * 通知RouteServer连接基站。
	 */	
	public boolean addBaseStaion(BaseStation bs){
		HttpResponse resp = null;
		Map<String, String> para = new HashMap<String, String>();
		try {
			para.put("q", "add_bs");
			para.put("uuid", bs.uuid);
			resp = http.post(para, EMPTY);
			String text = resp.getResponseMessage().trim();
			if(text.startsWith("0:")){
				return true;
			}else {
				log.error("Failed to add BaseStation, return:" + text);
			}
		}catch(IOException e) {
			log.error("Failed to add BaseStation, error:" + e.toString(), e);
		}
		return false;
	}	
	
	/**
	 * 通知RouteServer断开基站。
	 */	
	public boolean removeBaseStaion(BaseStation bs){
		HttpResponse resp = null;
		Map<String, String> para = new HashMap<String, String>();
		try {
			para.put("q", "del_bs");
			para.put("uuid", bs.uuid);
			resp = http.post(para, EMPTY);
			String text = resp.getResponseMessage().trim();
			if(text.startsWith("0:")){
				return true;
			}else {
				log.error("Failed to remove BaseStation, return:" + text);
			}
		}catch(IOException e) {
			log.error("Failed to remove BaseStation, error:" + e.toString(), e);
		}
		return false;
	}		
}
