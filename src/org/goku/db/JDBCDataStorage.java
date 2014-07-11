package org.goku.db;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.BaseStation;
import org.goku.core.model.RouteServer;
import org.goku.core.model.SimpleCache;
import org.goku.core.model.User;
import org.goku.core.model.VideoTask;
import org.goku.settings.Settings;

/**
 * 数据库操作的封装。有点饶，低估了实现ORM的复杂性。
 * ***已实现一个简单的连接池****
 * @author deon
 */
public class JDBCDataStorage extends DataStorage {
	protected DateFormat format= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private Log log = LogFactory.getLog("db");
	private int wokingDB = 0;
	private String[][] config = new String[2][3];
	private SimpleCache sessionCache = new SimpleCache();
	private long nextSession = 0;
	
	private ArrayList<Connection> connPool = new ArrayList<Connection>();
	
	public JDBCDataStorage(Settings settings){
		String[] master = new String[3];		
		master[0] = String.format("jdbc:mysql://%s?useUnicode=true&characterEncoding=utf8", 
								   settings.getString(Settings.DB_MASTER_DB, null));
		master[1] = settings.getString(Settings.DB_MASTER_USERNAME, null);
		master[2] = settings.getString(Settings.DB_MASTER_PASSWORD, null);
		config[0] = master;

		String[] secondary = new String[3];		
		secondary[0] = String.format("jdbc:mysql://%s?useUnicode=true&characterEncoding=utf8", 
									  settings.getString(Settings.DB_SECONDARY_DB, null));
		secondary[1] = settings.getString(Settings.DB_SECONDARY_USERNAME, null);
		secondary[2] = settings.getString(Settings.DB_SECONDARY_PASSWORD, null);
		config[1] = secondary;
		
	}
	
	public boolean checkConnect(){
		boolean isOk = false;
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
		} catch (ClassNotFoundException ex) {
			log.error("Not found DB driver.");
		} catch (Exception ex){
			log.error(ex.toString());
		}
		
		try{
			log.info("Check master db connection, url:" + config[0][0]);
			DriverManager.getConnection(config[0][0], 
					config[0][1],
					config[0][2]).close();
			URI host = new URI(config[0][0].split(":", 3)[2]);
			System.setProperty("db_master_db", host.getPath().substring(1));	
			log.info("Set db_master_db:" + System.getProperty("db_master_db"));
			System.setProperty("db_master_host", host.getHost());
			System.setProperty("db_master_port", host.getPort() + "");
			System.setProperty("db_master_username", config[0][1]);
			System.setProperty("db_master_password", config[0][2]);
			isOk = true;
		}catch (URISyntaxException e){
		}catch (SQLException e) {
			log.warn("Failed to connect master db, Error:" + e.toString());
		}

		try{
			log.info("Check secondary db connection, url:" + config[1][0]);
			DriverManager.getConnection(config[1][0], 
					config[1][1],
					config[1][2]).close();
			isOk = true;
		}catch (SQLException e) {
			log.warn("Failed to connect secondary db, Error:" + e.toString());
		}
		
		return isOk;
	}
	
	public Collection<Object> list(Class cls, String filterOrSelect, Object[] param){
		String sql = null;
		if(filterOrSelect.trim().startsWith("select")){
			sql = filterOrSelect;
		}else {
			sql = this.buildSelectSql(cls);
			sql += " where " + filterOrSelect;
		}
		
		Collection<Map<String, Object>> rowList = query(sql, param);		
		Collection<Object> result = new ArrayList<Object>();
		
		for(Map<String, Object> row: rowList){
			try {
				Object obj = cls.newInstance();
				for(String f: row.keySet()){
					try {
						//log.info("xxxx:" + f + ",,,row:" + row.get(f));
						cls.getField(f).set(obj, row.get(f));
					}catch (Exception ex){
						log.warn(String.format("Failed to set filed '%s' to class '%s'", f, cls.getName()));
					}
				}
				result.add(obj);
			} catch (Exception e) {
				log.warn(e.toString());
			}
		}
		return result;
	}
	
	public Collection<Map<String, Object>> query(String sql, Object[] param){
		sql = bindParam(sql, param);
		Connection conn = this.getConnection();
		Collection<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		if(conn == null)return result;
		
		Statement st = null;
		ResultSet rs = null;
	    try{
	    	log.debug("Query:" + sql);
	    	st = conn.createStatement();
	    	rs = st.executeQuery(sql);
	    	
	    	String[] names = null;
	    	int[] types = null;
	    	
	    	int count = rs.getMetaData().getColumnCount();
	    	ResultSetMetaData meta = rs.getMetaData();
	    	
	    	names = new String[count];
	    	types = new int[count];
	    	
	    	for(int i = 0; i < count; i++){
	    		names[i] = meta.getColumnName(i + 1);
	    		types[i] = meta.getColumnType(i + 1);
	    	}
	    	
	    	
	    	Map<String, Object> row = null;
	    	while(rs.next()){
	    		row = new HashMap<String, Object>();
	    		result.add(row);
	    		for(int i = 1; i <= count; i++){
	    			Object value = null;
	    			switch(types[i - 1]){
	    				case Types.TIMESTAMP:
	    					value = rs.getTimestamp(i);
	    					break;	    					
	    				case Types.TIME:
	    					value = rs.getTime(i);
	    					break;	    					
	    				case Types.DATE:
	    					value = rs.getDate(i);
	    					break;
	    				case Types.VARCHAR:
	    				case Types.CHAR:
	    				case Types.LONGNVARCHAR:
	    					value = rs.getString(i);
	    					break;
	    				case Types.BIGINT:
	    				case Types.INTEGER:
	    				case Types.TINYINT:
	    					value = rs.getInt(i);
	    					break;
	    				case Types.FLOAT:
	    					value = rs.getFloat(i);
	    			}
	    			row.put(names[i - 1], value);
	    		}
	    	}
	    	log.debug("Return records:" + result.size());
	    	
	    	if(!conn.getAutoCommit()){
	    		conn.commit();
	    	}
	    }
	    catch (SQLException ex){
	    	log.error(ex.toString(), ex);
	    	conn = null;
	    }finally{
	    	if(rs != null){
	    		try {
					rs.close();
				} catch (SQLException e) {
				}
	    	}
	    	if(st != null){
	    		try {
					st.close();
				} catch (SQLException e) {
				}
	    	}
	    	closeConnection(conn);
	    }
		
		return result;
	}	
	

	@Override
	public int execute_sql(String sql, Object[] param){
		sql = bindParam(sql, param);
		
		int updated = 0;
		log.debug("Execute sql:" + sql);
		Connection conn = this.getConnection();
		if(conn == null)return 0;
		Statement st = null;
	    try{
	    	st = conn.createStatement();
	    	updated = st.executeUpdate(sql);
	    	if(!conn.getAutoCommit()){
	    		conn.commit();
	    	}
	    }
	    catch (SQLException ex){
	    	log.error("sql:" + sql);
	    	try {
	    		if(!conn.getAutoCommit()){
	    			conn.rollback();
	    		}
			} catch (SQLException e) {
				log.error(e.toString());
			}
	    	log.error(ex.toString(), ex);
	    	conn = null;
	    }finally{
	    	if(st != null){
	    		try {
					st.close();
				} catch (SQLException e) {
					log.error(e.toString());
				}
	    	}
	    	closeConnection(conn);
	    }
	    
		return updated;
	}
	
	private String bindParam(String sql, Object[] param){
		for(int i = 0; i < param.length; i++){
			sql = sql.replaceAll(String.format("\\$\\{%s\\}", i), 
								 this.toSQLValue(param[i]));
		}
		return sql;
	}
	
	private String buildSaveSql(Object obj){
		
		String[] fields = this.getORMFields(obj.getClass());
		String sql = "insert " + this.getTableName(obj.getClass()) +
		"(";
		for(int i = 0; i < fields.length -1; i++){
			sql += fields[i] + ",";
		}
		sql += fields[fields.length - 1] + ")";
		
		sql += "values(";
		
		for(int i = 0; i < fields.length -1; i++){
			sql += this.getSQLValue(obj, fields[i]) + ",";
		}		
		
		sql += this.getSQLValue(obj, fields[fields.length - 1]) + ")";
		
		return sql;
	}
	
	private String buildUpdateSql(Object obj, String[] fields){
		if(fields == null){
			fields = this.getORMFields(obj.getClass());
		}
		String sql = "update " + this.getTableName(obj.getClass()) +		
		" set ";
		
		for(int i = 0; i < fields.length -1; i++){
			sql +=  fields[i] + "=" + this.getSQLValue(obj, fields[i]) + ",";
		}
		
		sql += fields[fields.length - 1] + "=" + this.getSQLValue(obj, fields[fields.length - 1]);
		
		sql += " where ";

		fields = this.getORMPkFields(obj.getClass());
		for(int i = 0; i < fields.length -1; i++){
			sql +=  fields[i] + "=" + this.getSQLValue(obj, fields[i]) + " and ";
		}
		
		sql += fields[fields.length - 1] + "=" + this.getSQLValue(obj, fields[fields.length - 1]);
		
		return sql;		
	}
	
	private String buildCheckExists(Object obj){
		String[] fields = this.getORMPkFields(obj.getClass());
		String sql = "select count(*) as have_row from " + this.getTableName(obj.getClass());
		sql += " where ";

		for(int i = 0; i < fields.length -1; i++){
			sql +=  fields[i] + "=" + this.getSQLValue(obj, fields[i]) + " and ";
		}
		
		sql += fields[fields.length - 1] + "=" + this.getSQLValue(obj, fields[fields.length - 1]);
		
		return sql;		
	}
	
	private String buildSelectSql(Class cls){
		String[] fields = this.getORMFields(cls);
		String sql = "select ";
		
		for(int i = 0; i < fields.length -1; i++){
			sql += fields[i] + ",";
		}
		
		sql += fields[fields.length - 1] + " from " +
			this.getTableName(cls);
		
		return sql;
	}
	
	private String getTableName(Class cls){
		try {
			return (String)cls.getField("ORM_TABLE").get(null);
		} catch (Exception e) {
			log.info(String.format("Not found ORM_TABLE field in class %s.", cls.getName()));
		}
		return null;
	}
	
	private String getSQLValue(Object obj, String field){
		Object val = null;
		try {
			val = obj.getClass().getField(field).get(obj);
		} catch (Exception e) {
			log.info(String.format("Not found %s field in class %s.", field, obj.getClass().getName()));
		}
		return toSQLValue(val);
	}	
	
	private String toSQLValue(Object val){
		if(val == null){
			return "null";
		}else if(val instanceof String){
			return String.format("'%s'", val);
		}else if(val instanceof Date){
			String x = String.format("STR_TO_DATE('%s',", format.format((Date)val));
			x += "'%Y-%m-%d %H:%i:%s')";
			return x;
		}else if(val instanceof Collection){
			StringBuffer b = new StringBuffer();
			for(Iterator<Object> iter = ((Collection)val).iterator(); iter.hasNext();){
				if(b.length() > 0){b.append(",");}
				b.append(toSQLValue(iter.next()));
			}
			return "(" + b.toString() + ")";
		}else {
			return String.format("%s", val);
		}
	}
	
	private String toOrderBy(String order){
		String r = "";		
		for(String o: order.split(",")){
			if(r.length() > 0){
				r += ",";
			}
			if(o.startsWith("-")){
				r += o.substring(1) + " desc";
			}else if(o.startsWith("+")){
				r += o.substring(1) + " asc";
			}else{
				r += o;
			}
		}
		return r;
	}
	
	private String[] getORMFields(Class cls){
		try {
			return (String[])cls.getField("ORM_FIELDS").get(null);
		} catch (Exception e) {
			log.info(String.format("Not found ORM_FIELDS field in class %s.", cls.getName()));
		}
		return null;
	}	
	
	
	private String[] getORMPkFields(Class cls){
		try {
			return (String[])cls.getField("ORM_PK_FIELDS").get(null);
		} catch (Exception e) {
			log.info(String.format("Not found ORM_FIELDS field in class %s.", cls.getName()));
		}
		return null;
	}
	
	private String getPKQuery(Class cls){
		String sql = "";
		String[] fields = this.getORMPkFields(cls);
		for(int i = 0; i < fields.length -1; i++){
			sql +=  fields[i] + "=${" + i + "} and ";
		}
		
		sql += fields[fields.length - 1] + "=${" + (fields.length - 1) + "}";
		
		return sql;		
	}	
	
	private String getORMSql(Class cls, String name){
		String sql = null;
		try {
			sql = (String)cls.getField(name).get(null);
		} catch (Exception e) {
			log.info(String.format("Not found %s field in class %s.", name, cls.getName()));
		}
		return sql;
	}
	
	private Connection getConnection(){
		Connection conn = null;
		synchronized(connPool){
			if(connPool.size() > 0){
				conn = connPool.remove(0);
				return conn;
			}
		}
		
		for(int i = 0; i < 2; i++){
			String[] settings = this.config[this.wokingDB];
			try{
				conn = DriverManager.getConnection(settings[0], 
						settings[1],
						settings[2]);
				conn.setAutoCommit(false);
			}catch (SQLException e) {
				this.wokingDB = (this.wokingDB + 1) % this.config.length;
				if(this.wokingDB == 0){
					log.warn("Failed to connect secondary db, try to connect master.");
				}else {
					log.warn("Failed to connect master db, try to connect secondary.");
				}
			}
		}
		if(conn == null){
			log.error("Failed to connect all DB.");
		}
		
		return conn;
	}
	
	private void closeConnection(Connection conn){
		if(conn != null){
			synchronized(connPool){
				connPool.add(conn);
			}		
		}
	}
	
	@Override
	public Object load(Class cls, String pk) {
		Collection<Object> result = this.list(cls, this.getPKQuery(cls), new Object[]{pk});
		if(result.size() > 0){
			return result.iterator().next();
		}
		return null;
	}

	@Override
	public boolean save(Object obj) {
		// TODO Auto-generated method stub		
		String sql = this.buildCheckExists(obj);
		
		Collection<Map<String, Object>> xx = query(sql, new Object[]{});
		int rowCount = (Integer)xx.iterator().next().get("have_row");		
		if(rowCount > 0){
			sql = this.buildUpdateSql(obj, null);
			rowCount = this.execute_sql(sql, new Object[]{});				
		}else {
			sql = this.buildSaveSql(obj);
			rowCount = this.execute_sql(sql, new Object[]{});
		}
		return rowCount > 0;
	}
	
	public boolean save(Object obj, String[] fields) {
		String sql = this.buildUpdateSql(obj, fields);
		int rowCount = this.execute_sql(sql, new Object[]{});
		return rowCount > 0;
	}
	
	public boolean isAdmin(User user){
		String sql_is_admin = "select g.name from user_group g " +
		  "join relation_user_group rg on(g.name=rg.user_group_id) " +
		  "where rg.user_id=${0} and g.isAdmin=1";

		Collection<Map<String, Object>> xx = query(sql_is_admin, new Object[]{user.name});
		return xx.size() > 0;
	}
	
	@SuppressWarnings("rawtypes")
	public Collection<BaseStation> listStation(User user){
		String sql_station = null;
		
		if(this.isAdmin(user)){
			sql_station = "select b.* from base_station b";
		}else {
			sql_station = "select b.* from base_station b " +
						  "join relation_station_group rsg on(b.uuid=rsg.base_station_id) " +
						  "join relation_user_group rg on(rsg.user_group_id=rg.user_group_id) " +
						  "where rg.user_id=${0} order by b.uuid asc";
		}
		
		Collection stationList = this.list(BaseStation.class, 
														sql_station,
														new String[]{user.name});
		
		return stationList;
	}

	@SuppressWarnings("unchecked")
	public Collection<BaseStation> listStation(RouteServer route) {
		Collection xx = this.list(BaseStation.class, 
				"routeServer=${0}",
				new String[]{route.ipAddress});
		return xx;
	}

	@Override
	public Collection<BaseStation> listDeadStation(String group) {		
		Date lastActive = new Date(System.currentTimeMillis() - 1000 * 60 * 5);
		
		Collection xx = this.list(BaseStation.class, 
				"(lastActive<${0} or routeServer is null) and groupName=${1}",
				new Object[]{lastActive, group});
		return xx;
	}

	@Override
	public void removeRouteServer(RouteServer route) {
		String cleanRoute = "update base_station set routeServer=null where routeServer=${0}";
		this.execute_sql(cleanRoute, new Object[]{route.ipAddress});
	}	
	
	public QueryResult queryData(Class obj, QueryParameter param){	
		
		String countSQL = null;
		String filter = null;
		
		if(param.qsid != null && !"".equals(param.qsid)){
			filter = (String)sessionCache.get(param.qsid); 
		}else {
			param.qsid = "" + (nextSession++ % 10000000);
			countSQL = "select count(*) as have_row from " + this.getTableName(obj);
			filter = "1=1";
			
			String field, op;
			String extra_where = "";
			String extra_join = "";
			for(String k: param.param.keySet()){
				if(k.startsWith("extra_where_")){
					extra_where += param.param.get(k);
					continue;
				}
				if(k.startsWith("extra_join_")){
					extra_join += param.param.get(k);
					continue;
				}
				
				if(k.indexOf("__") > 0){
					field = k.replaceAll("__", " ");
				}else {
					field = k + " ="; 
				}
				if(filter.length() > 0){
					filter += " and ";
				}
				filter += field + toSQLValue(param.param.get(k));
			}
			if (extra_where.trim().length() > 0){
				filter = "(" + filter + ")" + extra_where;
			}
			countSQL += " " + extra_join + " where " + filter;
			
			if(param.order != null){
				filter += " order by " + toOrderBy(param.order);
			}
			
			filter += String.format(" LIMIT ${0}, ${1}");
			
			//如果有额外的连接查询，拼装一个完整的SQL到查询。
			if(extra_join.length() > 0){
				filter = buildSelectSql(obj) + " " + extra_join + " where " + filter;
			}
		}
		
		QueryResult result = new QueryResult();
		result.sessionId = param.qsid;
		if(countSQL != null){
			Collection<Map<String, Object>> xx = query(countSQL, new Object[]{});
			result.count = (Integer)xx.iterator().next().get("have_row");
		}else {
			result.count = -1;
		}
		if(filter != null){
			result.data = this.list(obj, filter, new Object[]{param.offset, param.limit});
		}else {
			result.data = new ArrayList();
		}
		
		return result;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public Collection<VideoTask> listTask(User user) {
		Collection result = this.list(VideoTask.class, "userName=${0} order by windowID, showOrder",
				new String[]{user.name});
		return result;
	}
}
