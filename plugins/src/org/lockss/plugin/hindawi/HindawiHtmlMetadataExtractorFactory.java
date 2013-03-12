/*
 * $Id: HindawiHtmlMetadataExtractorFactory.java,v 1.1 2013-03-12 22:32:20 aishizaki Exp $
 */

/*

 Copyright (c) 2000-2009 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.hindawi;

import java.io.*;
import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.extractor.MetadataField.Extractor;
import org.lockss.plugin.*;

public class HindawiHtmlMetadataExtractorFactory implements
    FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger("HindawiHtmlMetadataExtractorFactory");

  public FileMetadataExtractor createFileMetadataExtractor(
      MetadataTarget target, String contentType) throws PluginException {
    return new HindawiHtmlMetadataExtractor();
  }
/*
        <meta name="citation_journal_title" content="Advances in Human-Computer Interaction"/>
        <meta name="citation_publisher" content="Hindawi Publishing Corporation"/>
        <meta name="citation_title" content="The Role of Usability in Business-to-Business E-Commerce Systems: Predictors and Its Impact on User&amp;#x27;s Strain and Commercial Transactions"/>
        <meta name="citation_date" content="2012/08/30"/>
        <meta name="citation_year" content="2012"/>
        <meta name="dcterms.issued" content="2012/08/30"/>
        <meta name="citation_volume" content="2012"/>
        <meta name="citation_issn" content="1687-5893"/>
        <meta name="citation_abstract" content="This study examines the impact of organizational antecedences (i.e., organizational support and information policy) and technical antecedences (i.e., subjective server response time and objective server response time) to perceived usability, perceived strain, and commercial transactions (i.e. purchases) in business-to-business (B2B) e-commerce. Data were gathered from a web-based study with 491 employees using e-procurement bookseller portals. Structural equation modeling results revealed positive relationships of organizational support and information policy, and negative relationships of subjective server response time to usability after controlling for users&amp;#x27; age, gender, and computer experience. Perceived usability held negative relationships to perceived strain and fully mediated the relation between the three significant antecedences and perceived strain while purchases were not predicted. Results are discussed in terms of theoretical implications and consequences for successfully designing and implementing B2B e-commerce information systems."/>
        <meta name="citation_pdf_url" content="http://downloads.hindawi.com/journals/ahci/2012/948693.pdf"/>
        <meta name="citation_abstract_html_url" content="http://www.hindawi.com/journals/ahci/2012/948693/abs/"/>
        <meta name="citation_author" content="Konradt, Udo"/>
        <meta name="dc.Contributor" content="Konradt, Udo"/>
        <meta name="citation_author" content="L&amp;#xfc;ckel, L&amp;#xfc;der"/>
        <meta name="dc.Contributor" content="L&amp;#xfc;ckel, L&amp;#xfc;der"/>
        <meta name="citation_author" content="Ellwart, Thomas"/>
        <meta name="dc.Contributor" content="Ellwart, Thomas"/>
        <link rel="alternate" type="application/rss+xml" title="Advances in Human-Computer Interaction latest articles" href="/journals/ahci/rss.xml" />
    <script src="/Scripts/jquery-1.4.4.min.js" type="text/javascript"></script>
    <meta name="Author" content="Hindawi Publishing Corporation" />
    <meta name="viewport" content="width=974px" />
        <meta name="Description" content="Advances in Human-Computer Interaction is an interdisciplinary journal that publishes theoretical and applied papers covering the broad spectrum of interactive systems. The journal is inherently interdisciplinary, publishing original research in the fields of computing, engineering, artificial intelligence, psychology, linguistics, and social and system organization, as applied to the design, implementation, application, analysis, and evaluation of interactive systems." />

 */
  public static class HindawiHtmlMetadataExtractor 
    implements FileMetadataExtractor {

    // Map Hindawi-specific HTML meta tag names to cooked metadata fields
    private static MultiMap tagMap = new MultiValueMap();
    static {
      tagMap.put("citation_journal_title", MetadataField.FIELD_JOURNAL_TITLE);
      tagMap.put("citation_publisher", MetadataField.FIELD_PUBLISHER);      
      tagMap.put("citation_title", MetadataField.FIELD_ARTICLE_TITLE);
      tagMap.put("citation_date", MetadataField.FIELD_DATE);
      tagMap.put("citation_volume", MetadataField.FIELD_VOLUME);
      tagMap.put("citation_issn", MetadataField.FIELD_ISSN);
      tagMap.put("citation_author", new MetadataField(MetadataField.FIELD_AUTHOR, MetadataField.splitAt(",")));
     /*
       tagMap.put("citation_mjid", new MetadataField(
         MetadataField.FIELD_PROPRIETARY_IDENTIFIER, 
         MetadataField.extract("^([^;]+);", 1)));
       */
    }

    @Override
    public void extract(MetadataTarget target, CachedUrl cu, Emitter emitter)
      throws IOException {      
      ArticleMetadata am = 
        new SimpleHtmlMetaTagMetadataExtractor().extract(target, cu);
      am.cook(tagMap);

      emitter.emitMetadata(cu, am);
      }
    }
  }
 
