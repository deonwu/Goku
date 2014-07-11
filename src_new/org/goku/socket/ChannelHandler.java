package org.goku.socket;

import java.nio.channels.SelectableChannel;

public interface ChannelHandler {
	public void setSocketChannel(SelectableChannel channel);
}
