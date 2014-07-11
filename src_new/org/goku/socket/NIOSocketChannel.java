package org.goku.socket;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface NIOSocketChannel {
	public void write(ByteBuffer src, boolean sync);
	public void read(ByteBuffer buffer) throws IOException;
}
