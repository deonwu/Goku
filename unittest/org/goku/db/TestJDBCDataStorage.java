package org.goku.db;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;

import org.goku.TestUtils;
import org.goku.settings.Settings;
import org.junit.Before;
import org.junit.Test;

public class TestJDBCDataStorage {
	private JDBCDataStorage testObj = null;
	
	@Test
	public void test_toOrderBy(){		
		assertEquals(invokeToOrderBy(testObj, "name"), "name");
		assertEquals(invokeToOrderBy(testObj, "+name"), "name asc");
		
		assertEquals(invokeToOrderBy(testObj, "name,age"), "name,age");
		assertEquals(invokeToOrderBy(testObj, "name,+age"), "name,age asc");
		assertEquals(invokeToOrderBy(testObj, "+name,+age"), "name asc,age asc");
		
		assertEquals(invokeToOrderBy(testObj, "name,-age"), "name,age desc");
	}

	@Test
	public void test_toSQLValue(){		
		assertEquals("'name'", invoketoSQLValue(testObj, "name"));
		assertEquals("1", invoketoSQLValue(testObj, 1));
		assertEquals("null", invoketoSQLValue(testObj, null));
		
		Collection<String> param = new ArrayList<String>();
		param.add("1");
		param.add("2");
		assertEquals("('1','2')", invoketoSQLValue(testObj, param));
	}
	
	@Before
	public void initTestObject(){
		Settings s = new Settings("test.conf");
		testObj = new JDBCDataStorage(s);
	}

	private String invokeToOrderBy(Object obj, String param){
		return (String)TestUtils.invokePrivateMethod(obj, "toOrderBy", new String[]{param});		
	}
	private String invoketoSQLValue(Object obj, Object parm){
		return (String)TestUtils.invokePrivateMethod(obj, "toSQLValue", new Object[]{parm}, new Class[]{Object.class});		
	}	
}
