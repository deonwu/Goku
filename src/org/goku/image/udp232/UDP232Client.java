package org.goku.image.udp232;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import javax.comm.SerialPort;

import org.goku.image.ASC100Client;

public class UDP232Client extends ASC100Client implements Runnable {
	private SerialPort port = null; 
	private OutputStream os = null;
	private FileWriter rawLog = null;
	
	public UDP232Client(String location, SerialPort port) throws IOException {
		super(location);
		this.port = port;
		os = port.getOutputStream();
		rawLog = new FileWriter(new File(this.getClientId().replace('.', '_') + "_raw.log"));
	}

	@Override
	public void run() {
		ByteBuffer readBuffer = ByteBuffer.allocate(64 * 1024);
		try {
			long length = 0;
			InputStream in = port.getInputStream();
			long lastWrite = System.currentTimeMillis();
			int data = 0;
			log.info("Start try to read port....");
			while(true){
				if(in.available() > 0){
					data = in.read();
					if(data != -1){
						readBuffer.put((byte)data);
						rawLog.append(String.format("%02X ", (byte)data));
						rawLog.flush();
						System.out.print(String.format("%02X ", (byte)data));
						length++;
						if(length % 16 == 0){
							rawLog.append("\n");
							System.out.println();
						}
					}
					if(System.currentTimeMillis() - lastWrite > 1000 && readBuffer.position() > 0){
						readBuffer.flip();
						mx.send(this, readBuffer);
						readBuffer.clear();
						lastWrite = System.currentTimeMillis();
					}
				}else {
					if(System.currentTimeMillis() - lastWrite > 1000 && readBuffer.position() > 0){
						readBuffer.flip();
						mx.send(this, readBuffer);
						readBuffer.clear();
						lastWrite = System.currentTimeMillis();
					}
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						log.warn("Sleep error:" + e.toString());
					}
				}
			}			
		}catch (IOException e) {
			log.error("Read Error:" + e.toString(), e);
		}finally{
			try{
				rawLog.flush();
				rawLog.close();
			}catch (IOException e) {
				log.error("Read Error:" + e.toString(), e);
			}
		}
	}
	
	/**
	 * 处理从UDP发过来的数据。
	 */
	public void process(ByteBuffer buffer){
		byte[] data = new byte[buffer.remaining()];		
		buffer.get(data);
		synchronized(os){
			try {
				String logBuffer = "\n<--";
				for(byte b: data){
					logBuffer += String.format("%02X ", b);
				}
				logBuffer += "-->\n";
				rawLog.append(logBuffer);
				rawLog.flush();
				log.info("write:" + logBuffer);
				
				os.write(data);
				os.flush();
			} catch (IOException e) {
				log.error(e.toString(), e);
			}
		}
	}
}
