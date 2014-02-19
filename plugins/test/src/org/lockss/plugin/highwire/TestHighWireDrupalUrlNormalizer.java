/*
 * $Id: TestHighWireDrupalUrlNormalizer.java,v 1.1 2014-02-19 22:37:23 etenbrink Exp $
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

package org.lockss.plugin.highwire;

import org.lockss.plugin.UrlNormalizer;
import org.lockss.test.LockssTestCase;
/*
 * UrlNormalizer removes  suffixes
 * http://ajpcell.physiology.org/content/ajpcell/303/1/C1/F1.large.jpg?width=800&height=600
 * http://ajpcell.physiology.org/content/ajpcell/303/1/C1/F1.large.jpg?download=true
 * to http://ajpcell.physiology.org/content/ajpcell/303/1/C1/F1.large.jpg
 * 
 * http://ajpheart.physiology.org/content/ajpheart/304/2/H253.full.pdf
 * http://ajpheart.physiology.org/content/304/2/H253.full-text.pdf
 * http://ajpheart.physiology.org/content/304/2/H253.full.pdf
 * 
 * http://ajpheart.physiology.org/content/304/2/H253.full-text.pdf+html
 * http://ajpheart.physiology.org/content/304/2/H253.full.pdf+html
 */

public class TestHighWireDrupalUrlNormalizer extends LockssTestCase {

  public void testUrlNormalizer() throws Exception {
    UrlNormalizer normalizer = new HighWireDrupalUrlNormalizer();
    assertEquals("http://ajpcell.physiology.org/content/ajpcell/303/1/C1/F1.large.jpg",
        normalizer.normalizeUrl("http://ajpcell.physiology.org/content/ajpcell/303/1/C1/F1.large.jpg?width=800&height=600", null));
    assertEquals("http://ajpcell.physiology.org/content/ajpcell/303/1/C1/F1.large.jpg",
        normalizer.normalizeUrl("http://ajpcell.physiology.org/content/ajpcell/303/1/C1/F1.large.jpg?download=true", null));
    assertEquals("http://ajpheart.physiology.org/content/304/2/H253.full.pdf",
        normalizer.normalizeUrl("http://ajpheart.physiology.org/content/ajpheart/304/2/H253.full.pdf", null));
    assertEquals("http://ajpheart.physiology.org/content/304/2/H253.full.pdf",
        normalizer.normalizeUrl("http://ajpheart.physiology.org/content/304/2/H253.full-text.pdf", null));
    assertEquals("http://ajpheart.physiology.org/content/304/2/H253.full.pdf+html",
        normalizer.normalizeUrl("http://ajpheart.physiology.org/content/304/2/H253.full-text.pdf+html", null));
  }
  
}
