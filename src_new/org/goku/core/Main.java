package org.goku.core;

import java.io.IOException;

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
				System.out.println(Version.getName() + " " + Version.getVersion());
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
		new MasterVideoServer(settings).startUp();
	}
	
	private void startAsVideoRoute() throws Exception {
		initLog4jFile("video.log");
		Settings settings = new Settings("video.conf");
		new VideoRouteServer(settings).startUp();
	}
	
	private void startAsImageRoute() throws Exception {
		initLog4jFile("image.log");
		Settings settings = new Settings("image.conf");
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
					".yyy-MM-dd"));
		} catch (IOException e) {
			System.out.println("Failed to add file appender.");
			// TODO Auto-generated catch block
		}
		
		//root.removeAppender("R");
		//org.apache.log4j.DailyRollingFileAppender appender = (org.apache.log4j.DailyRollingFileAppender)root.getAppender("R");
		//appender.setFile("logs/" + name);
		//root.info("===========================================");
		//root.info("===========================================");
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
