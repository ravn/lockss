package org.lockss.util;

import java.util.*;
import java.net.*;

public class TypedEntryMap {
  protected HashMap m_map;

  public TypedEntryMap() {
    m_map = new HashMap();
  }

  public Set keySet() {
   return m_map.keySet();
 }

 public Set entrySet() {
   return m_map.entrySet();
 }


 public Object getMapElement(String descrKey) {
   synchronized(m_map) {
     return m_map.get(descrKey);
   }
 }

 public void setMapElement(String descrKey, Object descrElement) {
   synchronized(m_map) {
     if (descrElement instanceof URL) {
       m_map.put(descrKey, descrElement.toString());
     } else {
       m_map.put(descrKey, descrElement);
     }
   }
 }


 public Object removeMapElement(String descrKey) {
   synchronized(m_map) {
     return m_map.remove(descrKey);
   }
 }

 public String getStringElement(String descrKey) {
   synchronized(m_map) {
     return m_map.get(descrKey).toString();
   }
 }

 /*
   *
   *  methods for retrieving typed data or returning a default
   *
   */
  public String getString(String key, String def) {
    String ret = def;
    String value = (String)getMapElement(key);
    if (value != null) {
      ret = value;
    }
    return ret;
  }

  public boolean getBoolean(String key, boolean def) {
    boolean ret = def;

    Boolean value = (Boolean)getMapElement(key);
    if (value != null) {
      ret = value.booleanValue();
    }
    return ret;
  }


  public double getDouble(String key, double def) {
    double ret = def;

    try {
      Double value = (Double)getMapElement(key);
      if (value != null) {
        ret = value.doubleValue();
      }
    } catch (NumberFormatException ex) { }
    return ret;
  }


  public float getFloat(String key, float def) {
    float ret = def;
    try {
      Float value = (Float)getMapElement(key);
      if (value != null) {
        ret = value.floatValue();
      }
    } catch (NumberFormatException ex) { }
    return ret;
  }


  public int getInt(String key, int def) {
    int ret = def;
    try {
      Integer value = (Integer)getMapElement(key);
      if (value != null) {
        ret = value.intValue();
      }
    } catch (NumberFormatException ex) { }
    return ret;
  }

  public long getLong(String key, long def) {
    long ret = def;

    try {
      Long value = (Long)getMapElement(key);
      if (value != null) {
        ret = value.longValue();
      }
    } catch (NumberFormatException ex) { }
    return ret;
  }

  public URL getUrl(String key, URL def) {
    URL ret = def;

    String valueStr = (String)getMapElement(key);
    if (valueStr!=null) {
      try {
        URL value = new URL(valueStr);
        ret = value;
      } catch (MalformedURLException mue) { }
    }
    return ret;
  }

  public Collection getCollection(String key, Collection def) {
    Collection ret = def;
    Collection value = (Collection)getMapElement(key);
    if (value != null) {
      ret = value;
    }
    return ret;
  }

  public Map getMap(String key, Map def) {
    Map ret = def;
    Map value = (Map)getMapElement(key);
    if (value != null) {
      ret = value;
    }
    return ret;
  }

  /*
     methods for return typed date which will throw an exception if item is not
     found
   */
  public String getString(String key) {
    String value = (String)getMapElement(key);
    if (value != null) {
      return value;
    }
    throw new NoSuchElementException("String element for " + key + " not found.");
  }

  public boolean getBoolean(String key) {
    Boolean value = (Boolean)getMapElement(key);
    if (value != null) {
      return value.booleanValue();
    }
    throw new NoSuchElementException("Boolean element for " + key + " not found.");
  }


  public double getDouble(String key) {
    try {
      Double value = (Double)getMapElement(key);
      if (value != null) {
        return value.doubleValue();
      }
    } catch (NumberFormatException ex) { }
    throw new NoSuchElementException("Double element for " + key + " not found.");
  }


  public float getFloat(String key) {
    try {
      Float value = (Float)getMapElement(key);
      if (value != null) {
        return value.floatValue();
      }
    } catch (NumberFormatException ex) { }
    throw new NoSuchElementException("Float element for " + key + " not found.");
  }


  public int getInt(String key) {
    try {
      Integer value = (Integer)getMapElement(key);
      if (value != null) {
        return value.intValue();
      }
    } catch (NumberFormatException ex) { }
    throw new NoSuchElementException("Integer element for " + key + " not found.");
  }

  public long getLong(String key) {
    try {
      Long value = (Long)getMapElement(key);
      if (value != null) {
        return value.longValue();
      }
    } catch (NumberFormatException ex) { }
    throw new NoSuchElementException("Long element for " + key + " not found.");
  }

  public URL getUrl(String key) {
    String valueStr = (String)getMapElement(key);
    if (valueStr!=null) {
      try {
        URL value = new URL(valueStr);
        return value;
      } catch (MalformedURLException mue) { }
    }
    throw new NoSuchElementException("Url element for " + key + " not found.");
  }

  public Collection getCollection(String key) {
    Collection value = (Collection)getMapElement(key);
    if (value != null) {
      return value;
    }
    throw new NoSuchElementException("Collection element for " + key + " not found.");
  }

  public Map getMap(String key) {
    Map value = (Map)getMapElement(key);
    if (value != null) {
      return value;
    }
    throw new NoSuchElementException("Map element for " + key + " not found.");
  }

  /*
   *
   * methods for storing typed primitive data
   *
   */

  public void putString(String key, String value) {
    setMapElement(key, value);
  }

  public void putBoolean(String key, boolean value) {
    setMapElement(key, new Boolean(value));
  }

  public void putDouble(String key, double value) {
    setMapElement(key, new Double(value));
  }

  public void putFloat(String key, float value) {
    setMapElement(key, new Float(value));
  }

  public void putInt(String key, int value) {
    setMapElement(key, new Integer(value));
  }

  public void putLong(String key, long value) {
    setMapElement(key, new Long(value));
  }

  public void putUrl(String key, URL url) {
    setMapElement(key, url.toString());
  }

  public void putCollection(String key, Collection value) {
    setMapElement(key, value);
  }

  public void putMap(String key, Map value) {
    setMapElement(key, value);
  }
}
