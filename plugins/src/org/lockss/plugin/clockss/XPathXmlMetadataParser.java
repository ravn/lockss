/*
 * $Id: XPathXmlMetadataParser.java,v 1.13 2015-01-15 05:06:46 alexandraohlson Exp $
 */

/*

Copyright (c) 2000-2014 Board of Trustees of Leland Stanford Jr. University,
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

import java.text.*;
import java.util.*;

import javax.xml.namespace.QName;
import javax.xml.parsers.*;
import javax.xml.xpath.*;

import org.lockss.extractor.*;
import org.lockss.extractor.XmlDomMetadataExtractor.XPathValue;
import org.lockss.plugin.*;
import org.lockss.util.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;


/**
 * This class extracts values from the XML specified by a CachedUrl
 * as the raw values of a ArticleMetadata object or objects. It supports multiple
 * article records in one file and returns a list of ArticleMetadata objects.
 *  This class uses the DocumentBuilderFactor to construct a 
 *  DocumentBuilder to parse the input 
 * stream into a Document. Validation and name spaces are disabled and 
 * DTD files are not consulted.
 * <p>
 * The metadata to extract is specified by XPath expressions. By default, 
 * the expression evaluates to a node-set whose values are the text content 
 * of the specified nodes. Optional NodeValue objects can be provided with 
 * the XPath expressions to specify the return types of the xpressions and 
 * how to format the resulting values as strings.
 * <p>
 * Global xPath expressions are evaluated across the entire tree and put in to
 * every returned ArticleMetadata object.
 * Article xPath expressions are evaluated from a defined article level node
 * down and put in to individual ArticleMetadata objects in the list.
 * See the following reference on XPath syntax: 
 * http://www.w3schools.com/xpath/xpath_syntax.asp
 * @author alexohlson
 *
 */
public class XPathXmlMetadataParser  {
  
  private static Logger log = Logger.getLogger(XPathXmlMetadataParser.class);


  /**
   * A class to hold the information we need to use an XPath<br>
   *     xKey - the string that defines the path of the XPath
   *     xExpr - the compiled XPath expression
   *     xVal - the evaluator to get a string return value
   * @author alexohlson
   *
   */
  protected class XPathInfo {
    
    String xKey;
    XPathExpression xExpr;
    XPathValue xVal;

    public XPathInfo(String keyVal,
                     XPathExpression exprVal,
                     XPathValue evalVal) {
      xKey = keyVal;
      xExpr = exprVal;
      xVal = evalVal;
    }

  }

  protected final XPathInfo[] gXPathList;
  
  protected final XPathInfo[] aXPathList;
  
  protected XPathExpression articlePath;
  
  protected boolean doXmlFiltering;

  /**
   *  Create an XPath based XML parser that will extract the textContent of
   * the nodes specified by the XPath expressions by applying the 
   * corresponding NodeValue evaluators.
   * <p>
   * Cases:<br/>
   *     If there is no globalMap, it is assumed that each ArticleMetadata will 
   *       be filled only by the articleMap<br/>
   *     If there is no articleMap, only one ArticleMetadata is filled and 
   *       returned, using the globalMap<br/>
   *     If the articleNodeDef is not set, it is assumed to be the top of the 
   *       document<br/>
   *     If the articleNodeDef is set, the articleMap paths should be relative 
   *       to that (not from the top of the document)
   *
   * @param globalMap xPaths for data that should be applied across entire XML
   * @param articleNode defines a path to the top of an individual article node
   * @param articleMap path relative to articleNode to apply to each article
   * @throws XPathExpressionException
   */
  public XPathXmlMetadataParser(Map<String, XPathValue> globalMap, 
                                String articleNode, 
                                Map<String, XPathValue> articleMap)
      throws XPathExpressionException {
    gXPathList = new XPathInfo[getMapSize(globalMap)];
    aXPathList = new XPathInfo[getMapSize(articleMap)];
    articlePath = null;
    doXmlFiltering = false; // default behavior

    XPath xpath = XPathFactory.newInstance().newXPath();
    if (globalMap != null) {
      int i = 0;
      for (Map.Entry<String, XPathValue> entry : globalMap.entrySet()) {
        gXPathList[i] = new XPathInfo(entry.getKey(), 
                                      xpath.compile(entry.getKey()),
                                      entry.getValue());
        i++;
      }
    }

    if (articleMap != null) {
      int i = 0;
      for (Map.Entry<String, XPathValue> entry : articleMap.entrySet()) {
        aXPathList[i] = new XPathInfo(entry.getKey(), 
                                      xpath.compile(entry.getKey()),
                                      entry.getValue());
        i++;
      }
    }
    
    if (articleNode != null) {
      articlePath = xpath.compile(articleNode);
    }
  }

  /*
   *  A constructor that allows for the xml filtering of the input stream
   */
  public XPathXmlMetadataParser(Map<String, XPathValue> globalMap, 
                                String articleNode, 
                                Map<String, XPathValue> articleMap,
                                boolean doXmlFiltering)
      throws XPathExpressionException {
    this(globalMap, articleNode, articleMap);
    setDoXmlFiltering(doXmlFiltering);
  }

  /*
   * getter/setter for the switch to do xml filtering of input stream
   */

  /**
   * <p>
   * Determines if XML pre-filtering with {@link XmlFilteringInputStream} has
   * been requested for this instance.
   * </p>
   * 
   * @return True if XML pre-filtering has been requested.
   * @since 1.66
   */
  public boolean isDoXmlFiltering() {
    return doXmlFiltering;
  }
  
  /**
   * @deprecated Use {@link #isDoXmlFiltering()} instead.
   */
  @Deprecated
  public boolean getDoXmlFiltering() {
    return isDoXmlFiltering();
  }

  public void setDoXmlFiltering(boolean doXmlFiltering) {
    this.doXmlFiltering = doXmlFiltering;
  }

  /* a convenience to ensure we don't dereference null - used by 
   * constructor for this class
   */
  private static int getMapSize(Map<String, XPathValue> xpathMap) {
    return ( (xpathMap != null) ? xpathMap.size() : 0);
  }

  /**
   * Extract metadata from the XML source specified by the input stream using
   * the constructor-set xPath definitions.
   * @param target 
   * @param cu the CachedUrl for the XML source file
   * @return list of ArticleMetadata objects; one per record in the XML
   * @throws IOException
   * @throws SAXException 
   */
  public List<ArticleMetadata> extractMetadata(MetadataTarget target, CachedUrl cu)
      throws IOException, SAXException {
    if (cu == null) {
      throw new IllegalArgumentException("Null CachedUrl");
    }
    if (!cu.hasContent()) {
      throw new IllegalArgumentException("CachedUrl has no content: " + cu.getUrl());
    }
    List<ArticleMetadata> amList = makeNewAMList();
    ArticleMetadata globalAM = null;

    Document doc = null;
    // this could throw IO or SAX exception - handled  upstream
    doc = createDocumentTree(cu);

    // no exception thrown but the document wasn't succesfully created
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
              log.debug3("NULL article node");
              continue;
            } else {
              ArticleMetadata singleAM = extractDataFromNode(articleNode, aXPathList);
              addGlobalToArticleAM(globalAM, singleAM);
              amList.add(singleAM); // before going on to the next individual item
            }
          }
        }
      } else {
        /* No article map, but if there was a global map, use that */
        if (globalAM != null) {
          amList.add(globalAM);
        }
      }

    } catch (XPathExpressionException e) {
      // indicates bath xPath expression,not bad xml
      log.warning("ignoring xpath error - " + e.getMessage());
    }
    return amList;
  }

  /*
   *  from a given node, using a set of xPath expressions
   */
  private ArticleMetadata extractDataFromNode(Object startNode, 
      XPathInfo[] xPathList) throws XPathExpressionException {

    ArticleMetadata returnAM = makeNewArticleMetadata(); 
    NumberFormat format = NumberFormat.getInstance();

    for (int i = 0; i < xPathList.length; i++) { 
      log.debug3("evaluate xpath: " + xPathList[i].xKey.toString());
      QName definedType = xPathList[i].xVal.getType();
      Object itemResult = xPathList[i].xExpr.evaluate(startNode, XPathConstants.NODESET);
      NodeList resultNodeList = (NodeList)itemResult;
      log.debug3(resultNodeList.getLength() + " results for this xKey");
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
            log.debug3("ignore invalid number", ex);
          }
        } else {
          log.debug("Unknown nodeValue type: " + definedType.toString());
        }

        if (!StringUtil.isNullString(value)) {
          log.debug3("  returning ("+xPathList[i].xKey+", "+ value);
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

  /**
   * <p>
   * Subclasses can override this method to create and configure a
   * {@link DocumentBuilderFactory} instance.
   * </p>
   * <p>
   * By default, this class uses {@link DocumentBuilderFactory#newInstance()}
   * and sets all the following to <code>false</code>:
   * </p>
   * <ul>
   * <li>{@link DocumentBuilderFactory#setValidating(boolean)}</li>
   * <li><code>http://xml.org/sax/features/namespaces</code></li>
   * <li><code>http://xml.org/sax/features/validation</code></li>
   * <li><code>http://apache.org/xml/features/nonvalidating/load-dtd-grammar</code></li>
   * <li><code>http://apache.org/xml/features/nonvalidating/load-external-dtd</code></li>
   * <li><code>http://apache.org/xml/features/dom/defer-node-expansion</code></li>
   * </ul>
   * 
   * @throws ParserConfigurationException
   *           if an error arises while configuring the document builder
   *           factory.
   * @since 1.66
   */
  protected DocumentBuilderFactory makeDocumentBuilderFactory()
      throws ParserConfigurationException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setValidating(false);
    dbf.setFeature("http://xml.org/sax/features/namespaces", false);
    dbf.setFeature("http://xml.org/sax/features/validation", false);
    dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
    dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); 
    // The following feature keeps some XML files (see T&Fsource) from causing DB.parse
    // null pointer exception
    dbf.setFeature("http://apache.org/xml/features/dom/defer-node-expansion", false);
    return dbf;
  }
  
  /**
   * <p>
   * Subclasses can override this method to make and configure a
   * {@link DocumentBuilder} instance from the given
   * {@link DocumentBuilderFactory} instance.
   * </p>
   * <p>
   * By default, this simply calls
   * {@link DocumentBuilderFactory#newDocumentBuilder()} on the
   * {@link DocumentBuilderFactory} instance.
   * </p>
   * 
   * @param db
   *          A document builder.
   * @throws ParserConfigurationException
   *           if an error arises while configuring the document builder
   *           factory.
   * @since 1.66
   */
  protected DocumentBuilder makeDocumentBuilder(DocumentBuilderFactory dbf)
      throws ParserConfigurationException {
    return dbf.newDocumentBuilder();
  }
  
  /**
   * <p>
   * Subclasses can override this method to make an {@link InputSource} from the
   * given {@link CachedUrl}.
   * </p>
   * <p>
   * By default, this class uses the cached URL's
   * {@link CachedUrl#getUnfilteredInputStream()} method and, if
   * {@link #isDoXmlFiltering()} return true, wraps it in a
   * {@link XmlFilteringInputStream}, setting the encoding of the input source
   * to the value returned by the util {@link CharsetUtil#guessCharsetName()} and
   * if that returns null, to {@link CachedUrl#getEncoding()}.
   * </p>
   * 
   * @param cu
   *          A cached URL.
   * @return An input source for the cached URL's data stream.
   * @throws IOException
   *           if an I/O exception occurs.
   * @since 1.67
   */
  protected InputSource makeInputSource(CachedUrl cu) throws IOException {
 
    // first create a reader so child classes can 
    // do filtering on the reader if they need to
    Pair<Reader, String> iReaderPair = makeInputSourceReader(cu);
    InputSource iSource = new InputSource(iReaderPair.getLeft());
    iSource.setEncoding(iReaderPair.getRight());
    return iSource;
  }
  
  /**
   *  Given a CU for an XML file, load and return the XML as a Document "tree". 
   * @param cu to the XML file
   * @return Document for the loaded XML file
   */
  protected Document createDocumentTree(CachedUrl cu) throws SAXException, IOException {
    DocumentBuilderFactory dbf = null;
    DocumentBuilder builder = null;
    try {
      dbf = makeDocumentBuilderFactory();
      builder = makeDocumentBuilder(dbf);
    }
    catch (ParserConfigurationException pce) {
      log.warning("Cannot setup document builder for XML file: " + cu.getUrl(), pce);
      return null;
    }

    Document doc = null;
    try {
      InputSource iSource = makeInputSource(cu);
      doc = builder.parse(iSource);
      return doc;
    }
    catch (SAXException se) {
      log.debug("SAXException while parsing XML file: " + cu.getUrl(), se);
      throw se;
    }
    catch (IOException ioe) {
      log.debug("IOException while parsing XML file: " + cu.getUrl(), ioe);
      throw ioe;
    }
    finally {
      AuUtil.safeRelease(cu);
    }
  }


  protected InputStream getInputStreamFromCU(CachedUrl cu) {
    if (isDoXmlFiltering()) {
      // This check is a little bogus because the charset isn't always correctly
      // set on the cu.  After this call, we do an additional check on the inputstream
      if (!(Constants.ENCODING_ISO_8859_1.equalsIgnoreCase(cu.getEncoding()))) { 
        log.error("Filtering XML that is not ISO-8859-1 which may or may not work");
      }
      return new XmlFilteringInputStream(cu.getUnfilteredInputStream());
    } else { 
      return cu.getUnfilteredInputStream();
    }
  }
  
  
  /*
   * Return a reader from the inputstream on the CU
   *  - do XML filtering on the stream if the flag is set
   *      this should only be done for IS0-8859 charsets
   *  - make an attempt to figure out the charset
   *  -if it's UTF8, remove any leading BOM characters
   *  return the reader with the charset set
   */
  protected Pair<Reader, String> makeInputSourceReader(CachedUrl cu) throws UnsupportedEncodingException {
  
    String guessed_cset = cu.getEncoding(); // set a default
    try {
      /* 
       * TODO 1.68  
       * With planned improvements to CharsetUtil should be able to 
       * a) just get back the charset
       * b) get back a charset & Reader with BOM bypassed already
       *  This is a little inefficient with current implementation as it
       *  creates a reader from the inputStream during charset evaluation that
       *  it never uses. 
       */
     Pair<Reader, String> retInfoPair = CharsetUtil.getCharsetReader(cu.getUnfilteredInputStream());
     guessed_cset = retInfoPair.getRight();
     log.debug3("guessed cset is: " + guessed_cset);
     } catch (IOException ex){
       log.debug3("Was not able to get a Reader/Charset from util");
     }
     Reader inputReader;
     if (guessed_cset == Constants.ENCODING_UTF_8) {
       /* this is utf-8, so clear out any initial BOM chars */
       log.debug3("UTF-8 input stream - remove any BOM chars");
       BOMInputStream cuInputStream = new BOMInputStream(cu.getUnfilteredInputStream());
       inputReader = new InputStreamReader(cuInputStream, Constants.ENCODING_UTF_8);
     } else {
       log.debug3("Not UTF-8; create reader, possibly doing XML filtering.");
       inputReader = new InputStreamReader(getInputStreamFromCU(cu), guessed_cset);
     }
     return new ImmutablePair<Reader,String>(inputReader, guessed_cset);
  }
  
  /**
   * A wrapper around ArticleMetadata creation to allow for override
   * @return newly created ArticleMetadata object
   */
  protected ArticleMetadata makeNewArticleMetadata() {
    return new ArticleMetadata();
  }

  /**
   * A wrapper around ArticleMetadata list creation to allow for override
   * @return newly created list of ArticleMetadata objects 
   */
  protected List<ArticleMetadata> makeNewAMList() {
    return new ArrayList<ArticleMetadata>();
  }

}
