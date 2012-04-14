/*
 * $Id: BaseArticleMetadataExtractor.java,v 1.8.2.1 2012-04-14 14:11:56 pgust Exp $
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

package org.lockss.extractor;

import java.io.*;

import org.lockss.util.*;
import org.lockss.config.TdbAu;
import org.lockss.daemon.*;
import org.lockss.plugin.*;

/**
 * Base class for metadata extractors that return a single ArticleMetadata or
 * null. This was the previous ArticleMetadataExtractor interface.
 */
public class BaseArticleMetadataExtractor implements ArticleMetadataExtractor {

  private static Logger log = Logger.getLogger("BaseArticleMetadataExtractor");

  protected String cuRole = null;
  protected boolean emitDefaultIfNone = false;

  public BaseArticleMetadataExtractor() {
  }

  public BaseArticleMetadataExtractor(String cuRole) {
    this.cuRole = cuRole;
  }

  protected CachedUrl getCuToExtract(ArticleFiles af) {
    return cuRole != null ? af.getRoleCu(cuRole) : af.getFullTextCu();
  }

  class MyEmitter implements FileMetadataExtractor.Emitter {
    private Emitter parent;
    private ArticleFiles af;
   

    MyEmitter(ArticleFiles af, Emitter parent) {
      this.af = af;
      this.parent = parent;
    }

    public void emitMetadata(CachedUrl cu, ArticleMetadata am) {

      TitleConfig tc = cu.getArchivalUnit().getTitleConfig();
      TdbAu tdbau = (tc == null) ? null : tc.getTdbAu();
      String isbn = (tdbau == null) ? null : tdbau.getIsbn();
      String issn = (tdbau == null) ? null : tdbau.getPrintIssn();
      String eissn = (tdbau == null) ? null : tdbau.getEissn();
      String year = (tdbau == null) ? null : tdbau.getStartYear();
      String volume = (tdbau == null) ? null : tdbau.getStartVolume();
      String issue = (tdbau == null) ? null : tdbau.getStartIssue();
      String journalTitle = (tdbau == null) ? null : tdbau.getJournalTitle();

      if (am.get(MetadataField.FIELD_ISSN) == null
          || am.hasInvalidValue(MetadataField.FIELD_ISSN)) {
        am.put(MetadataField.FIELD_ISSN, issn);
      }
      if (am.get(MetadataField.FIELD_EISSN) == null
          || am.hasInvalidValue(MetadataField.FIELD_EISSN)) {
        am.put(MetadataField.FIELD_EISSN, eissn);
      }
      if (am.get(MetadataField.FIELD_VOLUME) == null
          || am.hasInvalidValue(MetadataField.FIELD_VOLUME)) {
        am.put(MetadataField.FIELD_VOLUME, volume);
      }
      if (am.get(MetadataField.FIELD_DATE) == null
          || am.hasInvalidValue(MetadataField.FIELD_DATE)) {
        am.put(MetadataField.FIELD_DATE, year);
      }
      if (am.get(MetadataField.FIELD_ISSUE) == null
          || am.hasInvalidValue(MetadataField.FIELD_ISSUE)) {
        am.put(MetadataField.FIELD_ISSUE, issue);
      }
      if (am.get(MetadataField.FIELD_ISBN) == null
          || am.hasInvalidValue(MetadataField.FIELD_ISBN)) {
        am.put(MetadataField.FIELD_ISBN, isbn);
      }
      if (am.get(MetadataField.FIELD_JOURNAL_TITLE) == null
          || am.hasInvalidValue(MetadataField.FIELD_JOURNAL_TITLE)) {
        am.put(MetadataField.FIELD_JOURNAL_TITLE, journalTitle);
      }
      if (!am.hasValidValue(MetadataField.FIELD_ACCESS_URL)) {
        am.put(MetadataField.FIELD_ACCESS_URL, af.getFullTextUrl());
      }
      parent.emitMetadata(af, am);
    }

    void setParentEmitter(Emitter parent) {
      this.parent = parent;
    }

    void setEmitDefaultIfNone(boolean val) {
      emitDefaultIfNone = val;
    }

  }

  public void extract(MetadataTarget target, ArticleFiles af,
      ArticleMetadataExtractor.Emitter emitter) throws IOException,
      PluginException {

    MyEmitter myEmitter = new MyEmitter(af, emitter);
    CachedUrl cu = getCuToExtract(af);
    log.debug3("extract(" + af + ")");
    if (cu != null) {
      FileMetadataExtractor me = null;
      try {
        me = cu.getFileMetadataExtractor(target);
        if (me != null) {
          me.extract(target, cu, myEmitter);
          AuUtil.safeRelease(cu);
          return;
        }
      } catch (IOException ex) {
        // unable to read input stream or some error during plugin filtering.
       log.debug3("IO Exception has been caught",ex);
      }
      // generate default metadata in case of null filemetadataextractor or
      //IOException.
      
      ArticleMetadata am = new ArticleMetadata();
      myEmitter.emitMetadata(cu, am);
      AuUtil.safeRelease(cu);
    }
  }
}
