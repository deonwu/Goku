package org.goku.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.AlarmRecord;
import org.goku.core.model.BaseStation;
import org.goku.core.model.Location;
import org.goku.core.model.RouteServer;
import org.goku.core.model.User;
import org.goku.core.model.VideoTask;
import org.goku.settings.Settings;

/**
 * 用于调试接口使用的，测试数据。
 * @author deon
 *
 */
public class DummyDataStorage extends DataStorage {
	private Log log = LogFactory.getLog("dummy");
	private Map<Class, Collection<Object>> objects = new HashMap<Class, Collection<Object>>();
	
	public DummyDataStorage(Settings settings){
		Collection<Object> xxx = new Vector<Object>();
		objects.put(User.class, xxx);
		
		User u = new User();
		u.name = "test1";
		u.password = "pass";		
		xxx.add(u);
	}
	
	@Override
	public Object load(Class cls, String pk){
		Collection<Object> objList = objects.get(cls);
		if(cls.equals(User.class)){
			for(Object o: objList){
				User uu = (User)o;
				if(uu.name.equals(pk))return uu;
			}
		}else if(cls.equals(BaseStation.class)){
			for(Object o: objList){
				BaseStation uu = (BaseStation)o;
				if(uu.uuid.equals(pk)) return uu;
			}
		}else if(cls.equals(AlarmRecord.class)){
			for(Object o: objList){
				AlarmRecord uu = (AlarmRecord)o;
				if(uu.uuid.equals(pk)) return uu;
			}
		}else if(cls.equals(Location.class)){
			for(Object o: objList){
				Location uu = (Location)o;
				if(uu.uuid.equals(pk)) return uu;
			}
		}else if(cls.equals(VideoTask.class)){
			for(Object o: objList){
				VideoTask uu = (VideoTask)o;
				if(uu.taskID == Integer.parseInt(pk)) return uu;
			}
		}
		
		return null;
	}	

	@Override
	public boolean save(Object obj) {
		log.info("Save object:" + obj.toString());
		//if(thi)
		if(obj instanceof AlarmRecord){
			if(this.load(obj.getClass(), ((AlarmRecord)obj).uuid) == null){
				Collection<Object> objList = objects.get(obj.getClass());
				((AlarmRecord)obj).lastUpdateTime = new Date(System.currentTimeMillis());
				if(!objList.contains(obj)){
					objList.add(obj);
				}else {
					log.info("Failed to save AlarmRecord, uuid:" + ((AlarmRecord)obj).uuid);
				}
			}
		}else{
			Collection<Object> objList = objects.get(obj.getClass());
			if(objList != null && !objList.contains(obj)){
				objList.add(obj);
			}
		}
		return false;
	}

	@Override
	public int execute_sql(String sql, Object[] param) throws SQLException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Collection<Map<String, Object>> query(String sql, Object[] param) {
		// TODO Auto-generated method stub
		return new ArrayList<Map<String, Object>>();
	}

	@Override
	public boolean checkConnect() {
		
		log.warn("Loading DummayDataStorage, It's only used for development.");
		loadStanloneDB();
		
		log.warn("=================================================");
		log.warn("--------User----------");
		Collection<Object> objList = objects.get(User.class);
		for(Object o: objList){
			User uu = (User)o;
			log.info("name:" + uu.name + ", password:" + uu.password);
		}
		
		log.warn("--------BaseStation----------");
		objList = objects.get(BaseStation.class);
		for(Object o: objList){
			BaseStation uu = (BaseStation)o;
			log.info("name:" + uu.uuid + ", location:" + uu.locationId);
		}
		
		log.warn("--------Location----------");
		objList = objects.get(Location.class);
		for(Object o: objList){
			Location uu = (Location)o;
			log.info("name:" + uu.uuid + ", location:" + uu.name + ", parent:" + uu.parent);
		}
		
		
		log.warn("--------Alarm Record----------");
		objList = objects.get(AlarmRecord.class);
		for(Object o: objList){
			AlarmRecord uu = (AlarmRecord)o;
			log.info("uuid:" + uu.uuid + ", videoPath:" + uu.videoPath +
					", alarm code:" + uu.alarmCode + 
					", BTS id:" + uu.baseStation +
					", level:" + uu.getLevel());
		}
		
		log.warn("--------Task Record----------");
		objList = objects.get(VideoTask.class);
		for(Object o: objList){
			VideoTask uu = (VideoTask)o;
			log.info("uuid:" + uu.taskID + ", win:" + uu.windowID + ", uuid:" + uu.uuid + 
					", channel:" + uu.channel);
		}
		
		log.warn("=================================================");
		return true;
	}
	
	private void loadStanloneDB(){
		Collection<Object> bsList = new Vector<Object>();
		Collection<Object> alarmList = new Vector<Object>();
		Collection<Object> localList = new Vector<Object>();
		Collection<Object> taskList = new Vector<Object>();
		
		objects.put(BaseStation.class, bsList);
		objects.put(AlarmRecord.class, alarmList);
		objects.put(Location.class, localList);
		objects.put(VideoTask.class, taskList);
		
		File file = new File("standlone.db");
		if(file.isFile()){
			log.info("Loading db file:" + file.getAbsolutePath());
			log.info("BS:<uuid>$<devType>$<groupName>$<IP:port>$<channels>$<location>$<name>");
			log.info("RE:<uuid>$<videoPath>$<alarmCode>$<baseStation>$<channelId>$<alarmStatus>");
			log.info("LO:<uuid>$<name>$<parent>");
			log.info("TS:<taskID>$<name>$<uuid>$<channel>$<windowID>$<startDate>$<endDate>$<weeks>$<startTime>$<endTime>$<minShowTime>$<showOrder>$<status>");
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
				
				for(String line = ""; line != null;){
					line = reader.readLine();
					if(line == null)break;
					line = line.trim();
					if(line.length() == 0)continue;
					log.debug("Read line:" + line);
					if(line.startsWith("BS:")){
						line = line.split(":", 2)[1];
						BaseStation bs = new BaseStation();
						String[] bsinfo = line.split("\\$");
						log.debug("Bsinfo:" + bsinfo[0]);
						bs.uuid = bsinfo[0];
						bs.devType = Integer.parseInt(bsinfo[1]);
						bs.groupName = bsinfo[2];
						bs.locationId = bsinfo[3];
						if(bsinfo.length > 4){
							bs.channels = bsinfo[4];
						}
						if(bsinfo.length > 5){
							bs.locationUUID = bsinfo[5];
						}
						if(bsinfo.length > 6){
							bs.name = bsinfo[6];
						}						
						
						bsList.add(bs);
					}else if(line.startsWith("RE:")){
						line = line.split(":", 2)[1];
						AlarmRecord alarm = new AlarmRecord();
						String[] bsinfo = line.split("\\$");
						log.debug("Alarm:" + bsinfo[0]);
						alarm.uuid =bsinfo[0];
						alarm.videoPath = bsinfo[1];
						alarm.startTime = new Date();
						alarm.endTime = new Date();
						if(bsinfo.length > 2){
							alarm.alarmCode = bsinfo[2];
						}
						if(bsinfo.length > 3){
							alarm.baseStation = bsinfo[3];
						}
						if(bsinfo.length > 4){
							alarm.channelId = bsinfo[4];
						}
						if(bsinfo.length > 5){
							alarm.alarmStatus = bsinfo[5];
						}
						alarmList.add(alarm);						
					}else if(line.startsWith("LO:")){
						line = line.split(":", 2)[1];
						Location local = new Location();
						String[] bsinfo = line.split("\\$");
						log.debug("Location:" + bsinfo[0]);
						local.uuid =bsinfo[0];
						local.name = bsinfo[1];
						if(bsinfo.length > 2){
							local.parent = bsinfo[2];
						}
						localList.add(local);						
					}else if(line.startsWith("TS:")){
						line = line.split(":", 2)[1];
						VideoTask task = new VideoTask();
						task.userName = "test";
						String[] attrs = new String[]{
								"taskID", "name", "uuid", "channel", "windowID", "startDate", "endDate", "weekDays", "startTime",  
								"endTime",  "minShowTime", "showOrder", "status"};
						String[] val = line.split("\\$");
						for(int i = 0; i < Math.max(attrs.length, val.length); i++){
							if(attrs[i].equals("taskID") || 
							   attrs[i].equals("showOrder") || 
							   attrs[i].equals("windowID")){
								VideoTask.class.getField(attrs[i]).set(task, new Integer(val[i]));
							}else {
								VideoTask.class.getField(attrs[i]).set(task, val[i]);
							}
						}
						taskList.add(task);
					}
				}
			} catch (Exception e) {
				log.error(e.toString(), e);
			}
		}else {
			log.warn("Not found 'standlone.db' data file");
		}
	}

	@Override
	public Collection<BaseStation> listStation(User user) {
		//Collection<BaseStation> xxx = new Vector<BaseStation>();
		Collection xxx = objects.get(BaseStation.class);
		
		return xxx;
	}

	@Override
	public Collection<BaseStation> listStation(RouteServer route) {
		// TODO Auto-generated method stub
		return new ArrayList<BaseStation>();
	}

	@Override
	public Collection<BaseStation> listDeadStation(String group) {
		Collection xxx = objects.get(BaseStation.class);
		
		return xxx;		
		//return null;
	}

	@Override
	public void removeRouteServer(RouteServer route) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean save(Object obj, String[] fields) {
		// TODO Auto-generated method stub
		return false;
	}

	public QueryResult queryData(Class cls, QueryParameter param){
		QueryResult result = new QueryResult();
		result.data = new Vector();
		//result.data = objects.get(cls);
		if(objects.get(cls) != null){
			result.data.addAll(objects.get(cls)); // = new Vector();
		}
		Object val = param.param.get("lastUpdateTime__>=");
		if(val != null){
			AlarmRecord alarm = null;
			long curTime = ((Date)val).getTime();
			for(Iterator i = result.data.iterator(); i.hasNext();){
				alarm = (AlarmRecord)i.next();
				if(alarm.lastUpdateTime.getTime() < curTime){
					i.remove();
				}
			}
		}
		
		result.sessionId = "0001";
		result.count = result.data.size();
		
		return result;
	}

	@Override
	public Collection<VideoTask> listTask(User user) {
		// TODO Auto-generated method stub
		Collection list = objects.get(VideoTask.class);
		return list;
	}

	/*
	@Override
	public Location getRootLocation(User user) {
		Location root = new Location();
		root.uuid = "001";
		root.name = "杭州";

		Location subNode = new Location();
		subNode.uuid = "002";
		subNode.name = "滨江";
		
		root.children.add(subNode);
		

		subNode = new Location();
		subNode.uuid = "003";
		subNode.name = "萧山";
		root.children.add(subNode);
		
		BaseStation station = new BaseStation();
		station.uuid = "1001";
		station.name = "诺西大楼";
		station.devType = 1;
		station.routeServer = "127.0.0.1:8081";
		station.channels = "1:通道1,2:通道2";
		subNode.listBTS.add(station);

		station = new BaseStation();
		station.uuid = "10012";
		station.name = "信诚路口";
		station.devType = 1;
		station.routeServer = "127.0.0.1:8081";
		station.channels = "1:通道1,2:通道2";
		subNode.listBTS.add(station);
		
		return root;
	}*/
}
