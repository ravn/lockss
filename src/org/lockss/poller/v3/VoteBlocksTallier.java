/*
 * $Id: VoteBlocksTallier.java,v 1.9.6.1 2013-09-21 05:39:00 tlipkis Exp $
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

package org.lockss.poller.v3;

import java.io.IOException;
import java.util.*;

import org.lockss.config.Configuration;
import org.lockss.config.CurrentConfig;
import org.lockss.daemon.ShouldNotHappenException;
import org.lockss.protocol.VoteBlock;
import org.lockss.protocol.VoteBlocks;
import org.lockss.protocol.VoteBlocksIterator;
import org.lockss.protocol.OrderedVoteBlocksIterator;
import org.lockss.util.*;

/**
 * Representation of the tally of two votes in a symmetric poll,
 * that is the one generated by the voter while it is creating its
 * vote, and the one generated at the poller while it is tallying
 * that vote. This class encapsulates the process of comparing
 * two VoteBlocks instances by comparing their lists of VoteBlock
 * instances and the hashes of the versions they contain.
 */
class VoteBlocksTallier {
  private static final Logger log = Logger.getLogger(V3Voter.class);

  private static final String PREFIX = Configuration.PREFIX + "poll.v3.";

  // Should not be true in production. See comment at WithLists.
  /**
   * If true, lists of AGREE/DISAGREE/VOTER_ONLY/POLLER_ONLY URLs will
   * be kept.
   */
  public static final String PARAM_KEEP_URL_LISTS =
    PREFIX + "keepUrlLists";
  public static final boolean
    DEFAULT_KEEP_URL_LISTS = false;

  /**
   * Categories of the URLs.
   */
  enum Category {
    /** The poller and voter share at least one version of the content. */
    AGREE,
    /** The poller and voter both have content, but share no versions. */
    DISAGREE,
    /** The voter has the URL, but the poller does not. */
    VOTER_ONLY,
    /** The poller has the URL, but the voter does not. */
    POLLER_ONLY};

  /**
   * Create a VoteBlocksTallier. Use the current configuration to
   * decide if Lists of URLs should be kept.
   */
  public static VoteBlocksTallier make() {
    boolean keepUrlLists =
      CurrentConfig.getBooleanParam(PARAM_KEEP_URL_LISTS,
				    DEFAULT_KEEP_URL_LISTS);
    return make(keepUrlLists);
  }

  // package, for testing.
  static VoteBlocksTallier make(boolean keepUrlLists) {
    if (keepUrlLists) {
      return new WithLists();
    } else {
      return new VoteBlocksTallier();
    }
  }

  // XXX DSHR cannot keep these lists of URLs in memory - need to put them
  // XXX DSHR on disk, and make collecting them optional since we actually
  // XXX DSHR only need the counts
  /**
   * A subclass for use when the actual Lists of URLs should be made
   * available.
   */
  private static class WithLists extends VoteBlocksTallier {
    private final Map<Category, List<String>> categoryMap =
      new EnumMap<Category, List<String>>(Category.class);

    WithLists() {
      for (Category category: Category.values()) {
	categoryMap.put(category, new ArrayList<String>());
      }
      // For production, don't allow the lists to be kept.
      throw new ShouldNotHappenException("WithLists should not be enabled.");
    }

    @Override protected void addUrl(Category category, String url) {
      super.addUrl(category, url);
      getList(category).add(url);
    }

    @Override public List<String> getList(Category category) {
      return categoryMap.get(category);
    }
  }

  /**
   * Map from the Category to the number of blocks in that Category.
   */
  private final AbstractMap<Category, Integer> categoryMap =
    new EnumMap<Category, Integer>(Category.class);

  private VoteBlocksTallier() {
    for (Category category: Category.values()) {
      categoryMap.put(category, 0);
    }
  }

  /**
   * Update internal structures -- lists or counts -- for this newly-
   * categorized URL.
   * @param category The Category for this URL.
   * @param url A URL which has been categorized.
   */
  protected void addUrl(Category category, String url) {
    log.debug3(url+" is: "+category);
    categoryMap.put(category, getCount(category)+1);
  }

  /**
   * @param category The category of vote in question.
   * @return The number of URLs added which were classified into the category.
   */
  public int getCount(Category category) {
      return categoryMap.get(category);
  }

  /**
   * @param category The category of vote in question.
   * @return The List of URLs added which were classified into the category.
   * @throws ShouldNotHappenException If Lists are not being kept.
   */
  public List<String> getList(Category category) {
      throw new ShouldNotHappenException("VoteBlockTallier didn't keep lists");
  }

  /**
   * Used by the voter in a symmetric poll to tally the VoteBlocks in
   * the receipt message from the poller against the VoteBlocks object
   * which was created during our vote generation.
   * @param poller A ParticipantUserData instance to represent the poller
   * @param voterBlocks The {@link VoteBlocks} the voter generated
   * @param pollerBlocks The {@link VoteBlocks} the poller sent in the
   * receipt.
   * @throws IOException if voter's {@link VoteBlocksIterator}
   * VoteBlocks throws it.
   */
  public final void
    tallyVoteBlocks(VoteBlocks voterBlocks, VoteBlocks pollerBlocks)
      throws IOException, OrderedVoteBlocksIterator.OrderException {
    if (voterBlocks == null) {
      throw new ShouldNotHappenException("voterBlocks null");
    }
    if (pollerBlocks == null) {
      throw new IllegalArgumentException("pollerBlocks null");
    }
    // Our own hasher made the vote blocks. If they are out of order,
    // allow OrderedVoteBlocksIterator.OrderException to throw and
    // abort the poll.
    VoteBlocksIterator vIterator =
      new OrderedVoteBlocksIterator(voterBlocks.iterator());
    // The coordinator will log and catch all ordering problems from
    // the poller. If that happens near the end we might be close to
    // creating a repairer if we finish, so don't abort.
    VoteBlocksCoordinator coordinator =
      new VoteBlocksCoordinator(Arrays.asList(pollerBlocks.iterator()));

    while (vIterator.hasNext()) {
      // Consume exactly one vBlock each time around the loop.
      VoteBlock vBlock = vIterator.next();
      String vUrl = vBlock.getUrl();
      // Consume from the poller until it catches up or passes the voter.
      String pUrl = coordinator.peekUrl();
      while (VoteBlock.compareUrls(pUrl, vUrl) < 0) {
	VoteBlock pBlock = coordinator.getVoteBlock(pUrl, 0);
	addUrl(Category.POLLER_ONLY, pUrl);
	pUrl = coordinator.peekUrl();
      }
      // Consume from the poller iff we have the url
      if (VoteBlock.compareUrls(pUrl, vUrl) == 0) {
	VoteBlock pBlock = coordinator.getVoteBlock(pUrl, 0);
	if (VoteBlockVoteBlockComparerFactory.make(vBlock).
	    sharesVersion(pBlock)) {
	  addUrl(Category.AGREE, vUrl);
	} else {
	  addUrl(Category.DISAGREE, vUrl);
	}
      } else {
	addUrl(Category.VOTER_ONLY, vUrl);
      }
    }
    // vIterator is empty; consume the rest of the pBlocks
    String pUrl = coordinator.peekUrl();
    while (VoteBlock.compareUrls(coordinator.peekUrl(), null) < 0) {
      VoteBlock pBlock = coordinator.getVoteBlock(pUrl, 0);
      addUrl(Category.POLLER_ONLY, pUrl);
      pUrl = coordinator.peekUrl();
    }
  }

  /**
   * @return the percent for which the poller agrees with the us, the
   * voter.
   */
  public final float percentAgreement() {
    // XXX Split into two numbers: the overall agreement (taking all URLs
    // into account) and the repairability metric proving possession
    // (taking only URLs we have into account)
    int total = getCount(Category.AGREE) +
      getCount(Category.DISAGREE) +
      getCount(Category.VOTER_ONLY) +
      getCount(Category.POLLER_ONLY);
    return total == 0 ? 0.0f : ((float)getCount(Category.AGREE))/total;
  }
}
