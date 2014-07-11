package org.goku;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Ignore;

@Ignore
public class TestUtils {

	
	@SuppressWarnings("rawtypes")
	public static Object invokePrivateMethod(Object obj, String methodName, Object params[]) {
		Class[] cls = new Class[params.length];
		for(int i = 0; i < cls.length; i++){
			cls[i] = params[i].getClass();
		}
		return invokePrivateMethod(obj, methodName, params, cls);
	}
	@SuppressWarnings("rawtypes")
	public static Object invokePrivateMethod(Object obj, String methodName, Object params[], Class[] cls) {		
		Method method = null;
		Object ret = null;
		try {
			method = obj.getClass().getDeclaredMethod(methodName, cls);
			method.setAccessible(true);
			ret = method.invoke(obj, params);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		return ret;
	}
}
