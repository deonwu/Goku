package org.goku.db;

import java.util.Date;

import org.goku.core.model.BaseStation;
import org.goku.settings.Settings;

public class DBTesting {

	/**
	 * @param args
	 */
	public static void main(String[] args) {		
		System.out.println("start...");
		
		// TODO Auto-generated method stub
		Settings s = new Settings("video.conf");
		
		DataStorage db = DataStorage.create(s);
		db.checkConnect();
		
		BaseStation station = new BaseStation();
		station.uuid = "001";
		station.lastActive = new Date();
		station.createDate = new Date();
		station.lastUpdate = new Date();
		
		station.devType = 100;
		
		station.connectionStatus = "";
		station.routeServer = "";
		station.alarmStatus = "";
		
		station.groupName = "test goup";
		
		station.locationId = "test xxx";
		
		db.save(station);
		
		System.out.println("done.");
	}

}
