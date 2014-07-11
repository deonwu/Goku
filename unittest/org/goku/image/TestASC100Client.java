package org.goku.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

public class TestASC100Client {
	private ArrayList<ASC100Package> packages = null; 
	private ASC100Client testClient = null;
	
	/**
	 * 测试：转义处理，包格式解析，校验和计算都是正确的。
	 * <cmd>:<len>:<data>:<checksum>
	 */
	@Test
	public void test_ProcessSimpleClientPackage(){
		ByteBuffer data = ByteBuffer.allocate(100);
		data.put(new byte[]{(byte)0xff, 0x02, 0x01, 0x00, 0x06, (byte)0xf7, (byte)0xfd, 0x02, (byte)0xfe});
		data.flip();
		
		testClient.process(data);
		
		assertEquals("Not found processed package.", 1, packages.size());
		assertEquals("Check package command.", 0x02, packages.get(0).cmd);
		assertEquals("Check package data length.", 0x01, packages.get(0).len);
		
		byte[] readData = new byte[packages.get(0).inBuffer.remaining()];
		packages.get(0).inBuffer.get(readData);
		assertArrayEquals("Check data", new byte[]{(byte)0x06}, readData);		
	}
	
	/**
	 * 测试：数据分片读入，应该可以正确拼装。
	 * <cmd>:<len>:<data>:<checksum>
	 */
	@Test
	public void test_ProcessSplittedClientPackage(){
		ByteBuffer data = ByteBuffer.allocate(100);
		data.put(new byte[]{(byte)0xff, 0x02, 0x01, (byte)0xff,});
		data.flip();		
		testClient.process(data);
		
		data.clear(); data.put(new byte[]{0x02, 0x01, 0x00, 0x06}); data.flip();		
		testClient.process(data);

		data.clear(); data.put(new byte[]{(byte)0xf7, (byte)0xfd}); data.flip();		
		testClient.process(data);		

		data.clear(); data.put(new byte[]{0x02, (byte)0xfe, (byte)0xfd, 0x02, (byte)0xfe}); data.flip();		
		testClient.process(data);
		
		assertEquals("Not found processed package.", 1, packages.size());
		assertEquals("Check package command.", 0x02, packages.get(0).cmd);
		assertEquals("Check package data length.", 0x01, packages.get(0).len);
		
		byte[] readData = new byte[packages.get(0).inBuffer.remaining()];
		packages.get(0).inBuffer.get(readData);
		assertArrayEquals("Check data", new byte[]{(byte)0x06}, readData);		
	}
	
	/**
	 * 测试：错误的包格式应该可以自动修正，丢弃错误数据.
	 * <cmd>:<len>:<data>:<checksum>
	 */
	@Test
	public void test_ProcessClientPackageWithOtherData_01(){
		ByteBuffer data = ByteBuffer.allocate(100);
		data.put(new byte[]{(byte)0xff, 0x02, 0x01, 
						    (byte)0xff, 0x02, 0x01, 0x00, 0x06, (byte)0xf7, (byte)0xfd, 0x02, (byte)0xfe});
		data.flip();
		
		testClient.process(data);		
		assertEquals("Not found processed package.", 1, packages.size());
		assertEquals("Check package command.", 0x02, packages.get(0).cmd);
		assertEquals("Check package data length.", 0x01, packages.get(0).len);	

		byte[] readData = new byte[packages.get(0).inBuffer.remaining()];
		packages.get(0).inBuffer.get(readData);
		assertArrayEquals("Check data", new byte[]{(byte)0x06}, readData);	
	}
	
	/**
	 * 测试：错误的包格式应该可以自动修正，丢弃错误数据.
	 * <cmd>:<len>:<data>:<checksum>
	 */
	@Test	
	public void test_ProcessClientPackageWithOtherData_02(){
		//错误的不完整的包被丢弃。
		ByteBuffer data = ByteBuffer.allocate(100);
		data.put(new byte[]{(byte)0xff, 0x02, 0x01, (byte)0xfe, 
							(byte)0xff, 0x02, 0x01, 0x00, 0x06, (byte)0xf7, (byte)0xfd, 0x02, (byte)0xfe});
		data.flip();
		
		testClient.process(data);		
		assertEquals("Not found processed package.", 1, packages.size());
		assertEquals("Check package command.", 0x02, packages.get(0).cmd);
		assertEquals("Check package data length.", 0x01, packages.get(0).len);
		
		byte[] readData = new byte[packages.get(0).inBuffer.remaining()];
		packages.get(0).inBuffer.get(readData);
		assertArrayEquals("Check data", new byte[]{(byte)0x06}, readData);		
	}
	
	@Before
	public void setup_TestASC100Client(){
		System.out.println("...test setup...");
		packages = new ArrayList<ASC100Package>();
		/**
		 * 重载数据处理方法，跳过图片数据处理。
		 */
		testClient = new ASC100Client("12.34.1"){
			public void processData(ASC100Package data){
				if(data.checkSum != data.bufferCheckSum){
					log.debug(String.format("Drop package the check sum error. excepted:%x, actual:%x", data.checkSum, data.bufferCheckSum));
				}else {
					log.debug(String.format("process ASC100 message:0x%x, length:%s, remaining:%s", data.cmd, data.len, data.inBuffer.remaining()));
					packages.add(data.copy());
				}
			}
		};
	}

}
