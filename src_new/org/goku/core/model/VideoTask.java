package org.goku.core.model;

import java.util.Collection;
import java.util.Map;

import org.goku.db.DataStorage;

public class VideoTask {
	public static final String ORM_TABLE = "video_task";
	public static final String[] ORM_FIELDS = new String[]{"taskID", "name",
		"userName", "status", "startDate", "endDate", "weekDays",
		"startTime", "endTime", "uuid", "channel", 
		"windowID", "minShowTime", "showOrder"
		};
	public static final String[] ORM_PK_FIELDS = new String[]{"uuid"};
	
	
	/**
	 * 任务PK
	 */
	public int taskID = 0;
	/**
	 * 任务名称
	 */	
	public String name = "";

	/**
	 * 任务用户名
	 */	
	public String userName = "";

	/**
	 * 任务状态, 启用， 禁用等。
	 */	
	public String status = "";

	/**
	 * 开始时间
	 */	
	public String startDate = "";

	/**
	 * 结束时间
	 */	
	public String endDate = "";
	
	public String weekDays = "";


	public String startTime = "";

	/**
	 * 结束时间
	 */	
	public String endTime = "";

	/**
	 * 基站UUID
	 */	
	public String uuid = "";

	/**
	 * 视频通道
	 */	
	public String channel = "";

	/**
	 *显示窗口
	 */		
	public int windowID = 0;

	/**
	 *最短显示时间, 单位秒。
	 */
	public String minShowTime = "";

	/**
	 *order
	 */
	public int showOrder = 1;
	
	public static VideoTask newDefault(DataStorage storage, String userName){
		VideoTask task = new VideoTask();
		task.userName = userName;
		task.taskID = 1;
		String sql = "select max(taskID) as taskPK from video_task";
		Collection<Map<String, Object>> xx = storage.query(sql, new Object[]{});
		if(xx.size() > 0){
			task.taskID = (Integer)xx.iterator().next().get("taskPK") + 1;
		}
		task.showOrder = task.taskID;
		storage.save(task);
		return task;
	}
}
