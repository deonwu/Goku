package org.goku.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.goku.db.DataStorage;

public class AlarmDefine implements Cloneable{
	public static final String ORM_TABLE = "alarm_code_list";
	public static final String[] ORM_FIELDS = new String[]{"alarmCode", "alarmLevel",
		"alarmName", "reActiveTime", "alarmCategory", "alarmStatus", "alarmDesc" };
	public static final String[] ORM_PK_FIELDS = new String[]{"alarmCode"};
	
	//监控告警
	public static final String AL_1001 = "1001"; //外部报警
	public static final String AL_1002 = "1002"; //视频丢失
	public static final String AL_1003 = "1003"; //动态检测
	public static final String AL_1004 = "1004"; //硬盘错误

	//设备状态
	public static final String AL_2001 = "2001"; //连接超时
	public static final String AL_2002 = "2002"; //认证错误	
	public static final String AL_2003 = "2003"; //时间和服务器不同步	

	//系统状态
	public static final String AL_3001 = "3001"; //视频服务器超时
	public static final String AL_3002 = "3002"; //视频服务负载过高
	public static final String AL_3003 = "3003"; //视频存储错误
	//public static final String AL_3004 = "3004"; //客户端和服务器时间不同步
	
	//系统交互
	public static final String AL_4001 = "4001"; //刷新基站列表 --修改权限/基站信息/转发服务器等问题。 
	public static final String AL_4002 = "4002"; //刷新视频窗口 -- alarmLevel 表示窗口ID
	
	//图片告警
	public static final String AL_5001 = "5001"; //图片告警
	public static final String AL_5002 = "5002"; //图片连接错误
	
	public String alarmCode = "";
	public String alarmLevel = "1";
	public String alarmName = "";	
	
	//'1', "视频"  '2', "图片"  '3', "无视频/图片"
	public String alarmCategory = "1";
	
	//最小重新触发时间。单位分钟.
	public long reActiveTime = 5;

	//'1', "全局告警", '2', "策略告警", '3', "禁用告警"
	public String alarmStatus = "1";

	//最小重新触发时间。单位分钟.
	public String alarmDesc = "";
	
	//alarmStatus
	
	public boolean video = false;
	
	public String[] videoDepend = null;
	public int[] channels = null;
	
	public static Map<String, AlarmDefine> alarms = new HashMap<String, AlarmDefine>();
	static{
		add(new AlarmDefine(AL_1001, "外部报警", "1", 5, "2", ""));
		add(new AlarmDefine(AL_1002, "视频丢失", "3",  5, "2", ""));
		add(new AlarmDefine(AL_1003, "动态检测", "1", 5, "2", ""));
		add(new AlarmDefine(AL_1004, "硬盘丢失", "3", 60 * 24, "1", ""));
		
		add(new AlarmDefine(AL_2001, "连接超时", "3", 5, "1", ""));
		add(new AlarmDefine(AL_2002, "认证错误", "3", 5, "1", ""));
		add(new AlarmDefine(AL_2003, "时间和服务器不同步","3", 5, "1", ""));
		
		add(new AlarmDefine(AL_4001, "刷新基站列表", "3", 5, "1", ""));
		
		add(new AlarmDefine(AL_5001, "图片告警", "2", 10, "1", ""));
		add(new AlarmDefine(AL_5002, "硬件连接", "3", 10, "1", ""));
	}
	public static AlarmDefine alarm(String code){
		AlarmDefine alarm = alarms.get(code);		
		if(alarm == null){
			alarm = new AlarmDefine(code, code, "3");
		}else {
			try {
				alarm = (AlarmDefine) alarm.clone();
			} catch (CloneNotSupportedException e) {
				alarm = new AlarmDefine(code, code, "3");
			}
		}
		return alarm;
	}
	
	/**
	 * 从数据库中，加载告警定义，参数。如果数据库中不存在，把默认参数保存到系统。
	 */
	public static synchronized void initAlarmDefine(DataStorage storage){
		AlarmDefine alarm = null;
		for(String key: alarms.keySet()){
			alarm = (AlarmDefine) storage.load(AlarmDefine.class, key);
			if(alarm != null){
				alarms.put(key, alarm);
			}else {
				storage.save(alarms.get(key));
			}
		}
	}
	
	public static void add(AlarmDefine alarm){
		alarms.put(alarm.alarmCode, alarm);
	}
	
	public AlarmDefine(){		
	}
	
	private AlarmDefine(String code, String name, String category){
		this.alarmCode = code;
		this.alarmName = name;
		this.alarmCategory = category;
	}
	
	private AlarmDefine(String code, String name, String category, long reActive, String status, String desc){
		this(code, name, category);
		this.reActiveTime = reActive;
		this.alarmStatus = status;
		this.alarmDesc = desc;
	}	
	
	public boolean isGlobal(){
		return this.alarmStatus != null && this.alarmStatus.trim().equals("1");
	}	
	
	public boolean isCustomize(){
		return this.alarmStatus != null && this.alarmStatus.trim().equals("2");
	}
	
	public void fillAlarmChannels(int mask){
		List<Integer> channels = new ArrayList<Integer>();
		for(int i = 0; i < 32; i++){
			if((mask >> i & 1) == 1){
				channels.add(i+1);
			}
		}
		this.channels = new int[channels.size()];
		for(int i = channels.size() -1; i >= 0; i--){
			this.channels[i] = channels.get(i);
		}
	}
	
	public String toString(){
		String xx = "";
		if(this.channels != null){
			for(int x : channels){xx += x+ ",";}
		}
		return String.format("<%s>%s ch:%s", this.alarmCode, this.alarmName, xx);
	}
}
