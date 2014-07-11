package org.goku.image;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RawDataLogger implements ImageClientListener {
	private FileWriter rawLog = null;
	private int byteCount = 0;
	private Log log = null;

	public RawDataLogger(String id){
		 log = LogFactory.getLog(id);
		try{
			rawLog = new FileWriter(new File("logs/" + id + "_raw.log"));
		}catch(Exception e){
			log.error(e.toString(), e);
		}
	}

	@Override
	public void recevieImageOK(ImageClientEvent event) {
		// TODO Auto-generated method stub

	}

	@Override
	public void notFoundImage(ImageClientEvent event) {
		// TODO Auto-generated method stub

	}

	@Override
	public void connectionError(ImageClientEvent event) {
		// TODO Auto-generated method stub

	}

	@Override
	public void message(ImageClientEvent event) {
		// TODO Auto-generated method stub

	}

	@Override
	public void active(ImageClientEvent event) {
		// TODO Auto-generated method stub

	}

	@Override
	public void debugRawData(ImageClientEvent event) {
		if(this.rawLog == null || event.raw == null)return;
		
		try{
			synchronized(rawLog){
				if(event.isSend) {
					rawLog.append("\n<---");
					this.byteCount = 0;
				}
				ByteBuffer buf = event.raw.asReadOnlyBuffer();
				while(buf.hasRemaining()){
 					rawLog.append(String.format("%02X ", (byte)buf.get()));
 					byteCount++;
					if(byteCount % 16 == 0){
						rawLog.append("\n");
						byteCount = 0;
					}
				}
				if(event.isSend) {
					rawLog.append("--->\n");
					this.byteCount = 0;
				}
				rawLog.flush();
			}			
		}catch(IOException e){
			log.error(e.toString(), e);
		}
	}

}
