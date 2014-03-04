/*
 * $Id: SourceXmlMetadataExtractorFactory.java,v 1.17 2014-03-04 21:32:58 alexandraohlson Exp $
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

import org.apache.commons.io.FilenameUtils;
import org.apache.cxf.common.util.StringUtils;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import java.io.IOException;
import java.util.*;

import javax.xml.xpath.XPathExpressionException;

import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.clockss.SourceXmlSchemaHelper;
import org.lockss.plugin.clockss.XPathXmlMetadataParser;
import org.xml.sax.SAXException;


/**
 *  A factory to create an generic SourceXmlMetadataExtractor
 *  @author alexohlson
 */
public abstract class SourceXmlMetadataExtractorFactory
implements FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger(SourceXmlMetadataExtractorFactory.class);


  /**
   * The generic class to handle metadata extraction for source content
   * that uses XML with a set schema.
   * A specific plugin must subclass this. While it might inherit and use
   * the vast majority, at a minimum it must define the schema by overriding
   * the "setUpSchema()" method 
   * @author alexohlson
   *
   */
  public static abstract class SourceXmlMetadataExtractor 
  implements FileMetadataExtractor {


    /**
     *  Look at the AU to determine which type of XML will be processed and 
     *  then create an XPathXmlMetadataParser to handle the specified XML files
     *  by using the appropriate helper to pass information to the parser.
     *  Take the resulting list of ArticleMetadata objects, 
     *  optionaly consolidate it to remove redundant records  
     *  and then check for content file existence based on file naming
     *  information set up by the helper before cooking and emitting
     *  the metadata.
     */
    @Override
    public void extract(MetadataTarget target, CachedUrl cu, Emitter emitter)
        throws IOException, PluginException {
      try {

        SourceXmlSchemaHelper schemaHelper;
        // 1. figure out which XmlMetadataExtractorHelper class to use to get
        // the schema specific information
        if ((schemaHelper = setUpSchema(cu)) == null) {
          log.debug("Unable to set up XML schema. Cannot extract from XML");
          throw new PluginException("XML schema not set up for " + cu.getUrl());
        }     

        // 2. Gather all the metadata in to a list of AM records
        // XPathXmlMetadataParser is not thread safe, must be called each time
        List<ArticleMetadata> amList = 
            new XPathXmlMetadataParser(schemaHelper.getGlobalMetaMap(), 
                schemaHelper.getArticleNode(), 
                schemaHelper.getArticleMetaMap(),
                getDoXmlFiltering()).extractMetadata(target, cu);


 
        //3. Optional consolidation of duplicate records within one XML file
        // a child plugin can leave the default (no deduplication) or 
        // AMCollection pointing to just a subset of the full
        // AM list
        // 3. Consolidate identical records based on DeDuplicationXPathKey
        // consolidating as specified by the consolidateRecords() method
        
        Collection<ArticleMetadata> AMCollection = getConsolidatedAMList(schemaHelper,
            amList);

        // 4. check, cook, and emit every item in resulting AM collection (list)
        for ( ArticleMetadata oneAM : AMCollection) {
          if (preEmitCheck(schemaHelper, cu, oneAM)) {
            oneAM.cook(schemaHelper.getCookMap());
            postCookProcess(schemaHelper, cu, oneAM); // hook for optional processing
            emitter.emitMetadata(cu,oneAM);
          }
        }

      } catch (XPathExpressionException e) {
        log.debug3("Xpath expression exception:",e);
      } catch (SAXException ex) {
        handleSAXException(cu, emitter, ex);
      } catch (IOException ex) {
        handleIOException(cu, emitter, ex);
      }


    }

    // Overrideable method for plugins that want to catch and handle
    // a problem in the XML file
    protected void handleIOException(CachedUrl cu, Emitter emitter, IOException ex) {
      // Add an alert or more significant warning here
      log.siteWarning("IO exception loading XML file", ex);
    }

    // Overrideable method for plugins that want to catch and handle
    // a SAX parser problem in the XML file
    protected void handleSAXException(CachedUrl cu, Emitter emitter, SAXException ex) {
      // Add an alert or more significant warning here
      log.siteWarning("SAX exception loading XML file", ex);

    }

 
    /**
     * Verify that a content file exists described in the xml record actually
     * exists based on the XML path location.This also works even if content is 
     * zipped.<br/>
     * The schema helper defines a filename prefix (which could include path
     * information for a subdirectory or sibling directory) as well as a 
     * possible filename component based on a record item (using filenameXPathKey) 
     * and possibly multiple suffixes to handle filetype options.
     * If filenamePrefix, filenameXPathKey and filenameSuffixes are all null, 
     * no preEmitCheck occurs.<br/>
     * For more complicated situations, a child  might want to override this 
     * function entirely.
     * @param schemaHelper for thread safe access to schema specific information 
     * @param cu
     * @param thisAM
     * @return
     */
    protected boolean preEmitCheck(SourceXmlSchemaHelper schemaHelper, 
        CachedUrl cu, ArticleMetadata thisAM) {

      log.debug3("in SourceXmlMetadataExtractor preEmitCheck");
      
      ArrayList<String> filesToCheck;

      // If no files get returned in the list, nothing to check
      if ((filesToCheck = getFilenamesAssociatedWithRecord(schemaHelper, cu,thisAM)) == null) {
        return true;
      }
      ArchivalUnit B_au = cu.getArchivalUnit();
      CachedUrl fileCu;
      for (int i=0; i < filesToCheck.size(); i++) 
      { 
        fileCu = B_au.makeCachedUrl(filesToCheck.get(i));
        log.debug3("Check for existence of " + filesToCheck.get(i));
        if(fileCu != null && (fileCu.hasContent())) {
          // Set a cooked value for an access file. Otherwise it would get set to xml file
          thisAM.put(MetadataField.FIELD_ACCESS_URL, fileCu.getUrl());
          return true;
        }
      }
      log.debug3("No file exists associated with this record");
      return false; //No files found that match this record
    }

    // a routine to allow a child to add in some post-cook processing for each
    // AM record (eg. "putifbetter"
    // Default is to do nothing
    protected void postCookProcess(SourceXmlSchemaHelper schemaHelper, 
        CachedUrl cu, ArticleMetadata thisAM) {

      log.debug3("in SourceXmlMetadataExtractor postEmitProcess");
    }
    
    /**
     * A routine used by preEmitCheck to know which files to check for
     * existence. 
     * It returns a list of strings, each string is a
     * complete url for a file that could be used to check for whether a cu
     * with that name exists and has content.
     * If the returned list is null, preEmitCheck returns TRUE
     * If any of the files in the list is found and exists, preEmitCheck 
     * returns TRUE. It stops after finding one.
     * If the list is not null and no file exists, preEmitCheck returns FALSE
     * The first existing file from the list gets set as the access URL.
     * The child plugin could override preEmitCheck for different results.
     * The base version of this returns the value of the schema helper's value at
     * getFilenameXPathKey in the same directory as the XML file.
     * @param cu
     * @param oneAM
     * @return
     */
    protected ArrayList<String> getFilenamesAssociatedWithRecord(SourceXmlSchemaHelper helper, 
        CachedUrl cu,
        ArticleMetadata oneAM) {
      
      // get the key for a piece of metadata used in building the filename
      String fn_key = helper.getFilenameXPathKey();  
      // the schema doesn't define a filename so don't do a default preEmitCheck
      if (fn_key == null) {
        return null; // no preEmitCheck 
      }
      String filenameValue = oneAM.getRaw(helper.getFilenameXPathKey());
      // we expected a value, but didn't get one...we need to return something
      // for preEmitCheck to fail
      if (filenameValue == null) {
        filenameValue = "NOFILEINMETADATA"; // we expected a value, but got none
      }
      String cuBase = FilenameUtils.getFullPath(cu.getUrl());
      ArrayList<String> returnList = new ArrayList<String>();
      // default version is just the filename associated with the key, in this directory
      returnList.add(cuBase + filenameValue);
      return returnList;
    }
    
    /* default for a plugin is to NOT special filter the XML when loading */
    public boolean getDoXmlFiltering() {
      return false;
    }

    /*
     * Default behavior is not to consolidate
     * A child plugin can choose to override this
     */
    protected Collection<ArticleMetadata> getConsolidatedAMList(SourceXmlSchemaHelper helper,
        List<ArticleMetadata> allAMs) {
      return allAMs;
    }

    /* 
     * A particular XML extractor might inherit the rest of the base methods
     * but it MUST implement a definition for a specific schema
     */
    protected abstract SourceXmlSchemaHelper setUpSchema();

    // The default just calls the setUpSchema()
    // But this allows and optional alternative a plugin could override
    // for when it needs to choose schema type AFTER looking at the CU
    // See TaylorAndFrancisSourceXmlMetadataExtractor
    protected SourceXmlSchemaHelper setUpSchema(CachedUrl cu) {
      return setUpSchema();
    }

  }
}
