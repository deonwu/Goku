package org.goku.video.odip;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface VideoDestination {
	/**
	 * 判断接受数据类型，对于双码流的情况，客户端可能只需要接受底质量的码流。
	 * @param sourceType
	 * @param channel TODO
	 */
	public boolean accept(int sourceType, int channel);
	
	/**
	 * 实现接口需要考虑性能因素。该方法要求最高的性能。
	 * @param data 
	 */	
	public void write(ByteBuffer data, int type, int channel) throws IOException;
	
	/**
	 * 关闭目地, 内部处理异常。
	 * @param data 
	 */
	public void close();
	public boolean isClosed();
}
