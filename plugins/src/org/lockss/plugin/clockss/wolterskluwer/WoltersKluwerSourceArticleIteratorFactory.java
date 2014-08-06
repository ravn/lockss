/*
 * $Id: WoltersKluwerSourceArticleIteratorFactory.java,v 1.3 2014-08-06 17:27:45 alexandraohlson Exp $
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

package org.lockss.plugin.clockss.wolterskluwer;

import java.io.IOException;
import java.util.Iterator;
import java.util.regex.*;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.ArticleMetadata;
import org.lockss.extractor.ArticleMetadataExtractorFactory;
import org.lockss.extractor.BaseArticleMetadataExtractor;
import org.lockss.extractor.FileMetadataExtractor;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

public class WoltersKluwerSourceArticleIteratorFactory
  implements ArticleIteratorFactory, ArticleMetadataExtractorFactory {

  protected static Logger log = 
      Logger.getLogger(WoltersKluwerSourceArticleIteratorFactory.class);

  protected static final String ROOT_TEMPLATE = "\"%s\", base_url, ";
  // suffix has only ever been one digit, may be 0-9
  // 2014/CIRC20140304.0.zip!/20140304.0
  private static final String PATTERN_TEMPLATE = 
      "\"%s%d/[^/]+\\.zip!/(.*)\\.[\\d]$\",base_url,year";
  
  public static final Pattern XML_PATTERN = Pattern.compile("/(.*)\\.[\\d]$", Pattern.CASE_INSENSITIVE);
  public static final String XML_REPLACEMENT = "/$1.[\\d]";
  public static final String SGML_SUFFIX = ".0";
  public static final String SGML_CONTENT_TYPE = "application/xml";
  
  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au, MetadataTarget target) throws PluginException {
    SubTreeArticleIteratorBuilder builder = new SubTreeArticleIteratorBuilder(au);
    
    SubTreeArticleIterator.Spec theSpec = builder.newSpec();
    theSpec.setRootTemplate(ROOT_TEMPLATE);
    theSpec.setTarget(target);
    theSpec.setPatternTemplate(PATTERN_TEMPLATE, Pattern.CASE_INSENSITIVE);
    /* this is necessary to be able to see what's inside the zip file */
    theSpec.setVisitArchiveMembers(true);
    builder.setSpec(theSpec);

    // NOTE - full_text_cu is set automatically to the url used for the articlefiles
    // ultimately the metadata extractor needs to set the entire facet map 

    // set up XML to be an aspect that will trigger an ArticleFiles to feed the metadata extractor
    builder.addAspect(XML_PATTERN,
        XML_REPLACEMENT,
        ArticleFiles.ROLE_ARTICLE_METADATA);

    return builder.getSubTreeArticleIterator();
  }
  
  public ArticleMetadataExtractor createArticleMetadataExtractor(
                                                        MetadataTarget target)
      throws PluginException {
    return new BaseArticleMetadataExtractor(ArticleFiles.ROLE_ARTICLE_METADATA) {

      /* (non-Javadoc)
       * @see org.lockss.extractor.BaseArticleMetadataExtractor#extract(org.lockss.extractor.MetadataTarget, org.lockss.plugin.ArticleFiles, org.lockss.extractor.ArticleMetadataExtractor.Emitter)
       */
      class MyEmitter implements FileMetadataExtractor.Emitter {
        private Emitter parent;
        private ArticleFiles af;
       

        MyEmitter(ArticleFiles af, Emitter parent) {
          this.af = af;
          this.parent = parent;
        }

        public void emitMetadata(CachedUrl cu, ArticleMetadata am) {

          if (isAddTdbDefaults()) {
            addTdbDefaults(af, cu, am);
          }
          parent.emitMetadata(af, am);
        }

      }
      @Override
      public void extract(MetadataTarget target, ArticleFiles af,
          Emitter emitter) throws IOException, PluginException {
        MyEmitter myEmitter = new MyEmitter(af, emitter);
        CachedUrl cu = getCuToExtract(af);
        if (log.isDebug3()) log.debug3("extract(" + af + "), cu: " + cu);
        if (cu != null) {
          try {
            FileMetadataExtractor me = cu.getFileMetadataExtractor(target);
            // TODO: 1.67 - Tom promises to put a way to set the ContentType in the daemon
            // here's where we differ from superclass implementation
            // if the me is null because the contentType==null because the sgml-based
            // metadata file is not recognized, THEN we want to fix that...
            if (me == null) {
              // if this is the sgml file (only one that ends in ".0")
              if ((cu.getContentType() == null) && (cu.getUrl().endsWith(SGML_SUFFIX))){
                String ct = SGML_CONTENT_TYPE; //"application/xml"
                me = cu.getArchivalUnit().getFileMetadataExtractor(target, ct);
              }
            }
            if (me != null) {
              me.extract(target, cu, myEmitter);
              AuUtil.safeRelease(cu);
              return;
            } 
          } catch (IOException ex) {
            log.warning("Error in FileMetadataExtractor", ex);
          }
        } else {
          // use full-text CU if cuRole CU not available
          cu = af.getFullTextCu();
          if (log.isDebug3()) {
            log.debug3("Missing CU for role " + cuRole 
                          + ". Using fullTextCU " + af.getFullTextUrl());
          }
        }
        if (cu != null) {
          // emit at least basic metadata for the selected CU 
          // if no article metadata is available
          ArticleMetadata am = new ArticleMetadata();
          myEmitter.emitMetadata(cu, am);
          AuUtil.safeRelease(cu);
        }
      }
       
    };
  }
  
}
