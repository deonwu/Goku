package org.goku.socket;

import java.io.IOException;

public interface SocketListener {
	public void connected(NIOSession s) throws IOException;
	
	public void connect_error(NIOSession s) throws IOException;
	
	public void timeout(NIOSession s) throws IOException;
	
	public void disconnected(NIOSession s) throws IOException;
	
	/**
	 * 可以读会话。
	 * @param s
	 * @throws IOException
	 */
	public void read(NIOSession s) throws IOException;
	
	/**
	 * 需要发送一个响应包，避免服务端关闭。
	 * @param s
	 * @throws IOException
	 */
	public void active(NIOSession s) throws IOException;
}
