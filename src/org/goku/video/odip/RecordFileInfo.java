package org.goku.video.odip;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class RecordFileInfo {
	protected DateFormat format= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	public int channel;	//视频通道号。
	public int cardNo; //文档说是卡号，不知道什么意思。
	public int video_audio; //01:音频,10:视频, 00-图片, 11-透明串口录像
	
	public long startCluster;
	public int dirveNo;
	
	public long fileSize;
	public Date startTime;
	public Date endTime;

	public void decode(ByteBuffer buffer){
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		this.channel = buffer.getInt();
		startTime = decodeDate(buffer);
		endTime = decodeDate(buffer);
		fileSize = decodeUnsignedInt(buffer);
		startCluster = decodeUnsignedInt(buffer);
		dirveNo = (int)decodeUnsignedInt(buffer);
	}
	
	private Date decodeDate(ByteBuffer buffer){
		byte d1 = buffer.get();
		byte d2 = buffer.get();
		byte d3 = buffer.get();
		byte d4 = buffer.get();
		long d = ((d4 << 24) | 0xffffff) & 
				 ((d3 << 16) | 0xff00ffff) & 
				 ((d2 << 8)  | 0xffff00ff) & 
				 (d1         | 0xffffff00);
		//Integer.p
		//Long.parseLong(arg0, arg1)
		
		int second = (int)(d & 63);
		int minute = (int)((d >> 6) & 63);
		int hour = (int)((d >>12) & 31);
		int day = (int)((d >>17) & 31);
		int month = (int)((d >>22) & 15);
		int year = (int)((d >>26) & 63) + 2000;
		
		//System.out.println(String.format("%s-%s-%s %s:%s:%s", year, month, day, hour, minute, second));
		
		Calendar time = Calendar.getInstance();
		time.set(year, month -1, day, hour, minute, second);

		return time.getTime();
	} 

	private long decodeUnsignedInt(ByteBuffer buffer){
		byte d1 = buffer.get();
		byte d2 = buffer.get();
		byte d3 = buffer.get();
		byte d4 = buffer.get();
		long d = ((d4 << 24) | 0xffffff) & 
				 ((d3 << 16) | 0xff00ffff) & 
				 ((d2 << 8)  | 0xffff00ff) & 
				 (d1         | 0xffffff00);
		return d;	
	}
	
	public String toString(){
		// RecordFileInfo
		return "record:" + format.format(this.startTime) + ",size:" + this.fileSize;
	}
}
