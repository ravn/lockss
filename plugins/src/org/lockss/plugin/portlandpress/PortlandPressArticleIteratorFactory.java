/*
 * $Id: PortlandPressArticleIteratorFactory.java,v 1.5 2015-01-16 21:19:46 alexandraohlson Exp $
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

package org.lockss.plugin.portlandpress;

import java.util.Iterator;
import java.util.regex.*;

import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

public class PortlandPressArticleIteratorFactory
    implements ArticleIteratorFactory,
               ArticleMetadataExtractorFactory {

  protected static Logger log =
      Logger.getLogger("PortlandPressArticleIteratorFactory");
  /*
   * We need to also allow the oddball cases where the issuenum is concatenated to
   * the volume_name in the url. 
   * Sadly, ROOT_TEMPLATE doesn't allow for regexp;
   * Can we not use a ROOT TEMPLATE AT ALL? It's not really limiting beyond the AU limit
   */
  
 /* Would it be better to just not use a ROOT_TEMPLATE? */
  /* I can't define it down to volnum because of the few cases that 
   * concatenate issue to the vol - and ROOT_TEMPLATE doesn't take regexp
   */
  protected static final String ROOT_TEMPLATE =
      "\"%s%s/\", base_url, journal_id";
        
  
  // pick up the abstract as the logical definition of one article
  // - lives one level higher than pdf & fulltext
  // pick up <lettersnums>.htm, but not <lettersnums>add.htm
  //   or add1.htm, add01.htm... etc
  // allow for concatenated /<vol><issue>/ for the volnum
  // The journal id portion of the articlename is, in one case a truncated version
  // and therefore we should just assume characters preceding volnum.
  protected static final String PATTERN_TEMPLATE =
      "\"^%s%s/%s(?:[0-9S]{2})?/(?![^/]+add(?:[0-9]+)?[.]htm)[^/]+[.]htm\", " +
      "base_url,journal_id, volume_name, journal_id";
  
  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
                                                      MetadataTarget target)
      throws PluginException {
    SubTreeArticleIteratorBuilder builder = new SubTreeArticleIteratorBuilder(au);
    
    /*
     * Article lives at three locations:  
     * <baseurl>/<jid>/<volnum>/                : Abstract
     *           <lettersnums>.htm
     *           <lettersnums>add.htm - supplementary stuff (can also be add1.htm or add01.htm...)
     *           <lettersnums>add.mov  - ex - quicktime movie
     *           <lettersnums>add.pdf - ex - online data
     * <baseurl>/<jid>/<volnum>/startpagenum/  : PDF & legacy html
     *           <lettersnums>.htm
     *           <lettersnums>.pdf  (note that pdf filename does not start with jid)
     * <baseurl>/<jid>/ev/<volnum>/<stpage>    : Enhanced full text version
     *           <lettersnums_ev.htm>
     *                   
     * notes: 
     * volnum is usually just the volume_name but in 3 journals by IWA, it can
     *   be a concatenated /<volnum><issuenum>/ where issuenum is padded to 
     *   two, leading 0. The rest of the paradigm holds because the concatenated <volnum>
     *   is used consistently, even in the article name
     *     (see: http://www.iwaponline.com/wp/01505/wp015050816.htm) 
     * startpagenum can have letters in it, but follows volumenum which is all numbers
     *     (see: )
     * lettersnums seems to be concatenated <jid><volnum><startpagenum>
     *    except for ONE journal, which truncates the <jid> to first three chars in <lettersnums>
     *      (see: http://essays.biochemistry.org/bsessays/048/bse0480063.htm)
     *    except for pdf which is <volnum><startpagenum>
     *    
     */
    
    // various aspects of an article
    // http://www.clinsci.org/cs/120/cs1200013add.htm
    // http://www.iwaponline.com/wp/01505/wp015050816.htm
    // http://www.iwaponline.com/washdev/003/washdev0030375.htm
    // http://essays.biochemistry.org/bsessays/048/bse0480001.htm
    // http://essays.biochemistry.org/bsessays/048/0001/0480001.pdf
    // http://essays.biochemistry.org/bsessays/048/0001/bse0480001.htm
    // http://essays.biochemistry.org/bsessays/ev/048/0045/bse0480045_ev.htm
    // http://essays.biochemistry.org/bsessays/ev/048/0045/bse0480045_evf01.htm
    // http://essays.biochemistry.org/bsessays/ev/048/0045/bse0480045_evrefs.htm
    // http://essays.biochemistry.org/bsessays/ev/048/0045/bse0480045_evtab01.htm
    // http://essays.biochemistry.org/bsessays/ev/048/0045/bse0480045_evtext01.htm
    // http://essays.biochemistry.org/bsessays/ev/048/0045/bse0480045_evtitle.htm
    
    // Identify groups in the pattern "/(<jid>)/(<voldir>)/(<articlenum>).htm
    // articlenum is <jid><voldir><pageno> and we need pageno to find the content files
    // where <voldir> might be <volnum> or <volnumissuenum> but it stays as a unit
    // special case one journal in identifying the jid used in the articlenum
    // ONE journal "bsessays" becomes "bse" in the articlenum. All others stay complete

    //  this is "/(fulljid)/(volnumdir)/(jidbit)volnumdir(pageno).htm$"
    final Pattern ABSTRACT_PATTERN = Pattern.compile(
    "/([^/]+)/([^/]+)/([a-z]+)\\2([^/]+)[.]htm$", Pattern.CASE_INSENSITIVE);
    
    // this is "/(fulljid)/(volnumdir)/pageno/(jidbit)volnumdir(pageno).htm$"
    final Pattern HTML_PATTERN = Pattern.compile(
        "/([^/]+)/([^/]+)/[^/]+/([a-z]+)\\2([^/])[.]htm$", Pattern.CASE_INSENSITIVE);
    
    /*final Pattern PDF_PATTERN = Pattern.compile(
        "/([^/]{1,3})([^/]*)/([^/]+)/([^/]+)/\\3\\4[.]pdf$", Pattern.CASE_INSENSITIVE);*/
    
    // how to change from one form (aspect) of article to another
    // $1 = fulljid 
    // $2 = volnumdir (either vol_name or volname-issuenum)
    // $3 = jidbit (either the full journal_id or in the case of bsessays, just 'bse')
    // $4 = pageno (the numeric portion of the articlename, used as subdirectory for full text versions)
    final String ABSTRACT_REPLACEMENT = "/$1/$2/$3$2$4.htm";
    final String HTML_REPLACEMENT = "/$1/$2/$4/$3$2$4.htm";
    // pdf doesn't use the jidbit and we can't guess what this is so we can't use pdf as a full aspect
    final String PDF_REPLACEMENT = "/$1/$2/$4/$2$4.pdf";
    final String ADD_REPLACEMENT = "/$1/$2/$4/$3$2$4add.htm"; // won't catch add01 or add1...
    final String EV_REPLACEMENT = "/$1/ev/$2/$4/$3$2$4_ev.htm";
    
    
    builder.setSpec(target,
        ROOT_TEMPLATE, PATTERN_TEMPLATE, Pattern.CASE_INSENSITIVE);
    
    // set up abstract to be an aspect that will trigger an ArticleFiles
    // NOTE - for the moment this also means it is considered a FULL_TEXT_CU
    // until this fulltext concept is deprecated
    builder.addAspect(
        ABSTRACT_PATTERN, ABSTRACT_REPLACEMENT,
        ArticleFiles.ROLE_ABSTRACT,
        ArticleFiles.ROLE_ARTICLE_METADATA,
        ArticleFiles.ROLE_FULL_TEXT_PDF_LANDING_PAGE);
    
    builder.addAspect(
        EV_REPLACEMENT,
        ArticleFiles.ROLE_FULL_TEXT_HTML);
    
    builder.addAspect(
        HTML_PATTERN, HTML_REPLACEMENT,
        ArticleFiles.ROLE_FULL_TEXT_HTML,
        ArticleFiles.ROLE_ARTICLE_METADATA);
    
/* even though logically a pdf would trigger an article files, we cannot
 * easily generate the other aspects from the information in the pdf url
 * (we have no way of automatically getting the 'jidbit' for the one case (bsessays) where it
 * is shortened.  But we will always have an abstract, so just set pdf from that
 */
    builder.addAspect(
        PDF_REPLACEMENT,
        ArticleFiles.ROLE_FULL_TEXT_PDF);
    
    // set up *add.htm to be a suppl aspect
    builder.addAspect(ADD_REPLACEMENT,
        ArticleFiles.ROLE_SUPPLEMENTARY_MATERIALS);
    
    // The order in which we want to define full_text_cu.
    // First one that exists will get the job
    builder.setFullTextFromRoles(
        ArticleFiles.ROLE_FULL_TEXT_HTML, 
        ArticleFiles.ROLE_FULL_TEXT_PDF, 
        ArticleFiles.ROLE_ABSTRACT);
    
    return builder.getSubTreeArticleIterator();
  }
  
  @Override
  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
      throws PluginException {
    return new BaseArticleMetadataExtractor(ArticleFiles.ROLE_ARTICLE_METADATA);
  }

}
