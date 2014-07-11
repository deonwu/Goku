package org.goku.video.odip;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestingProcess {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		byte a = (byte)254;
		a = (byte) (a+ 4);
		System.out.println("aa:" + a);
	}
}
