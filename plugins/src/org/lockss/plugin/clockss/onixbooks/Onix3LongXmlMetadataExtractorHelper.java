/*
 * $Id: Onix3LongXmlMetadataExtractorHelper.java,v 1.1 2013-11-25 18:46:09 alexandraohlson Exp $
 */

/*

 Copyright (c) 2000-2010 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.clockss.onixbooks;

import org.apache.commons.collections.map.MultiValueMap;
import org.lockss.util.*;
import org.lockss.extractor.*;
import org.lockss.extractor.XmlDomMetadataExtractor.NodeValue;
import org.lockss.extractor.XmlDomMetadataExtractor.XPathValue;

import java.util.*;

import org.lockss.plugin.clockss.SourceXmlMetadataExtractorFactory.SourceXmlMetadataExtractorHelper;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  A helper class that defines a schema for XML metadata extraction for
 *  Onix 3 Long form
 *  with the filenames based on isbn13 plus .pdf or .epub
 *  There can be multiple records for the same item, one for each format
 *  @author alexohlson
 */
public class Onix3LongXmlMetadataExtractorHelper
implements SourceXmlMetadataExtractorHelper {
  static Logger log = Logger.getLogger(Onix3LongXmlMetadataExtractorHelper.class);

  private static final String AUTHOR_SEPARATOR = ",";

  /* 
   *  ONIX 3.0 specific node evaluators to extract the information we want
   */
  /*
   * ProductIdentifier: handles ISBN13, DOI & LCCN
   * NODE=<ProductIdentifier>
   *   ProductIDType/
   *   IDValue/
   * xPath that gets here has already figured out which type of ID it is
   */
  static private final NodeValue ONIX_ID_VALUE = new NodeValue() {
    @Override
    public String getValue(Node node) {
      if (node == null) {
        return null;
      }
      log.debug3("getValue of ONIX ID");
      // the TYPE has already been captured by xpath search in raw key
      String idVal = null;
      NodeList childNodes = node.getChildNodes(); 
      for (int m = 0; m < childNodes.getLength(); m++) {
        Node infoNode = childNodes.item(m); 
        if (infoNode.getNodeName().equals("IDValue")) {
          idVal = infoNode.getTextContent();
          break;
        }
      }
      if (idVal != null)  {
        return idVal;
      } else {
        log.debug3("no IDVal in this productIdentifier");
        return null;
      }
    }
  };

  /*
   * AUTHOR information
   * NODE=<Contributor>     
   *   ContributorRole/
   * not all of these will necessarily be there...  
   *   NamesBeforeKey/
   *   KeyNames/
   *   PersonName/
   *   PersonNameInverted
   */
  static private final NodeValue ONIX_AUTHOR_VALUE = new NodeValue() {
    @Override
    public String getValue(Node node) {
      if (node == null) {
        return null;
      }
      log.debug3("getValue of ONIX contributor");
      String auType = null;
      String auKey = null;
      String auBeforeKey = null;
      String straightName = null;
      String invertedName = null;
      NodeList childNodes = node.getChildNodes(); 
      for (int m = 0; m < childNodes.getLength(); m++) {
        Node infoNode = childNodes.item(m);
        String nodeName = infoNode.getNodeName();
        if ("ContributorRole".equals(nodeName)) {
          auType = infoNode.getTextContent();
        } else if ("NamesBeforeKey".equals(nodeName)) {
          auBeforeKey = infoNode.getTextContent();
        } else if ("KeyNames".equals(nodeName)) {
          auKey = infoNode.getTextContent();
        } else if ("PersonName".equals(nodeName)) {
          straightName = infoNode.getTextContent();
        } else if ("PersonNameInverted".equals(nodeName)) {
          invertedName = infoNode.getTextContent();
        }
      }
      // We may choose to limit the type of roles, but not sure which yet
      if  (auType != null) {
        // first choice, PersonNameInverted
        if (invertedName != null) {
          return invertedName;
        } else if (auKey != null) { // otherwise use KeyNames, NamesBeforeKey
          StringBuilder valbuilder = new StringBuilder();
          valbuilder.append(auKey);
          if (auBeforeKey != null) {
            valbuilder.append(AUTHOR_SEPARATOR +  " " + auBeforeKey);
          } 
          return valbuilder.toString();
        } else if (straightName != null) { //otherwise use PersonName
          return straightName;
        }
        log.debug3("No valid contributor in this contributor node.");
        return null;
      }
      return null;
    }
  };

  /* 
   * TITLE INFORMATION
   * NODE=<TitleDetail>
   *   <TitleElement>
   *     TitleElementLevel=01 <--this is the one we want
   *     TitleText
   *     Subtitle
   *   </TitleElement>
   *   <TitleElement>
   *     possible additional elements with different levels
   *   </TitleElement>
   */
  static private final NodeValue ONIX_TITLE_VALUE = new NodeValue() {
    @Override
    public String getValue(Node node) {

      log.debug3("getValue of ONIX title");
      NodeList elementNodes = node.getChildNodes();
      if (elementNodes == null) return null;

      String tLevel = null;
      String tTitle = null;
      String tSubtitle = null;
      // look at each child of the TitleDetail for TitleElements
      for (int m = 0; m < elementNodes.getLength(); m++) {
        Node titleChild = elementNodes.item(m);
        if ( "TitleElement".equals(titleChild.getNodeName())) {
          NodeList elementChildren = titleChild.getChildNodes();
          if (elementChildren != null) {
            for (int j = 0; j < elementChildren.getLength(); j++) {
              Node checkNode = elementChildren.item(j);
              if ("TitleElementLevel".equals(checkNode.getNodeName())) {
                tLevel = checkNode.getTextContent();
                if (!"01".equals(tLevel)) {
                  break;
                }
              } else if ("TitleText".equals(checkNode.getNodeName())) {
                tTitle = checkNode.getTextContent();
              } else if ("Subtitle".equals(checkNode.getNodeName())) {
                tSubtitle = checkNode.getTextContent();
              }
            }
            // found it; stop looking
            if ("01".equals(tLevel)) break;
          }
        }
      }
      if("01".equals(tLevel)) {
        StringBuilder valbuilder = new StringBuilder();
        valbuilder.append(tTitle);
        if (tSubtitle != null) {
          valbuilder.append(": " + tSubtitle);
        }
        log.debug3("title found: " + valbuilder.toString());
        return valbuilder.toString();
      } else {
        // only here if we didn't find a title level of 1
        log.debug3("no level1 title available");
        return null;
      }
    }
  };

  /* 
   * PUBLISHING DATE - could be under one of two nodes
   * NODE=<PublishingDate/>
   *   <PublishingDateRole/>
   *   <Date dateformat="xx"/>  // unspecified format means YYYYMMDD 
   *   
   * NODE=<MarketDate/>
   *   <MarketDateRole/>
   *   <Date dateformat="xx"/>   // unspecified format means YYYYMMDD 
   */
  static private final NodeValue ONIX_DATE_VALUE = new NodeValue() {
    @Override
    public String getValue(Node node) {
      log.debug3("getValue of ONIX date");
      NodeList childNodes = node.getChildNodes();
      if (childNodes == null) return null;

      String dRole = null;
      String dFormat = null;
      String dDate = null;

      String RoleName = "PublishingDateRole";
      if (!(node.getNodeName().equals("PublishingDate"))) {
        RoleName = "MarketDateRole";
      }
      for (int m = 0; m < childNodes.getLength(); m++) {
        Node childNode = childNodes.item(m);
        if (childNode.getNodeName().equals(RoleName)) {
          dRole = childNode.getTextContent();
        } else if (childNode.getNodeName().equals("Date")) {
          dDate = childNode.getTextContent();
          dFormat = ((Element)childNode).getAttribute("dateformat");
          if ((dFormat == null) || dFormat.isEmpty()) {
            dFormat = "00";
          }
        }
      }
      if (!(dRole.equals("01") || dRole.equals("11") || dRole.equals("12")) ) {
        // not a type of date role we care about 
        return null;
      } 
      if (dFormat.equals("00")) { 
        // make it W3C format instead of YYYYMMDD
        StringBuilder dBuilder = new StringBuilder();
        dBuilder.append(dDate.substring(0, 4)); //YYYY
        dBuilder.append("-");
        dBuilder.append(dDate.substring(4, 6)); //MM
        dBuilder.append("-");
        dBuilder.append(dDate.substring(6, 8)); //DD
        return dBuilder.toString();
      } else {
        return dDate; //not sure what the format is, just return it as is
      }
    }
  };

  /* 
   *  ONIX specific XPATH key definitions that we care about
   */

  /* Under an item node, the interesting bits live at these relative locations */
  private static String ONIX_product_id =  "ProductIdentifier";
  protected static String ONIX_idtype_isbn13 =
      ONIX_product_id + "[ProductIDType = \"15\"]"; 
  private static String ONIX_idtype_lccn =
      ONIX_product_id + "[ProductIDType = \"13\"]";
  private static String ONIX_idtype_doi =
      ONIX_product_id + "[ProductIDType = \"06\"]";
  /* components under DescriptiveDetail */
  private static String ONIX_product_descrip =
      "DescriptiveDetail";  
  private static String ONIX_product_form =
      ONIX_product_descrip + "/ProductFormDetail";
  private static String ONIX_product_title =
      ONIX_product_descrip + "/TitleDetail";
  private static String ONIX_product_contrib =
      ONIX_product_descrip + "/Contributor";
  private static String ONIX_product_comp =
      ONIX_product_descrip + "/ProductComposition";
  /* components under DescriptiveDetail if this is part of series */
  private static String ONIX_product_seriestitle =
      ONIX_product_descrip + "/Collection/TitleDetail";
  private static String ONIX_product_seriesISSN =
      ONIX_product_descrip + "/Collection/CollectionIdentifier[CollectionIDType = '02']";
  /* components under PublishingDetail */
  private static String ONIX_product_pub =
      "PublishingDetail";
  private static String ONIX_pub_name =
      ONIX_product_pub + "/Publisher/PublisherName";
  private static String ONIX_pub_date =
      ONIX_product_pub + "/PublishingDate";
  /* components under MarketPublishingDetail */
  private static String ONIX_product_mktdetail =
      "ProductSupply/MarketPublishingDetail"; 
  private static String ONIX_mkt_date =
      ONIX_product_mktdetail + "/MarketDate";


  /*
   *  The following 3 variables are needed to construct the XPathXmlMetadataParser
   */

  /* 1.  MAP associating xpath with value type with evaluator */
  static private final Map<String,XPathValue> ONIX_articleMap = 
      new HashMap<String,XPathValue>();
  static {
    ONIX_articleMap.put("/RecordReference", XmlDomMetadataExtractor.TEXT_VALUE);
    ONIX_articleMap.put(ONIX_idtype_isbn13, ONIX_ID_VALUE); 
    ONIX_articleMap.put(ONIX_idtype_lccn, ONIX_ID_VALUE); 
    ONIX_articleMap.put(ONIX_idtype_doi, ONIX_ID_VALUE); 
    ONIX_articleMap.put(ONIX_product_form, XmlDomMetadataExtractor.TEXT_VALUE);
    ONIX_articleMap.put(ONIX_product_title, ONIX_TITLE_VALUE);
    ONIX_articleMap.put(ONIX_product_contrib, ONIX_AUTHOR_VALUE);
    ONIX_articleMap.put(ONIX_product_comp, XmlDomMetadataExtractor.TEXT_VALUE);
    ONIX_articleMap.put(ONIX_pub_name, XmlDomMetadataExtractor.TEXT_VALUE);
    ONIX_articleMap.put(ONIX_pub_date, ONIX_DATE_VALUE);
    ONIX_articleMap.put(ONIX_mkt_date, ONIX_DATE_VALUE);
    ONIX_articleMap.put(ONIX_product_seriestitle, ONIX_TITLE_VALUE);
    ONIX_articleMap.put(ONIX_product_seriesISSN, ONIX_ID_VALUE);

  }

  /* 2. Each item (book) has its own subNode */
  static private final String ONIX_articleNode = "/ONIXMessage/Product"; 

  /* 3. in ONIX, there is no global information we care about, it is repeated per article */ 
  static private final Map<String,XPathValue> ONIX_globalMap = null;

  /*
   * The emitter will need a map to know how to cook ONIX raw values
   */
  private static final MultiValueMap cookMap = new MultiValueMap();
  static {
    // normal journal article schema
    cookMap.put(ONIX_idtype_isbn13, MetadataField.FIELD_ISBN);
    cookMap.put(ONIX_idtype_doi, MetadataField.FIELD_DOI);
    cookMap.put(ONIX_product_title, MetadataField.FIELD_ARTICLE_TITLE);
    cookMap.put(ONIX_product_contrib, MetadataField.FIELD_AUTHOR);
    cookMap.put(ONIX_pub_name, MetadataField.FIELD_PUBLISHER);
    cookMap.put(ONIX_pub_date, MetadataField.FIELD_DATE);
    //TODO: If book is part of sereis, currently no way to store title,issn
    //cookMap.put(ONIX_product_seriestitle, MetadataField.FIELD_SERIES_TITLE);
    //cookMap.put(ONIX_product_seriesISSN, MetadataField.FIELD_SERIES_ISSN);
    //TODO: Book, BookSeries...currently no key field to put the information in to      
    //cookMap.put(ONIX_product_pub + "/PublishingComposition", ONIX_FIELD_TYPE);
    //TODO: currently no way to store multiple formats in MetadataField (FIELD_FORMAT is a single);
  }


  /**
   * ONIX3 does not contain needed global information outside of article records
   * return NULL
   */
  @Override
  public Map<String, XPathValue> getGlobalMetaMap() {
    return ONIX_globalMap;
  }

  /**
   * return ONIX3 article paths representing metadata of interest  
   */
  @Override
  public Map<String, XPathValue> getArticleMetaMap() {
    return ONIX_articleMap;
  }

  /**
   * Return the article node path
   */
  @Override
  public String getArticleNode() {
    return ONIX_articleNode;
  }

  /**
   * Return a map to translate raw values to cooked values
   */
  @Override
  public MultiValueMap getCookMap() {
    return cookMap;
  }

  /**
   * Return the path for isbn13 so multiple records for the same item
   * can be combined
   */
  @Override
  public String getUniqueIDKey() {
    return ONIX_idtype_isbn13;
  }

  /**
   * Return the path for product form so when multiple records for the same
   * item are combined, the product forms are combined together
   */
  @Override
  public String getConsolidationKey() {
    return ONIX_product_descrip + "/ProductFormDetail";
  }

  /**
   * No filename prefix needed - return null
   */
  @Override
  public String getFilenamePrefix() {
    return null;
  }

  /**
   * Look for both pdf and epub filetype to decide if we should emit
   */
  @Override
  public ArrayList<String> getFilenameSuffixList() {
    ArrayList<String> returnList = new ArrayList<String>();
    returnList.add(".pdf");
    returnList.add(".epub");
    return returnList;
  }

  /**
   * The filenames are based on the isbn13 value 
   */
  @Override
  public String getFilenameKey() {
    return ONIX_idtype_isbn13;
  }
}    