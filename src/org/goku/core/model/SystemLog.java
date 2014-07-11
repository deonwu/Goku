package org.goku.core.model;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.db.DataStorage;
import org.goku.video.FileVideoRecorder;

public class SystemLog {
	public static final String ORM_TABLE = "goku_system_log";
	public static final String[] ORM_FIELDS = new String[]{"uuid", "actionOwner", "actionObject",
		"actionType", "createDate", "description", 
		};
	public static final String[] ORM_PK_FIELDS = new String[]{"uuid"};
	
	public static final String LOGIN_OK = "login_ok";
	public static final String LOGIN_FAIL = "login_fail";
	public static final String LOGOUT = "logout";
	
	public static final String ALARM_CONFIRM = "alarm_confirm";	
	
	public static final String NEW_ALARM = "alarm";
	public static final String VIDEO_REPLAY = "video_replay";
	public static final String VIDEO_RECORD_START = "video_record_start";
	public static final String VIDEO_RECORD_STOP = "video_record_stop";
	
	public String uuid;
	/**
	 * 操作的发起对象，可以是一个设备，一个服务器，也可以是人。
	 */
	public String actionOwner;
	/**
	 * 操作的目标对象。
	 */
	public String actionObject;
	/**
	 * 操作的名称.
	 */	
	public String actionType;
	//public String actionDate;
	/**
	 * 操作描述信息。
	 */		
	public String description;
	
	public Date createDate;
	
	public static transient DataStorage dataStorage = null;	
	public static void saveLog(String actionType, String owner, String obj, String desc){
		Log logger = LogFactory.getLog("log.system");
		SystemLog log = new SystemLog();
		log.actionType = actionType;
		log.actionOwner = owner;
		log.actionObject = obj;
		log.description = desc;
		log.createDate = new Date(System.currentTimeMillis());		
		String uuid = log.createDate.getTime() + log.actionType + log.actionOwner + log.actionObject;
		log.uuid = md5(uuid);
		if(dataStorage != null){
			dataStorage.save(log);
			logger.info(String.format("Write log:%s,%s->%s, desc:%s", actionType, owner, obj, desc));
		}else {
			logger.warn("NOT initailize log DB connection...");
		}
	}
	
	private static String md5(String str){
	    MessageDigest messageDigest = null;
		try {
			messageDigest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e1) {
		}
		if(messageDigest != null){
			messageDigest.reset();
			messageDigest.update(str.getBytes(Charset.forName("UTF8")));
			final byte[] resultByte = messageDigest.digest();
		    String result = "";
		    for(byte e: resultByte){
		    	result += String.format("%x", e);
		    }
		    return result;			
		}
		return "n/a";
	}	

}
