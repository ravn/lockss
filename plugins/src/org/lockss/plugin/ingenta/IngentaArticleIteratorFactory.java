/*
 * $Id: IngentaArticleIteratorFactory.java,v 1.1 2011-11-12 00:42:06 thib_gc Exp $
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

package org.lockss.plugin.ingenta;

import java.util.Iterator;
import java.util.regex.*;

import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

public class IngentaArticleIteratorFactory
    implements ArticleIteratorFactory,
               ArticleMetadataExtractorFactory {

  protected static Logger log = Logger.getLogger("IngentaArticleIteratorFactory");

  protected static final String ROOT_TEMPLATE = "\"%scontent/%s/%s\", api_url, publisher_id, journal_id";
  
  protected static final String PATTERN_TEMPLATE = "\"^%scontent/%s/%s/[0-9]{4}/0*%s/.{8}/art[0-9]{5}\\?crawler=true(&mimetype=(application/pdf|text/html))?$\", api_url, publisher_id, journal_id, volume_name";

  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
                                                      MetadataTarget target)
      throws PluginException {
    return new IngentaArticleIterator(au, new SubTreeArticleIterator.Spec()
                                              .setTarget(target)
                                              .setRootTemplate(ROOT_TEMPLATE)
                                              .setPatternTemplate(PATTERN_TEMPLATE));
  }
 
  protected static class IngentaArticleIterator extends SubTreeArticleIterator {

    protected static Pattern PLAIN_PATTERN = Pattern.compile("^(.*)content/([^/]+/[^/]+/[0-9]{4}/[^/]+/[^/]+/[^/]+)\\?crawler=true$", Pattern.CASE_INSENSITIVE);
    
    protected static Pattern HTML_PATTERN = Pattern.compile("^(.*)content/([^/]+/[^/]+/[0-9]{4}/[^/]+/[^/]+/[^/]+)\\?crawler=true&mimetype=text/html$", Pattern.CASE_INSENSITIVE);
    
    protected static Pattern PDF_PATTERN = Pattern.compile("^(.*)content/([^/]+/[^/]+/[0-9]{4}/[^/]+/[^/]+/[^/]+)\\?crawler=true&mimetype=application/pdf$", Pattern.CASE_INSENSITIVE);
    
    protected String baseUrl;
    
    public IngentaArticleIterator(ArchivalUnit au,
                                  SubTreeArticleIterator.Spec spec) {
      super(au, spec);
      this.baseUrl = au.getConfiguration().get(ConfigParamDescr.BASE_URL.getKey());
    }
    
    @Override
    protected ArticleFiles createArticleFiles(CachedUrl cu) {
      String url = cu.getUrl();
      
      Matcher mat = PLAIN_PATTERN.matcher(url);
      if (mat.find()) {
        return processPlainFullTextCu(cu, mat);
      }
      
      mat = HTML_PATTERN.matcher(url);
      if (mat.find()) {
        return processFullTextHtml(cu, mat);
      }
      
      mat = PDF_PATTERN.matcher(url);
      if (mat.find()) {
        return processFullTextPdf(cu, mat);
      }
      
      log.warning("Mismatch between article iterator factory and article iterator: " + url);
      return null;
    }

    protected ArticleFiles processFullTextHtml(CachedUrl htmlCu, Matcher htmlMat) {
      CachedUrl plainCu = au.makeCachedUrl(htmlMat.replaceFirst("$1content/$2?crawler=true"));
      if (plainCu != null && plainCu.hasContent()) {
        AuUtil.safeRelease(plainCu);
        return null; // Defer to plain URL
      }
      
      ArticleFiles af = new ArticleFiles();
      af.setFullTextCu(htmlCu);
      guessFullTextPdf(af, htmlMat);
      guessAbstract(af, htmlMat);
      guessReferences(af, htmlMat);
      return af;
    }

    protected ArticleFiles processFullTextPdf(CachedUrl pdfCu, Matcher pdfMat) {
      CachedUrl plainCu = au.makeCachedUrl(pdfMat.replaceFirst("$1content/$2?crawler=true"));
      if (plainCu != null && plainCu.hasContent()) {
        AuUtil.safeRelease(plainCu);
        return null; // Defer to plain URL
      }
      
      CachedUrl htmlCu = au.makeCachedUrl(pdfMat.replaceFirst("$1content/$2?crawler=true&mimetype=text/html"));
      if (htmlCu != null && htmlCu.hasContent()) {
        AuUtil.safeRelease(htmlCu);
        return null; // Defer to HTML URL
      }
      
      ArticleFiles af = new ArticleFiles();
      af.setFullTextCu(pdfCu);
      guessAbstract(af, pdfMat);
      guessReferences(af, pdfMat);
      return af;
    }

    protected ArticleFiles processPlainFullTextCu(CachedUrl cu, Matcher mat) {
      if (!cu.hasContent()) {
        return null;
      }
      
      ArticleFiles af = new ArticleFiles();
      af.setFullTextCu(cu);
      if (cu.getContentType().toLowerCase().startsWith("text/html")) {
        af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_HTML, cu);
        guessFullTextPdf(af, mat);
      }
      else if (cu.getContentType().toLowerCase().startsWith("application/pdf")) {
        af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, cu);
        guessFullTextHtml(af, mat);
      }
      else {
        log.warning("Unexpected content type of " + cu.getUrl() + ": " + cu.getContentType());
      }
      guessAbstract(af, mat);
      guessReferences(af, mat);
      return af;
    }
    
    protected void guessFullTextHtml(ArticleFiles af, Matcher mat) {
      CachedUrl htmlCu = au.makeCachedUrl(mat.replaceFirst("$1content/$2?crawler=true&mimetype=text/html"));
      if (htmlCu != null && htmlCu.hasContent()) {
        af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_HTML, htmlCu);
        AuUtil.safeRelease(htmlCu);
      }
    }
    
    protected void guessFullTextPdf(ArticleFiles af, Matcher mat) {
      CachedUrl pdfCu = au.makeCachedUrl(mat.replaceFirst("$1content/$2?crawler=true&mimetype=application/pdf"));
      if (pdfCu != null && pdfCu.hasContent()) {
        af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, pdfCu);
        AuUtil.safeRelease(pdfCu);
      }
    }
    
    protected void guessAbstract(ArticleFiles af, Matcher mat) {
      CachedUrl absCu = au.makeCachedUrl(mat.replaceFirst(String.format("%scontent/$2", baseUrl)));
      if (absCu != null && absCu.hasContent()) {
        af.setRoleCu(ArticleFiles.ROLE_ABSTRACT, absCu);
        AuUtil.safeRelease(absCu);
      }
    }
    
    protected void guessReferences(ArticleFiles af, Matcher mat) {
      CachedUrl refCu = au.makeCachedUrl(mat.replaceFirst(String.format("%scontent/$2/references", baseUrl)));
      if (refCu != null && refCu.hasContent()) {
        af.setRoleCu(ArticleFiles.ROLE_REFERENCES, refCu);
        AuUtil.safeRelease(refCu);
      }
    }
    
  }

  @Override
  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
      throws PluginException {
    return new BaseArticleMetadataExtractor(null);
  }
  
}
