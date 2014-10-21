/*
 * $Id: BaseAtyponArticleIteratorFactory.java,v 1.12 2014-10-21 16:47:13 alexandraohlson Exp $
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

package org.lockss.plugin.atypon;

import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.lockss.config.TdbAu;
import org.lockss.daemon.*;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.ArticleMetadataExtractorFactory;
import org.lockss.extractor.BaseArticleMetadataExtractor;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

public class BaseAtyponArticleIteratorFactory
implements ArticleIteratorFactory,
ArticleMetadataExtractorFactory {

  protected static Logger log = 
      Logger.getLogger(BaseAtyponArticleIteratorFactory.class);

  //arbitrary string is used in daemon for this  
  private static final String ABSTRACTS_ONLY = "abstracts";  
  private static final String ROLE_PDFPLUS = "PdfPlus";

  private static final String ROOT_TEMPLATE = "\"%sdoi/\", base_url";
  private static final String PATTERN_TEMPLATE = 
      "\"^%sdoi/(abs|full|pdf|pdfplus)/[.0-9]+/\", base_url";

  // various aspects of an article
  // DOI's can have "/"s in the suffix
  private static final Pattern PDF_PATTERN = Pattern.compile("/doi/pdf/([.0-9]+)/([^?&]+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern ABSTRACT_PATTERN = Pattern.compile("/doi/abs/([.0-9]+)/([^?&]+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern HTML_PATTERN = Pattern.compile("/doi/full/([.0-9]+)/([^?&]+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PDFPLUS_PATTERN = Pattern.compile("/doi/pdfplus/([.0-9]+)/([^?&]+)$", Pattern.CASE_INSENSITIVE);

  // how to change from one form (aspect) of article to another
  private static final String HTML_REPLACEMENT = "/doi/full/$1/$2";
  private static final String ABSTRACT_REPLACEMENT = "/doi/abs/$1/$2";
  private static final String PDF_REPLACEMENT = "/doi/pdf/$1/$2";
  private static final String PDFPLUS_REPLACEMENT = "/doi/pdfplus/$1/$2";

  // Things not an "article" but in support of an article
  private static final String REFERENCES_REPLACEMENT = "/doi/ref/$1/$2";
  private static final String SUPPL_REPLACEMENT = "/doi/suppl/$1/$2";
  // link extractor used forms to pick up this URL

  /* TODO: Note that if the DOI suffix has a "/" this will not work because the 
   * slashes that are part of the DOI will not get encoded so they don't
   * match the CU.  Waiting for builder support for smarter replacement
   * Taylor & Francis works around this because it has current need
   */
  // After normalization, the citation information will live at this URL if it exists
  private static final String RIS_REPLACEMENT = "/action/downloadCitation?doi=$1%2F$2&format=ris&include=cit";
  // AMetSoc doens't do an "include=cit", only "include=abs"
  // Do these as two separate patterns (not "OR") so we can have a priority choice
  private static final String SECOND_RIS_REPLACEMENT = "/action/downloadCitation?doi=$1%2F$2&format=ris&include=abs";


  //
  // On an Atypon publisher, article content may look like this but you do not know
  // how many of the aspects will exist for a particular journal
  //
  //  <atyponbase>.org/doi/abs/10.3366/drs.2011.0010 (abstract or summary)
  //  <atyponbase>.org/doi/full/10.3366/drs.2011.0010 (full text html)
  //  <atyponbase>.org/doi/pdf/10.3366/drs.2011.0010 (full text pdf)
  //  <atyponbase>.org/doi/pdfplus/10.3366/drs.2011.0010  (fancy pdf - could be in frameset or could have active links)
  //  <atyponbase>.org/doi/suppl/10.3366/drs.2011.0010 (page from which you can access supplementary info)
  //  <atyponbase>.org/doi/ref/10.3366/drs.2011.0010  (page with references on it)
  //
  // note: at least one publisher has a doi suffix that includes a "/", eg:
  // <base>/doi/pdfplus/10.1093/wsr/wsr0023
  //
  //  There is the possibility of downloaded citation information which will get normalized to look something like this:
  //  <atyponbase>.org/action/downloadCitation?doi=<partone>%2F<parttwo>&format=ris&include=cit
  //

  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au, MetadataTarget target) throws PluginException {
    SubTreeArticleIteratorBuilder builder = localBuilderCreator(au);

    builder.setSpec(target,
        ROOT_TEMPLATE,
        PATTERN_TEMPLATE, Pattern.CASE_INSENSITIVE);

    // The order in which these aspects are added is important. They determine which will trigger
    // the ArticleFiles and if you are only counting articles (not pulling metadata) then the 
    // lower aspects aren't looked for, once you get a match.

    // set up PDF to be an aspect that will trigger an ArticleFiles
    builder.addAspect(PDF_PATTERN,
        PDF_REPLACEMENT,
        ArticleFiles.ROLE_FULL_TEXT_PDF);

    // set up PDFPLUS to be an aspect that will trigger an ArticleFiles
    builder.addAspect(PDFPLUS_PATTERN,
        PDFPLUS_REPLACEMENT,
        ROLE_PDFPLUS); 

    // set up full text html to be an aspect that will trigger an ArticleFiles
    builder.addAspect(HTML_PATTERN,
        HTML_REPLACEMENT,
        ArticleFiles.ROLE_FULL_TEXT_HTML,
        ArticleFiles.ROLE_ARTICLE_METADATA); // use for metadata if abstract doesn't exist

    if (isAbstractOnly(au)) {
      // When part of an abstract only AU, set up an abstract to be an aspect
      // that will trigger an articleFiles. 
      // This also means an abstract could be considered a FULL_TEXT_CU until this is deprecated
      builder.addAspect(ABSTRACT_PATTERN,
          ABSTRACT_REPLACEMENT,
          ArticleFiles.ROLE_ABSTRACT,
          ArticleFiles.ROLE_ARTICLE_METADATA);
    } else {
      // If this isn't an "abstracts only" AU, an abstract alone should not
      // be enough to trigger an ArticleFiles
      builder.addAspect(ABSTRACT_REPLACEMENT,
          ArticleFiles.ROLE_ABSTRACT,
          ArticleFiles.ROLE_ARTICLE_METADATA);
    }

    // set a role, but it isn't sufficient to trigger an ArticleFiles
    builder.addAspect(REFERENCES_REPLACEMENT,
        ArticleFiles.ROLE_REFERENCES);

    // set a role, but it isn't sufficient to trigger an ArticleFiles
    builder.addAspect(SUPPL_REPLACEMENT,
        ArticleFiles.ROLE_SUPPLEMENTARY_MATERIALS);

    // set a role, but it isn't sufficient to trigger an ArticleFiles
    // First choice is &include=cit; second choice is &include=abs (AMetSoc)
    builder.addAspect(Arrays.asList(
        RIS_REPLACEMENT, SECOND_RIS_REPLACEMENT),
        ArticleFiles.ROLE_CITATION_RIS);
    
    // The order in which we want to define full_text_cu.  
    // First one that exists will get the job
    builder.setFullTextFromRoles(ArticleFiles.ROLE_FULL_TEXT_PDF,
        ArticleFiles.ROLE_FULL_TEXT_HTML,
        ROLE_PDFPLUS);  

    // The order in which we want to define what a PDF is 
    // if we only have PDFPLUS, that should become a FULL_TEXT_PDF
    builder.setRoleFromOtherRoles(ArticleFiles.ROLE_FULL_TEXT_PDF,
        ArticleFiles.ROLE_FULL_TEXT_PDF,
        ROLE_PDFPLUS); // this should be ROLE_PDFPLUS when it's defined

    // set the ROLE_ARTICLE_METADATA to the first one that exists 
    builder.setRoleFromOtherRoles(ArticleFiles.ROLE_ARTICLE_METADATA,
        ArticleFiles.ROLE_CITATION_RIS,
        ArticleFiles.ROLE_ABSTRACT,
        ArticleFiles.ROLE_FULL_TEXT_HTML);

    return builder.getSubTreeArticleIterator();
  }

  // Enclose the method that creates the builder to allow a child to do additional processing
  // for example Taylor&Francis
  protected SubTreeArticleIteratorBuilder localBuilderCreator(ArchivalUnit au) { 
    return new SubTreeArticleIteratorBuilder(au);
  }

  @Override
  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
      throws PluginException {
    return new BaseArticleMetadataExtractor(ArticleFiles.ROLE_ARTICLE_METADATA);
  }

  // return true if the AU is of type "abstracts"
  private static boolean isAbstractOnly(ArchivalUnit au) {
    TdbAu tdbAu = au.getTdbAu();
    return tdbAu != null && ABSTRACTS_ONLY.equals(tdbAu.getCoverageDepth());
  }

}
