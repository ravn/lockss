package org.lockss.protocol;

import java.io.*;
import java.util.*;

import org.lockss.util.*;

/**
 * Maintain an ordered list of VoteBlocks.  Concrete implementations offer
 * memory based or disk-based access, depending on the size.
 */

public interface VoteBlocks extends LockssSerializable {

  public void addVoteBlock(VoteBlock b);

  public VoteBlock getVoteBlock(int i);

  public Iterator iterator();

  public int size();
  
}