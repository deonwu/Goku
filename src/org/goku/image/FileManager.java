package org.goku.image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.AlarmDefine;
import org.goku.core.model.AlarmRecord;
import org.goku.core.model.SimpleCache;
import org.goku.db.DataStorage;
import org.goku.db.QueryParameter;
import org.goku.db.QueryResult;
import org.goku.settings.Settings;

public class FileManager {
	public String pattern = null;

	private static final String DEFAULT = "${yyyy}-${mm}/" +
			"${UUID}-${ALARM_TYPE}-" +
			"${USER}-${mm}${dd}-" +
			"${HH}${MM}${SS}.${SSS}.h264";
	private Log log = LogFactory.getLog("video.recorder");
	private File rootPath = null;
	private DataStorage storage = null;
	private Timer timer = new Timer();
	private SimpleCache activeAlarm = new SimpleCache();
	
	//单位秒。
	private long alarmTimeOut = 5;
	
	private Map<String, AlarmRecord> runningRecorder = new HashMap<String, AlarmRecord>();
	
	public FileManager(Settings settings, DataStorage storage){
		this.storage = storage;
		initRootPath(settings.getString(Settings.FILE_ROOT_PATH, "data"));	
		pattern = settings.getString(Settings.FILE_NAME_PATTERN, DEFAULT);		
	}
	
	class AlarmUUID{
		String uuid;
		Date createTime;
		AlarmUUID(String d, Date c){uuid=d; createTime = c;}
	}
	
	public AlarmRecord saveImageFile(ASC100Client client, ImageInfo image) throws IOException{
		log.debug(String.format("Save image uuid:%s, ch:%s, time:%s", client.info.uuid, image.channel, image.generateDate));
		AlarmDefine alarm = AlarmDefine.alarm(AlarmDefine.AL_5001);
		String path = getSavePath(image.generateDate, client.info.uuid, "", alarm.alarmCode, image.channel + "");
		FileOutputStream os = new FileOutputStream(new File(rootPath, path));
		os.getChannel().write(image.buffer.asReadOnlyBuffer());
		os.close();
		
		//
		String combineUuid = null;
		String cacheKey = client.info.uuid + "_" + image.channel + "_" + alarm.alarmCode;
		if(activeAlarm.get(cacheKey, false) != null){
			AlarmUUID u = (AlarmUUID)activeAlarm.get(cacheKey, false);
			long diff = image.generateDate.getTime() - u.createTime.getTime();
			if(Math.abs(diff) < alarm.reActiveTime * 1000 * 60){
				combineUuid = u.uuid;
			}
		}

		//检查最短触发时间, 有可能是倒序的。
		Date checkTime = new Date(image.generateDate.getTime() - alarm.reActiveTime * 1000 * 60 /2);
		Date endTime = new Date(image.generateDate.getTime() + alarm.reActiveTime * 1000 * 60 /2);
		if(combineUuid == null){
			String sql = "select combineUuid from alarm_record where " +
							"startTime > ${0} and startTime <= ${4} and baseStation = ${1} and channelId= ${2} " +
							"and alarmCode = ${3} limit 1";
			Collection<Map<String, Object>> xx = storage.query(sql, new Object[]{checkTime,
					client.info.uuid,
					image.channel,
					alarm.alarmCode,
					endTime});
			if(xx.size() > 0){
				combineUuid = (String)xx.iterator().next().get("combineUuid");
				activeAlarm.set(cacheKey, new AlarmUUID(combineUuid, image.generateDate), (int)alarm.reActiveTime * 60);
			}
		}
		
		AlarmRecord rec = new AlarmRecord();
		rec.videoPath = path;
		rec.alarmCode = alarm.alarmCode;
		rec.startTime = image.generateDate;
		rec.endTime = image.generateDate;
		rec.baseStation = client.info.uuid;
		rec.channelId = image.channel + "";
		rec.alarmLevel = alarm.alarmLevel;
		rec.alarmStatus = "1";
		rec.dataSize = image.imageSize;
		rec.generatePK();
		if(combineUuid != null){
			rec.alarmCategory = "4";
			rec.combineUuid = combineUuid;
		}else {
			rec.alarmCategory = alarm.alarmCategory;
			rec.combineUuid = rec.uuid;
			activeAlarm.set(cacheKey, new AlarmUUID(rec.uuid, image.generateDate), (int)alarm.reActiveTime * 60);
		}
		storage.save(rec);
		log.info(String.format("Save image alarm file, BTS uuid:%s, ch:%s, alarm id:%s, alarm group:%s, path:%s", rec.baseStation, rec.channelId,
				rec.uuid, rec.combineUuid, rec.videoPath));
		
		return rec;
	}
	
	/**
	 * 通过告警ID，查询图片列表。 
	 */
	@SuppressWarnings("unchecked")
	public Collection<AlarmRecord> getImageListByAlaram(String alarmUUID, int last, int status, String baseUUID, String ch){
		Map<String, Object> filter = new HashMap<String, Object>();
		QueryParameter param = new QueryParameter();
		param.qsid = null;
		param.limit = 500;
		param.offset = 0;
		param.order = "startTime";
		
		if(alarmUUID != null && !"".equals(alarmUUID)){
			filter.put("combineUuid__=", alarmUUID);
		}
		if(baseUUID != null && !"".equals(baseUUID)){
			filter.put("baseStation__=", baseUUID);
		}
		if(ch != null && !"".equals(ch)){
			filter.put("channelId__=", ch);
		}		
		if(status != 0){
			filter.put("alarmStatus__=", status + "");
		}
		if(last != 0){
			param.order = "-startTime";
			param.limit = last;
		}
		
		param.param = filter;
		QueryResult alarms = storage.queryData(AlarmRecord.class, param);
		
		if(log.isDebugEnabled()){
			log.debug(String.format("image files:%s, alarmID:%s, last:%s, status:%s, uuid:%s", alarms.data.size(), 
					alarmUUID, last, status, baseUUID));
		}		
		return alarms.data;
	}
	
	public File getRealPath(AlarmRecord alarm){
		File f = new File(this.rootPath, alarm.videoPath);
		return f.isFile() ? f: null;
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
		log.info("Image root path:" + this.rootPath.getAbsolutePath());
	}	
	
	protected String getSavePath(Date startDate, String clientId, String user,
			String alarmType, String ch) {
		String path = pattern;
		Calendar now = Calendar.getInstance();
		now.setTimeInMillis(startDate.getTime());

		path = path.replaceAll("\\$\\{yyyy\\}",
				String.format("%04d", now.get(Calendar.YEAR)));
		path = path.replaceAll("\\$\\{mm\\}",
				String.format("%02d", now.get(Calendar.MONTH) + 1));
		path = path.replaceAll("\\$\\{dd\\}",
				String.format("%02d", now.get(Calendar.DAY_OF_MONTH)));

		path = path.replaceAll("\\$\\{HH\\}",
				String.format("%02d", now.get(Calendar.HOUR_OF_DAY)));
		path = path.replaceAll("\\$\\{MM\\}",
				String.format("%02d", now.get(Calendar.MINUTE)));
		path = path.replaceAll("\\$\\{SS\\}",
				String.format("%02d", now.get(Calendar.SECOND)));
		path = path.replaceAll("\\$\\{SSS\\}",
				String.format("%03d", now.get(Calendar.MILLISECOND)));

		path = path.replaceAll("\\$\\{UUID\\}", clientId);
		path = path.replaceAll("\\$\\{USER\\}", user);
		path = path.replaceAll("\\$\\{ALARM_TYPE\\}", alarmType);
		path = path.replaceAll("\\$\\{CHANNEL\\}", ch);

		path = path.replaceAll("\\\\/", File.separator);

		File pathFile = new File(rootPath, path);
		File dirFile = pathFile.getParentFile();
		if (!dirFile.exists()) {
			if (!dirFile.mkdirs()) {
				log.error("Failed to create directory:"
						+ dirFile.getAbsolutePath());
			}
		}

		return path;
	}	
	

}
