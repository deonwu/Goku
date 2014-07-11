package org.goku.image.udp232;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

import org.goku.image.ASC100Client;

public class ReplyFileData extends ASC100Client implements Runnable {
	private String path = null;
	private BufferedReader reader = null;
	private int count = 0;
	
	public ReplyFileData(String id, String path) {
		super(id);
		this.path = path;
	}

	@Override
	public void run() {
		log.info("Start replay data from file:" + path);
		ByteBuffer readBuffer = ByteBuffer.allocate(64 * 1024);
		byte[] data = null;
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e1) {
		}
		try{
			while(true){
				data = nextFrame();
				if(data != null && data.length > 0){
					readBuffer.clear();
					readBuffer.put(data);
					readBuffer.flip();
					mx.send(this, readBuffer);
				}
				try {
					Thread.sleep(2);
				} catch (InterruptedException e) {
					log.warn("Sleep error:" + e.toString());
				}		
			}
		}catch (IOException e) {
			log.error("Read Error:" + e.toString(), e);
		}finally{
			try{
				if(reader != null){
					reader.close();
				}
			}catch (IOException e) {
				log.error("Read Error:" + e.toString(), e);
			}
		}
		
		
	}
	
	private byte[] nextFrame() throws IOException{
		byte[] data = null;
		if (reader == null){
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path))));
		}
		
		for(String line = reader.readLine(); line != null; 
			line = reader.readLine()){
			count++;
			try{
				if((!line.contains("---")) && line.trim().length() > 0){
					String[] tmp = line.split(" ");
					data = new byte[tmp.length];
					for(int i = 0; i < tmp.length; i++){
						data[i] = (byte)Integer.parseInt(tmp[i], 16);
					}
					log.info("read data file line:" + count);
					break;
				}
			}catch(Exception e){
				log.error("error line:" + line);
			}
		}
		if(data == null){
			log.info("Close the file, and reset to read from header...");
			try {
				Thread.sleep(5 * 1000);
			} catch (InterruptedException e) {
			}
			if(reader != null){
				reader.close();
				reader = null;
			}
		}
				
		return data;
	}
	

	/**
	 * 处理从UDP发过来的数据。
	 */
	public void process(ByteBuffer buffer){
		byte[] data = new byte[buffer.remaining()];		
		buffer.get(data);
		
		String logBuffer = "\n<--";
		for(byte b: data){
			logBuffer += String.format("%02X ", b);
		}
		logBuffer += "-->\n";
		log.info("write:" + logBuffer);		
	}


}
