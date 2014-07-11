package org.goku.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Settings {
	
	public final static String LISTEN_PORT = "listen_port";
	public final static String HTTP_PORT = "http_port";
	public final static String GROUP_NAME = "group_name";
	
	public final static String CORE_ROUTE_THREAD_COUNT = "core_route_thread_count";
	public final static String MAX_ROUTE_THREAD_COUNT = "max_route_thread_count";
	
	public final static String PROXY_PORT_START = "proxy_port_start";
	public final static String PROXY_PORT_END = "proxy_port_end";
	public final static String PROXY_TIMEOUT = "proxy_timeout";
	
	public final static String UDP_LOCAL_PORT = "udp_local_port";
	public final static String UDP_REMOTE_PORT = "udp_remote_port";
	
	public final static String MASTER_SERVER_URL = "master_server_url";
	
	/**
	 * 主数据库配置。
	 */
	public final static String DB_MASTER_DB = "db_master_db";
	public final static String DB_MASTER_USERNAME = "db_master_username";
	public final static String DB_MASTER_PASSWORD = "db_master_password";

	/**
	 * 副数据库配置。
	 */
	public final static String DB_SECONDARY_DB = "db_secondary_db";
	public final static String DB_SECONDARY_USERNAME = "db_secondary_username";
	public final static String DB_SECONDARY_PASSWORD = "db_secondary_password";
	
	/**
	 * 文件保存路径
	 */
	public final static String FILE_ROOT_PATH = "file_root_path";
	public final static String FILE_NAME_PATTERN = "file_name_pattern";
	
	//public final static String MAX_ROUTE_THREAD_COUNT = "max_route_thread_count";
	//public final static String MAX_ROUTE_THREAD_COUNT = "max_route_thread_count";
	
	private static Log log = LogFactory.getLog("settings");
	protected Properties settings = System.getProperties();	
	private String confName = "master.conf";
	
	//private String[] masterSettings = new String[]{};
	//private String[] routeSettings = new String[]{};
	
	public Settings(String name){
		this.confName = name;
		this.loadSettings();
	}
	
	public void loadSettings(){
		try {
			InputStream is = this.getClass().getClassLoader().getResourceAsStream("org/goku/settings/" + this.confName);
			if(is != null){
				settings.load(is);
			}else {
				log.info("Not found default configuration!");
			}
		} catch (IOException e) {
			log.error(e, e.getCause());
		}
		
		File f = new File(this.confName);
		if(f.isFile()){
			try {
				settings.load(new FileInputStream(f));
			} catch (FileNotFoundException e) {
				log.error(e, e.getCause());
			} catch (IOException e) {
				log.error(e, e.getCause());
			}
		}
	}
	
	public String getString(String name, String def){
		return settings.getProperty(name, def);
	}
	
	public int getInt(String name, int def){
		String val = settings.getProperty(name);
		int intVal = def;
		try{
			if(val != null) intVal = Integer.parseInt(val);
		}catch(Exception e){
		}
		
		return intVal;
	}	
		
}
