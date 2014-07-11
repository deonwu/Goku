package org.goku.core.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class SimpleCache {
	private Map<String, CacheItem> cache = Collections.synchronizedMap(new HashMap<String, CacheItem>());
	
	/**
	 * @param key
	 * @param obj
	 * @param expired -- 过期时间，单位:秒
	 */
	public void set(String key, Object obj, int expired){
		CacheItem item = new CacheItem();
		item.obj = obj;
		item.expiredTime = expired * 1000;
		cache.put(key, item);
	}

	public Object get(String key){
		cleanObject();
		CacheItem item = cache.get(key); 
		if(item != null){
			if(System.currentTimeMillis() - item.lastAccess < item.expiredTime){
				item.lastAccess = System.currentTimeMillis();
				return item.obj;
			}else {
				cache.remove(key);
			}
		}
		return null;
	}
	
	public void remove(String key){
		cache.remove(key);
	}	
	
	private void cleanObject(){
		Vector<String> keys = new Vector<String>();
		keys.addAll(cache.keySet());
		for(String key: keys){
			CacheItem item = cache.get(key); 
			if(item != null &&
			   System.currentTimeMillis() - item.lastAccess 
			   > item.expiredTime ){
				cache.remove(key);
			}
		}
	}
	
	class CacheItem{
		Object obj = null;
		long lastAccess = System.currentTimeMillis();
		long expiredTime = 0;
	}
}
