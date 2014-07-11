package org.goku.image;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.goku.core.model.BaseStation;
import org.goku.db.DataStorage;

public class ImageText {
	private static Log log = LogFactory.getLog("img");
	private static Map<String, String> names = new HashMap<String, String>();
	
	public static byte[] drawText(byte[] image, String text, String location){		
		InputStream input = new ByteArrayInputStream(image);
		ByteArrayOutputStream os = new ByteArrayOutputStream(1024 * 64); 

		BufferedImage img = null;
		try{
			img = ImageIO.read(input);
			int[] l = parseLocation(location);
			Graphics g = img.getGraphics(); 
			g.setColor(Color.white);
			if(l[0] < 0){
				l[0] += img.getWidth();
			}
			if(l[1] < 0){
				l[1] += img.getHeight();
			}	
			g.drawString(text, l[0], l[1]);
			ImageIO.write(img, "jpeg", os);
		} catch (IOException e) {
			log.error(e.toString(), e);
		} finally{
			try {
				os.flush();
				os.close();
			} catch (IOException e1) {
				log.error(e1.toString(), e1);
			}
		}
		
		return os.toByteArray();
	}
	
	private static int[] parseLocation(String location){
		int data[] = new int[2];
		String tmp[] = location.split("x");
		
		data[0] = Integer.parseInt(tmp[0].trim());
		data[1] = Integer.parseInt(tmp[1].trim());
		return data;
	}
	
	public static String titleName(String uuid, String ch){
		if(!names.containsKey(uuid)){
			DataStorage storage = ImageRouteServer.getInstance().storage; 
			BaseStation bs = (BaseStation) storage.load(BaseStation.class, uuid);
			if(bs != null){
				names.put(uuid, bs.name);
			}else {
				names.put(uuid, "not found");
			}
		}
		
		return names.get(uuid);
	}
	
	
	public static void main(String[] args) throws Exception{
		File f = new File("D:\\照片\\叶问2\\IMG_2591.jpg");
		byte[] data = new byte[(int)f.length()];
		InputStream in = new FileInputStream(f);
		in.read(data);
		in.close();	
		
		data = drawText(data, "this is atest", "10x-30");
		
		File f2 = new File("test.jpg");
		OutputStream os = new FileOutputStream(f2);
		
		os.write(data);
		os.close();
		System.out.println("len:" + data.length  + ", done:" + f2.getAbsolutePath());
	}
}
