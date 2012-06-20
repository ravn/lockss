/*
 * $Id: StringUtil.java,v 1.106.2.1 2012-06-20 00:02:57 nchondros Exp $
 */

/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.util;
import java.util.*;
import java.io.*;
import java.text.*;
import java.text.Normalizer.Form;
import java.lang.reflect.*;
import org.apache.oro.text.regex.*;
import org.apache.commons.lang.StringUtils;

/**
 * This is a class to contain generic string utilities
 *
 */

public class StringUtil {

  static Logger log = Logger.getLogger("StringUtil");

  /**
   * Find the longest common prefix of a pair of strings. Case sensitive.
   * @param s1 a string
   * @param s2 another string
   * @return the longest common prefix, which may be the emopty string
   */
  public static String commonPrefix(String s1, String s2) {
    char[] c1 = s1.toCharArray();
    char[] c2 = s2.toCharArray();
    StringBuilder sb = new StringBuilder();
    for (int i=0; i<Math.min(c1.length, c2.length); i++) {
      if (c1[i]==c2[i]) sb.append(c1[i]);
      else break;
    }
    return sb.toString();
  }

  /**
   * Find the longest common suffix of a pair of strings. Case sensitive.
   * @param s1 a string
   * @param s2 another string
   * @return the longest common suffix, which may be the emopty string
   */
  public static String commonSuffix(String s1, String s2) {
    char[] c1 = s1.toCharArray();
    char[] c2 = s2.toCharArray();
    StringBuilder sb = new StringBuilder();
    for (int i=1; i<=Math.min(c1.length, c2.length); i++) {
      if (c1[c1.length-i]==c2[c2.length-i]) sb.append(c1[c1.length-i]);
      else break;
    }
    return sb.reverse().toString();
  }

  /**
   * Replace all occurrences of oldstr in source with newstr
   * @param source string to be modified
   * @param oldstr string to be replace
   * @param newstr string to replace oldstr
   * @return new string with oldstr replaced by newstr
   */
  public static String replaceString(String source,
				     String oldstr, String newstr) {
    int oldLen = oldstr.length();
    if (oldLen == 0 || oldstr.equals(newstr)) {
      return source;
    }
    int thisIdx = source.indexOf(oldstr);
    if (thisIdx < 0) {
      return source;
    }
    int sourceLen = source.length();
    StringBuilder sb = new StringBuilder(sourceLen);
    int oldIdx = 0;
    do {
      for (int ix = oldIdx; ix < thisIdx; ix++) {
	sb.append(source.charAt(ix));
      }
      sb.append(newstr);
      oldIdx = thisIdx + oldLen;
    } while ((thisIdx = source.indexOf(oldstr, oldIdx)) >= 0);
    for (int ix = oldIdx; ix < sourceLen; ix++) {
      sb.append(source.charAt(ix));
    }
    return sb.toString();
  }

  public static String replaceFirst(String source,
				    String oldstr, String newstr) {
    int oldLen = oldstr.length();
    if (oldLen == 0 || oldstr.equals(newstr)) {
      return source;
    }
    int index = source.indexOf(oldstr);
    if (index < 0) {
      return source;
    } else {
      int sourceLen = source.length();
      StringBuilder sb = new StringBuilder(sourceLen);
      sb.append(source.substring(0, index));
      sb.append(newstr);
      if (index + oldLen < sourceLen) {
	sb.append(source.substring(index + oldLen));
      }
      return sb.toString();
    }
  }

  public static String replaceLast(String source,
				   String oldstr, String newstr) {
    int oldLen = oldstr.length();
    if (oldLen == 0 || oldstr.equals(newstr)) {
      return source;
    }
    int index = source.lastIndexOf(oldstr);
    if (index < 0) {
      return source;
    } else {
      int sourceLen = source.length();
      StringBuilder sb = new StringBuilder(sourceLen);
      sb.append(source.substring(0, index));
      sb.append(newstr);
      if (index + oldLen < sourceLen) {
	sb.append(source.substring(index + oldLen));
      }
      return sb.toString();
    }
  }

  /**
   * Concatenate elements of collection into string, separated by commas
   * @param c - Collection of object (on which toString() will be called)
   * @return Concatenated string
   */
  public static String separatedString(Collection c) {
    return separatedString(c, ", ");
  }

  /**
   * Concatenate elements of collection into string, with separators
   * @param c - Collection of object (on which toString() will be called)
   * @param separator - String to put between elements
   * @return Concatenated string
   */
  public static String separatedString(Collection c, String separator) {
    return separatedString(c, "", separator, "",
			   new StringBuilder()).toString();
  }

  /**
   * Concatenate elements of object array into string, with separators
   * @param arr - Array of object (on which toString() will be called)
   * @param separator - String to put between elements
   * @return Concatenated string
   */
  public static String separatedString(Object[] arr, String separator) {
    return separatedString(ListUtil.fromArray(arr), "", separator, "",
			   new StringBuilder()).toString();
  }

  /**
   * Concatenate elements of int array into string, with separators
   * @param arr - Array of int elements
   * @param separator - String to put between elements
   * @return Concatenated string
   */
  public static String separatedString(int[] arr, String separator) {
    ArrayList col = new ArrayList(arr.length);
    for (int ii = 0 ; ii < arr.length ; ++ii) {
      col.add(Integer.toString(arr[ii]));
    }
    return separatedString(col, "", separator, "",
			   new StringBuilder()).toString();
  }

  /**
   * Concatenate elements of long array into string, with separators
   * @param arr - Array of int elements
   * @param separator - String to put between elements
   * @return Concatenated string
   */
  public static String separatedString(long[] arr, String separator) {
    ArrayList col = new ArrayList(arr.length);
    for (int ii = 0 ; ii < arr.length ; ++ii) {
      col.add(Long.toString(arr[ii]));
    }
    return separatedString(col, "", separator, "",
			   new StringBuilder()).toString();
  }

  /**
   * Concatenate elements of collection into string, with separators
   * @param c - Collection of object (on which toString() will be called)
   * @param separator - String to put between elements
   * @param sb - StringBuilder to write result into
   * @return sb
   */
  public static StringBuilder separatedString(Collection c, String separator,
					      StringBuilder sb) {
    return separatedString(c, "", separator, "", sb);
  }

  /**
   * Concatenate elements of collection into string, delimiting each element,
   * adding separators
   * @param c - Collection of object (on which toString() will be called)
   * @param separator - String to put between elements
   * @param delimiter - String with which to surround each element
   * @return Concatenated string
   */
  public static String separatedDelimitedString(Collection c, String separator,
						String delimiter) {
    return separatedString(c, delimiter,
			   delimiter + separator + delimiter, delimiter,
			   new StringBuilder()).toString();
  }

  /**
   * Concatenate elements of collection into string, delimiting each element,
   * adding separators
   * @param c - Collection of object (on which toString() will be called)
   * @param separator - String to put between elements
   * @param delimiter1 - String with which to prefix each element
   * @param delimiter2 - String with which to suffix each element
   * @return Concatenated string
   */
  public static String separatedDelimitedString(Collection c, String separator,
						String delimiter1,
						String delimiter2) {
    return separatedString(c, delimiter1,
			   delimiter2 + separator + delimiter1, delimiter2,
			   new StringBuilder()).toString();
  }

  /**
   * Concatenate elements of collection into string, adding separators,
   * terminating with terminator
   * @param c - Collection of object (on which toString() will be called)
   * @param separator - String to put between elements
   * @param terminator - String with which to terminate result
   * @return Concatenated string
   */
  public static String terminatedSeparatedString(Collection c, String separator,
						 String terminator) {
    return separatedString(c, "", separator, terminator,
			   new StringBuilder()).toString();
  }

  /**
   * Concatenate elements of collection into string, adding separators,
   * delimitig each element
   * @param c - Collection of object (on which toString() will be called)
   * @param separatorFirst - String to place before first element
   * @param separatorInner - String with which to separate elements
   * @param separatorLast - String to place after last element
   * @return Concatenated string
   */
  public static String separatedString(Collection c,
				       String separatorFirst,
				       String separatorInner,
				       String separatorLast) {

    return separatedString(c, separatorFirst, separatorInner, separatorLast,
			   new StringBuilder()).toString();
  }

  /**
   * Concatenate elements of collection into string, adding separators,
   * delimitig each element
   * @param c - Collection of object (on which toString() will be called)
   * @param separatorFirst - String to place before first element
   * @param separatorInner - String with which to separate elements
   * @param separatorLast - String to place after last element
   * @param sb - StringBuilder to write result into
   * @return sb
   */
  public static StringBuilder separatedString(Collection c,
					      String separatorFirst,
					      String separatorInner,
					      String separatorLast,
					      StringBuilder sb) {
    if (c == null) {
      return sb;
    }
    Iterator iter = c.iterator();
    boolean first = true;
    while (iter.hasNext()) {
      if (first) {
	first = false;
	sb.append(separatorFirst);
      } else {
	sb.append(separatorInner);
      }
      Object obj = iter.next();
      sb.append(obj == null ? "(null)" : obj.toString());
    }
    if (!first) {
      sb.append(separatorLast);
    }
    return sb;
  }

  /** Break a string at a separator char, returning a vector of at most
   * maxItems strings.
   * @param s string containing zero or more occurrences of separator
   * @param sep the separator char
   * @param maxItems maximum size of returned vector, 0 = unlimited.  If
   * nonzero, substrings past the nth are discarded.
   * @param discardEmptyStrings if true, empty strings (caused by delimiters
   * at the start or end of the string, or adjacent delimiters) will not be
   * included in the result.
   * @param trimEachString is true, each string in the result will be trim()ed
   */
  public static Vector<String> breakAt(String s, char sep,
			       int maxItems,
			       boolean discardEmptyStrings,
			       boolean trimEachString) {
    Vector<String> res = new Vector<String>();
    int len;
    if (s == null || (len = s.length()) == 0) {
      return res;
    }
    if (maxItems <= 0) {
      maxItems = Integer.MAX_VALUE;
    }
    for (int pos = 0; maxItems > 0; maxItems-- ) {
      int end = s.indexOf(sep, pos);
      if (end == -1) {
	if (pos > len) {
	  break;
	}
	end = len;
      }
      if (!discardEmptyStrings || pos != end) {
	String str = s.substring(pos, end);
	if (trimEachString) {
	  str = str.trim();
	}
	if (!discardEmptyStrings || str.length() != 0) {
	  res.addElement(str);
	}
      }
      pos = end + 1;
    }
    return res;
  }

  /** Break a string at a separator string, returning a vector of at most
   * maxItems strings.
   * @param s string containing zero or more occurrences of separator
   * @param sep the separator string
   * @param maxItems maximum size of returned vector, 0 = unlimited.  If
   * nonzero, substrings past the nth are discarded.
   * @param discardEmptyStrings if true, empty strings (caused by delimiters
   * at the start or end of the string, or adjacent delimiters) will not be
   * included in the result.
   * @param trimEachString is true, each string in the result will be trim()ed
   */
  public static Vector<String> breakAt(String s, String sep,
				       int maxItems,
				       boolean discardEmptyStrings,
				       boolean trimEachString) {
    Vector res = new Vector();
    int len;
    if (s == null || (len = s.length()) == 0) {
      return res;
    }
    if (maxItems <= 0) {
      maxItems = Integer.MAX_VALUE;
    }
    for (int pos = 0; maxItems > 0; maxItems-- ) {
      int end = s.indexOf(sep, pos);
      if (end == -1) {
	if (pos > len) {
	  break;
	}
	end = len;
      }
      if (!discardEmptyStrings || pos != end) {
	String str = s.substring(pos, end);
	if (trimEachString) {
	  str = str.trim();
	}
	if (!discardEmptyStrings || str.length() != 0) {
	  res.addElement(str);
	}
      }
      pos = end + sep.length();
    }
    return res;
  }

  /** Break a string at a separator char, returning a vector of at most
   * maxItems strings.
   * @param s string containing zero or more occurrences of separator
   * @param sep the separator char
   * @param maxItems maximum size of returned vector, 0 = unlimited.  If
   * nonzero, substrings past the nth are discarded.
   * @param discardEmptyStrings if true, empty strings (caused by delimiters
   * at the start or end of the string, or adjacent delimiters) will not be
   * included in the result. */
  public static Vector<String> breakAt(String s, char sep,
				       int maxItems,
				       boolean discardEmptyStrings) {
    return breakAt(s, sep, maxItems, discardEmptyStrings, false);
  }

  /** Break a string at a separator char, returning a vector of strings.
   * Include any empty strings in the result.
   * @param s string containing zero or more occurrences of separator
   * @param sep the separator char
   */
  public static Vector<String> breakAt(String s, char sep) {
    return breakAt(s, sep, 0);
  }

  /** Break a string at a separator char, returning a vector of at most
   * maxItems strings.  Include any empty strings in the result.
   * @param s string containing zero or more occurrences of separator
   * @param sep the separator char
   * @param maxItems maximum size of returned vector, 0 = unlimited.  If
   * nonzero, substrings past the nth are discarded.
   */
  public static Vector<String> breakAt(String s, char sep, int maxItems) {
    return breakAt(s, sep, maxItems, false);
  }

  /** Break a string at a separator String, returning a vector of at most
   * maxItems strings.
   * @param s string containing zero or more occurrences of separator
   * @param sep the separator String
   * @param maxItems maximum size of returned vector, 0 = unlimited.  If
   * nonzero, substrings past the nth are discarded.
   * @param discardEmptyStrings if true, empty strings (caused by delimiters
   * at the start or end of the string, or adjacent delimiters) will not be
   * included in the result. */
  public static Vector<String> breakAt(String s, String sep,
					    int maxItems,
					    boolean discardEmptyStrings) {
    return breakAt(s, sep, maxItems, discardEmptyStrings, false);
  }

  /** Break a string at a separator String, returning a vector of strings.
   * Include any empty strings in the result.
   * @param s string containing zero or more occurrences of separator
   * @param sep the separator String
   */
  public static Vector<String> breakAt(String s, String sep) {
    return breakAt(s, sep, 0);
  }

  /** Break a string at a separator String, returning a vector of at most
   * maxItems strings.  Include any empty strings in the result.
   * @param s string containing zero or more occurrences of separator
   * @param sep the separator String
   * @param maxItems maximum size of returned vector, 0 = unlimited.  If
   * nonzero, substrings past the nth are discarded.
   */
  public static Vector<String> breakAt(String s, String sep, int maxItems) {
    return breakAt(s, sep, maxItems, false);
  }

  /**
   * Trim the end off of a string starting at the first occurrence of any
   * of the characters specified.
   *
   * @param str String to trim
   * @param chars String containing the chars to trim at
   * @return str turncated at the first occurrence of any of the chars, or
   * the original string if no occurrence
   */
  public static String truncateAtAny(String str, String chars) {
    if (str == null) {
      return null;
    }
    if (chars != null) {
      for (int jx=0, len = chars.length(); jx < len; jx++) {
	int pos = str.indexOf(chars.charAt(jx));
	if (pos >= 0) {
	  str = str.substring(0, pos);
	}
      }
    }
    return str;
  }

  /**
   * Trim the end off of a string starting at the specified character.
   *
   * @param str String to trim
   * @param chr char to trim at
   * @return str turncated at the first occurrence of char, or
   * the original string if no occurrence
   */
  public static String truncateAt(String str, char chr) {
    if (str == null) {
      return null;
    }
    int pos = str.indexOf(chr);
    if (pos < 0) {
      return str;
    }
    return str.substring(0, pos);
  }

  /** If string is longer than len, replace characters in the middle with
   * an elipsis so that the string is no longer than len
   * @param s the string
   * @param len maximum length of returned string
   */
  public static String elideMiddleToMaxLen(String s, int len) {
    if (s == null || s.length() <= len) {
      return s;
    }
    int split = len / 2;
    return s.substring(0, split) + "..." + s.substring(s.length() - split);
  }

  /** Like indexOf except is case-independent */
  public static int indexOfIgnoreCase(String str, String substr) {
    return indexOfIgnoreCase(str, substr, 0);
  }

  /** Like indexOf except is case-independent */
  public static int indexOfIgnoreCase(String str, String substr,
				      int fromIndex) {
    if (str == null || substr == null) {
      return -1;
    }
    int sublen = substr.length();
    int last = str.length() - sublen;
    for (int ix = fromIndex; ix <= last; ix++) {
      if (str.regionMatches(true, ix, substr, 0, sublen)) {
	return ix;
      }
    }
    return -1;
  }

  /** Like endsWith except is case-independent */
  public static boolean endsWithIgnoreCase(String str, String end) {
    int lend = end.length();
    return str.regionMatches(true, str.length() - lend, end, 0, lend);
  }

  /** Like startsWith except is case-independent */
  public static boolean startsWithIgnoreCase(String str, String start) {
    return str.regionMatches(true, 0, start, 0, start.length());
  }

  /** Return true if the string has any consecutive repeated characters */
  public static boolean hasRepeatedChar(String str) {
    if (str.length() < 2) {
      return false;
    }
    for (int ix = str.length() - 2; ix >= 0; ix--) {
      if (str.charAt(ix) == str.charAt(ix+1)) {
	return true;
      }
    }
    return false;
  }

  /** Remove the substring beginning with the final occurrence of the
   * separator, if any. */
  public static String upToFinal(String str, String sep) {
    int pos = str.lastIndexOf(sep);
    if (pos < 0) {
      return str;
    }
    return str.substring(0, pos);
  }

  /** Iff the string ends with <code>end</code>, remove it. */
  public static String removeTrailing(String str, String end) {
    if (str.endsWith(end)) {
      return str.substring(0, str.length() - end.length());
    }
    return str;
  }

  /* Return the substring following the final dot */
  public static String shortName(Object object) {
    if (object == null) {
      return null;
    }
    String name = object.toString();
    return name.substring(name.lastIndexOf('.')+1);
  }

  /* Return the non-qualified name of the class */
  public static String shortName(Class clazz) {
    String className = clazz.getName();
    return className.substring(className.lastIndexOf('.')+1);
  }

  /* Return the non-qualified name of the method (Class.method) */
  public static String shortName(Method method) {
    return shortName(method.getDeclaringClass()) +
      "." + method.getName();
  }

  public static String sanitizeToIdentifier(String name) {
    StringBuilder sb = new StringBuilder();
    for (int ix = 0; ix < name.length(); ix++) {
      char ch = name.charAt(ix);
      if (Character.isJavaIdentifierPart(ch)) {
	sb.append(ch);
      }
    }
    return sb.toString();
  }

  static Pattern alphanum =
    RegexpUtil.uncheckedCompile("([^a-zA-Z0-9])",
				Perl5Compiler.READ_ONLY_MASK);

  /** Return a copy of the string with all non-alphanumeric chars
   * escaped by backslash.  Useful when embedding an unknown string in
   * a regexp
   */
  public static String escapeNonAlphaNum(String str) {
    Substitution subst = new Perl5Substitution("\\\\$1");
    return Util.substitute(RegexpUtil.getMatcher(), alphanum, subst, str,
			   Util.SUBSTITUTE_ALL);
  }

  private static java.util.regex.Pattern COMBINING_DIACRIT_PAT =
    java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

  /**
   * Normalize string by removing diacritical marks.
   * @param s the string
   * @return the string with diacritical marks removed
   */
  static public String toUnaccented(String s) {
    return COMBINING_DIACRIT_PAT.matcher(Normalizer.normalize(s, Form.NFD)).replaceAll("");
  }

//   /** Accented character table for use by {@link #toUnaccentedFast(String).
//    * See comments there. */
//   private static final String[][] ACCENTTABLE = {
//     {"\u00c0","A"}, // À, A with grave
//     {"\u00c1","A"}, // Á, A with acute
//     {"\u00c2","A"}, // Â, A with circumflex
//     {"\u00c3","A"}, // Â, A with tilde
//     {"\u00c4","A"}, // Ä, A with diaeresis
//     {"\u00c5","A"}, // Å, A with ring above
//     {"\u00c6","AE"}, // Æ, AE
//     {"\u00c7","C"}, // Ç, C with cedilla
//     {"\u00c8","E"}, // È, E with grave
//     {"\u00c9","E"}, // É, E with acute
//     {"\u00ca","E"}, // Ê, E with circumflex
//     {"\u00cb","E"}, // Ë, E with diaeresis
//     {"\u00cc","I"}, // Ì, I with grave
//     {"\u00cd","I"}, // Í, I with acute
//     {"\u00ce","I"}, // Î, I with circumflex
//     {"\u00cf","I"}, // Ï, I with diaeresis
//     {"\u00d1","N"}, // Ñ, N with tilde
//     {"\u00d2","O"}, // Ò, O with grave
//     {"\u00d3","O"}, // Ó, O with acute
//     {"\u00d4","O"}, // Ô, O with circumflex
//     {"\u00d5","O"}, // Õ, O with tilde
//     {"\u00d6","O"}, // Ö, O with diaeresis
//     {"\u00d8","O"}, // Ø, O with a stroke
//     {"\u00d9","U"}, // Ù, U with grave
//     {"\u00da","U"}, // Ú, U with acute
//     {"\u00db","U"}, // Û, U with circumflex
//     {"\u00dc","U"}, // Ü, U with diaeresis
//     {"\u00dd","Y"}, // Ý, Y with acute
//     {"\u00e0","a"}, // à, a with grave
//     {"\u00e1","a"}, // á, a with acute
//     {"\u00e2","a"}, // â, a with circumflex
//     {"\u00e3","a"}, // ã, a with tilde
//     {"\u00e4","a"}, // ä, a with diaeresis
//     {"\u00e5","a"}, // å, a with ring above
//     {"\u00e6","ae"}, // æ, ae
//     {"\u00e7","c"}, // ç, c with cedilla
//     {"\u00e8","e"}, // è, e with grave
//     {"\u00e9","e"}, // é, e with acute
//     {"\u00ea","e"}, // ê, e with circumflex
//     {"\u00eb","e"}, // ë, e with diaeresis
//     {"\u00ec","i"}, // ì, i with grave
//     {"\u00ed","i"}, // í, i with acute
//     {"\u00ee","i"}, // î, i with circumflex
//     {"\u00ef","i"}, // ï, i with diaeresis
//     {"\u00f1","n"}, // ñ, n with tilde
//     {"\u00f2","o"}, // ò, o with grave
//     {"\u00f3","o"}, // ó, o with acute
//     {"\u00f4","o"}, // ô, o with circumflex
//     {"\u00f5","o"}, // õ, o with tilde
//     {"\u00f6","o"}, // ö, o with diaeresis
//     {"\u00f8","o"}, // ø, o with stroke
//     {"\u00f9","u"}, // ù, u with grave
//     {"\u00fa","u"}, // ú, u with acute
//     {"\u00fb","u"}, // û, u with circumflex
//     {"\u00fc","u"}, // ü, u with diaeresis
//     {"\u00fd","y"}, // ý, y with acute
//     {"\u00ff","y"}, // ÿ, y with diaeresis
//   };

//   private static char[] AC_CHAR = new char[ACCENTTABLE.length];
//   private static String[] AC_REP = new String[ACCENTTABLE.length];

//   static {
//     for (int ix = 0; ix < ACCENTTABLE.length; ix++) {
//       AC_CHAR[ix] = ACCENTTABLE[ix][0].charAt(0);
//       AC_REP[ix] = ACCENTTABLE[ix][1];
//     }
//   }    

//   /** Alternate implementation of {@link @toUnaccented(String)}.  Can be
//    * several times faster (depending on string length) but only handles
//    * accented characters that are in its table. */

//   public static String toUnaccentedFast(String s) {
//     boolean modified = false;
//     int slen = s.length();
//     StringBuilder sb = null;
//     outer:
//     for (int ix = 0; ix < slen; ix++) {
//       char ch = s.charAt(ix);
//       for (int jx = 0; jx < AC_CHAR.length; jx++) {
// 	if (AC_CHAR[jx] == ch) {
// 	  if (!modified) {
// 	    sb = new StringBuilder(slen);
// 	    sb.append(s, 0, ix);
// 	    modified = true;
// 	  }
// 	  sb.append(AC_REP[jx]);
// 	  continue outer;
// 	}
//       }
//       if (modified) {
// 	sb.append(ch);
//       }
//     }
//     if (modified) {
//       return sb.toString();
//     } else {
//       return s;
//     }
//   }

//   static String toUnaccentedFast0(String s) {
//     for (int iy = 0; iy < ACCENTTABLE.length; iy++) {
//       s = StringUtil.replaceString(s, ACCENTTABLE[iy][0], ACCENTTABLE[iy][1]);
//     }
//     return s;
//   }

  /** Escape values (and keys) to be included in a comma-separated string
   * of key=value.  Comma, equals and backslash are escaped with
   * backslash */
  public static String ckvEscape(String s) {
    if (s.indexOf('\\') < 0 && s.indexOf(',') < 0 && s.indexOf('=') < 0) {
      return s;
    }
    int len = s.length();
    StringBuilder sb = new StringBuilder(len + 8);
    for (int ix = 0; ix < len; ix++) {
      char c = s.charAt(ix);
      switch(c) {
	// Special characters
      case '\\':
	sb.append("\\\\");
	break;
      case ',':
	sb.append("\\,");
	break;
      case '=':
	sb.append("\\=");
	break;
      default:
	sb.append(c);
	break;
      }
    }
    return sb.toString();
  }

  /** Encode a string to be included in a CSV.  Values containing comma,
   * space or quote are quoted, quotes are doubled */
  public static String csvEncode(String s) {
    if (s.indexOf('"') >= 0) {
      int len = s.length();
      StringBuilder sb = new StringBuilder(len + 5);
      sb.append("\"");
      for (int ix = 0; ix < len; ix++) {
	char c = s.charAt(ix);
	switch (c) {
	case '\"':
	  sb.append("\"\"");
	  break;
	default:
	  sb.append(c);
	  break;
	}
      }
      sb.append("\"");
      return sb.toString();
    }
    if (s.indexOf(' ') >= 0 || s.indexOf(',') >= 0) {
      StringBuilder sb = new StringBuilder(s.length() + 2);
      sb.append("\"");
      sb.append(s);
      sb.append("\"");
      return sb.toString();
    }
    return s;
  }

    
  /**
   * Returns the number of instances of a particular substring in a string.
   * This ignores overlap, starting from the left, so 'xxxxxy' would have
   * 2 instances of 'xx', not 4.  Empty string as a substring returns 0.
   */
  public static int countOccurences(String str, String subStr) {
    int len = subStr.length();
    if (len == 0) {
      return 0;
    }
    int pos = 0;
    int count = 0;
    while ((pos = str.indexOf(subStr, pos)) >= 0) {
      count++;
      pos += len;
    }
    return count;
  }

  /**
   * Get the text between two other chunks of text.
   * @param line a String containing some text sandwiched between two known pieces of text 
   * @param beginFlag the String coming before the required text
   * @param endFlag the String coming after the required text
   * @return the extracted text, or null if the string did not fit the specified format
   * @author Neil Mayo
   */
  public static String getTextBetween(String line, String beginFlag, String endFlag){
    int tBegin = StringUtil.indexOfIgnoreCase(line, beginFlag);
    // Get the first position of the endFlag appearing after the beginFlag
    tBegin += beginFlag.length();
    int tEnd = StringUtil.indexOfIgnoreCase(line, endFlag, tBegin);
    // Check that the flags were found
    if(tBegin < 0 || tEnd<0 || tBegin>tEnd) {
      return null;
    }
    return line.substring(tBegin, tEnd);
  }


  /** Return a reader that transforms platform newline sequences to standard
   * newline characters. 
   * @param r a Reader
   * @return a filtered reader that transforms platform newline sequences to standard
   * newline characters. 
   */
  public static Reader getLineReader(final Reader r) {
    return new Reader() {
      boolean saw_CR = false;
      final char[] cb = new char[1];
      public int read(char cbuf[], int off, int len) throws IOException {
	int i;
	int n = 0;
	for (i = 0; i < len; i++) {
	  if ((n = r.read(cb, 0, 1)) <= 0) {
	    break;
	  }
	  if (saw_CR) {
	    saw_CR = false;
	    if (cb[0] == '\n') {
	      if (r.read(cb, 0, 1) <= 0) {
		break;
	      }
	    }
	  }
	  if (cb[0] == '\r') {
	    saw_CR = true;
	    cb[0] = '\n';
	  }
	  cbuf[off+i] = cb[0];
	}
	return (i == 0) ? n : i;
      }
      public void close() throws IOException {
	r.close();
      }
    };
  }

  /** Return a reader that transforms platform newline sequences to standard
   * newline characters. 
   * @param in an input stream
   * @return a filtered reader that transforms platform newline sequences to standard
   * newline characters. 
   */
  public static Reader getLineReader(InputStream in) {
    return getLineReader(in, null);
  }

  /** Return a reader that transforms platform newline sequences to standard
   * newline characters. 
   * @param in an input stream
   * @param encoding the character encoding
   * @return a filtered reader that transforms platform newline sequences to standard
   * newline characters. 
   */
  public static Reader getLineReader(InputStream in, String encoding) {
    return getLineReader(StreamUtil.getReader(in, encoding));
  }

  /** Return a reader that removes backslash-newline sequences
   * (line-continuation)
   * @param r a Reader
   * @return a filtered reader that removes line-continuation sequences
   */
  public static Reader getLineContinuationReader(final Reader r) {
    return new Reader() {
      boolean saw_bslash = false;
      final char[] cb = new char[1];
      int lastch = -1;
      public int read(char cbuf[], int off, int len) throws IOException {
	int i;
	int n = 0;
	int endoff = off + len;
	while (off < endoff) {
	  // if have a character waiting, emit it
	  if (lastch >= 0) {
	    cbuf[off++] = (char)lastch;
	    lastch = -1;
	  } else {
	    if ((n = r.read(cb, 0, 1)) <= 0) {
	      // end of input.  do we have a hanging backslash?
	      if (saw_bslash) {
		cbuf[off++] = '\\';
		saw_bslash = false;
	      }
	      break;
	    }
	    switch (cb[0]) {
	    case '\\':
	      if (saw_bslash) {
		// if already seen a backslash, output that one
		cbuf[off++] = '\\';
	      } else {
		saw_bslash = true;
	      }
	      break;
	    case '\n':
	      if (saw_bslash) {
		saw_bslash = false;
	      } else {
		cbuf[off++] = cb[0];
	      }
	      break;
	    default:
	      if (saw_bslash) {
		cbuf[off++] = '\\';
		saw_bslash = false;
		lastch = cb[0];
	      } else {
		cbuf[off++] = cb[0];
	      }
	      break;
	    }
	  }
	}
	int nread = len - (endoff - off);
	return nread == 0 ? -1 : nread;
      }
      public void close() throws IOException {
	r.close();
      }
    };
  }

  /** Reads a line from a BufferedReader, interpreting backslash-newline as
   * line-continuation. */
  public static String readLineWithContinuation(BufferedReader rdr)
      throws IOException {
    StringBuilder sb = null;
    while (true) {
      String s = rdr.readLine();
      if (s == null) {
	if (sb == null || sb.length() == 0) {
	  return null;
	} else {
	  return sb.toString();
	}
      }
      if (s.endsWith("\\")) {
	if (sb == null) {
	  sb = new StringBuilder(120);
	}
	sb.append(s, 0, s.length() - 1);
      } else if (sb == null || sb.length() == 0) {
	return s;
      } else {
	sb.append(s);
	return sb.toString();
      }
    }
  }

  /** Reads line from a BuffereReader into a StringBuilder, interpreting
   * backslash-newline as line-continuation, until either end-of-stream or
   * maxSize chars read.  May read one more line beyond maxSize.  */
  public static boolean readLinesWithContinuation(BufferedReader rdr,
						  StringBuilder sb,
						  int maxSize)
      throws IOException {
    while (true) {
      String s = rdr.readLine();
      if (s == null) {
	return sb.length() != 0;
      }
      if (s.endsWith("\\")) {
	sb.append(s, 0, s.length() - 1);
	continue;
      }
      sb.append(s, 0, s.length());
      sb.append("\n");
      if (sb.length() >= maxSize) {
	return true;
      }
    }
  }

  /** Return a string with lines from a reader, separated by a newline
   * character.  The reader is not closed.  Throw if more than maxSize
   * chars. Reader is wrapped with a reader returned by {@link
   * #getLineReader(Reader) before processing.
   */
  public static String fromReader(Reader r, int maxSize) throws IOException {
    r = getLineReader(r);
    char[] buf = new char[1000];
    StringBuilder sb = new StringBuilder(1000);
    int len;
    while ((len = r.read(buf)) >= 0) {
      sb.append(buf, 0, len);
      if (maxSize > 0 && sb.length() > maxSize) {
	throw new FileTooLargeException();
      }
    }
    return sb.toString();
  }

  /** Read chars from a Reader into a StringBuilder up to maxSize.
   * @param r the Reader to read from
   * @param sb the StringBuilder to fill
   * @param maxChars maximum number of chars to read
   * @return true if anything was read (false if reader was already at
   * eof) */
  public static boolean fillFromReader(Reader r, StringBuilder sb, int maxChars)
      throws IOException {
    char[] buf = new char[1000];
    int tot = 0;
    int len;
    while (tot < maxChars
	   && (len = r.read(buf, 0,
			    Math.min(buf.length, maxChars - tot))) >= 0) {
      sb.append(buf, 0, len);
      tot += len;
    }
    return tot != 0;
  }

  /** Return a string with lines from a reader, separated by a newline character.
   * Reader is wrapped with a reader returned by {@link #getLineReader(Reader) 
   * before processing. */
  public static String fromReader(Reader r) throws IOException {
    return fromReader(r, -1);
  }

  /** Return a string with lines from an InputStream separated by a newline
   * character using the default encoding*/
  public static String fromInputStream(InputStream in) throws IOException {
    // use our default encoding rather than system default
    return fromReader(new InputStreamReader(in, Constants.DEFAULT_ENCODING));
  }

  /** Return a string with lines from an InputStream separated by a newline
   * character using the default encoding.  The InputStream is not closed.
   * Throw if more than maxSize chars  */
  public static String fromInputStream(InputStream in, int maxSize)
      throws IOException {
    // use our default encoding rather than system default
    return fromReader(new InputStreamReader(in, Constants.DEFAULT_ENCODING),
		      maxSize);
  }

  /** Return a string with lines from the file path separated by a newline character */
  public static String fromFile(String path) throws IOException {
    return fromFile(new File(path));
  }

  /** Return a string with lines from the file separated by a newline character */
  public static String fromFile(File file) throws IOException {
    Reader rdr = new FileReader(file);
    try {
      return fromReader(rdr);
    } finally {
      IOUtil.safeClose(rdr);
    }
  }

  /** Write a string to a File */
  public static void toFile(File file, String s) throws IOException {
    Writer w = new BufferedWriter(new FileWriter(file));
    try {
      StringUtil.toWriter(w, s);
    } finally {
      IOUtil.safeClose(w);
    }
  }

  /* Write the string to the OutputStream */
  public static void toOutputStream(OutputStream out, String s)
      throws IOException {
    toWriter(new BufferedWriter(new OutputStreamWriter(out)),s);
  }

  /* Write the string to the Writer */
  public static void toWriter(Writer w, String s)
      throws IOException {
    w.write(s);
    w.flush();
  }

  /**
   * Test whether a string is null or the empty string
   * @param s the string
   * @return true if s is null or the empty string
   */
  public static boolean isNullString(String s) {
    return s == null || s.length() == 0;
  }

  /**
   * Same as str.compareTo(str), except null is the lowest value.
   */
  public static int compareToNullLow(String str1, String str2) {
    if (str1 == null) {
      return (str2 == null) ? 0 : -1;
    }
    if (str2 == null) {
      return 1;
    }
    return str1.compareTo(str2);
  }

  /**
   * Same as str.compareTo(str), except null is the highest value.
   */
  public static int compareToNullHigh(String str1, String str2) {
    if (str1 == null) {
      return (str2 == null) ? 0 : 1;
    }
    if (str2 == null) {
      return -1;
    }
    return str1.compareTo(str2);
  }

  /**
   * Compare two strings for equality or both null.
   * @param s1 string 1
   * @param s2 string 2
   * @return true if strings are equal or both null
   */
  public static boolean equalStrings(String s1, String s2) {
    if (s1 == null) {
      return s2 == null;
    } else {
      return s1.equals(s2);
    }
  }

  /**
   * Compare two strings for case-independent equality or both null.
   * @param s1 string 1
   * @param s2 string 2
   * @return true if strings are equal or both null
   */
  public static boolean equalStringsIgnoreCase(String s1, String s2) {
    if (s1 == null) {
      return s2 == null;
    } else {
      return s1.equalsIgnoreCase(s2);
    }
  }

  /** Sort a set of strings case-independently  */
   public static Set<String> caseIndependentSortedSet(Collection<String> coll) {
     Set<String> res = new TreeSet(new CaseIndependentComparator());
     res.addAll(coll);
     return res;
   }

  public static class CaseIndependentComparator implements Comparator<String> {
    public int compare(String s1, String s2) {
      // Don't allow null to cause NPE
      if (s1 == null) {
	return (s2 == null ? 0 : -1);
      } else if (s2 == null) {
	return 1;
      }
      return s1.compareToIgnoreCase(s2);
    }
  }


  /** Like System.arrayCopy, for characters within one StringBuilder.
   * @param sb the buffer
   * @param srcPos chars copied starting from here
   * @param destPos chars copied starting to here
   * @param len number of chars copied */
  public static void copyChars(StringBuilder sb,
			       int srcPos, int destPos, int len) {
    if (srcPos > destPos) {
      while (--len >= 0) {
	sb.setCharAt(destPos++, sb.charAt(srcPos++));
      }
    } else if (srcPos < destPos) {
      while (--len >= 0) {
	sb.setCharAt(destPos + len, sb.charAt(srcPos + len));
      }
    }
  }

  private static long gensymCtr = 0;

  /**
   * Generate a unique string.
   * @param base the initial substring
   * @return a string consisting of the supplied initial substring and a
   * unique counter value.
   */
  public static String gensym(String base) {
    return base + (gensymCtr++);
  }

  /** Return a string of n spaces */
  public static String tab(int n) {
    return StringUtils.repeat(" ", n);
  }      

  /**
   * Trim a hostname, removing "www." from the front, if present, and the
   * TLD from the end.  If this would result in an empty string, the entire
   * name is returned.
   * @param hostname a hostname string
   * @return the trimmed hostname
   */
  public static String trimHostName(String hostname) {
    if (hostname == null) return null;
    int start = 0;
    if (hostname.regionMatches(true, 0, "www.", 0, 4)) {
      start = 4;
    }
    int end = hostname.lastIndexOf('.');
    if (end <= start) {
      // if trimming www left nothing but TLD, return whole name
      return hostname;
    }
    return hostname.substring(start, end);
  }

  /** Parse a string as a time interval.  An interval is specified as an
   * integer with an optional suffix.  No suffix means milliseconds, s, m,
   * h, d, w indicates seconds, minutes, hours, days and weeks
   * respectively.  As a special case, "ms" means milliseconds.
   * @param str the interval string
   * @return interval in milliseconds
   */
  // tk - extend to accept combinations: xxHyyMzzS, etc.
  public static long parseTimeInterval(String str) {
    try {
      int len = str.length();
      char suffix = str.charAt(len - 1);
      String numstr;
      long mult = 1;
      if (Character.isDigit(suffix)) {
	numstr = str;
      } else {
	if (StringUtil.endsWithIgnoreCase(str, "ms")) {
	  numstr = str.substring(0, len - 2);
	} else {
	  numstr = str.substring(0, len - 1);
	  switch (Character.toUpperCase(suffix)) {
	  case 'S': mult = Constants.SECOND; break;
	  case 'M': mult = Constants.MINUTE; break;
	  case 'H': mult = Constants.HOUR; break;
	  case 'D': mult = Constants.DAY; break;
	  case 'W': mult = Constants.WEEK; break;
	  case 'Y': mult = Constants.YEAR; break;
	  default:
	    throw new NumberFormatException("Illegal time interval suffix");
	  }
	}
      }
      return Long.parseLong(numstr) * mult;
    } catch (IndexOutOfBoundsException e) {
      throw new NumberFormatException("empty string");
    }
  }

  private static Pattern sizePat =
    RegexpUtil.uncheckedCompile("^([0-9.]+)\\s*([a-zA-Z]*)",
				Perl5Compiler.READ_ONLY_MASK);

  private static String suffixes[] = {"b", "KB", "MB", "GB", "TB", "PB"};

  /** Parse a string as a size in bytes, with a optional suffix.  No suffix
   * means bytes, kb, mb, gb, tb indicate kilo-, mega-, giga, tera-bytes
   * respectively.
   * @param str the size string
   * @return size in bytes
   */
  public static long parseSize(String str) {
    str = str.trim();
    Perl5Matcher matcher = RegexpUtil.getMatcher();
    if (!matcher.contains(str, sizePat)) {
      throw new NumberFormatException("Illegal size syntax: " + str);
    }
    MatchResult matchResult = matcher.getMatch();
    String num = matchResult.group(1);
    String suffix = matchResult.group(2);
    try {
      float f = Float.parseFloat(num);
      long mult = 1;
      if (StringUtil.isNullString(suffix)) {
	return (long)f;
      }
      for (int ix = 0; ix < suffixes.length; ix++) {
	if (suffix.equalsIgnoreCase(suffixes[ix])) {
	  return (long)(f * mult);
	}
	mult *= 1024;
      }
      throw new NumberFormatException("Illegal size suffix: " + str);
    } catch (NumberFormatException ex) {
      throw new NumberFormatException("Illegal size syntax: " + str);
    }
  }

  /** Trim leading and trailing blank lines from a block of text */
  public static String trimBlankLines(String txt) {
    StringBuilder buf = new StringBuilder(txt);
    while (buf.length()>0 && buf.charAt(0) == '\n') {
      buf.deleteCharAt(0);
    }
    while (buf.length()>0 && buf.charAt(buf.length() - 1) == '\n') {
      buf.deleteCharAt(buf.length() - 1);
    }
    return buf.toString();
  }

  private static Pattern nlEol =
    RegexpUtil.uncheckedCompile("([\n\r][\n\t ]*)",
				Perl5Compiler.MULTILINE_MASK);


  /** Trim EOLs and leading whitespace from a block of text */
  public static String trimNewlinesAndLeadingWhitespace(String str) {
    if (str.indexOf("\n") == -1 && str.indexOf("\r") == -1) {
      return str;
    }
    Substitution subst = new Perl5Substitution("");
    return Util.substitute(RegexpUtil.getMatcher(), nlEol, subst, str,
			   Util.SUBSTITUTE_ALL);
  }


  // Unit Descriptor
  private static class UD {
    String str;				// suffix string
    long millis;			// milliseconds in unit
    int threshold;			// min units to output
    String stop;			// last unit to output if this matched

    UD(String str, long millis) {
      this(str, millis, 1);
    }

    UD(String str, long millis, int threshold) {
      this(str, millis, threshold, null);
    }

    UD(String str, long millis, int threshold, String stop) {
      this.str = str;
      this.millis = millis;
      this.threshold = threshold;
      this.stop = stop;
    }
  }

  static UD units[] = {
    new UD("w", Constants.WEEK, 3, "h"),
    new UD("d", Constants.DAY, 1, "m"),
    new UD("h", Constants.HOUR),
    new UD("m", Constants.MINUTE),
    new UD("s", Constants.SECOND, 0),
  };

  public static String protectedDivide(long numerator, long denominator) {
    return protectedDivide(numerator, denominator, "inf");
  }

  public static String protectedDivide(long numerator, long denominator,
				       String infStr) {
    if (denominator == 0) {
      return infStr;
    }
    long val = numerator / denominator;
    return String.valueOf(val);
  }

  /** Generate a string representing the time interval.
   * @param millis the time interval in milliseconds
   * @return a string in the form dDhHmMsS
   */
  public static String timeIntervalToString(long millis) {
    StringBuilder sb = new StringBuilder();

    if (millis < 0) {
      sb.append("-");
      millis = -millis;
    }
    return posTimeIntervalToString(millis, sb);
  }

  private static String posTimeIntervalToString(long millis, StringBuilder sb) {
    if (millis < 10 * Constants.SECOND) {
      sb.append(millis);
      sb.append("ms");
    } else {
      boolean force = false;
      String stop = null;
      for (int ix = 0; ix < units.length; ix++) {
	UD iu = units[ix];
	long n = millis / iu.millis;
	if (force || n >= iu.threshold) {
	  millis %= iu.millis;
	  sb.append(n);
	  sb.append(iu.str);
	  force = true;
	  if (stop == null) {
	    if (iu.stop != null) {
	      stop = iu.stop;
	    }
	  } else {
	    if (stop.equals(iu.str)) {
	      break;
	    }
	  }
	}
      }
    }
    return sb.toString();
  }

  /** Generate a more verbose string representing the time interval.
   * @param millis the time interval in milliseconds
   * @return a string in the form "<d> days, <h> hours, <m> minutes, <s>
   * seconds"
   */
  public static String timeIntervalToLongString(long millis) {
    StringBuilder sb = new StringBuilder();
    long temp = 0;
    if (millis < 0) {
      sb.append("-");
      millis = -millis;
    }
    if (millis >= Constants.SECOND) {
      temp = millis / Constants.DAY;
      if (temp > 0) {
	sb.append(numberOfUnits(temp, "day"));
	millis -= temp * Constants.DAY;
	if (millis >= Constants.MINUTE) {
	  sb.append(", ");
	}
      }
      temp = millis / Constants.HOUR;
      if (temp > 0) {
	sb.append(numberOfUnits(temp, "hour"));
	millis -= temp * Constants.HOUR;
	if (millis >= Constants.MINUTE) {
	  sb.append(", ");
	}
      }
      temp = millis / Constants.MINUTE;
      if (temp > 0) {
	sb.append(numberOfUnits(temp, "minute"));
	millis -= temp * Constants.MINUTE;

	if(millis >= Constants.SECOND) {
	  sb.append(", ");
	}
      }
      temp = millis / Constants.SECOND;
      if (temp > 0) {
	sb.append(numberOfUnits(temp, "second"));
      }
      return sb.toString();
    }
    else {
      return "0 seconds";
    }
  }



  private static final NumberFormat fmt_1dec = new DecimalFormat("0.0");
  private static final NumberFormat fmt_0dec = new DecimalFormat("0");

  static final String[] byteSuffixes = {"KB", "MB", "GB", "TB", "PB"};

  public static String sizeToString(long size) {
    if (size < 1024) {
      return size + "B";
    }
    return sizeKBToString(size / 1024);
  }

  public static String sizeKBToString(long size) {
    double base = 1024.0;
    double x = (double)size;

    int len = byteSuffixes.length;
    for (int ix = 0; ix < len; ix++) {
      if (x < base || ix == len-1) {
	StringBuilder sb = new StringBuilder();
	if (x < 10.0) {
	  sb.append(fmt_1dec.format(x));
	} else {
	  sb.append(fmt_0dec.format(x));
	}
	sb.append(byteSuffixes[ix]);
	return sb.toString();
      }
      x = x / base;
    }
    return ""+size;
  }

  /** Remove the first line of the stack trace, iff it duplicates the end
   * of the exception message */
  public static String trimStackTrace(String msg, String trace) {
    int pos = trace.indexOf("\n");
    if (pos > 0) {
      String l1 = trace.substring(0, pos);
      if (msg.endsWith(l1)) {
	return trace.substring(pos + 1);
      }
    }
    return trace;
  }

  /** Translate an exception's stack trace to a string.
   */
  public static String stackTraceString(Throwable th) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    th.printStackTrace(pw);
    return sw.toString();
  }

  /** Convert the first character and every character that follows a space
   *   to uppercase.
   */
  public static String titleCase(String txt) {
    return titleCase(txt, ' ');
  }

  /** Convert the first character and every character that follows the
   * separator char to uppercase.
   */
  public static String titleCase(String txt, char separator) {
    int len = txt.length();
    if (len == 0) {
      return "";
    }
    StringBuilder buf = new StringBuilder(txt);
    buf.setCharAt(0,Character.toUpperCase(buf.charAt(0)));
    for (int i=1; i<len; i++) {
      if (buf.charAt(i-1)==separator) {
	buf.setCharAt(i,Character.toUpperCase(buf.charAt(i)));
      }
    }
    return buf.toString();
  }

  public static class FileTooLargeException extends IOException {
    public FileTooLargeException() {
      super();
    }

    public FileTooLargeException(String message) {
      super(message);
    }
  }

  /**
   * Returns the index of the nth occurrence of searchStr in sourceStr or -1
   * if there aren't n instances of searchStr in sourceStr
   */
  public static int nthIndexOf(int n, String sourceStr, String searchStr) {
    if (n <= 0) {
      return -1;
    }

    int idx = -1;
    do {
      idx = sourceStr.indexOf(searchStr, idx+1);
    } while (--n > 0 && idx >= 0);

    return idx;
  }


  /**
   * Scans through the reader looking for the String str; case sensitive
   * @param reader Reader to search; it will be at least partially consumed
   * @return true if the string is found, false if the end of reader is
   * reached without finding the string
   */
  public static boolean containsString(Reader reader, String str)
      throws IOException {
    return StringUtil.containsString(reader, str, false);
  }

  public static boolean containsString(Reader reader, String str,
				       int buffSize)
      throws IOException {
    return containsString(reader, str, false, buffSize);
  }

  public static boolean containsString(Reader reader, String str,
				       boolean ignoreCase)
      throws IOException {
    return containsString(reader, str, ignoreCase, 256);
  }

  /**
   * Scans through the reader looking for the String str
   * @param reader Reader to search; it will be at least partially consumed
   * @param ignoreCase whether to ignore case or not
   * @return true if the string is found, false if the end of reader is
   * reached without finding the string
   */
  public static boolean containsString(Reader reader, String str,
				       boolean ignoreCase, int buffSize)
      throws IOException {
    if (reader == null) {
      throw new NullPointerException("Called with a null reader");
    } else if (str == null) {
      throw new NullPointerException("Called with a null string");
    } else if (str.length() == 0) {
      throw new IllegalArgumentException("Called with a blank String");
    } else if (buffSize <= 0) {
      throw new IllegalArgumentException("Called with a buffSize < 0");
    }

    if (ignoreCase) {
      str = str.toLowerCase();
    }

    char[] buff = new char[buffSize];
    BoyerMoore bm = new BoyerMoore(ignoreCase);
    bm.compile(str);

    int bcount;

    int numPartialMatch = 0;

    while ((bcount = StreamUtil.readChars(reader, buff, buff.length)) > 0) {
      if (numPartialMatch > 0 && bcount >= (str.length() - numPartialMatch)) {
	//we previously matched this many chars at the end of the last buff
	if (log.isDebug3()) {
	  log.debug3("Found a partial match before in last buffer: "+
		     str.substring(numPartialMatch)+"; looking for the rest");
	}
	if (startsWith(buff, str.substring(numPartialMatch), ignoreCase)) {
	  if (log.isDebug3()) {log.debug3("Found the second half of a partial match");}
	  return true;
	}
      }
      if (bm.search(buff, 0, bcount) >= 0) {
	if (log.isDebug3()) {log.debug3("Found a full match in one buffer");}
	return true;
      } else {
	numPartialMatch = bm.partialMatch();
	if (log.isDebug3() && numPartialMatch != 0) {
	  log.debug3("Found a partial match of "+numPartialMatch);
	}
      }
    }
    return false;
  }

  /**
   *
   * @return true if the first str.length() chars in buffer match str
   */
  private static boolean startsWith(char[]buffer, String str,
				    boolean ignoreCase) {
    for (int ix=0; ix<(str.length()); ix++) {
      if (Character.toLowerCase(str.charAt(ix))
	  != Character.toLowerCase(buffer[ix])) {
	if (log.isDebug3()) {
	  log.debug3(str.charAt(ix)+" didn't match "+ buffer[ix]);
	}
	return false;
      }
    }
    return true;
  }

  /** Return a string like "0 units", "1 unit", "n units"
   * @param number the number of whatever units
   * @param unit Single form of unit, plural formed by adding "s"
   */
  public static String numberOfUnits(long number, String unit) {
    return numberOfUnits(number, unit, unit + "s");
  }

  /** Return a string like "0 units", "1 unit", "n units"
   * @param number the number of whatever units
   * @param unit Single form of unit
   * @param pluralUnit plural form of unit
   */
  public static String numberOfUnits(long number, String unit,
				     String pluralUnit) {
    if (number == 1) {
      return number + " " + unit;
    } else {
      return number + " " + pluralUnit;
    }
  }

  public static boolean equalsIgnoreCase(char kar1, char kar2) {
    return (Character.toLowerCase(kar1) == Character.toLowerCase(kar2));
  }


}
