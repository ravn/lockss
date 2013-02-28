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

package org.lockss.plugin.ammonsscientific;

import java.util.Iterator;
import java.util.regex.*;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.daemon.PluginException;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.ArticleMetadataExtractorFactory;
import org.lockss.extractor.BaseArticleMetadataExtractor;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

public class ClockssAmmonsScientificArticleIteratorFactory implements ArticleIteratorFactory, ArticleMetadataExtractorFactory {
  
  protected static Logger log = Logger.getLogger("ClockssAmmonsScientificArticleIteratorFactory");
  //http://www.amsciepub.com/
  protected static final String ROOT_TEMPLATE = "\"%s\",base_url";
  //http://www.amsciepub.com/doi/abs/10.2466/09.02.10.CP.1.17
  //http://www.amsciepub.com/doi/pdf/10.2466/pms.110.2.353-354
  //protected static final String PATTERN_TEMPLATE = "\"^%sdoi/pdf/[\\d\\.]+/[\\d\\.]+%s[\\d\\.\\-]+\", base_url, journal_abbr, volume_name";
  protected static final String PATTERN_TEMPLATE = "\"^%sdoi/pdf/[\\d\\.]+/[\\d\\.]+%s[\\d\\.\\-]+\", base_url, journal_abbr, volume_name";

  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
                                                      MetadataTarget target)
      throws PluginException {
    return new ClockssAmmonsArticleIterator(au, new SubTreeArticleIterator.Spec()
                                       .setTarget(target)
                                       .setRootTemplate(ROOT_TEMPLATE)
                                       .setPatternTemplate(PATTERN_TEMPLATE, Pattern.CASE_INSENSITIVE)
                                       );
  }
  
  protected static class ClockssAmmonsArticleIterator extends SubTreeArticleIterator {
    //http://www.amsciepub.com/doi/abs/10.2466/01.07.21.28.CP.1.10
    protected Pattern ABS_PATTERN = Pattern.compile(
              //String.format("(%sdoi/)abs(/\\d{2}.\\d{4}/(\\d+.){0,5}.%s.%s.)", 
              String.format("(%sdoi/)abs(/[\\d\\.]+/[\\d\\.]+%s.%s)",            
              au.getConfiguration().get(ConfigParamDescr.BASE_URL.getKey()),
              au.getConfiguration().get(ConfigParamDescr.JOURNAL_ABBR.getKey()),
              au.getConfiguration().get(ConfigParamDescr.VOLUME_NAME.getKey())),
              Pattern.CASE_INSENSITIVE);
    //http://www.amsciepub.com/doi/pdf/10.2466/28.49.CP.1.15
    //http://www.amsciepub.com/doi/pdfplus/10.2466/01.11.21.CP.1.6
    protected Pattern PDF_PATTERN = Pattern.compile(
              //String.format("(%sdoi/)pdf((plus)?)/([\\d\\.]+/[\\d\\.]+%s.%s)", 
              String.format("(%sdoi/)pdf(/[\\d\\.]+/[\\d\\.]+%s.%s)", 
              au.getConfiguration().get(ConfigParamDescr.BASE_URL.getKey()),
              au.getConfiguration().get(ConfigParamDescr.JOURNAL_ABBR.getKey()),
              au.getConfiguration().get(ConfigParamDescr.VOLUME_NAME.getKey())),
              Pattern.CASE_INSENSITIVE);
    
    public ClockssAmmonsArticleIterator(ArchivalUnit au,
                                  SubTreeArticleIterator.Spec spec) {
      super(au, spec);
      this.au = au;
    }
    /*
     *  Ammons Scientific (an atypon publisher) has an abstract, pdf and pdfplus
     *  there's no full html file provided. as of 2/28/13, the pdfplus provided
     *  does not have any special links (though it looks as if there are "pre-links"
     *  for references (area looks like it should be a link, but not connected
     *  to anything, yet)
     */
    @Override
    protected ArticleFiles createArticleFiles(CachedUrl cu) {
      String url = cu.getUrl();
      log.debug("createArticleFiles("+url+")");
      /*
      Matcher mat = ABS_PATTERN.matcher(url);
      if (mat.find()) {
        return processAbstractHtml(cu, mat);
      }
      */
      Matcher pmat = PDF_PATTERN.matcher(url);
      if (pmat.find()){
        return processPdf(cu, pmat);
      }
      log.warning("Mismatch between article iterator factory and article iterator: " + url);
      return null;
    }

    protected ArticleFiles processAbstractHtml(CachedUrl cu, Matcher mat) {
      ArticleFiles af = new ArticleFiles();
      log.debug("processAbstractHtml("+cu+")");
      
      af.setRoleCu(ArticleFiles.ROLE_ABSTRACT, cu);
      af.setRoleCu(ArticleFiles.ROLE_ARTICLE_METADATA, cu);
      return af;
    }
    
    protected void guessAbstract(ArticleFiles af, Matcher mat) {
      CachedUrl absCu = au.makeCachedUrl(mat.replaceFirst("$1abs$2"));
      log.debug("guessAbstract("+absCu+")");

      if (absCu != null && absCu.hasContent()) {
        af.setRoleCu(ArticleFiles.ROLE_ABSTRACT, absCu);
        af.setRoleCu(ArticleFiles.ROLE_ARTICLE_METADATA, absCu);
      }
      AuUtil.safeRelease(absCu);
    }
    protected void guessPdfPlus(ArticleFiles af, Matcher mat) {
      CachedUrl cu = au.makeCachedUrl(mat.replaceFirst("$1pdfplus$2"));
      log.debug("guessPdfPlus("+cu+")");

      if (cu != null && cu.hasContent()) {
        af.setRoleCu(AtyponArticleFiles.ROLE_FULL_TEXT_PDFPLUS, cu);
      } 
      AuUtil.safeRelease(cu);
    }    
    protected ArticleFiles processPdf(CachedUrl cu, Matcher mat) {
      ArticleFiles af = new ArticleFiles();
      log.debug("processPdf("+cu+")");
      af.setFullTextCu(cu);
      // roles only need to be set for getting metadata
      if (spec.getTarget() != null && !(spec.getTarget().isArticle())){
        af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF_LANDING_PAGE, cu);
        af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, cu);
        guessAbstract(af, mat);
        guessPdfPlus(af, mat);
     }
      
      return af;
    }
    
  }
  
  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
	      throws PluginException {
    return new BaseArticleMetadataExtractor(ArticleFiles.ROLE_ARTICLE_METADATA);
	  
  }
  // maybe this is a silly way to do this, when just a string will do...
  // this reminds me that perhaps will add this new role to ArticleFiles, if
  // more publishers start using it.
  protected class AtyponArticleFiles extends ArticleFiles {
    public static final String ROLE_FULL_TEXT_PDFPLUS = "FullTextPdfPlusfile";
     
  }
}