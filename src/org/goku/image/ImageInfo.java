package org.goku.image;

import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ImageInfo {
	private int frameSize = 1024;
	
	//0等待发送， 1, 成功接收 3, 丢包重传。
	public byte ack[] = null;
	
	//1表示704*576，2表示352*288，其它无效
	public int imageSize = 0;
	
	//0x1 告警上传， 0x00点播上报
	public int imageStatus = 0;
	public int zipRate = 0;
	public int channel = 0;
	public ByteBuffer buffer = null;
	public Date generateDate = new Date(System.currentTimeMillis());
	public int waitingFrames = 0;
	public int reTry = 0;
	
	//图片开始传的时间，用来计算传输超时。
	public Date startDate = new Date(System.currentTimeMillis());
	
	//图片分片总数
	private int frameCount = 0;	
	private int dataSize = 0;
	
	public void setFameCount(int count){
		this.frameCount = count;
		ack = new byte[count -1 ];
	}
	
	public int getFrameCount(){
		return this.frameCount;
	}
	
	public void setDataSize(int dataSize){
		this.dataSize = dataSize;
		buffer = ByteBuffer.allocate(dataSize);
	}

	public int getDataSize(){
		return this.dataSize;
	}
	
	public int recieveData(int frame, ByteBuffer data){
		if(frame == 1){
			this.frameSize = data.remaining();			
		}
		
		if(buffer != null){
			buffer.position((frame - 1) * this.frameSize);
			buffer.put(data);
			ack[frame -1] = 1;
		}
		
		/**
		 * 从图片数据里面取到图片生成时间。
		 */
		if(frame == 1 && buffer.position() > 32){
			byte[] date = new byte[14];
			buffer.position(0x18);
			buffer.get(date);
			if(date[0] == '2' && date[1] == '0'){
				setGenerateDate(new String(date) + "000");
			}
		}
		
		return frame;
	};
	
	public void setGenerateDate(String date){
		DateFormat format= new SimpleDateFormat("yyyyMMddHHmmssSSS");
		try {
			generateDate = format.parse(date);
		} catch (ParseException e) {
		}
	}
	
	public int[] getReTryFrames(){
		int[] frames = new int[ack.length];
		int next = 0;
		for(int i = 0; i < ack.length; i++){
			if(ack[i] == 0){
				frames[next++] = i+1;
			}
		}
		
		int[] r = new int[next];
		System.arraycopy(frames, 0, r, 0, r.length);		
		return r;
	}
	
	public boolean haveMoreData(){
		for(int i = 1; i <= ack.length; i++){
			if(ack[i-1] == 0){
				return true;
			}
		}
		return false;
	}
}
