package game.state;

import org.apache.log4j.Logger;

import game.model.ModelObject;
import util.Util;

public class StateObject extends ModelObject implements StateI {
	
    private String name;
	private Object object = null;
	private Class clazz = null;
	
	protected static Logger log = Logger.getLogger(StateObject.class.getPackage().getName());

	public StateObject (String name, Class clazz) {
	    this.name = name;
		if (clazz == null) {
			new Exception ("NULL class not allowed in creating State wrapper")
				.printStackTrace();
		} else {
			this.clazz = clazz;
		}
	}
	
	public StateObject (String name, Object object) {
	    this.name = name;
		if (object == null) {
			new Exception ("NULL object not allowed in creating State wrapper")
				.printStackTrace();
		} else if (clazz != null && Util.isInstanceOf(object, clazz)) {
			new Exception ("Object "+object+" must be instance of "+clazz)
				.printStackTrace();
		} else {
			this.object = object;
			if (clazz == null) clazz = object.getClass();
		}
	}

	public Object getState() {
		return object;
	}

	public void setState(Object object) {
		if (object == null || Util.isInstanceOf (object, clazz)) {
			this.object = object;
			log.debug ("StateObject "+name+" set to "+object);
		} else {
		    log.error("Incompatible object type "+object.getClass().getName()
					+ "passed to State wrapper for object type "+this.object.getClass().getName()+" at:",
					new Exception (""));
		}
		update();
	}
	
	public String toString() {
	    return name;
	}
	
	public Object getNotificationObject() {
	    return object;
	}

}
