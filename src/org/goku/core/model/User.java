package org.goku.core.model;

import java.util.Date;

public class User {
	public static final String ORM_TABLE = "user_account";
	public static final String[] ORM_FIELDS = new String[]{"name", "password",
		"display", "status", "lastActive", 
		};
	public static final String[] ORM_PK_FIELDS = new String[]{"name"};
	
	public String name;
	public String password;

	public String display;
	
	public String status = "ok";
	public Date lastActive;
	
	/**
	 * 最后一次查询实时告警的时间, 在查询实时告警时，只更新这个时间以后的告警。
	 */
	public Date lastRealAlarmTime = null;
	public boolean isAdmin = false;
}
