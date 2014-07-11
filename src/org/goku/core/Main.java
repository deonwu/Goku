package org.goku.core;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.goku.image.ImageRouteServer;
import org.goku.master.MasterVideoServer;
import org.goku.settings.Settings;
import org.goku.video.VideoRouteServer;

public class Main {
	
	static{
		if(System.getProperty("org.apache.commons.logging.simplelog.defaultlog") == null){
			System.setProperty("org.apache.commons.logging.simplelog.defaultlog", "trace");	
		}
		if(System.getProperty("org.apache.commons.logging.simplelog.showdatetime") == null){
			System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");	
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception{
		//LogFactory.getLog("main");
		if (args.length == 1){
			if(args[0].equals("--version")){
				System.out.println(Version.getName() + " " + Version.getVersion() + " at " + Version.getBuildDate());
				System.exit(0);
			}else if(args[0].equals("--help")){
				help();
			}else if(args[0].equals("--master")){
				new Main().startAsMaster();
			}else if(args[0].equals("--video")){
				new Main().startAsVideoRoute();
			}else if(args[0].equals("--image")){
				new Main().startAsImageRoute();
			}else if(args[0].equals("--single")){
				new Main().startAsSingle();
			}else {
				help();
			}
		}else{
			help();
		}
	}
	
	private void startAsMaster() throws Exception{
		initLog4jFile("master.log");
		Settings settings = new Settings("master.conf");
		startCleanLog(settings, "master.log");
		new MasterVideoServer(settings).startUp();
	}
	
	private void startAsVideoRoute() throws Exception {
		initLog4jFile("video.log");
		Settings settings = new Settings("video.conf");
		startCleanLog(settings, "video.log");
		new VideoRouteServer(settings).startUp();
	}
	
	private void startAsImageRoute() throws Exception {
		initLog4jFile("image.log");
		Settings settings = new Settings("image.conf");
		startCleanLog(settings, "image.log");
		new ImageRouteServer(settings).startUp();
	}
	
	private void startAsSingle() throws Exception {
		initLog4jFile("standlone.log");
		new Thread(){
			public void run(){
				try{
					Settings settings = new Settings("master.conf");
					new MasterVideoServer(settings).startUp();
				}catch(Exception e){
					e.printStackTrace();
					System.exit(1);
				}
			}
		}.start();	
		Thread.sleep(1000 * 4);
		new Thread(){
			public void run(){
				try{
					Settings settings = new Settings("video.conf");
					new VideoRouteServer(settings).startUp();
				}catch(Exception e){
					e.printStackTrace();
					System.exit(1);
				}
			}
		}.start();
	}	
	
	private void initLog4jFile(String name){
		//LogFactory.getLog("main");
		org.apache.log4j.Logger root = org.apache.log4j.Logger.getRootLogger();
		try {
			root.addAppender(new org.apache.log4j.DailyRollingFileAppender(root.getAppender("S").getLayout(),
					"logs/" + name, 
					".yy-MM-dd"));
		} catch (IOException e) {
			System.out.println("Failed to add file appender.");
			// TODO Auto-generated catch block
		}
		
		root.info(Version.getName() + " " + Version.getVersion());
		root.info("build at " + Version.getBuildDate());
		root.info("java.home:" + System.getProperty("java.home"));
		root.info("java.runtime.version:" + System.getProperty("java.runtime.version"));
		root.info("java.runtime.name:" + System.getProperty("java.runtime.name"));
		
		//root.removeAppender("R");
		//org.apache.log4j.DailyRollingFileAppender appender = (org.apache.log4j.DailyRollingFileAppender)root.getAppender("R");
		//appender.setFile("logs/" + name);
		//root.info("===========================================");
		//root.info("===========================================");
	}
	
	private void startCleanLog(final Settings s, final String name){
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask(){
			@Override
			public void run() {
				org.apache.log4j.Logger root = org.apache.log4j.Logger.getRootLogger();
				try{
					updateLog4jLevel(s, name);
				}catch(Throwable e){
					root.info(e.toString());
				}
			}
		}, 100, 1000 * 3600 * 12);		
	}
	
	private void updateLog4jLevel(Settings s, String name){
		org.apache.log4j.Logger root = org.apache.log4j.Logger.getRootLogger();
		String level = s.getString("log_level", "debug").toLowerCase().trim();
		if(level.equals("trace")){
			root.setLevel(org.apache.log4j.Level.TRACE);
		}else if(level.equals("debug")){
			root.setLevel(org.apache.log4j.Level.DEBUG);
		}else if(level.equals("info")){
			root.setLevel(org.apache.log4j.Level.INFO);
		}else if(level.equals("warn")){
			root.setLevel(org.apache.log4j.Level.WARN);
		}
		File r = new File("logs");
		
		int max_log_days = s.getInt("max_log_days", 10);		
		Date d = new Date(System.currentTimeMillis() - 1000 * 3600 * 24 * max_log_days);		
		DateFormat format= new SimpleDateFormat("yy-MM-dd"); 		
		root.debug("Remove log before " + format.format(d));
		for(File log : r.listFiles()){
			if(!log.getName().startsWith(name))continue;
			String[] p = log.getName().split("\\.");
			String logDate = p[p.length -1];
			if(logDate.indexOf("-") > 0){
				try {
					if(format.parse(logDate).getTime() < d.getTime()){
						root.info("remove old log file:" + log.getName());
						log.delete();
					}
				} catch (Exception e) {
					root.info(e.toString());
				}
			}
		}
	}

	private static void help(){
		System.out.println("java -jar Goku.jar <Option>\n" +
				"    --master              Run as master server.\n" +
				"    --video               Run as video routing server.\n" +
				"    --image               Run as image routing server.\n" +
				"    --single              Run in singlton mode.\n" +
				"    --version             Show version.\n");
		System.exit(0);
	}
}
