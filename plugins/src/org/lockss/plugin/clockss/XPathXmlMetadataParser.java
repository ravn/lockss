/*
 * $Id: XPathXmlMetadataParser.java,v 1.1 2013-10-15 23:28:06 alexandraohlson Exp $
 */

/*

Copyright (c) 2000-2013 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.clockss;

import java.io.*;
import java.text.NumberFormat;
import java.text.ParseException;
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

import org.lockss.extractor.ArticleMetadata;
import org.lockss.extractor.MetadataTarget;
import org.lockss.extractor.XmlDomMetadataExtractor.XPathValue;
import org.lockss.plugin.CachedUrl;
import org.lockss.util.IOUtil;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


public class XPathXmlMetadataParser  {
  static Logger log = Logger.getLogger(XPathXmlMetadataParser.class);


  /*
   *  Create a class to hold the information we need for every XPath, that is:
   *     xKey - the string that defines the path of the XPath
   *     xExpr - the compiled XPath expression
   *     xVal - the evaluator to get a string return value
   */
  protected class XPathInfo {
    String xKey;
    XPathExpression xExpr;
    XPathValue xVal;

    public XPathInfo(String keyVal, XPathExpression exprVal, XPathValue evalVal) {
      xKey = keyVal;
      xExpr = exprVal;
      xVal = evalVal;
    }

  }

  final protected XPathInfo[] gXPathList;
  final protected XPathInfo[] aXPathList;
  protected XPathExpression articlePath;

  protected XPathXmlMetadataParser(int gSize, int aSize) {
    gXPathList = new XPathInfo[gSize];
    aXPathList = new XPathInfo[aSize];
    articlePath = null;
  }

  /*
   * Create an XPath based metadata parser that will extract the textContent of
   * the nodes specified by the XPath expressions by applying the 
   * corresponding NodeValue evaluators.
   * The globalMap defines paths for data that should be applied across all
   * The articleMap defines paths for data that applies to only one article
   * The articleNodeDef defines a path to the top of an individual article node
   * 
   * Cases:
   *     If there is no globalMap, it is assumed that each ArticleMetadata will 
   *       be filled only by the articleMap
   *     If there is no articleMap, only one ArticleMetadata is filled and 
   *       returned, using the globalMap
   *     If the articleNodeDef is not set, it is assumed to be the top of the 
   *       document
   *     If the articleNodeDef is set, the articleMap paths should be relative 
   *       to that (not from the top of the document)
   */
  public XPathXmlMetadataParser(Map<String, XPathValue> globalMap, 
      String articleNode, 
      Map<String, XPathValue> articleMap)
          throws XPathExpressionException {
    this(getMapSize(globalMap), getMapSize(articleMap));

    XPath xpath = XPathFactory.newInstance().newXPath();
    if (globalMap != null) {
      int i = 0;
      for (Map.Entry<String,XPathValue> entry : globalMap.entrySet()) {
        gXPathList[i] = new XPathInfo(entry.getKey(), 
            xpath.compile(entry.getKey()), entry.getValue());
        i++;
      }
    }

    if (articleMap != null) {
      int i = 0;
      for (Map.Entry<String,XPathValue> entry : articleMap.entrySet()) {
        aXPathList[i] = new XPathInfo(entry.getKey(), 
            xpath.compile(entry.getKey()), entry.getValue());
        i++;
      }
    }
    if (articleNode != null) {
      articlePath = xpath.compile(articleNode);
    }

  }

  /* a convenience to ensure we don't dereference null - used by 
   * constructor for this class
   */
  private static int getMapSize(Map<String, XPathValue> xpathMap) {
    return ( (xpathMap != null) ? xpathMap.size() : 0);
  }

  /*
   * Pull metadata from source specified by the input stream.
   * 
   */
  public List<ArticleMetadata> extractMetadata(MetadataTarget target, CachedUrl cu)
      throws IOException {
    if (cu == null) {
      throw new IllegalArgumentException("Null CachedUrl");
    }
    if (!cu.hasContent()) {
      throw new IllegalArgumentException("CachedUrl has no content" + cu.getUrl());
    }
    List<ArticleMetadata> amList = makeNewAMList();
    ArticleMetadata globalAM = null;

    Document doc = CreateDocumentTree(cu);
    if (doc == null) return amList; // return empty list

    try {
      /* GLOBAL - If global data map exists, collect it and put it in a temporary AM */
      if(gXPathList.length > 0) {
        log.debug3("extracting global metadata");
        globalAM = extractDataFromNode(doc, gXPathList);
      }
      if(aXPathList.length > 0) {
        /* ARTICLE - If there is no definition of an article node, collect article data from entire tree */
        if (articlePath == null) {
          log.debug3("extract article data from entire document");
          ArticleMetadata oneAM = extractDataFromNode(doc, aXPathList);
          addGlobalToArticleAM(globalAM, oneAM);
          amList.add(oneAM); 
        } else {
          /* Get a list of article nodes from the full tree and then collect article data from each one */
          Object result;
          log.debug3("extracting article data from each article path:" + articlePath);
          // if no articles, this returns an empty nodelist, not null
          result = articlePath.evaluate(doc, XPathConstants.NODESET);
          NodeList nodeList = (NodeList)result;
          for (int j = 0; j < nodeList.getLength(); j++) {
            Node articleNode = nodeList.item(j);
            log.debug3("Article node");
            if (articleNode == null) {
              continue;
            } else {
              ArticleMetadata singleAM = extractDataFromNode(articleNode, aXPathList);
              addGlobalToArticleAM(globalAM, singleAM);
              amList.add(singleAM); // before going on to the next individual item
            }
          }
        }
      }

    } catch (XPathExpressionException e) {
      // indicates bath xPath expression,not bad xml
      log.warning("ignoring xpath error - " + e.getMessage());
    }
    return amList;
  }

  private ArticleMetadata extractDataFromNode(Object startNode, 
      XPathInfo[] xPathList) throws XPathExpressionException {

    ArticleMetadata returnAM = makeNewArticleMetadata(); 
    NumberFormat format = NumberFormat.getInstance();


    for (int i = 0; i < xPathList.length; i++) { 
      QName definedType = xPathList[i].xVal.getType();
      Object itemResult = xPathList[i].xExpr.evaluate(startNode, XPathConstants.NODESET);
      NodeList resultNodeList = (NodeList)itemResult;
      for (int p = 0; p < resultNodeList.getLength(); p++) {
        Node resultNode = resultNodeList.item(p);
        if (resultNode == null) {
          continue;
        }
        String value = null;
        if (definedType == XPathConstants.NODE) {
          // filter node
          value = xPathList[i].xVal.getValue(resultNode);
        } else if (definedType == XPathConstants.STRING) {
          // filter node text content
          String text = resultNode.getTextContent();
          if (!StringUtil.isNullString(text)) {
            value = xPathList[i].xVal.getValue(text);
          }
        } else if (definedType == XPathConstants.BOOLEAN) {
          // filter boolean value of node text content
          String text = resultNode.getTextContent();
          if (!StringUtil.isNullString(text)) {
            value = xPathList[i].xVal.getValue(Boolean.parseBoolean(text));
          }
        } else if (definedType == XPathConstants.NUMBER) {
          // filter number value of node text content
          try {
            String text = resultNode.getTextContent();
            if (!StringUtil.isNullString(text)) {
              value = xPathList[i].xVal.getValue(format.parse(text));
            }
          } catch (ParseException ex) {
            // ignore invalid number
            log.debug3(ex.getMessage());
          }
        } else {
          log.debug("Unknown nodeValue type: " + definedType.toString());
        }

        if (!StringUtil.isNullString(value)) {
          returnAM.putRaw(xPathList[i].xKey, value);
        } 
      }
    }
    return returnAM;
  }

  /*
   * If the globalAM isn't null, take any values from the globalAM and put them 
   * in to the singleAM as raw values
   */
  private void addGlobalToArticleAM(ArticleMetadata globalAM, ArticleMetadata singleAM) {
    if (globalAM == null) return; // possible, just ignore
    if (singleAM == null) {
      log.debug3("Null article AM passed in to addGlobalToArticleAM"); // an error
      return;
    }

    // loop over the keys in the global raw map and put their values in to the single raw map
    // don't check for key already in single map - relative v. absolute xpath makes it unlikely
    // and put won't overwrite anyway
    if (globalAM.rawSize() > 0) {
      for (String gKey : globalAM.rawKeySet()) {
        singleAM.putRaw(gKey, globalAM.getRaw(gKey));
      }
    }
  }
  
  /* 
   * Take in the CU and return the correctly parsed XML as a Document "tree"
   */
  protected Document CreateDocumentTree(CachedUrl cu) {
    
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
    log.warning("Cannot parse XML file for Metadata -" + ex.getMessage());
    return null;
  }

  InputSource bReader = new InputSource(cu.openForReading());
  Document doc;
  try {
    doc = builder.parse(bReader);
    return doc;
  } catch (SAXException ex) {
    log.warning(ex.getMessage());
    return null;
  } catch (IOException e) {
    log.warning(e.getMessage());
    return null;
  } finally {
    IOUtil.safeClose(bReader.getCharacterStream());
  }
  /* The entire document tree is now loaded*/
  }
  
  /*
   * a wrapper to allow for future override
   */
  protected ArticleMetadata makeNewArticleMetadata() {
    return new ArticleMetadata();
  }

  /*
   * a wrapper to allow for future override
   */
  protected List<ArticleMetadata> makeNewAMList() {
    return new ArrayList<ArticleMetadata>();
  }
}
