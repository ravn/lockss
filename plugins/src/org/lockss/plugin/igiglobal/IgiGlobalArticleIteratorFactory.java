/*
 * $Id: IgiGlobalArticleIteratorFactory.java,v 1.2.4.2 2012-06-20 00:03:03 nchondros Exp $
 */

/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.igiglobal;

import java.util.Iterator;
import java.util.regex.*;

import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.lockss.util.Constants;
import org.lockss.util.Logger;

/*
 * PDF Full Text: http://www.igi-global.com/article/full-text-pdf/56564
 * HTML Abstract: http://www.igi-global.com/article/56564
 */

public class IgiGlobalArticleIteratorFactory
    implements ArticleIteratorFactory,
               ArticleMetadataExtractorFactory {

  protected static Logger log = Logger.getLogger("IgiArticleIteratorFactory");
  
  protected static final String ROOT_TEMPLATE = "\"%sgateway/article/\", base_url"; // params from tdb file corresponding to AU
  
  protected static final String PATTERN_TEMPLATE = "\"^%sgateway/article/full-text-pdf/\", base_url";

  
  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
                                                      MetadataTarget target)
      throws PluginException {
    return new MassachusettsMedicalSocietyArticleIterator(au,
                                         new SubTreeArticleIterator.Spec()
                                             .setTarget(target)
                                             .setRootTemplate(ROOT_TEMPLATE)
                                             .setPatternTemplate(PATTERN_TEMPLATE));
  }

  protected static class MassachusettsMedicalSocietyArticleIterator extends SubTreeArticleIterator {

    protected Pattern ABSTRACT_PATTERN = Pattern.compile("article/([0-9]+)$", Pattern.CASE_INSENSITIVE);
    
    protected Pattern PDF_PATTERN = Pattern.compile("article/full-text-pdf/([0-9]+)$", Pattern.CASE_INSENSITIVE);
    
    public MassachusettsMedicalSocietyArticleIterator(ArchivalUnit au,
                                     SubTreeArticleIterator.Spec spec) {
      super(au, spec);
    }
    
    @Override
    protected ArticleFiles createArticleFiles(CachedUrl cu) {
      String url = cu.getUrl();
      log.info("article url?: " + url);
      Matcher mat;
      mat = PDF_PATTERN.matcher(url);
      if (mat.find()) {
        return processFullTextPdf(cu, mat);
      }

      log.warning("Mismatch between article iterator factory and article iterator: " + url);
      return null;
    }
    

    protected ArticleFiles processFullTextPdf(CachedUrl pdfCu, Matcher pdfMat) {
      ArticleFiles af = new ArticleFiles();
      af.setFullTextCu(pdfCu);
      if (spec.getTarget() != MetadataTarget.Article) {
          guessAbstract(af, pdfMat);
      }
      return af;
    }
    
    protected void guessAbstract(ArticleFiles af, Matcher mat) {
      String absUrlBase = mat.replaceFirst("article/$1");
      CachedUrl absCu = au.makeCachedUrl(absUrlBase);
      if (absCu != null && absCu.hasContent()) {
    	  af.setRoleCu(ArticleFiles.ROLE_ABSTRACT, absCu);
      } 
    }
  }
  
  @Override
  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
      throws PluginException {
    return new BaseArticleMetadataExtractor(ArticleFiles.ROLE_ABSTRACT);
    // Ask Phil how to talk to our real metadata extractor here
  }

}
