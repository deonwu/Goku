package org.goku.core.model;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import org.goku.db.DataStorage;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;

public class Location implements JSONStreamAware{
	public static final String ORM_TABLE = "location";
	public static final String[] ORM_FIELDS = new String[]{"uuid", "name",
		"parent", };
	public static final String[] ORM_PK_FIELDS = new String[]{"uuid"};
	
	public String uuid = null;	
	public String name = null;
	public String parent = null;
	
	public Collection<Location> children = new TreeSet<Location>(compartor);
	public Collection<BaseStation> listBTS = new TreeSet<BaseStation>(compartor);
	
	public static Comparator compartor = new Comparator(){
		@Override
		public int compare(Object arg0, Object arg1) {
			if(arg0.equals(arg1)){ 
				return 0;
			}
			return 1;
		}};
	
	public void load(DataStorage storage, User user){
		
	}
	
	public int getTreeCount(){
		int count = 1 + listBTS.size();
		for(Location l : children){
			count += l.getTreeCount();
		}
		return count;
	}
	
	@Override
	public void writeJSONString(Writer out) throws IOException {
        Map<String, Object> obj = new HashMap<String, Object>();
        obj.put("uuid", uuid);
        obj.put("name", name);
        obj.put("children", children);
        obj.put("listBTS", listBTS);
        JSONValue.writeJSONString(obj, out);
	}
	
	public boolean equals(Object obj){
		if(obj instanceof Location){
			String u = ((Location) obj).uuid; 
			if(u != null && this.uuid != null && this.uuid.equals(u)){
				return true;
			}
		}
		return false;
	}	
	
	public String toString(){
		return String.format("%s<%s>", this.name, this.uuid);
	}
}
