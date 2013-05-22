/*
 * $Id: Subscription.java,v 1.1 2013-05-22 23:40:20 fergaloy-sf Exp $
 */

/*

 Copyright (c) 2013 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.subscription;

import java.util.Collection;
import java.util.HashSet;

/**
 * Representation of a serial publication subscription.
 * 
 * @author Fernando Garcia-Loygorri
 */
public class Subscription {
  private SerialPublication publication;
  private Long subscriptionSeq;
  private Collection<BibliographicPeriod> subscribedRanges;
  private Collection<BibliographicPeriod> unsubscribedRanges;

  public SerialPublication getPublication() {
    return publication;
  }

  public void setPublication(SerialPublication publication) {
    this.publication = publication;
  }

  public Long getSubscriptionSeq() {
    return subscriptionSeq;
  }

  public void setSubscriptionSeq(Long subscriptionSeq) {
    this.subscriptionSeq = subscriptionSeq;
  }

  public Collection<BibliographicPeriod> getSubscribedRanges() {
    return subscribedRanges;
  }

  public void setSubscribedRanges(Collection<BibliographicPeriod> ranges) {
    if (ranges == null || ranges.size() == 0) {
      subscribedRanges = ranges;
      return;
    }

    subscribedRanges = new HashSet<BibliographicPeriod>(ranges.size());
    subscribedRanges.addAll(ranges);
  }

  public boolean addSubscribedRanges(Collection<BibliographicPeriod> ranges) {
    if (ranges == null || ranges.size() == 0) {
      return false;
    }

    if (subscribedRanges == null) {
      subscribedRanges = new HashSet<BibliographicPeriod>(ranges.size());
    }

    return subscribedRanges.addAll(ranges);
  }

  public Collection<BibliographicPeriod> getUnsubscribedRanges() {
    return unsubscribedRanges;
  }

  public void setUnsubscribedRanges(Collection<BibliographicPeriod> ranges) {
    if (ranges == null || ranges.size() == 0) {
      unsubscribedRanges = ranges;
      return;
    }

    unsubscribedRanges = new HashSet<BibliographicPeriod>(ranges.size());
    unsubscribedRanges.addAll(ranges);
  }

  public boolean addUnsubscribedRanges(Collection<BibliographicPeriod> ranges) {
    if (ranges == null || ranges.size() == 0) {
      return false;
    }

    if (unsubscribedRanges == null) {
      unsubscribedRanges = new HashSet<BibliographicPeriod>(ranges.size());
    }

    return unsubscribedRanges.addAll(ranges);
  }

  @Override
  public String toString() {
    return "Subscription [subscriptionSeq=" + subscriptionSeq
	+ ", subscribedRanges='" + subscribedRanges
	+ "', unsubscribedRanges='" + unsubscribedRanges
	+ "', " + publication.toString() + "]";
  }
}
