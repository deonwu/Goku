package org.goku.video;

import java.io.IOException;
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
import org.goku.core.model.MonitorChannel;
import org.goku.core.model.SimpleCache;
import org.goku.core.model.SystemReload;
import org.goku.db.DataStorage;
import org.goku.settings.Settings;
import org.goku.video.odip.AbstractMonitorListener;
import org.goku.video.odip.MonitorClient;
import org.goku.video.odip.MonitorClientEvent;
import org.goku.video.odip.MonitorClientListener;
import org.goku.video.odip.RecordFileInfo;

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
	private long alarmCheckPeriod = 3; //秒。
	private long autoConfirmTime = 5; //分钟。
	private long lastAutoConfirmTime = 0;
	private SystemReload reload = null;
	private SimpleCache activeAlarm = new SimpleCache();
	
	public AlarmMonitorCenter(ThreadPoolExecutor executor, Settings s){
		this.executor = executor;
		this.alarmCheckPeriod = s.getInt(Settings.ALARM_CHECK_PERIOD, 3);
		this.autoConfirmTime = s.getInt(Settings.AUTO_CONFIRM_TIME, 5);
	}
	
	@Override
	public void run() {
		reload = new SystemReload();
		timer.scheduleAtFixedRate(new TimerTask(){
			@Override
			public void run() {
				long st = System.currentTimeMillis(), et = 0;
				try{
					checkAllClientAlarm();
					autoComfirmAlarm();
					checkReload();
					et = System.currentTimeMillis() - st;
					//如果每次轮询告警的时间超过2秒。服务器太慢，可能需要降低视频终端的数量。
					if(et > 2000){
						log.warn(String.format("Alarm check process too slow, %s ms in one times.", et));
					}
				}catch(Throwable e){
					log.error(e.toString(), e);
				}
			}
		}, 100, 1000 * alarmCheckPeriod);
		log.info(String.format("start alarm monitor, check period:%s sec, auto confirm time:%s min",
				 this.alarmCheckPeriod, this.autoConfirmTime));
	}
	
	public void checkReload(){
		this.executor.execute(new Runnable(){
			@Override
			public void run() {
				DataStorage storage = VideoRouteServer.getInstance().storage;
				reload.check(storage);
			}});
	}
	
	public void checkAllClientAlarm(){
		synchronized(clients){
			for(MonitorClient c: clients){
				c.sendAlarmRequest();
			}
		}
	}
	
	public void autoComfirmAlarm(){
		//每10秒钟自动确认一次。
		if(System.currentTimeMillis() - lastAutoConfirmTime < 1000 * 30) return;
		//
		final String sql = "update alarm_record set alarmStatus='2', comfirmTime=${0}, lastUpdateTime=${0}, endTime=${0} " +
					 "where alarmStatus='1' and startTime < ${1}";
		
		final Date now = new Date(System.currentTimeMillis());		
		final Date startConfirm = new Date(System.currentTimeMillis() - 60 * 1000 * this.autoConfirmTime);
		//放到线程里面运行，是避免数据库操作阻塞，导致告警检查的延迟。
		this.executor.execute(new Runnable(){
			@Override
			public void run() {
				DataStorage storage = VideoRouteServer.getInstance().storage;
				try {
					int count = storage.execute_sql(sql, new Object[]{now, startConfirm});
					lastAutoConfirmTime = System.currentTimeMillis();
					log.debug(String.format("Auto confirm alarms, count:%s, starting before:%s", count, startConfirm));
				} catch (Throwable e) {
					log.warn(e.toString(), e);
				}
			}});
		
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
		/**
		 * 在登录成功后，自动开始下载视频录像。
		 */
		public void loginOK(final MonitorClientEvent event){
			executor.execute(new DownloadVideoHandler(event.client,
					VideoRouteServer.getInstance().recordManager
					));
		}
		
		@Override
		public void alarm(MonitorClientEvent event) {
			executor.execute(new AlarmHandler(event.client, event.alarms));
		}
	};

	class DownloadVideoHandler implements Runnable{
		private MonitorClient client = null;
		private VideoRecorderManager recordManager = null;
		public DownloadVideoHandler(MonitorClient client, VideoRecorderManager rm){
			this.client = client;
			this.recordManager = rm;
		}
		
		@Override
		public void run() {
			try{
				if(client.info.lastDownVideo != null){
					for(MonitorChannel ch: client.info.getChannels()){
						downLoadOneChannel(ch.id, client.info.lastDownVideo);
					}
				}
				client.info.lastDownVideo = new Date(System.currentTimeMillis());
				DataStorage storage = VideoRouteServer.getInstance().storage;
				storage.save(client.info, new String[]{"lastDownVideo"});
			}catch(Throwable e){
				log.error(e.toString(), e);
			}
		}
		
		public void downLoadOneChannel(int channel, Date start) throws IOException{
			log.debug(String.format("Auto download alarm video client:%s, ch:%s, start time:%s", client, channel, start));
			Collection<RecordFileInfo> records = new ArrayList<RecordFileInfo>();
			records.addAll(client.queryRecordFile(channel, 0, start));
			log.debug(String.format("Find video on DVR, count:%s, ch:%s", records.size(), channel));
			for(RecordFileInfo file: records){
				//小于100K的视频下载了也看不见。没有用
				if(file.fileSize < 100) {
					log.debug("Ignore small size video:" + file.fileSize);
					continue;
				}
				if(recordManager.findAlarmByTime(client.info.uuid, channel, 
						file.startTime, file.endTime) != null) {
					log.debug("Aready exists alarm at time:" + file.startTime);
					continue;
				}
				recordManager.downloadAlarmRecord(client, 
						createAlarmRecord(file), file);
			}
		}
		
		public AlarmRecord createAlarmRecord(RecordFileInfo file){
			AlarmRecord record = new AlarmRecord();
			record.baseStation = client.info.uuid;
			
			record.channelId = file.channel + "";
			record.startTime = file.startTime;
			record.endTime = file.endTime;
			
			AlarmDefine alarm = AlarmDefine.alarm(AlarmDefine.AL_1001);			
			record.alarmCode = alarm.alarmCode;
			record.alarmCategory = alarm.alarmCategory;
			record.alarmLevel = alarm.alarmLevel;
			record.combineUuid = "download";
			record.alarmStatus = "2";
			
			return record;
		}

	}
	
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
			String activeKey = record.baseStation + "_" + record.channelId + "_" + record.alarmCode;
			//告警还在处理中，避免保存重复告警。避免每次都通过数据库查询来判断是否有告警。
			if(activeAlarm.hasKey(activeKey)){
				log.debug("Duplicated alarm in cache:"+ alarm.toString() + ", client:" + client.info.toString() + ", channel:" + record.channelId);
				return false;
			}
			if(alarm.isGlobal() ||
			   (alarm.isCustomize() && client.info.isSupport(alarm.alarmCode))
			  ){
				DataStorage storage = VideoRouteServer.getInstance().storage;
				//检查最短触发时间。
				if(alarm.reActiveTime <= 0) alarm.reActiveTime = 1; 
				Date checkTime = new Date(System.currentTimeMillis() - alarm.reActiveTime * 1000 * 60);
				//
				String sql = "select count(*) as have_row from alarm_record where " +
								"startTime <= ${4} and startTime > ${0} and baseStation = ${1} and channelId= ${2} " +
								"and alarmCode = ${3}";
				
				Collection<Map<String, Object>> xx = storage.query(sql, new Object[]{checkTime,
						record.baseStation,
						record.channelId,
						record.alarmCode,
						new Date(System.currentTimeMillis())
						});
				int rowCount = 0;
				if(xx.size() > 0){
					rowCount = (Integer)xx.iterator().next().get("have_row");
				}
				
				//如果没有找到告警，当前告警有效。
				active = rowCount == 0;
				if(!active){
					log.debug("Duplicated alarm:"+ alarm.toString() + ", client:" + client.info.toString() + ", channel:" + record.channelId);
				}else {
					activeAlarm.set(activeKey, "", (int)alarm.reActiveTime * 60);
				}
			}else if(alarm.isCustomize()){
				log.debug("Not support alarm:"+ alarm.toString() + ", client:" + client.info.toString());
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
			record.alarmLevel = alarm.alarmLevel.trim();
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
