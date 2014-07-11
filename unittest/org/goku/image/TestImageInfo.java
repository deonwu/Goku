package org.goku.image;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import org.junit.Test;

public class TestImageInfo {
	
	/**
	 * 向ImageInfo添一些数据，确定得到的ReTry Frames是正确的。
	 */
	@Test
	public void test_getReTryFrames(){
		ImageInfo info = new ImageInfo();
		ByteBuffer buf = ByteBuffer.allocate(2);
		info.setFameCount(10);
		info.setDataSize(20);
		assertArrayEquals(new int[]{1,2,3,4,5,6,7,8,9}, info.getReTryFrames());
		
		info.recieveData(1, buf);
		info.recieveData(2, buf);
		
		info.recieveData(9, buf);		
		assertArrayEquals(new int[]{3,4,5,6,7,8}, info.getReTryFrames());
		
		info.recieveData(5, buf);
		assertArrayEquals(new int[]{3,4,6,7,8}, info.getReTryFrames());
	}
	
	
	/**
	 * 测试数据帧拼装正确。
	 */
	@Test
	public void test_FillFrameData(){
		ImageInfo info = new ImageInfo();
		ByteBuffer buf = ByteBuffer.allocate(2);
		info.setFameCount(5);
		info.setDataSize(8);		
		assertArrayEquals(new int[]{1,2,3,4}, info.getReTryFrames());		
		assertEquals(info.haveMoreData(), true);
		
		buf.clear();buf.put(new byte[]{1,1});buf.flip();	
		info.recieveData(1, buf);
		
		buf.clear();buf.put(new byte[]{2,2});buf.flip();	
		info.recieveData(2, buf);
		
		//buf.clear();buf.put(new byte[]{5,5});buf.flip();		
		//info.recieveData(5, buf);
		buf.clear();buf.put(new byte[]{4,4});buf.flip();		
		info.recieveData(4, buf);
		
		buf.clear();buf.put(new byte[]{3,3});buf.flip();		
		info.recieveData(3, buf);

		
				
		assertEquals(info.haveMoreData(), false);
		info.buffer.position(0);
		
		byte[] data = new byte[info.buffer.remaining()];
		info.buffer.get(data);
		assertArrayEquals(new byte[]{1,1,2,2,3,3,4,4}, data);				
	}	

}
