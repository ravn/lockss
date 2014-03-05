/*
 * $Id: TaylorAndFrancisSourceZipXmlArticleIteratorFactory.java,v 1.1 2014-03-05 19:38:47 alexandraohlson Exp $
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

package org.lockss.plugin.taylorandfrancis;

import java.util.Iterator;
import java.util.regex.Pattern;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.lockss.plugin.clockss.SourceZipXmlArticleIteratorFactory;
import org.lockss.util.Logger;

//
// A slight variation on the generic CLOCKSS source zip article iterator
// this one just excludes xml files that start with CATS as they are 
// redundant and in a different schema - probably the catalog version of 
// information, not the xml of the actual articles
// But not available for every volume so we're using the other xml schema
// to extract metadata
//
public class TaylorAndFrancisSourceZipXmlArticleIteratorFactory extends SourceZipXmlArticleIteratorFactory {

  protected static Logger log = Logger.getLogger(TaylorAndFrancisSourceZipXmlArticleIteratorFactory.class);
  
  // ROOT_TEMPLATE doesn't need to be defined as sub-tree is entire tree under base/year
  // This pattern is specific to TandF - exclude the XML that start with "CATS_"
  private static final String PATTERN_TEMPLATE = 
      "\"%s%d/.*\\.zip!/(?!.*CATS_).*\\.xml$\", base_url, year";

  public static final Pattern XML_PATTERN = Pattern.compile("/(.*)\\.xml$", Pattern.CASE_INSENSITIVE);
  public static final String XML_REPLACEMENT = "/$1.xml";
  
  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
                                                      MetadataTarget target)
      throws PluginException {
    SubTreeArticleIteratorBuilder builder = new SubTreeArticleIteratorBuilder(au);
    
    // no need to limit to ROOT_TEMPLATE
    builder.setSpec(builder.newSpec()
                    .setTarget(target)
                    .setPatternTemplate(PATTERN_TEMPLATE, Pattern.CASE_INSENSITIVE)
                    .setVisitArchiveMembers(true)); // to be able to see what is in zip
    
    // NOTE - full_text_cu is set automatically to the url used for the articlefiles
    // ultimately the metadata extractor needs to set the entire facet map 

    // set up XML to be an aspect that will trigger an ArticleFiles to feed the metadata extractor
    builder.addAspect(XML_PATTERN,
                      XML_REPLACEMENT,
                      ArticleFiles.ROLE_ARTICLE_METADATA);

    return builder.getSubTreeArticleIterator();
  }
  
}
