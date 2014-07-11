package org.goku.image;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.image.ImageSocketAdaptor.SessionCache;
import org.goku.settings.Settings;

/**
 * 告警管理中心，定时向设备发送。告警查询命令。根据告警图片信息，生成一个客户端通知消息。
 */
public class AlarmMonitorCenter implements Runnable {
	private Log log = LogFactory.getLog("image.alarm");
	//private VideoRouteServer server = null;
	public Map<String, ASC100Client> clients = Collections.synchronizedMap(new HashMap<String, ASC100Client>());
	//private Collection<ASC100Client> clients = Collections.synchronizedCollection(new ArrayList<ASC100Client>());
	private ThreadPoolExecutor executor = null;
	private Timer timer = new Timer();
	private boolean isRunning = false;
	
	/**
	 * 告警检测间隔时间，单位秒。
	 */
	private int alarmCheckTime = 10;
	
	public AlarmMonitorCenter(ThreadPoolExecutor executor){
		this.executor = executor;
	}

	@Override
	public void run() {
		isRunning = true;
		//this.setAlarmCheckTime(seconds)
	}
	
	public void checkAllClient(){
		synchronized(clients){
			log.info("Check all image alarm, client size:" + clients.size());
			for(ASC100Client c: clients.values()){
				c.getAlarmImage();
			}
		}
	}
	
	public void setAlarmCheckTime(int seconds){
		timer.cancel();
		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask(){
			@Override
			public void run() {
				try{
					if(isRunning){
						checkAllClient();
					}
				}catch(Throwable e){
					log.error(e.toString(), e);
				}
			}
		}, 100, 1000 * seconds);
		this.alarmCheckTime = seconds;
		log.info(String.format("start image alarm monitor, check period:%s sec.",
				this.alarmCheckTime));

	}
	
	public void addClient(ASC100Client client){
		//this.clientTable.put(client.getClientId(), client);
		synchronized(clients){
			clients.put(client.info.uuid, client);
			client.addListener(this.alarmListener);
		}
	}
	
	public void removeClient(ASC100Client client){
		synchronized(clients){
			clients.remove(client.info.uuid);
			client.removeListener(this.alarmListener);
		}
	}
	
	private ImageClientListener alarmListener = new AbstractImageListener(){
		public void recevieImageOK(final ImageClientEvent event) {
			//log.debug("recevieImageOK, status:" + event.image.imageStatus);
			if(event.image != null && event.image.imageStatus == 1){
				//保存图片需要数据库操作和写文件，所以放到线程里面做。
				executor.execute(new Runnable(){
					@Override
					public void run() {
						try {
							ImageRouteServer.getInstance().fileManager.saveImageFile(event.source, event.image);
						} catch (Throwable e) {
							log.error("Failed to save image file, " + e.toString(), e);
						}
					}});
			}
		};
	};
}
