package org.goku.video;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.AlarmDefine;
import org.goku.core.model.AlarmRecord;
import org.goku.db.DataStorage;
import org.goku.video.odip.AbstractMonitorListener;
import org.goku.video.odip.MonitorClient;
import org.goku.video.odip.MonitorClientEvent;
import org.goku.video.odip.MonitorClientListener;

/**
 * Alarm管理中心，检测Alarm，自动收集告警录像，自动清理过期录像。
 * 
 * 同时负责终端的连接状态管理，如果不能连接成功，需要向中心服务器，报告状态。
 * @author deon
 */
public class AlarmMonitorCenter implements Runnable{
	private Log log = LogFactory.getLog("video.alarm");
	//private VideoRouteServer server = null;
	private Collection<MonitorClient> clients = Collections.synchronizedCollection(new ArrayList<MonitorClient>());
	private ThreadPoolExecutor executor = null;
	private Timer timer = new Timer();
	
	public AlarmMonitorCenter(ThreadPoolExecutor executor){
		this.executor = executor;
	}
	
	@Override
	public void run() {
		timer.scheduleAtFixedRate(new TimerTask(){
			@Override
			public void run() {
				try{
					checkAllClientAlarm();
				}catch(Throwable e){
					log.error(e.toString(), e);
				}
			}
		}, 100, 1000 * 5);
	}
	
	
	public void checkAllClientAlarm(){
		synchronized(clients){
			for(MonitorClient c: clients){
				c.sendAlarmRequest();
			}
		}
	}
	
	public void addClient(MonitorClient client){
		if(!clients.contains(client)){
			clients.add(client);
			client.addListener(this.alarmListener);
		}
	}

	public void removeClient(MonitorClient client){
		if(clients.contains(client)){
			clients.remove(client);
			client.removeListener(this.alarmListener);
		}
	}
	
	public void close(){
		this.timer.cancel();
	}
	
	private MonitorClientListener alarmListener = new AbstractMonitorListener(){
		@Override
		public void alarm(MonitorClientEvent event) {
			executor.execute(new AlarmHandler(event.client, event.alarms));
		}
	};
	
	class AlarmHandler implements Runnable{
		private MonitorClient client = null;
		private Collection<AlarmDefine> alarms = null;
		public AlarmHandler(MonitorClient client, Collection<AlarmDefine> alarms){
			this.client = client;
			this.alarms = alarms;
		}
		@Override
		public void run() {
			//需要检查告警是否被屏蔽，如果告警已屏蔽则不保存告警信息也不录像。
			for(AlarmDefine alarm: alarms){
				log.info(String.format("Process alarm:%s on '%s'.", alarm.toString(), client.info.toString()));
				
				//有些告警是整个设备的告警。
				if(alarm.channels == null || alarm.channels.length == 0){
					processAlarm(client, alarm, 0);
				}else {
					for(int ch: alarm.channels){
						processAlarm(client, alarm, ch);
					}
				}
			}
		}
		
		/**
		 * 检查是否是一个有效的告警，或者是否被屏蔽, 是否是最小时间间隔内的重复告警。 
		 * @param client
		 * @param alarm
		 * @param channel
		 * @return
		 */
		private boolean preCheckAlarm(MonitorClient client, AlarmDefine alarm, AlarmRecord record){
			boolean active = false;
			if(alarm.isGlobal() ||
			   (alarm.isCustomize() && client.info.isSupport(alarm.alarmCode))
			  ){
				DataStorage storage = VideoRouteServer.getInstance().storage;
				//检查最短触发时间。
				Date checkTime = new Date(System.currentTimeMillis() - alarm.reActiveTime * 1000 * 60);
				//
				String sql = "select count(*) as have_row from alarm_record where " +
								"startTime > ${0} and baseStation = ${1} and channelId= ${2} " +
								"and alarmCode = ${3}";
				
				Collection<Map<String, Object>> xx = storage.query(sql, new Object[]{checkTime,
						record.baseStation,
						record.channelId,
						record.alarmCode});
				int rowCount = 0;
				if(xx.size() > 0){
					rowCount = (Integer)xx.iterator().next().get("have_row");
				}
				
				//如果没有找到告警，当前告警有效。
				active = rowCount == 0;
				if(!active){
					log.debug("Duplicated alarm:"+ alarm.toString() + ", client:" + client.toString() + ", channel:" + record.channelId);
				}
			}else if(alarm.isCustomize()){
				log.debug("Not support alarm:"+ alarm.toString() + ", client:" + client.toString());
			}
			
			return active;
		}
		
		private void processAlarm(MonitorClient client, AlarmDefine alarm, int channel){
			AlarmRecord record = new AlarmRecord();
			record.baseStation = client.info.uuid;
			
			record.channelId = channel + "";
			record.startTime = new Date();
			record.alarmCode = alarm.alarmCode;
			record.alarmCategory = alarm.alarmCategory;
			record.alarmLevel = alarm.alarmLevel;
			record.alarmStatus = "1";
			
			/*
			if(log.isDebugEnabled()){
				log.debug("process alarm:" + alarm.toString() + ", client:" + client.toString() + ", channel:" + channel);
			}*/
			if(preCheckAlarm(client, alarm, record)){
				DataStorage storage = VideoRouteServer.getInstance().storage;
				record.generatePK();
				storage.save(record);				
				if(alarm.alarmCategory.trim().equals("1")) {
					//需要判断是否需要启动录像。
					VideoRouteServer.getInstance().recordManager.startAlarmRecord(client, record);
				}
			}
		}
	}
}
