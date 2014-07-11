package org.goku.master;

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.AlarmDefine;
import org.goku.core.model.AlarmRecord;
import org.goku.core.model.VideoTask;
import org.goku.db.DataStorage;

public class VideoTaskManager implements Runnable {
	
	private Log log = LogFactory.getLog("task.manager");
	
	protected DateFormat curDate= new SimpleDateFormat("yyyyMMdd");
	protected DateFormat curTime= new SimpleDateFormat("HHmm");
	
	//保存视频调度记录，什么通道切换到什么窗口
	private Collection<AlarmRecord> doneList = new ArrayList<AlarmRecord>();
	
	private Map<String, TaskStatus> activeTasks = new HashMap<String, TaskStatus>();
	private boolean isRunning = true;
	private long lastRefreshTime = 0;	
	private DataStorage storage = null; 

	public Collection<AlarmRecord> getVideoEvents(String name, long startTime){
		Collection<AlarmRecord> list = new ArrayList<AlarmRecord>();
		list.addAll(this.doneList);
		for(Iterator<AlarmRecord> iter = list.iterator(); iter.hasNext();){
			AlarmRecord a = iter.next();
			if(!a.user.trim().equals(name.trim()) || a.lastUpdateTime.getTime() < startTime){
				iter.remove();
			}
		}
		log.debug("Get video task, user:" + name +", size:" + list.size());
		return list;
	}
	
	public VideoTaskManager(DataStorage storage){
		this.storage = storage;
	}
	
	@Override
	public void run() {
		try{
			while(isRunning){
				this.reRefreshTask(false);
				Collection<TaskStatus> wins = new ArrayList<TaskStatus>();
				wins.addAll(activeTasks.values());
				
				for(TaskStatus task: wins){
					VideoTask video = task.next();
					if(video == null) continue;
					AlarmRecord alarm = new AlarmRecord();
					alarm.user = video.userName;
					alarm.alarmCode = AlarmDefine.AL_4002;
					alarm.alarmCategory = "3";
					alarm.alarmStatus = "1";
					alarm.baseStation = video.uuid;
					alarm.channelId = video.channel;
					alarm.alarmLevel = video.windowID + "";
					alarm.lastUpdateTime = new Date(System.currentTimeMillis());
					alarm.uuid = "0";
					log.debug(String.format("video task, id:%s, user:%s, uuid:%s, ch:%s, win:%s, time:%ss",
							video.taskID, alarm.user, alarm.baseStation, alarm.channelId,
							alarm.alarmLevel, video.minShowTime
							));
					doneList.add(alarm);	
				}
				
				//删除15秒前的变化。
				long cur = System.currentTimeMillis();
				for(Iterator<AlarmRecord> iter = doneList.iterator(); iter.hasNext(); ){
					if(cur - iter.next().lastUpdateTime.getTime() > 1000 * 15){
						iter.remove();
					}
				}
				try {
					Thread.sleep(1000);
				}catch(InterruptedException e) {
					log.warn(e.toString());
				}
			}
		}catch(Throwable e){
			log.error(e.toString(), e);
		}
	}
	
	/**
	 * 刷新当天需要执行的计划任务。每分钟刷新一次，激活所有需要播放的视频。
	 */
	@SuppressWarnings("unchecked")
	private synchronized void reRefreshTask(boolean force){
		if(force || System.currentTimeMillis() - this.lastRefreshTime > 1000 * 60){
			lastRefreshTime = System.currentTimeMillis();
			disableNotAllowTask();
			
			Collection tasks = this.storage.list(VideoTask.class, "status=1 order by userName, windowID, showOrder",
						new String[]{});
			for(TaskStatus status: this.activeTasks.values()){
				status.videoList.clear();
			}
			
			VideoTask task = null;
			String key = null;
			TaskStatus status = null;
			
			long curDate = Integer.parseInt(this.curDate.format(new Date()));
			long curTime = Integer.parseInt(this.curTime.format(new Date()));
			
			Calendar now = Calendar.getInstance();
			now.setTimeInMillis(System.currentTimeMillis());
			int curWeek = now.get(Calendar.DAY_OF_WEEK);
			
			for(Iterator<VideoTask> iter = tasks.iterator(); iter.hasNext();){
				task = iter.next();
				key = task.userName + "_" + task.windowID;
				status = this.activeTasks.get(key);
				if(status == null){
					status = new TaskStatus();
					this.activeTasks.put(key, status);
				}
				if(this.isActiveTask(task, curDate, curTime, xxxToWeek(curWeek))){
					log.debug(String.format("enable task, id:%s, user:%s, uuid:%s, ch:%s, win:%s, time:%ss, start:%s",
							task.taskID, task.userName, task.uuid, task.channel,
							task.windowID, task.minShowTime, task.startTime
							));
					status.addVideoTask(task);
				}
			}
			
			for(Iterator<TaskStatus> iter = activeTasks.values().iterator(); iter.hasNext();){
				if(iter.next().videoList.size() == 0){
					iter.remove();
				}
			}
		}
	}
	
	/**
	 * 如果用户定义的基站任务，根据用户分组没有权限查看，先把任务设置为不可运行。 
	 */
	private void disableNotAllowTask(){
		String sql = "update video_task r set status='2' where not exists(" +
					 "select 1 from relation_station_group r1 " + 
					 " join relation_user_group rg " + 
					 " on(r1.user_group_id=rg.user_group_id) " +
					 " where " +
					 " rg.user_id=r.userName and r1.base_station_id = r.uuid " +
					 " ) and not exists ( " +
					 " select 1 from user_group g " + 
					 " join relation_user_group rg on(g.name=rg.user_group_id) " +
					 " where rg.user_id=r.userName and g.isAdmin=1 " + 
					 ")"; 
		try{
			this.storage.execute_sql(sql, new Object[]{});
		}catch(SQLException e){
			log.warn("Failed to disableNotAllowTask, error:" + e.toString());
		}
	}
	
	private int xxxToWeek(int x){
		switch(x){
			case Calendar.MONDAY: return 1;
			case Calendar.TUESDAY: return 2;
			case Calendar.WEDNESDAY: return 3;
			case Calendar.THURSDAY: return 4;
			case Calendar.FRIDAY: return 5;
			case Calendar.SATURDAY: return 6;
			case Calendar.SUNDAY: return 7;
		}
		return 0;
	}
	
	private boolean isActiveTask(VideoTask task, long curDate, long curTime, int week){
		if(task.startDate != null && !"".equals(task.startDate.trim())){
			String date = task.startDate.replaceAll("[^0-9]+", "");
			if(Integer.parseInt(date.trim()) > curDate){
				return false;
			}
		}
		
		//开始时间大于当前时间
		if(task.startTime != null && !"".equals(task.startTime.trim())){
			String date = task.startTime.replaceAll("[^0-9]+", "");
			if(Integer.parseInt(date.trim()) > curTime){
				return false;
			}
		}
		
		if(task.endDate != null && !"".equals(task.endDate.trim())){
			String date = task.endDate.replaceAll("[^0-9]+", "");
			if(Integer.parseInt(date.trim()) < curDate){
				return false;
			}
		}
		
		//结束时间小于当前时间。
		if(task.endTime != null && !"".equals(task.endTime.trim())){
			String date = task.endTime.replaceAll("[^0-9]+", "");
			if(Integer.parseInt(date.trim()) < curTime){
				return false;
			}
		}
		
		//星期不存在。
		if(task.weekDays != null && !"".equals(task.weekDays.trim())){
			if(task.weekDays.indexOf(week + "") == -1){
				return false;
			}
		}
		
		return true;
	}
	
	class TaskStatus{
		//public String userName;
		public long nextChangeTime = 0;
		public int curVideoIndex = -1;
		public Set<VideoTask> videoList = new TreeSet<VideoTask>(
				new Comparator<VideoTask>(){
					@Override
					public int compare(VideoTask t, VideoTask t2) {
						if(t.uuid.equals(t2.uuid) && 
						   t.channel.equals(t2.channel)){
							return 0;
						}else {
							return t.showOrder > t2.showOrder ? 1 : -1;
						}
					}}
				);
		
		/**
		 * 返回本窗口下一个显示的视频通道。如果没有变化返回null.
		 * @return
		 */
		public VideoTask next(){
			if(System.currentTimeMillis() < this.nextChangeTime) return null;
			if(this.videoList.size() == 0) return null;
			curVideoIndex++;
			curVideoIndex = curVideoIndex % this.videoList.size();
			VideoTask t = null;
			int i = 0;
			for(Iterator<VideoTask> iter = this.videoList.iterator(); iter.hasNext() && i <= this.curVideoIndex; i++){
				t = iter.next();
			}
			if(t != null){
				nextChangeTime = Integer.parseInt(t.minShowTime.trim()) * 1000 + System.currentTimeMillis(); 
			}			
			return t;
		}
		
		public void addVideoTask(Collection<VideoTask> list){
			for(VideoTask t: list){
				addVideoTask(t);
			}
		}
		public void addVideoTask(VideoTask task){
			this.videoList.add(task);
		}
	}
}
