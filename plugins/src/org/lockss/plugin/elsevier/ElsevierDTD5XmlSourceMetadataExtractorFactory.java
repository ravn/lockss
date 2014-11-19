/*
 * $Id: ElsevierDTD5XmlSourceMetadataExtractorFactory.java,v 1.3 2014-11-19 00:50:18 alexandraohlson Exp $
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

package org.lockss.plugin.elsevier;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathExpressionException;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.ArticleMetadata;
import org.lockss.extractor.FileMetadataExtractor;
import org.lockss.extractor.MetadataField;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArticleFiles;
import org.lockss.plugin.AuUtil;
import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.SubTreeArticleIterator;
import org.lockss.plugin.SubTreeArticleIteratorBuilder;
import org.lockss.plugin.clockss.SourceXmlMetadataExtractorFactory;
import org.lockss.plugin.clockss.SourceXmlSchemaHelper;
import org.lockss.plugin.clockss.XPathXmlMetadataParser;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;
import org.xml.sax.SAXException;

public class ElsevierDTD5XmlSourceMetadataExtractorFactory extends SourceXmlMetadataExtractorFactory {
  static Logger log = Logger.getLogger(ElsevierDTD5XmlSourceMetadataExtractorFactory.class);

  // Used in modifyAMList to identify the name for the current SET of tar files 
  static final Pattern TOP_METADATA_PATTERN = Pattern.compile("(.*/)[^/]+A\\.tar!/([^/]+)/dataset\\.xml$", Pattern.CASE_INSENSITIVE);
  // used to exclude underlying archives so we don't open them
  static final Pattern NESTED_ARCHIVE_PATTERN = Pattern.compile(".*/[^/]+[A-Z]\\.tar!/.+\\.(zip|tar|gz|tgz|tar\\.gz)$", Pattern.CASE_INSENSITIVE);
  

  // Use this map to determine which node to use for underlying article schema
  static private final Map<String, String> SchemaMap =
      new HashMap<String,String>();
  static {
    SchemaMap.put("JA 5.2.0 ARTICLE", "/article/head");
    SchemaMap.put("JA 5.2.0 SIMPLE-ARTICLE", "/simple-article/simple-head"); 
    SchemaMap.put("JA 5.2.0 BOOK-REVIEW", "/book-review/book-review-head");
    // will need to add support for JA 5.2.0 BOOK, once we have examples
  }

  private static SourceXmlSchemaHelper ElsevierDTD5PublishingHelper = null;

  // one delivery, eg CLKS0000000000003.tar is broken in to 
  //         CLKS0000000000003A.tar, CLKS0000000000003B.tar, ....
  // we extract metadate for the entire group from only one file
  //    CLKS<#>A.tar/dataset.xml (top level metadata for entire delivery)
  // but before we cook the resulting AMList, we do a second iteration
  // over all the tar files looking for low-level "main.xml" files
  // from in the same number group and create a map of
  // underlying articles to their specific tar files.
  // We use this map to check for the existence of the correct "main.pdf" 
  // and to extract the final necessary metadata - article title and authors


  private static final Pattern XML_PATTERN = Pattern.compile("/(.*)\\.xml$", Pattern.CASE_INSENSITIVE);
  private static final String XML_REPLACEMENT = "/$1.xml";

  @Override
  public FileMetadataExtractor createFileMetadataExtractor(MetadataTarget target,
      String contentType)
          throws PluginException {
    return new ElsevierDTD5XmlSourceMetadataExtractor();
  }

  public class ElsevierDTD5XmlSourceMetadataExtractor extends SourceXmlMetadataExtractor {



    /* 
     * This must live in the extractor, not the extractor factory
     * There must be one for each SET of related tar files, not per AU
     */
    private final Map<String,String> TarContentsMap;
    public ElsevierDTD5XmlSourceMetadataExtractor() {
      //log.setLevel("debug3");
      TarContentsMap = new HashMap<String, String>();
    }

    /*
     * This version of the method is abstract and must be implemented but should
     * be deprecated and ultimately removed in favor of the one that takes a 
     * CachedUrl
     */
    @Override
    protected SourceXmlSchemaHelper setUpSchema() {
      return null; // cause a plugin exception to get thrown
    }

    @Override
    protected SourceXmlSchemaHelper setUpSchema(CachedUrl cu) {
      // Once you have it, just keep returning the same one. It won't change.
      if (ElsevierDTD5PublishingHelper != null) {
        return ElsevierDTD5PublishingHelper;
      }
      ElsevierDTD5PublishingHelper = new ElsevierDTD5XmlSchemaHelper();
      return ElsevierDTD5PublishingHelper;
    }


    /*
     * (non-Javadoc)
     * Before we emit, we want to check if the related PDF file exists AND
     * set the access_url AND 
     * we pull the title and author from the article level main.xml file
     * Use the TarContentsMap we created to associate relative article xml filename 
     * with its actual location in a specific cu.
     */
    @Override
    protected boolean preEmitCheck(SourceXmlSchemaHelper schemaHelper, 
        CachedUrl cu, ArticleMetadata thisAM) {
      ArchivalUnit B_au = cu.getArchivalUnit();

      // The schema tells us which raw metadata value points to the correct article xml file
      String key_for_filename = schemaHelper.getFilenameXPathKey();

      // Use the map created earlier to locate the file from it's relative path
      String full_article_md_file = TarContentsMap.get(thisAM.getRaw(key_for_filename));
      log.debug3("full_article_md_file is : " + thisAM.getRaw(key_for_filename));
      if (full_article_md_file == null) {
        return false;
      }

      /*
       * 1. Check for existence of PDF file; otherwise return false & don't emit
       */
      // pdf file has the same name as the xml file, but with ".pdf" suffix

      CachedUrl fileCu = null;
      CachedUrl mdCu = null;;
      try {
        String full_article_pdf = full_article_md_file.substring(0,full_article_md_file.length() - 3) + "pdf"; 
        fileCu = B_au.makeCachedUrl(full_article_pdf);
        log.debug3("Check for existence of " + full_article_pdf);
        if(fileCu != null && (fileCu.hasContent())) {
          thisAM.put(MetadataField.FIELD_ACCESS_URL, fileCu.getUrl());
          /* 
           * 2. Now get remaining metadata from the article xml file 
           */
          mdCu = B_au.makeCachedUrl(full_article_md_file);
          /*
           * This is defensive programming. It's not clear how this could happen.
           * Since we got the full_article_md_file from the map, we know it's in
           * the AU. So an error here is a sign of a big problem.
           */
          if(mdCu == null || !(mdCu.hasContent())) {
            log.siteWarning("The stored article XML file is no longer accessible");
            return true; 
          }
          extractRemainingMetadata(thisAM, mdCu);
          return true;
        }
      } finally {
        AuUtil.safeRelease(fileCu);
        AuUtil.safeRelease(mdCu);
      }
      log.debug3("No pdf file exists associated with this record - don't emit");
      return false; 
    }


    /*
     *  Pull the article title and author information from the low-level
     *  "main.xml" file 
     *    thisAM - where to put the informmation once it's been extracted
     *    mdCu - the CU from which to pull the information
     *  
     *  If there is some issue extracting from the file, just return
     */
    private void extractRemainingMetadata(ArticleMetadata thisAM,
        CachedUrl mdCu) {

      // Which top node is appropriate for this specific dtd
      String dtdString = thisAM.getRaw(ElsevierDTD5XmlSchemaHelper.dataset_dtd_metadata);
      String top_node = SchemaMap.get(dtdString);
      if (top_node == null) {
        log.debug3("unknown dtd for article level metadata");
        return; // we can't extract
      }
      try {
        List<ArticleMetadata> amList = 
            new XPathXmlMetadataParser(null, 
                top_node,
                ElsevierDTD5XmlSchemaHelper.articleLevelMDMap,
                false).extractMetadata(MetadataTarget.Any(), mdCu);
        /*
         * There should only be ONE top_node per main.xml; don't verify
         * but just access first one.
         */
        if (amList.size() > 0) {
          log.debug3("found article level metadata...");
          ArticleMetadata oneAM = amList.get(0);
          String rawVal = oneAM.getRaw(ElsevierDTD5XmlSchemaHelper.common_title);
          if (rawVal != null) {
            thisAM.putRaw(ElsevierDTD5XmlSchemaHelper.common_title, rawVal);
          }
          rawVal = oneAM.getRaw(ElsevierDTD5XmlSchemaHelper.common_author_group);
          if ( rawVal != null) {
            thisAM.putRaw(ElsevierDTD5XmlSchemaHelper.common_author_group, rawVal);
          }
        } else {
          log.debug3("no md extracted from " + mdCu.getUrl());
        }
      } catch (XPathExpressionException e) {
        log.debug3("Xpath expression exception:",e); // this is a note to the PLUGIN writer!
      } catch (IOException e) {
        // We going to keep going and just not extract from this file
        log.siteWarning("IO exception loading article level XML file", e);
      } catch (SAXException e) {
        // We going to keep going and just not extract from this file
        log.siteWarning("SAX exception loading article level XML file", e);
      }      
    }

    /* 
     * This will get called ONCE for each dataset.xml 
     * and therefore once per delivery set (CLKS#A.tar, CLKS#B.tar...)
     *    with the same unique file number
     *  Use this opportunity to generate a map identifying which specific tar a particular
     *   article lives in
     *  We generate the ARTICLE_METADATA_PATTERN here because we need the current cu, to limit
     *   the results to just the set of tar files with the same unique file number.  
     */
    protected Collection<ArticleMetadata> modifyAMList(SourceXmlSchemaHelper helper,
        CachedUrl cu, List<ArticleMetadata> allAMs) {

      Matcher mat = TOP_METADATA_PATTERN.matcher(cu.getUrl());
      Pattern ARTICLE_METADATA_PATTERN = null;
      if (mat.matches()) {
        String pattern_string = mat.group(1) + mat.group(2) + "[A-Z]\\.tar!/" + mat.group(2) + "/.*/main\\.xml$";
        log.debug3("Iterate and find the pattern: " + pattern_string);
        ARTICLE_METADATA_PATTERN = Pattern.compile(pattern_string, Pattern.CASE_INSENSITIVE);

        // Now create the map of files to the tarfile they're in
        ArchivalUnit au = cu.getArchivalUnit();
        SubTreeArticleIteratorBuilder articlebuilder = new SubTreeArticleIteratorBuilder(au);
        SubTreeArticleIterator.Spec artSpec = articlebuilder.newSpec();
        // Limit it just to this group of tar files
        artSpec.setPattern(ARTICLE_METADATA_PATTERN); // look for url-ending "main.xml" files
        artSpec.setExcludeSubTreePattern(NESTED_ARCHIVE_PATTERN); //but do not descend in to any underlying archives
        artSpec.setVisitArchiveMembers(true);
        articlebuilder.setSpec(artSpec);
        articlebuilder.addAspect(XML_PATTERN,
            XML_REPLACEMENT,
            ArticleFiles.ROLE_ARTICLE_METADATA);

        for (SubTreeArticleIterator art_iterator = articlebuilder.getSubTreeArticleIterator();
            art_iterator.hasNext(); ) {
          // because we haven't set any roles, the AF will be what the iterator matched
          String article_xml_url = art_iterator.next().getFullTextCu().getUrl();
          log.debug3("tar map iterator found: " + article_xml_url);
          int tarspot = StringUtil.indexOfIgnoreCase(article_xml_url, ".tar!/");
          int dividespot = StringUtil.indexOfIgnoreCase(article_xml_url, "/", tarspot+6);
          TarContentsMap.put(article_xml_url.substring(dividespot + 1), article_xml_url);
          log.debug3("TarContentsMap add key: " + article_xml_url.substring(dividespot + 1));
        }
      } else {
        log.warning("ElsevierDTD5: Unable to create article-level map for " + cu.getUrl() + " - metadata will not include article titles or useful access.urls");
      }
      return  allAMs;
    }
  }

}
