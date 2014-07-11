package org.goku.image;

public interface ImageClientListener {
	/**
	 * 成功读到一个文件.
	 * @param event
	 */
	public void recevieImageOK(ImageClientEvent event);

	/**
	 * 得到文件响应失败消息。
	 * @param event
	 */	
	public void notFoundImage(ImageClientEvent event);
	
	/**
	 * 协议连接错误
	 * @param event
	 */
	public void connectionError(ImageClientEvent event);
	
	/**
	 * 收到需要处理的Message
	 * @param event
	 */
	public void message(ImageClientEvent event);

	/**
	 * 定时报告设备有数据到达。
	 * @param event
	 */
	public void active(ImageClientEvent event);

	/**
	 * 测试用的没有解码的数据包。
	 * @param event
	 */
	public void debugRawData(ImageClientEvent event);
	
}
