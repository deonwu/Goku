package org.goku.video.encoder;

/**
 * 由于进程pipe不支持NIO操作，使用一个自定义的轮询机制实现线程复用。有一个独立的线程
 * 检查所有pipe操作，如果isReady返回True.则启动线程处理。
 * @author deon
 */
public interface Pollable extends Runnable{
	public boolean isReady();
	public boolean isClosed();
}
