package org.goku.core.model;

import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.goku.video.FileVideoRecorder;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;

public class AlarmRecord implements JSONStreamAware{
	protected DateFormat format= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	public static final String ORM_TABLE = "alarm_record";
	public static final String[] ORM_FIELDS = new String[]{"uuid", "baseStation", 
		"channelId", "alarmCode", "alarmStatus", "user",  "alarmLevel", "alarmCategory",
		"startTime", "endTime", "lastUpdateTime", "comfirmTime", "videoPath"};
	public static final String[] ORM_PK_FIELDS = new String[]{"uuid"};
	
	public String uuid = null;
	public String baseStation = "";
	public String channelId = ""; //发生告警的通道号
	public String startTimeString;
	public String alarmCode = "";
	public String alarmLevel = "1";
	public String alarmCategory = "1"; //图片,视频,无视频／图片
	/**
	 * 告警处理状态，超时，正在发生，手动取消，删除
	 */
	public String alarmStatus = "1";
	public String user = "";        //告警确认人
	public Date startTime = null;
	public Date endTime = null;
	public Date lastUpdateTime = new Date();
	public String videoPath;

	public Date comfirmTime = null; //告警确认时间
	
	/**
	 * 零时保存。
	 */
	public transient FileVideoRecorder recorder = null;
	
	public String getLevel(){
		return this.alarmLevel;
	}
	
	public String getChannelId(){
		return this.baseStation + ":" + this.channelId;
	}
	
	public void generatePK(){
		if(this.uuid == null){
			long t = System.currentTimeMillis();
			t = t % (1000L * 3600 * 24 * 365);
			uuid = baseStation + String.format("%011d", t);
		}
	}
	
	public boolean equals(Object obj){
		if(obj instanceof AlarmRecord){
			String u = ((AlarmRecord) obj).uuid; 
			if(u != null && this.uuid != null && this.uuid.equals(u)){
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void writeJSONString(Writer out) throws IOException {
        Map<String, Object> obj = new HashMap<String, Object>();
        obj.put("uuid", uuid);
        obj.put("baseStation", baseStation);
        obj.put("alarmStatus", alarmStatus);
        obj.put("alarmType", alarmCode);
        obj.put("level", getLevel());
        obj.put("startTime", format.format(startTime));
        obj.put("endTime", format.format(endTime));
        
        JSONValue.writeJSONString(obj, out);
	}		
	
	public static void main(String[] args){
		AlarmRecord alarm = new AlarmRecord();
		alarm.generatePK();
	}
}
