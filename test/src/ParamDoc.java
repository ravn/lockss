/*
 * $Id: ParamDoc.java,v 1.5 2004-01-07 18:56:14 tlipkis Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.jar.*;
import org.lockss.util.*;

public class ParamDoc {
  static final String blanks = "                                                                                ";
  private static Logger log = Logger.getLogger("ParamDoc");

  static Map paramMap = new TreeMap();
  static Map classMap = new TreeMap();
  static Map paramToSymbol = new HashMap();

  static Map defaultMap = new TreeMap();

  public static void main(String argv[]) {
    Vector jars = new Vector();
    for (int i=0; i<argv.length; i++) {
      String s = argv[i];
      jars.add(s);
      File jarfile = find_jar(s);
      JarFile jar;
      try {
	jar = new JarFile(jarfile);
      } catch (IOException e) {
	log.error("Couldn't open jar " + s, e);
	return;
      }
      for (Enumeration enum = jar.entries();
	   enum.hasMoreElements(); ) {
	JarEntry ent = (JarEntry)enum.nextElement();
	doClass(ent);
      }
    }
    System.out.println("Params with defaults");
    printDefaults();
//     System.out.println("\nParameters by class");
//     printMap(classMap);
    System.out.println("\nParameters by name");
    printMap(paramMap);
  }

  static final int COL = 40;

  static void printMap(Map map) {
    for (Iterator keyIter = map.keySet().iterator();
	 keyIter.hasNext(); ) {
      String key = (String)keyIter.next();
      List list = (List)map.get(key);
      System.out.print(key);
      Collections.sort(list);
      int len = key.length();
      for (Iterator iter = list.iterator(); iter.hasNext(); ) {
	if (len >= COL) {
	  System.out.println();
	  len = 0;
	}
	System.out.print(nblanks(COL - len));
	System.out.println((String)iter.next());
	len = 0;
      }
    }
  }

  static void printDefaults() {
    for (Iterator paramNameIter = defaultMap.keySet().iterator();
	 paramNameIter.hasNext(); ) {
      String paramName = (String)paramNameIter.next();
      Object defaultVal;

      defaultVal = defaultMap.get(paramName);

      System.out.print(paramName);
      int len = paramName.length();
      if (len >= COL) {
	System.out.println();
	len = 0;
      }
      System.out.print(nblanks(COL - len));
      System.out.print(defaultVal);
      if (defaultVal instanceof Long) {
	String timeStr = 
	  StringUtil.timeIntervalToString(((Long)defaultVal).longValue());
	System.out.println(" ("+timeStr+")");
      } else {
	System.out.println();
      }
    }
  }

  static String classNameFromEntry(JarEntry ent) {
    String entname = ent.getName();
    if (entname.endsWith(".class") && (-1 == entname.indexOf("$"))) {
      String cname =
	StringUtil.replaceString(entname.substring(0, entname.length() - 6),
				 File.separator, ".");
      return cname;
    }
    return null;
  }

  static void doClass(JarEntry ent) {
    Class cls;
    String cname = classNameFromEntry(ent);
    if (cname == null) return;
    try {
      cls = Class.forName(cname);
    } catch (ClassNotFoundException e) {
      log.error(cname, e);
      return;
    }
    Field flds[] = cls.getDeclaredFields();

    Map defaultSymToDefVal = new HashMap(); //def symbol to def value
    Map paramToSymbol = new HashMap(); //param symbol to param name

    for (int ix = 0; ix < flds.length; ix++) {
      doField(cls, flds[ix], paramToSymbol);
      doFieldDefault(cls, flds[ix], defaultSymToDefVal);
    }
    for (Iterator it = paramToSymbol.keySet().iterator(); it.hasNext();) {
      String paramName = (String)it.next();
      String paramSym = (String)paramToSymbol.get(paramName);
      String defaultSym = "DEFAULT"+paramSym.substring(5);
      Object defaultVal = defaultSymToDefVal.get(defaultSym);

      if (defaultVal != null) {
	putIfNotDifferent(defaultMap, paramName, defaultVal,
			  "Conflicting defaults");
      }
//       putIfNotDifferent(defaultMap, paramName, 
// 			defaultVal != null ? defaultVal : "(none)", 
// 			"Conflicting defaults");
    }
  }

  static void doField(Class cls, Field fld, Map paramToSymbol) {
    String fname = fld.getName();
    
    if (Modifier.isStatic(fld.getModifiers()) &&
	String.class == fld.getType() &&
	fname.startsWith("PARAM_")) {
      String paramName;
      try {
	fld.setAccessible(true);
	paramName = (String)fld.get(null);
      } catch (IllegalAccessException e) {
	log.error(fld.toString(), e);
	return;
      }
      if (paramName.indexOf("..") >= 0 && !paramMap.containsKey(paramName)) {
	System.err.println("*** Suspicious parameter name: " + paramName);
      }	
      addParam(paramMap, paramName, cls.getName());
      addParam(classMap, cls.getName(), paramName);
      putIfNotDifferent(paramToSymbol, paramName, fname, 
			"Multiple symbols used to define parameter name ");
    }
  }

  static void doFieldDefault(Class enclosingClass, Field fld, 
			     Map defaultSymToDefVal) {
    String fname = fld.getName();
    
    if (Modifier.isStatic(fld.getModifiers()) &&
	fname.startsWith("DEFAULT_")) {
      Object defaultVal;
      try {
	fld.setAccessible(true);
	Class cls = fld.getType();
	if (int.class == cls) {
	  defaultVal = new Integer(fld.getInt(null));
	} else if (long.class == cls) {
	  defaultVal = new Long(fld.getLong(null));
	} else if (boolean.class == cls) {
	  defaultVal = new Boolean(fld.getBoolean(null));
	} else {
	  defaultVal = fld.get(null);
	}
      } catch (IllegalAccessException e) {
	log.error(fld.toString(), e);
	return;
      }

      defaultSymToDefVal.put(fname, defaultVal);
    }
  }

  static void putIfNotDifferent(Map map, Object key, Object val, String msg) {
    Object existingVal = map.get(key);
    if (existingVal != null && !existingVal.equals(val)) {
      System.err.println("*** " +msg+" "+key+" "+val+" "+existingVal);
    } else {
      map.put(key, val);
    }
  }

  static void addParam(Map map, String key, String val) {
    if (!map.containsKey(key)) {
      map.put(key, new ArrayList());
    }
    List list = (List)map.get(key);
    list.add(val);
  }

  static String nblanks(int n) {
    return blanks.substring(0, n);
  }

  public static final String CLASSPATH = System.getProperty("java.class.path");

  public static final File [] CLASSPATH_DIRS;
  static {
    StringTokenizer st = new StringTokenizer(CLASSPATH, File.pathSeparator);
    int count = st.countTokens();
    CLASSPATH_DIRS = new File[count];
    for (int i = 0; i<count; i++) {
      CLASSPATH_DIRS[i] = new File(st.nextToken());
    }
  }

  /**
   * Find a plain file or a directory in default classpath.
   * 
   * @see #find_file(File[], String)
   */
  public static File find_file(String name) {
    return find_file(CLASSPATH_DIRS, name);
  }

  public static File find_jar(String name) {
    return find_jar(CLASSPATH_DIRS, name);
  }

  /** 
   * Find a plain file or a directory.
   *
   * @param dirs search paths
   * @param name filename (basename with extension) or dirname
   * @return <code>null</code> if not found
   */
  public static File find_file(File[] dirs, String name) {
    for (int i=0; i<dirs.length; i++) {
      File file = new File(dirs[i], name);
      if (file.canRead()) return file;
    }
    return null;
  }
  /** 
   * Find a plain file or a directory.
   *
   * @param dirs search paths
   * @param name filename (basename with extension) or dirname
   * @return <code>null</code> if not found
   */
  public static File find_jar(File[] dirs, String name) {
    for (int i=0; i<dirs.length; i++) {
      File file = dirs[i];
      if (file.getName().equals(name)) return file;
    }
    return null;
  }
}
