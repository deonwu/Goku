package org.goku.core.model;

public class UserGroup {
	public static final String ORM_TABLE = "user_group";
	public static final String[] ORM_FIELDS = new String[]{"name", "isAdmin" };
	public static final String[] ORM_PK_FIELDS = new String[]{"name"};
	
	public String name;
	public int isAdmin = 0;
}
