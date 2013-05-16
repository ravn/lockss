/*
 * $Id: ClockssNRCResearchPressHtmlMetadataExtractorFactory.java,v 1.1 2013-04-03 22:53:30 aishizaki Exp $
 */

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

package org.lockss.plugin.nrcresearchpress;

import java.io.*;

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.extractor.FileMetadataExtractor.Emitter;
import org.lockss.plugin.*;

/**  typical metadata
  <link rel="schema.DC" href="http://purl.org/DC/elements/1.0/"></link>
  <meta name="dc.Title" content="An analysis of resistance to change exposed in individuals' thoughts and behaviors"></meta>
  <meta name="dc.Creator" content=" Lena M. Forsell"></meta>
  <meta name="dc.Creator" content=" Jan A. �str�m"></meta>
  <meta name="dc.Subject" content="exercise; metabolism; "></meta>
  <meta name="dc.Description" content="Abstract Resistance to change can be the cause of difficulty when it is either too strong or too weak. Therapy or information can be used to either strengthen or weaken resistance to change to appropriate levels. The purpose of this article is intended to disclose the relationship between resistance to change and some aspects of human behavior. Resistance to change has affective, cognitive, and behavioral components that create a psychological resistance to making a change in particular situations or overall changes in one's life, and often appears in psychotherapy and/or when organizational alterations are underway. Four subfactors of resistance to change have been found and are related to extraversion and neuroticism in the �Big Five� personality model. Much indicates that the development of resistance to change begins early in childhood and may be neurophysiologically founded. It can be traced in both macro and micro gestures in body language and is believed to influence general health. Whereas previou..."></meta>
  <meta name="dc.Publisher" content=" Ammons Scientific, Ltd.  P.O. Box 9229, Missoula, MT 59807-9229 USA  "></meta>
  <meta name="dc.Date" scheme="WTN8601" content="2012-12-26"></meta>
  <meta name="dc.Type" content="research-article"></meta>
  <meta name="dc.Format" content="text/HTML"></meta>
  <meta name="dc.Language" content="en"></meta>
  <meta name="dc.Identifier" scheme="manuscript" content="2011-0116"></meta>
  <meta name="dc.Source" content="http://dx.doi.org/10.1139/h11-070"></meta>
 */
public class ClockssNRCResearchPressHtmlMetadataExtractorFactory
  implements FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger("ClockssNRCResearchPressMetadataExtractorFactory");
  
  public FileMetadataExtractor createFileMetadataExtractor(MetadataTarget target,
                                                           String contentType)
      throws PluginException {
    return new ClockssNRCResearchMetadataExtractor();
  }
  
  public static class ClockssNRCResearchMetadataExtractor 
    implements FileMetadataExtractor {

    // Map NRCResearch -specific HTML meta tag names to cooked metadata fields
    private static MultiMap tagMap = new MultiValueMap();
    static { 
      tagMap.put("dc.Title", MetadataField.DC_FIELD_TITLE);
      tagMap.put("dc.Title", MetadataField.FIELD_ARTICLE_TITLE);
      tagMap.put("dc.Creator", MetadataField.DC_FIELD_CREATOR);
      tagMap.put("dc.Creator", MetadataField.FIELD_AUTHOR);
      tagMap.put("dc.Description", MetadataField.DC_FIELD_DESCRIPTION);
      tagMap.put("dc.Publisher", MetadataField.DC_FIELD_PUBLISHER);
      tagMap.put("dc.Publisher", MetadataField.FIELD_PUBLISHER);
      tagMap.put("dc.Date", MetadataField.DC_FIELD_DATE);
      tagMap.put("dc.Date", MetadataField.FIELD_DATE);
      tagMap.put("dc.Format", MetadataField.DC_FIELD_FORMAT);
      tagMap.put("dc.Type", MetadataField.DC_FIELD_TYPE);
      tagMap.put("dc.Language", MetadataField.DC_FIELD_LANGUAGE);
      tagMap.put("dc.Rights", MetadataField.DC_FIELD_RIGHTS);
      tagMap.put("dc.Coverage", MetadataField.DC_FIELD_COVERAGE);
      tagMap.put("dc.Source", MetadataField.DC_FIELD_SOURCE);

    }

    /**
     * Use SimpleHtmlMetaTagMetadataExtractor to extract raw metadata, map
     * to cooked fields
     */ 
    @Override
    public void extract(MetadataTarget target, CachedUrl cu, Emitter emitter)
      throws IOException {
        log.debug("The MetadataExtractor attempted to extract metadata from cu: "+cu);
      ArticleMetadata am = 
        new SimpleHtmlMetaTagMetadataExtractor().extract(target, cu);
      am.cook(tagMap);

      emitter.emitMetadata(cu, am);
    }

  }

}