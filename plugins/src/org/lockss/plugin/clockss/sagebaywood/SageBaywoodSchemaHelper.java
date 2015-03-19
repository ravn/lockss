/*
 * $Id:$
 */

/*

 Copyright (c) 2000-2015 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.clockss.sagebaywood;

import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang3.StringUtils;
import org.lockss.plugin.clockss.SourceXmlSchemaHelper;
import org.lockss.util.*;
import org.lockss.extractor.*;
import org.lockss.extractor.XmlDomMetadataExtractor.NodeValue;
import org.lockss.extractor.XmlDomMetadataExtractor.XPathValue;

import java.util.*;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  A helper class that defines a schema for XML metadata extraction for
 *  the JATS for the "Journal Publishing" tagset
 *  with the filenames based on same name as the .xml file
 *  There is only one record for each file
 *  @author alexohlson
 */
public class SageBaywoodSchemaHelper
implements SourceXmlSchemaHelper {
  private static final Logger log = Logger.getLogger(SageBaywoodSchemaHelper.class);

  
  /* 
   * AUTHOR INFORMATION - we're at an 'Author' node. 
   *     there could be more than one author
   * <AuthorGroup>
   *   <Author>
   *     <GivenName>Edward J. Madara</GivenName>
   *     <Initials/>
   *     <FamilyName/>
   *     <Degrees/>
   *     <Roles/>
   *   </Author>
   * </AuthorGroup>
* or
   *
   *<Author AffiliationID="A1">
   *   <GivenName>Stephen</GivenName>
   *   <Initials>M.</Initials>
   *   <FamilyName>Crow</FamilyName>
   *   <Degrees/>
   *   <Roles/>
   * </Author>
   *
   * There can be multiple authors or there are some cases where "GivenName" is 
   * populated by "John Q. Writer and Sam Scribbles"
   */
  static private final NodeValue BAYWOOD_AUTHOR_VALUE = new NodeValue() {
    @Override
    public String getValue(Node node) {
      log.debug3("getValue of baywood author");

      String givenName = null;
      String inits = null;
      String surName = null;
      NodeList childNodes = node.getChildNodes(); 
      for (int m = 0; m < childNodes.getLength(); m++) {
        Node infoNode = childNodes.item(m);
        String nodeName = infoNode.getNodeName();
        if ("GivenName".equals(nodeName)) {
          givenName = infoNode.getTextContent();
        } else if ("Initials".equals(nodeName)) {
          inits = infoNode.getTextContent();
        } else if ("FamilyName".equals(nodeName)) {
          surName = infoNode.getTextContent();
        }
      }
      if (givenName == null && surName == null) {
        log.debug3("No recognizable author schema in this author");
        return null;
      }
      // names are either in "givenName" in their entirety, or spread
      // across "given" + "inits" + "surname".
      StringBuilder valbuilder = new StringBuilder();
      valbuilder.append(givenName);
      if (surName != null) {
        if (!StringUtils.isBlank(inits)) {
          // <Initials/> yields an single WS string
          valbuilder.append(" " + inits);
        }
        valbuilder.append(" " + surName);
      }
      return valbuilder.toString();
    }
  };
  
  
  /* 
   * The issue is complicated in that it might have more than one number
   * The IssueNumberBegin will be there and so we trap on that
   *   but there might also be an IssueNumberEnd and if it is something
   *   other than the issue start, we want to represent the issue as a span
   *   So:
   *   <Issue>
   *      <IssueInfo IssueType="Regular">
   *        <IssueNumberBegin>1</IssueNumberBegin>
   *        <IssueNumberEnd>1</IssueNumberEnd>
   * would be: issue 1 whereas       
   *   <Issue>
   *      <IssueInfo IssueType="Regular">
   *        <IssueNumberBegin>1</IssueNumberBegin>
   *        <IssueNumberEnd>2</IssueNumberEnd>
   * would be: issue 1-2
   */
  static private final NodeValue BAYWOOD_ISSUE_VALUE = new NodeValue() {
    @Override
    public String getValue(Node node) {
      log.debug3("getValue of baywood issue");

      String startNum = node.getTextContent();
      String endNum = null;
      Node nextSibNode = node.getNextSibling(); 
      while (nextSibNode != null) {
        if ("IssueNumberEnd".equals(nextSibNode.getNodeName())) {
          endNum = nextSibNode.getTextContent();
          break; //look no further
        }
        // It was probably immediately after the startNum, but go through all siblings
        nextSibNode = nextSibNode.getNextSibling();
      }
      if (endNum == null) {
        return startNum;
      }
      if (startNum.equals(endNum)) {
        return startNum;
      }
      StringBuilder valbuilder = new StringBuilder();
      valbuilder.append(startNum);
      valbuilder.append("-");
      valbuilder.append(endNum);
      return valbuilder.toString();
    }
  };
  
  /*
   * Dates are returned in the format YYYYMMDD
   * which for some unknown reason, our PublicationDate does not handle.
   * So for now, insert the hyphens that would allow this to work
   * YYYY-MM-DD
   */
  // will the date format of yyyymmdd work with our database?
  static private final NodeValue BAYWOOD_DATE_VALUE = new NodeValue() {
    @Override
    public String getValue(Node node) {
      log.debug3("getValue of baywood date");
      String dateStr = node.getTextContent();
      if (dateStr.length() == 8) {
        StringBuilder formatBldr = new StringBuilder();
        formatBldr.append(dateStr.substring(0,4));
        formatBldr.append("-");
        formatBldr.append(dateStr.substring(4,6));
        formatBldr.append("-");
        formatBldr.append(dateStr.substring(6,8));
        dateStr = formatBldr.toString();
        log.debug3("reformatted date string to :" + dateStr);
      }
      return dateStr;
    }
  };


  /* 
   *  SageBaywood (really Metapress XML) specific XPATH key definitions that we care about
   *  There is only one article per xml file and the 
   *  filename.xml == filename.pdf == Article PII (internal ID)
   */

  private static String BAY_journal_info_node = "/Publisher/Journal/JournalInfo";
  private static String BAY_volume_info_node = "/Publisher/Journal/Volume/VolumeInfo";
  private static String BAY_issue_info_node = "/Publisher/Journal/Volume/Issue/IssueInfo";
  private static String BAY_article_node = "/Publisher/Journal/Volume/Issue/Article";
  
  //journal level
  private static String BAY_jtitle = BAY_journal_info_node + "/JournalTitle";
  private static String BAY_eissn = BAY_journal_info_node + "/JournalElectronicISSN";
  private static String BAY_pissn = BAY_journal_info_node + "/JournalPrintISSN";
  
  // volume/issue level
  private static String BAY_volume = BAY_volume_info_node + "/VolumeNumber";
  private static String BAY_issue = BAY_issue_info_node + "/IssueNumberBegin"; // use evaluator in case > 1 number
  
  // article level
  private static String BAY_doi =  BAY_article_node + "/ArticleInfo/ArticleDOI";
  private static String BAY_atitle = BAY_article_node + "/ArticleInfo/ArticleTitle";
  private static String BAY_fpage = BAY_article_node + "/ArticleInfo/ArticleFirstPage";
  private static String BAY_lpage = BAY_article_node + "/ArticleInfo/ArticleLastPage"; 
  private static String BAY_author = BAY_article_node + "/ArticleHeader/AuthorGroup/Author"; 
  private static String BAY_adate = BAY_article_node + "/ArticleInfo/ArticleHistory/OnlineDate";
  
  /*
   *  The following 3 variables are needed to construct the XPathXmlMetadataParser
   */
  
  /* 1.  MAP associating xpath with value type with evaluator */
  static private final Map<String,XPathValue> BAY_articleMap = 
      new HashMap<String,XPathValue>();
  static {
    BAY_articleMap.put(BAY_jtitle, XmlDomMetadataExtractor.TEXT_VALUE);
    BAY_articleMap.put(BAY_pissn, XmlDomMetadataExtractor.TEXT_VALUE);
    BAY_articleMap.put(BAY_eissn, XmlDomMetadataExtractor.TEXT_VALUE);
    BAY_articleMap.put(BAY_doi, XmlDomMetadataExtractor.TEXT_VALUE);
    BAY_articleMap.put(BAY_atitle, XmlDomMetadataExtractor.TEXT_VALUE);
    BAY_articleMap.put(BAY_volume, XmlDomMetadataExtractor.TEXT_VALUE);
    BAY_articleMap.put(BAY_issue, BAYWOOD_ISSUE_VALUE); // evaluator needed
    BAY_articleMap.put(BAY_fpage, XmlDomMetadataExtractor.TEXT_VALUE);
    BAY_articleMap.put(BAY_lpage, XmlDomMetadataExtractor.TEXT_VALUE);
    BAY_articleMap.put(BAY_adate, BAYWOOD_DATE_VALUE);
    BAY_articleMap.put(BAY_author, BAYWOOD_AUTHOR_VALUE);

  }

  /* 2. Each item (article) has its own XML file */
  static private final String BAY_articleNode = BAY_article_node; 

  /* 3. in BAY there is no global information because one file/article */
  static private final Map<String,XPathValue> BAY_globalMap = null;

  /*
   * The emitter will need a map to know how to cook ONIX raw values
   */
  private static final MultiValueMap cookMap = new MultiValueMap();
  static {
    // do NOT cook publisher_name; get from TDB file for consistency
    // also get PROVIDER from the TDB file
    cookMap.put(BAY_jtitle, MetadataField.FIELD_PUBLICATION_TITLE);
    cookMap.put(BAY_atitle, MetadataField.FIELD_ARTICLE_TITLE);
    cookMap.put(BAY_doi, MetadataField.FIELD_DOI);
    cookMap.put(BAY_pissn, MetadataField.FIELD_ISSN);
    cookMap.put(BAY_eissn, MetadataField.FIELD_EISSN);
    // do not use the metadata publisher - it's been bought by sage
    cookMap.put(BAY_volume, MetadataField.FIELD_VOLUME);
    cookMap.put(BAY_issue, MetadataField.FIELD_ISSUE);
    cookMap.put(BAY_fpage, MetadataField.FIELD_START_PAGE);
    cookMap.put(BAY_lpage, MetadataField.FIELD_END_PAGE);
    cookMap.put(BAY_author, MetadataField.FIELD_AUTHOR);
    cookMap.put(BAY_adate, MetadataField.FIELD_DATE);
    
  }


  /**
   * BAY does not contain needed global information outside of article records
   * return NULL
   */
  @Override
  public Map<String, XPathValue> getGlobalMetaMap() {
    return BAY_globalMap;
  }

  /**
   * return BAY article map to identify xpaths of interest
   */
  @Override
  public Map<String, XPathValue> getArticleMetaMap() {
    return BAY_articleMap;
  }

  /**
   * Return the article node path
   * There is only one article per xml file so the top of the document is the
   * article and all paths are relative do document.
   */
  @Override
  public String getArticleNode() {
    return null;
  }

  /**
   * Return a map to translate raw values to cooked values
   */
  @Override
  public MultiValueMap getCookMap() {
    return cookMap;
  }

  /**
   * No duplicate data 
   */
  @Override
  public String getDeDuplicationXPathKey() {
    return null;
  }

  /**
   * No consolidation required
   */
  @Override
  public String getConsolidationXPathKey() {
    return null;
  }

  /**
   * The filenames are the same as the XML filenames with .pdf suffix
   */
  @Override
  public String getFilenameXPathKey() {
    return null;
  }

}
