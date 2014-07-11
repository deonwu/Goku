package org.goku.core.model;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;

public class BaseStation  implements JSONStreamAware{
	public static final String ORM_TABLE = "base_station";
	public static final String[] ORM_FIELDS = new String[]{"uuid", "name", "connectionStatus",
		"groupName", "routeServer", "locationId", 
		"lastUpdate", "lastActive", "createDate", "lastDownVideo",
		"alarmStatus",
		"devType",
		"channels", "btsCategory", "locationUUID", "supportAlarm"};
	public static final String[] ORM_PK_FIELDS = new String[]{"uuid"};
	
	public static final int TYPE_VIDEO = 1;
	public static final int TYPE_IMAGE = 2;
	
	/**
	 * 基站的唯一标识，在客户端界面显示，初始化时配置。
	 */
	public String uuid;
	
	public String name;
	
	/**
	 * 基站的连接状态，
     choices=(('connected', "连接成功"),
             ('timeout', "连接超时"),
             ('error', "登录错误"),
             ('new', "新增"),
	 */
	public String connectionStatus = "01";
	
	/**
	 * 基站的分组编号，同一组的基站，转发服务器实现自动分配。某个服务器负载过高，或关闭
	 * 后，由组内其他服务器分担，监控任务。
	 * 
	 * 由基站初始化时定义。
	 */	
	public String groupName;

	/**
	 * 基站的转发服务器的地址，<ip>:<port>. 由中心服务器分配更新。由运行时动态调整。
	 */
	public String routeServer;

	/**
	 * 基站的内部连接字符串，转发服务器使用其，连接监控终端。
	 * 
	 * 由基站初始化时定义。
	 */	
	public String locationId;
	
	/**
	 * 基站配置信息的最后更新时间。
	 */
	public Date lastUpdate;
	
	/**
	 * 基站配置信息的创建时间。
	 */	
	public Date createDate;

	/**
	 * 最后一次下载录像的时间。
	 */
	public Date lastDownVideo;
	
	/**
	 * 最后活动时间，用来计算心跳时间。
	 */	
	public Date lastActive;

	/**
	 * alarm状态。
	 */
	public String alarmStatus;
	
	/**
	 * 设备类型， (视频｜图片|地点)
	 */
	public int devType;
	
	/**
	 * 摄像头, 例如：1:通道1,2:通道2
	 */
	public String channels;
	
	/**
	 * 端局类型. (客户定义)
	 */	
	public String btsCategory;
	
	/**
	 * 基站的位置，（关联Location对象，例如：杭州，萧山)
	 */	
	public String locationUUID;
	
	/**
	 * 支持的告警列表。
	 */
	public String supportAlarm;
	
	private MonitorChannel[] channelList = null;
	private Collection<String> supportAlarmList = null;
	
	//public 
	
	public String getStatus(){
		if(alarmStatus != null && !"".equals(this.alarmStatus)){
			return this.alarmStatus;
		}else {
			return this.connectionStatus;
		}
	}
	
	public String getName(){
		return name == null || "".equals(name.trim())
				? uuid : name;
	}
	
	public boolean equals(Object o){
		if(o instanceof BaseStation){
			BaseStation bs = (BaseStation)o;
			if(bs.uuid != null && this.uuid != null){
				return this.uuid.equals(((BaseStation) o).uuid);
			}
		}
		return false;
	}
	
	public MonitorChannel getChannel(int id){
		if(this.channelList == null){
			this.initChannelList();
		}
		if(id >= 0 && id < this.channelList.length){
			return this.channelList[id];
		}	
		return null;
	}
	
	public MonitorChannel[] getChannels(){
		if(this.channelList == null){
			this.initChannelList();
		}		
		List<MonitorChannel> temp = new ArrayList<MonitorChannel>();
		for(int i = 0; i < this.channelList.length; i++){
			if(this.channelList[i] != null){
				temp.add(this.channelList[i]);
			}
		}
		return temp.toArray(new MonitorChannel[]{});
	}
	
	public boolean isSupport(String code){
		if (supportAlarmList == null){
			supportAlarmList = new ArrayList<String>();
			if(this.supportAlarm != null){
				for(String s : supportAlarm.split(",")){
					if(s.trim().length() > 0){
						this.supportAlarmList.add(s.trim());
					}
				}
			}
		}
		if(code == null || "".equals(code.trim()))return false;
		
		return supportAlarmList.contains(code.trim());
	}
	
	public String getBTSCategoryName(){
		if(this.btsCategory == null || "".equals(this.btsCategory)){
			return "0";
		}
		return this.btsCategory;
	}
	
	private void initChannelList(){
		List<MonitorChannel> temp = new ArrayList<MonitorChannel>();
		MonitorChannel ch = null;
		
		int maxId = 0;
		for(String x : this.channels.split(",")){
			String[] info = x.split(":", 2);
			ch = new MonitorChannel();
			try{
				ch.id = Integer.parseInt(info[0]);
			}catch(Throwable e){}
			maxId = maxId > ch.id ? maxId : ch.id;
			ch.name = info[1];
			temp.add(ch);
		}
		
		this.channelList = new MonitorChannel[maxId + 1];
		for(MonitorChannel ch1: temp){
			this.channelList[ch1.id] = ch1;
		}
	}
	
	@Override
	public void writeJSONString(Writer out) throws IOException {
        Map<String, Object> obj = new HashMap<String, Object>();
        obj.put("uuid", uuid);
        obj.put("name", name);
        obj.put("devType", devType);
        obj.put("routeServer", routeServer);
        obj.put("status", getStatus());
        
        JSONValue.writeJSONString(obj, out);
	}
	
	public String toString(){
		return String.format("%s<%s>", this.uuid, this.name); 
	}
}
