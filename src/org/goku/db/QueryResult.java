package org.goku.db;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;

/**
 * 查询记录结果。
 * @author deon
 *
 */
public class QueryResult implements JSONStreamAware{
	/**
	 * 查询会话ID.
	 */
	public String sessionId = "";
	/**
	 * 结果总数
	 */
	public int count = 0;	
	public Collection data = null;
	
	@SuppressWarnings("unchecked")
	@Override
	public void writeJSONString(Writer out) throws IOException {
		// TODO Auto-generated method stub
        Map<String, Object> obj = new HashMap<String, Object>();
        List data = new ArrayList();
        data.addAll(this.data);
        obj.put("sessionId", sessionId);
        obj.put("count", count);
        obj.put("data", data);
        
        JSONValue.writeJSONString(obj, out);		
		
	}
}
