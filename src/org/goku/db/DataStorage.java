package org.goku.db;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.goku.core.model.BaseStation;
import org.goku.core.model.Location;
import org.goku.core.model.RouteServer;
import org.goku.core.model.User;
import org.goku.core.model.VideoTask;
import org.goku.settings.Settings;

/**
 * 数据访问接口，支持JDBC和HTTP的实现。转发服务器，如果不能直接访问数据库，需要实现
 * HTTP方式从控制服务器读取数据。
 * @author deon
 *
 */
public abstract class DataStorage {
	/**
	 * 根据主建加载一个对象，如果不存在。返回为null.
	 * @param obj
	 * @return 加载的对象，如果不存在，返回为null.
	 */	
	public abstract Object load(Class cls, String pk);
	
	/**
	 * 根据条件过滤对象。
	 */		
	public Collection list(Class cls, String filter, Object[] param){
		return null;
	}
	
	
	/**
	 * 保存一个数据对象，如果不存在，创建一个新对象，否则更新已存在对象。
	 * @param obj
	 * @return 返回true, 如果创建了新对象，否则为false;
	 */
	public abstract boolean save(Object obj);
	public abstract boolean save(Object obj, String[] fields);
	public abstract int execute_sql(String sql, Object[] param) throws SQLException;
	public abstract Collection<Map<String, Object>> query(String sql, Object[] param);
	
	public abstract boolean checkConnect();
	public boolean isAdmin(User user){return false;}
	
	private static DataStorage dummy = new DummyDataStorage(null);
	
	public static DataStorage create(Settings param){
		if(param.getString(Settings.DB_MASTER_DB, "").equals("dev_dummy")){
			return dummy;
		}else {
			return new JDBCDataStorage(param);
		}
	}
	
	//public abstract Location getRootLocation(User user);
	
	/**
	 * 根据用户取到可以监控的基站。
	 * @param user
	 * @return
	 */
	public abstract Collection<BaseStation> listStation(User user);

	/**
	 * 根据用户取到用户相关的计划任务。
	 * @param user
	 * @return
	 */
	public abstract Collection<VideoTask> listTask(User user);	
	
	
	/**
	 * 取到RouteServer下面的基站列表。
	 * @param route
	 * @return
	 */
	public abstract Collection<BaseStation> listStation(RouteServer route);
	
	/**
	 * 取到僵死的基站列表，1. 长时间没有更新的， 2. 没有RouteServer的。
	 * 取出后进行重新调度。
	 * @param route
	 * @return
	 */
	public abstract Collection<BaseStation> listDeadStation(String group);

	/**
	 * 删除RouteServer，清除基站和Route之间的关系。
	 * @param route
	 * @return
	 */
	public abstract void removeRouteServer(RouteServer route);
	
	/**
	 * 删除RouteServer，清除基站和Route之间的关系。
	 * @param route
	 * @return
	 */
	public abstract QueryResult queryData(Class obj, QueryParameter param);	
	
	public Location getRootLocation(User user) {
		Collection<BaseStation> baseList = this.listStation(user);
		Map<String, Location> cache = new HashMap<String, Location>();
		
		Location l = null, root = null;
		Location[] tmp = null;
		Collection<Location> unkownRoot = new ArrayList<Location>();
		for(BaseStation bs : baseList){
			l = cache.get(bs.locationUUID);
			if(l == null){
				tmp = this.loadTreeByLeaf(bs.locationUUID, cache);
				l = tmp[1];
				if(root == null){
					root = tmp[0];
				}else if(tmp[0] != null && !root.equals(tmp[0]) && !unkownRoot.contains(tmp[0])){
					unkownRoot.add(tmp[0]);					
				}
			}
			l.listBTS.add(bs);
		}
		if(root != null && !unkownRoot.isEmpty()){
			root.children.addAll(unkownRoot);
		}else if(root == null){
			root = new Location();
			root.uuid = "0";
			root.name = "NotFound";
		}
		return root;
	}
	
	protected Location[] loadTreeByLeaf(final String uuid, final Map<String, Location> cache){
		Location[] result = new Location[2];
		Location l = (Location)this.load(Location.class, uuid);
		if(l == null){
			l = new Location();
			l.uuid = uuid;
			l.name = uuid;
			result[0] = l;
			result[1] = l;
			cache.put(uuid, l);
		}else {
			cache.put(uuid, l);
			result[1] = l;
			if(l.parent == null || "".equals(l.parent)){
				result[0] = l;
			}else if(cache.containsKey(l.parent)){
				cache.get(l.parent).children.add(l);
			}else {
				Location[] temp = loadTreeByLeaf(l.parent, cache);
				result[0] = temp[0];
				temp[1].children.add(l);
			}
		}
		return result;		
	}	
}
