package org.goku.image;

import java.nio.ByteBuffer;

public class ImageInfo {
	private int frameSize = 0;
	
	//0等待发送， 1, 成功接收 3, 丢包重传。
	public byte ack[] = null;
	
	//1表示704*576，2表示352*288，其它无效
	public int imageSize = 0;
	
	//0x1 告警上传， 0x00点播上报
	public int imageStatus = 0;
	public int zipRate = 0;
	public int channel = 0;
	public ByteBuffer buffer = null;
	
	//图片分片总数
	private int frameCount = 0;	
	private int dataSize = 0;
	
	public void setFameCount(int count){
		this.frameCount = count;
		ack = new byte[count];
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
		}
		
		return frame;
	};
	
	
	public int[] getReTryFrames(){
		return null;
	}
	
	public boolean haveMoreData(){
		return false;
	}
}
