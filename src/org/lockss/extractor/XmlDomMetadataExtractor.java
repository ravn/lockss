/*
 * $Id: XmlDomMetadataExtractor.java,v 1.2.2.2 2012-03-06 01:10:20 pgust Exp $
 */

/*

Copyright (c) 2000-2012 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.extractor;

import java.io.*;
import java.util.*;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.lockss.plugin.CachedUrl;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This class extracts values from the XML specified by a CachedUrl
 * as the raw values of a ArticleMetadata object. This class uses the 
 * DocumentBuilderFactor to construct a DocumentBuilder to parse the input 
 * stream into a Document. Validation and name spaces are disabled and 
 * DTD files are not consulted.
 * <p>
 * The metadata to extract is specified by XPath expressions. By default, 
 * the expression evaluates to a node-set whose values are the text content 
 * of the specified nodes. Optional NodeValue objects can be provided with 
 * the XPath expressions to specify the return types of the xpressions and 
 * how to format the resulting values as strings.
 * <p>
 * See the following reference on XPath syntax: 
 * http://www.w3schools.com/xpath/xpath_syntax.asp
 * 
 * @author Philip Gust
 */
public class XmlDomMetadataExtractor extends SimpleFileMetadataExtractor {
  static Logger log = Logger.getLogger("XmlMetadataExtractor");
  
  /** The xpath map to use for extracting */
  final protected XPathExpression[] xpathExprs;
  final protected String[] xpathKeys;
  final protected XPathValue[] nodeValues;

  /**
   * This class defines a function to extract text from an XPath value
   * according to the specified type: as a nodeset if type is 
   * XPathConstants.NODESET, as a String if type is XPathConstants.STRING, 
   * as a Boolean if type is XPathConstants.BOOLEAN, or as a Number 
   * if type is XPathConstants.NUMBER. 
   * 
   * @author Philip Gust
   */
  static abstract public class XPathValue {
    /** Override this method to specify a different type */
    public abstract QName getType();
    /** Override this method to handle nodes differently */
    public String getValue(Node node) {
      return (node == null) ? null : node.getTextContent();
    }
    /** Override this method to handle strings differently */
    public String getValue(String s) {
      return s;
    }
    /** Override this method to handle booleans differently */
    public String getValue(Boolean b) {
      return (b == null) ? null : b.toString();
    }
    /** Override this method to handle numbers differently */
    public String getValue(Number n) {
      return (n == null) ? null : n.toString();
    }
  }
  
  /** XPathValue for a nodeset */
  static public class NodeValue extends XPathValue {
    @Override
    public QName getType() {
      return XPathConstants.NODESET;
    }
  }
  static final public XPathValue NODE_VALUE = new NodeValue();
  
  /** XPathValue value for text */
  static public class TextValue extends XPathValue {
    @Override
    public QName getType() {
      return XPathConstants.STRING;
    }
  }
  static final public XPathValue TEXT_VALUE = new TextValue();

  /** XPathValue for a number */
  static public class NumberValue extends XPathValue {
    @Override
    public QName getType() {
      return XPathConstants.NUMBER;
    }
  }
  static final public XPathValue NUMBER_VALUE = new NumberValue();

  /** XPathValue for a boolean */
  static public class BooleanValue extends XPathValue {
    @Override
    public QName getType() {
      return XPathConstants.BOOLEAN;
    }
  }
  static final public XPathValue BOOLEAN_VALUE = new BooleanValue();

  
  /**
   * Create an extractor based on the required size.
   * 
   * @param size the required size.
   */
  protected XmlDomMetadataExtractor(int size) {
    xpathExprs = new XPathExpression[size];
    xpathKeys = new String[size];
    nodeValues = new XPathValue[size];
  }
  
  /**
   * Create an extractor that will extract the textContent of the 
   * nodes specified by the XPath expressions.
   * 
   * @param xpaths the collection of XPath expressions whose 
   * text content to extract.
   */
  public XmlDomMetadataExtractor(Collection<String> xpaths) 
      throws XPathExpressionException {
    this(xpaths.size());
    
    int i = 0;
    XPath xpath = XPathFactory.newInstance().newXPath();
    for (String xp : xpaths) {
      xpathKeys[i] = xp;
      xpathExprs[i] = xpath.compile(xp);
      nodeValues[i] = TEXT_VALUE;
      i++;
    }
  }

  /**
   * Create an extractor that will extract the textContent of the 
   * nodes specified by the XPath expressions by applying the 
   * corresponding NodeValues.
   * 
   * @param xpathMap the map of XPath expressions whose value to extract 
   *  by applying the corresponding NodeValues.
   */
  public XmlDomMetadataExtractor(Map<String, XPathValue> xpathMap)
      throws XPathExpressionException {
    this(xpathMap.size());
    
    int i = 0;
    XPath xpath = XPathFactory.newInstance().newXPath();
    for (Map.Entry<String,XPathValue> entry : xpathMap.entrySet()) {
      xpathKeys[i] = entry.getKey();
      xpathExprs[i] = xpath.compile(entry.getKey());
      nodeValues[i] = entry.getValue();
      i++;
    }
  }

  /**
   * Extract metadata from source specified by the input stream.
   * 
   * @param target the MetadataTarget
   * @param in the input stream to use as the source
   */
  public ArticleMetadata extract(MetadataTarget target, CachedUrl cu)
      throws IOException {
    if (cu == null) {
      throw new IllegalArgumentException("null CachedUrl");
    }
    ArticleMetadata am = new ArticleMetadata();

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder;
    try {
        dbf.setValidating(false);
        dbf.setFeature("http://xml.org/sax/features/namespaces", false);
        dbf.setFeature("http://xml.org/sax/features/validation", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);          
        builder = dbf.newDocumentBuilder();
    } catch (ParserConfigurationException ex) {
      log.warning(ex.getMessage());
      return am;
    }
    Document doc;
    InputSource bReader = new InputSource(cu.openForReading());
    try {
      doc = builder.parse(bReader);
    } catch (SAXException ex) {
      log.warning(ex.getMessage());
      return am;
    }

    // search for values using specified XPath expressions and
    // set raw value using string based on type of node value 
    for (int i = 0; i < xpathKeys.length; i++) { 
      try {
        QName type = nodeValues[i].getType();
        Object result = xpathExprs[i].evaluate(doc, type);
        if (result instanceof NodeList) {
          NodeList nodeList = (NodeList)result;
          for (int j = 0; j < nodeList.getLength(); j++) {
            String value = nodeValues[i].getValue(nodeList.item(j));
            if (!StringUtil.isNullString(value)) am.putRaw(xpathKeys[i], value);
          }
        } else if (result instanceof Number) {
          String value = nodeValues[i].getValue((Number)result);
          if (!StringUtil.isNullString(value)) am.putRaw(xpathKeys[i], value);
        } else if (result instanceof Boolean) {
          String value = nodeValues[i].getValue((Boolean)result);
          if (!StringUtil.isNullString(value)) am.putRaw(xpathKeys[i], value);
        } else if (result instanceof String) {
          String value = nodeValues[i].getValue((String)result);
          if (!StringUtil.isNullString(value)) am.putRaw(xpathKeys[i], value);
        } else {
          log.warning("Unknown return type for XPath expression: "
                  + ((result == null) ? "Null" : result.getClass().getName()));
        }
      } catch (XPathExpressionException ex) {
        // ignore evaluation errors
        log.warning("ignorning xpath error", ex);
      }
    }

    return am;
  }
}
