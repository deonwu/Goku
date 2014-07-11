package org.goku.image;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.BaseStation;

/**
 * 图片监控客户端。
 * @author deon
 */
public class ASC100Client {
	public BaseStation info = null;
	public Collection<ImageClientListener> ls = Collections.synchronizedCollection(new ArrayList<ImageClientListener>());

	private Log log = null;
	private ASC100MX mx = null;
	private String location = "";
	private ByteBuffer outBuffer = ByteBuffer.allocate(64 * 1024);
	private ByteBuffer inBuffer = ByteBuffer.allocate(64 * 1024);
	private ImageInfo image = null;
	
	private byte lastCmd = 0;
	
	public ASC100Client(String location){
		this.location = location;
		log = LogFactory.getLog("asc100." + location);
	};
	
	public ASC100Client(BaseStation info){
		this(info.locationId);
		this.info = info;
	};
	
	public void process(ByteBuffer buffer){
		//当前数据包处理状态。
		byte status = (byte)0xff; 
		short cur = 0;
		byte cmd = 0;
		short len = 0;
		
		inBuffer.clear();
		short checksum = 0;
		while(buffer.hasRemaining()){
			cur = buffer.get();
			if(status != 0 && cur != status) continue;
			if(status == 0xff){	//开始标志
				cmd = buffer.get();
				len = buffer.getShort();
				status = 0;
			}else if(status == 0){ //数据处理
				if(cur == 0xFD){
					cur += buffer.get(); //转义。
				}
				len--;
				inBuffer.put((byte)cur);
				if(len == 0){
					checksum = buffer.getShort();
					status = (byte)0xfe;
					inBuffer.flip();
					if(checksum == this.getCheckSum(inBuffer.asReadOnlyBuffer())){
						processData(cmd, inBuffer);
					}else {
						log.warn("check sum error, drop data");
					}
				}
			}else if(status == 0xfe){
				status = (byte)0xff;	//当前读到结束标志了，等待下一个开始标志。
			}			
		}
		
	}
	
	public short getCheckSum(ByteBuffer data){
		short sum = 0;
		while(data.hasRemaining()){
			sum += data.get();
		}
		return sum;
	}	
	
	/**
	 * 发送一个终端命令，封装了“图像监控系统通信协议v1.34".
	 * @param cmd
	 * @param data
	 * @throws IOException 
	 */
	public void sendCommand(byte cmd, byte data[]) throws IOException{
		ByteBuffer temp = null;
		synchronized(outBuffer){
			outBuffer.clear();
			outBuffer.put((byte)0xff);
			outBuffer.put(cmd);
			outBuffer.putShort((short)data.length);
			for(int b: data){	//处理数据转义。
				if(b < 0xfd){
					outBuffer.put((byte)b);
				}else {
					outBuffer.put((byte)0xfd);
					outBuffer.put((byte)(b - 0xfd));
				}
			}
			temp = outBuffer.asReadOnlyBuffer();
			temp.limit(outBuffer.position());
			temp.position(4);
			
			outBuffer.putShort(getCheckSum(temp));
			outBuffer.put((byte)0xfe);
			if(mx != null){
				mx.send(this, outBuffer);
			}else {
				log.warn("Can't send data, the client have not register to MX");
			}
		}
	}
	
	public void processData(byte cmd, ByteBuffer inBuffer){
		if(this.lastCmd == 0x02){
			if(cmd == 0x00){
				eventProxy.notFoundImage(new ImageClientEvent(this));
			}else if(cmd == 0x06){
				try {
					processImageData(inBuffer);
				} catch (IOException e) {
					log.error(e.toString(), e);
				}
			}
		}
	}
	
	private void processImageData(ByteBuffer inBuffer) throws IOException{
		int count = inBuffer.getShort();
		int curFrame = inBuffer.getShort();
		int len = inBuffer.getShort();
		int tem = inBuffer.get();
		len += (tem << 24);
				
		if(curFrame == 0){
			inBuffer.getShort();
			image = new ImageInfo();
			image.setFameCount(count);
			image.setDataSize(len);
			image.imageStatus = inBuffer.get();
			image.channel = inBuffer.get();
			
			int xxLen = inBuffer.getShort();
			tem = inBuffer.get();
			len += (tem << 24);
			if(len != xxLen){
				log.error(String.format("The picture length error, %s(head) != %s(picdata)", len, xxLen));
			}
			image.imageSize = inBuffer.get();
			image.zipRate = inBuffer.get();
			this.sendCommand((byte)0x21, new byte[]{});
		}else if (image != null){
			int paddingLen = inBuffer.getShort();
			if(paddingLen == inBuffer.remaining()){
				image.recieveData(curFrame, inBuffer);
			}else {
				log.error(String.format("The picture data error, %s(remaining) != %s(buffer)", paddingLen, inBuffer.remaining()));				
			}
			if(!image.haveMoreData()){
				int[] retry = image.getReTryFrames();
				if(retry == null){
					sendRetryFrame(new int[]{});
					image.buffer.position(0);
					ImageClientEvent event = new ImageClientEvent(this);
					event.image = this.image;
					this.eventProxy.recevieImageOK(event);
					this.image = null;
				}else {
					sendRetryFrame(retry);
				}
			}
		}else {
			log.error(String.format("Get picture data, but not found picture head."));
		}
	}
	
	private void sendRetryFrame(int[] frames) throws IOException{
		outBuffer.clear();
		outBuffer.putShort((short)frames.length);
		for(int i: frames){
			outBuffer.putShort((short)i);
		}
		byte[] data = new byte[outBuffer.position()];
		outBuffer.flip();
		outBuffer.get(data);
		this.sendCommand((byte)0x20, data);
	}
	
	public void setASC100MX(ASC100MX mx){
		this.mx = mx;
	}
	
	public String getClientId(){
		return this.location;
	}
	
	public void readImage(){
		try {
			sendCommand((byte)0x02, new byte[]{06});
		} catch (IOException e) {
			log.error(e.toString(), e);
		}
	}
	
	public void addListener(ImageClientListener l){
		if(!this.ls.contains(l)){
			this.ls.add(l);
		}
	}
	
	public void removeListener(ImageClientListener l){
		if(this.ls.contains(l)){
			this.ls.remove(l);
		}
	}
	
	public ImageClientListener eventProxy = new ImageClientListener(){

		@Override
		public void recevieImageOK(ImageClientEvent event) {
			for(ImageClientListener l: ls){
				l.recevieImageOK(event);
			}
		}

		@Override
		public void notFoundImage(ImageClientEvent event) {
			for(ImageClientListener l: ls){
				l.notFoundImage(event);
			}
		}
	};
}
