package org.goku.core.model;

import java.util.Collection;
import java.util.Date;
import java.util.Vector;

import org.goku.db.DataStorage;
import org.goku.master.MasterVideoServer;
import org.goku.video.VideoRouteServer;

public class SystemReload {
	private Date lastCheck = new Date();
	
	public SystemReload(){
		
	}
	@SuppressWarnings("unchecked")
	public void check(DataStorage storage){
		if(System.currentTimeMillis() - lastCheck.getTime() < 5000) return;
		//如果环境变量中有no_auto_reload，就不自动加载配置。用于在测试环境
		if(System.getenv("no_auto_reload") != null) return;
		SystemLog status = null;
		Object obj = storage.load(SystemLog.class, "param_updated");		
		if(obj == null)return;
		status = (SystemLog)obj;
		if(status.createDate.getTime() > lastCheck.getTime()){
			//如果配置更新时间超过当前时间1分钟。说明系统时间被修改。需要同步配置更新时间，
			//不然配置总是需要被Reload
			if(status.createDate.getTime() - lastCheck.getTime() > 1000 * 60){
				status.createDate = new Date(System.currentTimeMillis());
				storage.save(status);
			}
			AlarmDefine.initAlarmDefine(storage);
			Collection<BaseStation> updated = null;
			updated = storage.list(BaseStation.class, "lastUpdate > ${0}", 
					new Object[]{this.lastCheck});
			VideoRouteServer server = VideoRouteServer.getInstance();
			if(server != null){
				for(BaseStation bs : updated){
					server.reloadMonitorClient(bs);
				}
			}
			//重新计算修改过的基站。
			MasterVideoServer master = MasterVideoServer.getInstance(); 
			if(master != null){
				Collection<String> groups = new Vector<String>();
				for(BaseStation bs :updated){
					if(!groups.contains(bs.groupName)){
						master.routeManager.balanceGroup(bs.groupName);
						groups.add(bs.groupName);
					}
				}
			}
		}
		lastCheck = new Date(System.currentTimeMillis());
	}
}
