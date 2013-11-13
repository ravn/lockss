/*
 * $Id: OnixBooksZipSourceArticleIteratorFactory.java,v 1.1 2013-11-13 18:47:37 alexandraohlson Exp $
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

package org.lockss.plugin.clockss.onixbooks;

import java.util.Iterator;
import java.util.regex.*;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.ArticleMetadataExtractorFactory;
import org.lockss.extractor.BaseArticleMetadataExtractor;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

public class OnixBooksZipSourceArticleIteratorFactory implements ArticleIteratorFactory, ArticleMetadataExtractorFactory {

  protected static Logger log = Logger.getLogger(OnixBooksZipSourceArticleIteratorFactory.class);

  // ROOT_TEMPLATE doesn't need to be defined as sub-tree is entire tree under base/year
  //Zip file is a flat directory that has both content and xml file in it
  private static final String PATTERN_TEMPLATE = 
      "\"%s%d/[^/]+\\.zip!/(.*)\\.xml$\",base_url,year";
  //
  // The source content structure looks like this:
  // <root_location>/<year>/<BigZipBall>.zip
  //     where theunderlying structure is one big directory containing
  //         Stanford to CLOCKSS/<name>.pdf - the book in pdf format
  //         Stanford to CLOCKSS/<name>.jpg - the cover for the book in <name>.pdf
  //  as well as one other <blah>.xml file which are ONOIX for books
  //  metadata for the accompanying files 
  //      //    correlation between the name of the XML file and the name of the pdf files it contains
  //    BUT the name of the format=15 productIdentifier is the isbn13 which is the <name> for both pdf
  //
  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au, MetadataTarget target) throws PluginException {
    SubTreeArticleIteratorBuilder builder = localBuilderCreator(au);
    
    final Pattern XML_PATTERN = Pattern.compile("/(.*)\\.xml$", Pattern.CASE_INSENSITIVE);
    final String XML_REPLACEMENT = "/$1.xml";

    // no need to limit to ROOT_TEMPLATE
    SubTreeArticleIterator.Spec theSpec = new SubTreeArticleIterator.Spec();
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
  
  // Enclose the method that creates the builder to allow a child to do additional processing
  protected SubTreeArticleIteratorBuilder localBuilderCreator(ArchivalUnit au) { 
   return new SubTreeArticleIteratorBuilder(au);
  }
  
  
  @Override
  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
    throws PluginException {
    return new BaseArticleMetadataExtractor(ArticleFiles.ROLE_ARTICLE_METADATA);
  }
}
