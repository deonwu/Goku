package org.goku.video;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.goku.core.model.BaseStation;
import org.goku.socket.SocketManager;
import org.goku.video.odip.MonitorClient;
import org.goku.video.odip.RecordFileInfo;
import org.goku.video.odip.VideoRoute;

public class TestFileRecorder {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		BaseStation station = new BaseStation();
		
		station.uuid = "1234";
		station.locationId = "192.168.1.156:9001";
		station.channels = "1:ch1,2:ch2";
		
		ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
				3,
				20,
				60, 
				TimeUnit.SECONDS, 
				new LinkedBlockingDeque<Runnable>(6)
				);
		
		SocketManager socketManager = new SocketManager(threadPool);
		threadPool.execute(socketManager);
		
		Thread.sleep(1000 * 1);
		
		MonitorClient client = new MonitorClient(station, 
				 new VideoRoute(threadPool),
				 socketManager);
		
		System.out.println("login....");
		
		client.login(true);
		
		Collection<RecordFileInfo> list = client.queryRecordFile(1, 0, new Date(System.currentTimeMillis() - 1000 * 3600 * 24));
		
		System.out.println("==================================");
		File out = new File("xxx.h264");
		OutputStream os = new FileOutputStream(out);
		for(RecordFileInfo f : list){
			System.out.println("size:" + f.fileSize);
			if(f.fileSize > 100){
				client.downloadByRecordFile(f, os, true);
			}
		}

		Thread.sleep(1000 * 10);
		
		client.close();
	}

}
