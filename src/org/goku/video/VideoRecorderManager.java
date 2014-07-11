package org.goku.video;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.AlarmRecord;
import org.goku.core.model.MonitorChannel;
import org.goku.db.DataStorage;
import org.goku.settings.Settings;
import org.goku.video.odip.MonitorClient;
import org.goku.video.odip.RecordFileInfo;

/**
 * 管理视频录像。
 * @author deon
 */
public class VideoRecorderManager implements Runnable{
	public String pattern = null;

	public File rootPath = null;
	private static final String DEFAULT = "${yyyy}-${mm}/" +
			"${UUID}-${ALARM_TYPE}-" +
			"${USER}-${mm}${dd}-" +
			"${HH}${MM}${SS}.${SSS}.h264";
	private Log log = LogFactory.getLog("video.recorder");
	private DataStorage storage = null;
	private Timer timer = new Timer();
	
	//单位秒。
	private long alarmTimeOut = 5;
	
	private Map<String, AlarmRecord> runningRecorder = new HashMap<String, AlarmRecord>();
	
	public VideoRecorderManager(Settings settings, DataStorage storage){
		this.storage = storage;
		initRootPath(settings.getString(Settings.FILE_ROOT_PATH, "data"));	
		pattern = settings.getString(Settings.FILE_NAME_PATTERN, DEFAULT);
		
		alarmTimeOut = settings.getInt(Settings.AUTO_CONFIRM_TIME, 5);
	}
	
	
	/**
	 * 下载告警录像
	 * @param client
	 * @param alarm
	 * @return
	 * @throws IOException 
	 */
	public void downloadAlarmRecord(MonitorClient client, AlarmRecord alarm, RecordFileInfo info) throws IOException{
		Date startTime = new Date();
		String path = getSavePath(startTime, client.info.uuid, alarm.user, alarm.alarmCode, alarm.channelId);
		alarm.videoPath = path;
		alarm.generatePK();
		storage.save(alarm);
		
		log.info(String.format("Start download, sid:%s, path:%s", alarm.uuid, alarm.videoPath));
		OutputStream os = new FileOutputStream(new File(rootPath, path));
		client.downloadByRecordFile(info, os, true);
		os.close();		
	}
	
	public AlarmRecord findAlarmByTime(String uuid, int ch, Date start, Date end){
		/*
		public String baseStation = "";
		public String channelId = ""; //发生告警的通道号
		public Date startTime = null;
		public Date endTime = null;		
		*/
		String filter = "baseStation=${0} and channelId=${1} and (startTime > ${2} and startTime < ${3})";
			//只判断开始时间一致就不需要重复下载视频了。
			//	" and (endTime > ${4} and endTime < ${5})";
		Object[] param = new Object[6];
		param[0] = uuid;
		param[1] = ch;
		param[2] = new Date(start.getTime() - 1000 * 5);
		param[3] = new Date(start.getTime() + 1000 * 5);

		param[4] = new Date(end.getTime() - 1000 * 5);
		param[5] = new Date(end.getTime() + 1000 * 5);
		
		Collection xx = storage.list(AlarmRecord.class, filter, param);
		if(xx.size() > 0){
			return (AlarmRecord)xx.iterator().next();
		}
		return null;
	}
	
	/**
	 * 返回当前录像的会话ID.
	 * @param client
	 * @param alarm
	 * @return
	 */
	public String startAlarmRecord(MonitorClient client, AlarmRecord alarm){
		Date startTime = new Date();
		String path = getSavePath(startTime, client.info.uuid, alarm.user, alarm.alarmCode, alarm.channelId);
		alarm.videoPath = path;
		alarm.generatePK();
		
		int ch = 0;
		try{
			ch = Integer.parseInt(alarm.channelId);
		}catch(Exception e){}
		
		alarm.recorder = new FileVideoRecorder(new File(rootPath, path), 
				MonitorChannel.MAIN_VIDEO,
				ch);
		
		log.info(String.format("Start record, sid:%s, path:%s", alarm.uuid, alarm.videoPath));
		client.route.addDestination(alarm.recorder);
		client.realPlay(ch, MonitorChannel.MAIN_VIDEO);
		storage.save(alarm);
		
		runningRecorder.put(alarm.uuid, alarm);
		
		return alarm.uuid;
		
		//return alarm.uuid;
	}
	
	public String startManualRecord(MonitorClient client, String user){
		AlarmRecord alarm = new AlarmRecord();
		alarm.user = user;
		alarm.baseStation = client.info.uuid;
		alarm.startTime = new Date();
		alarm.alarmCode = "none";
		
		return startAlarmRecord(client, alarm);
	}
	
	/**
	 * 根据录像ID查询文件路径，
	 * @param uuid
	 * @return 录像文件路径，如果没有找到或文件不存在，返回null;
	 */
	public File getAlarmRecordFile(String uuid){
		AlarmRecord record = (AlarmRecord)storage.load(AlarmRecord.class, uuid);
		File path = null;
		if(record != null){
			if(record.videoPath != null){
				path = new File(this.rootPath, record.videoPath);
				if(!path.isFile()){
					log.warn("Not found video file:" + path + ", by id:" + uuid);
				}
			}else {
				log.debug("It's not a video alarm, uuid:" + record.alarmCode);
			}
		}else {
			log.warn("Not found video record by id:" + uuid);
		}
		return path;
	}
	
	public void stoptRecord(String sid){
		AlarmRecord alarm = runningRecorder.get(sid);
		if(alarm != null){
			alarm.recorder.close();
			alarm.endTime = new Date();
			storage.save(alarm);
			
			runningRecorder.remove(sid);
			log.info(String.format("Closed record, sid:%s", alarm.uuid));
		}else {
			log.warn("Try to stop record, but Not found session by id " + sid);
		}
	}
	
	/**
	 * 关闭超时的录像。
	 */
	protected void closeTimeOutRecord(){
		Collection<String> xx = new ArrayList<String>();
		xx.addAll(runningRecorder.keySet());
		
		long startTime = System.currentTimeMillis() - 60 * 1000 * alarmTimeOut;
		AlarmRecord alarm = null;
		for(String k: xx){
			alarm = runningRecorder.get(k);
			if(alarm != null && alarm.startTime.getTime() < startTime){
				alarm.alarmStatus = "2";
				stoptRecord(k);
			}
		}
	}
	
	/**
	 * 模式类型:
	 * ${yyyy} -- 年份
	 * ${mm} -- 月
	 * ${dd} -- 天
	 * 
	 * ${HH} -- 24时
	 * ${MM} -- 分钟
	 * ${SS} -- 秒
	 * 
	 * ${USER} -- 
	 * ${UUID} --
	 *  
	 * ${ALARM_TYPE} -- 
	 * 
	 * like: ${yyyy}-${mm}/${UUID}-${ALARM_TYPE}-${USER}-${mm}${dd}-${HH}${MM}${SS}.${SSS}.h264
	 * 
	 * @param startDate
	 * @param clientId
	 * @param user
	 * @param alarmType
	 * @return
	 */
	protected String getSavePath(Date startDate, String clientId, 
							   String user, String alarmType, String ch){
		String path = pattern;
		Calendar now = Calendar.getInstance();
		now.setTimeInMillis(startDate.getTime());
		
		path = path.replaceAll("\\$\\{yyyy\\}", String.format("%04d", now.get(Calendar.YEAR)));
		path = path.replaceAll("\\$\\{mm\\}", String.format("%02d", now.get(Calendar.MONTH) + 1));
		path = path.replaceAll("\\$\\{dd\\}", String.format("%02d", now.get(Calendar.DAY_OF_MONTH)));
		
		path = path.replaceAll("\\$\\{HH\\}", String.format("%02d", now.get(Calendar.HOUR_OF_DAY)));
		path = path.replaceAll("\\$\\{MM\\}", String.format("%02d", now.get(Calendar.MINUTE)));
		path = path.replaceAll("\\$\\{SS\\}", String.format("%02d", now.get(Calendar.SECOND)));
		path = path.replaceAll("\\$\\{SSS\\}", String.format("%03d", now.get(Calendar.MILLISECOND)));
		
		path = path.replaceAll("\\$\\{UUID\\}", clientId);
		path = path.replaceAll("\\$\\{USER\\}", user);
		path = path.replaceAll("\\$\\{ALARM_TYPE\\}", alarmType);
		path = path.replaceAll("\\$\\{CHANNEL\\}", ch);
		
		path = path.replaceAll("\\\\/", File.separator);
		
		File pathFile = new File(rootPath, path);
		File dirFile = pathFile.getParentFile();
		if(!dirFile.exists()){
			if(!dirFile.mkdirs()){
				log.error("Failed to create directory:" + dirFile.getAbsolutePath());
			}
		}
		
		return path;
	}
	
	private void initRootPath(String path){
		path = path.replaceAll("\\/", File.separator);
		File dirFile = new File(path);
		if(!dirFile.exists()){
			if(!dirFile.mkdirs()){
				log.error("Failed to create directory:" + dirFile.getAbsolutePath());
			}
		}
		this.rootPath = dirFile;
		log.info("Video root path:" + this.rootPath.getAbsolutePath());
	}

	@Override
	public void run() {
		timer.scheduleAtFixedRate(new TimerTask(){
			@Override
			public void run() {
				try{
					closeTimeOutRecord();
				}catch(Throwable e){
					log.error(e.toString(), e);
				}
			}
		}, 100, 1000 * 60);		
	}
}
