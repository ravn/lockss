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

package org.lockss.plugin.springer;

import java.io.IOException;

import org.lockss.config.TdbAu;
import org.lockss.daemon.PluginException;
import org.lockss.daemon.TitleConfig;
import org.lockss.extractor.ArticleMetadata;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.FileMetadataExtractor;
import org.lockss.extractor.MetadataField;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.ArticleFiles;
import org.lockss.plugin.AuUtil;
import org.lockss.plugin.CachedUrl;
import org.lockss.util.Logger;

public class SpringerLinkBookArticleMetadataExtractor implements ArticleMetadataExtractor{

//	private SpringerEmitter emit = null;
	private static Logger log = Logger.getLogger("SpringerLinkBookArticleMetadataExtractor");

	public SpringerLinkBookArticleMetadataExtractor()
	{
		super();
	}

        /** For standard bibiographic metadata fields for which the extractor did
         * not produce a valid value, fill in a value from the TDB if available.
         * @param af the ArticleFiles on which extract() was called.
         * @param cu the CachedUrl selected by {@link #getCuToExtract(ArticleFiles)}.
         * @param am the ArticleMetadata being emitted.
         */
        protected void addTdbDefaults(ArticleFiles af, CachedUrl cu, ArticleMetadata am) {
          if (log.isDebug3()) log.debug3("addTdbDefaults("+af+", "+cu+", "+am+")");
          if (!cu.getArchivalUnit().isBulkContent()) {
            // Fill in missing values rom TDB if TDB entries reflect bibliographic
            // information for the content. This is not the case for bulk data
            TitleConfig tc = cu.getArchivalUnit().getTitleConfig();
            if (log.isDebug3()) log.debug3("tc; "+tc);
            TdbAu tdbau = (tc == null) ? null : tc.getTdbAu();
            if (log.isDebug3()) log.debug3("tdbau; "+tdbau);
            if (tdbau != null) {
              if (log.isDebug3()) log.debug3("Adding data from " + tdbau + " to " + am);
              am.putIfBetter(MetadataField.FIELD_ISBN, tdbau.getPrintIsbn());
              am.putIfBetter(MetadataField.FIELD_EISBN, tdbau.getEisbn());
              am.putIfBetter(MetadataField.FIELD_ISSN, tdbau.getPrintIssn());
              am.putIfBetter(MetadataField.FIELD_EISSN, tdbau.getEissn());
              am.putIfBetter(MetadataField.FIELD_DATE, tdbau.getStartYear());
              am.putIfBetter(MetadataField.FIELD_VOLUME, tdbau.getStartVolume());
              am.putIfBetter(MetadataField.FIELD_ISSUE, tdbau.getStartIssue());
              am.putIfBetter(MetadataField.FIELD_JOURNAL_TITLE,tdbau.getJournalTitle());
              am.putIfBetter(MetadataField.FIELD_PUBLISHER,tdbau.getPublisherName());
            }
          }
          if (log.isDebug3()) log.debug3("adding("+af.getFullTextUrl());
          am.putIfBetter(MetadataField.FIELD_ACCESS_URL, af.getFullTextUrl());
          if (log.isDebug3()) log.debug3("am: ("+am);
        }

	@Override
	public void extract(MetadataTarget target, ArticleFiles af, Emitter emitter)
			throws IOException, PluginException {

	  /* create a new SpringerEmitter off the passed in emitter */
	  SpringerEmitter emit = new SpringerEmitter(af, emitter);

		CachedUrl cu = af.getRoleCu(ArticleFiles.ROLE_ARTICLE_METADATA);
		FileMetadataExtractor me = null;

		if(cu != null) {
			try{
				me = cu.getFileMetadataExtractor(target);

				if(me != null) {
				  me.extract(target, cu, emit);
				} else {
                                  ArticleMetadata am = new ArticleMetadata();
                                  emit.emitMetadata(cu, am);
				}
			} catch (RuntimeException e) {
				log.debug("for af (" + af + ")", e);

				if(me != null)
					try{
					  ArticleMetadata am = new ArticleMetadata();
					  emit.emitMetadata(cu, am);
					}
					catch (RuntimeException e2) {
						log.debug("retry with default metadata for af (" + af + ")", e2);
					}
			} finally {
				AuUtil.safeRelease(cu);
			}
		}
	  }

	  class SpringerEmitter implements FileMetadataExtractor.Emitter {
	    private Emitter parent;
	    private ArticleFiles af;

	    SpringerEmitter(ArticleFiles af, Emitter parent) {
	      this.af = af;
	      this.parent = parent;
	    }

	    @Override
            public void emitMetadata(CachedUrl cu, ArticleMetadata am) {
	      addTdbDefaults(af, cu, am);
	      parent.emitMetadata(af, am);
	    }

	    void setParentEmitter(Emitter parent) {
	      this.parent = parent;
	    }
	  }
}