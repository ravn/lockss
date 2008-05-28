package org.lockss.protocol;

import java.util.*;
import java.io.*;
import java.nio.*;

import org.lockss.app.LockssApp;
import org.lockss.util.*;

/**
 * This is a VoteBlocks data structure backed by a disk file.
 * 
 * @author sethm
 * 
 */
public class DiskVoteBlocks extends BaseVoteBlocks {

  private String filePath;
  private transient File file;
  private int size = 0;

  // Hint to allow seeking to the correct point in the InputStream
  private transient long nextVoteBlockAddress = 0;
  private transient int nextVoteBlockIndex = 0;

  private static final Logger log = Logger.getLogger("DiskVoteBlocks");

  /**
   * <p>
   * Decode a DiskVoteBlocks object from the supplied inputstream, to be stored
   * in the supplied directory.
   * </p>
   * 
   * <p>
   * This method is used when decoding V3LcapMessages.
   * </p>
   * 
   * @param blocksToRead Number of blocks to read from the InputStream.
   * @param from Input stream from which to read.
   * @param toDir Directory to use as temporary storage.
   * @throws IOException
   */
  public DiskVoteBlocks(int blocksToRead, InputStream from, File toDir)
      throws IOException {
    this(toDir);
    FileOutputStream fos = new FileOutputStream(file);
    // Just copy to the output stream.
    StreamUtil.copy(from, fos);
    // Close
    fos.close();
    this.size = blocksToRead;
  }

  /**
   * Create a new VoteBlocks collection to be backed by a file in the supplied
   * directory.
   * 
   * @param toDir  Directory to use as temporary storage.
   * @throws IOException
   */
  public DiskVoteBlocks(File toDir) throws IOException {
    file = FileUtil.createTempFile("voteblocks-", ".bin", toDir);
    filePath = file.getAbsolutePath();
  }

  /**
   * Automagically restore File object following deserialization.
   */
  protected void postUnmarshal(LockssApp lockssContext) {
    file = new File(filePath);
  }

  public synchronized void addVoteBlock(VoteBlock b) throws IOException {
    // Append to the end of the file.
    FileOutputStream fos = null;
    fos = new FileOutputStream(file, true);
    DataOutputStream dos = new DataOutputStream(fos);
    byte[] encodedBlock = b.getEncoded();
    dos.writeShort(encodedBlock.length);
    dos.write(encodedBlock);
    this.size++;
    dos.close();
  }

  protected synchronized VoteBlock getVoteBlock(int i) throws IOException {
    // Read from the file until we reach VoteBlock i, or run out of blocks.
    RandomAccessFile raf = new RandomAccessFile(file, "r");

    try {
      // Shortcut for quickly finding the next iterable block
      if (i == nextVoteBlockIndex) {
        raf.skipBytes((int) nextVoteBlockAddress);
      } else {
        nextVoteBlockIndex = 0;
        nextVoteBlockAddress = 0;
        for (int idx = 0; idx < i; idx++) {
          short len = raf.readShort();
          raf.skipBytes(len);
          nextVoteBlockIndex++;
          nextVoteBlockAddress += len + 2;
        }
      }

      // Should be there!
      short len = raf.readShort();
      byte[] encodedBlock = new byte[len];
      raf.readFully(encodedBlock);
      nextVoteBlockIndex++;
      nextVoteBlockAddress += len + 2;
      return new VoteBlock(encodedBlock);
    } catch (IOException ex) {
      // This probably means that we've run out of blocks, so we should
      // return null.
      log.warning("Unable to find block " + i + " while seeking "
                  + "DiskVoteBlocks file " + filePath);
      return null;
    } finally {
      IOUtil.safeClose(raf);
    }
  }
  
  /** Search the collection for the requested VoteBlock.
   * 
   * XXX: This is implemented as a simple linear search, so it is O(n).
   * The disk structure is not terribly easy to seek into because each
   * record is variable length, so I'm not sure it will be easy to implement
   * binary search and improve performance.  Therefore, this method should
   * be used with care, and sparingly.
   */
  public VoteBlock getVoteBlock(String url) {
    try {
      for (VoteBlocksIterator it = iterator(); it.hasNext(); ) {
        VoteBlock vb = (VoteBlock)it.next();
        if (url.equals(vb.getUrl())) {
          return vb;
        }
      }
      return null;
    } catch (IOException ex) {
      log.error("IOException while searching for VoteBlock " + url, ex);
      return null;
    }
  }

  public int size() {
    return size;
  }

  public synchronized void release() {
    // The poller should have already cleaned up our directory by now,
    // but just in case, we'll run some cleanup code.
    if (file != null && !file.delete() && log.isDebug2()) {
      log.debug2("Unable to delete file: " + file);
    }

    file = null;
  }

  public synchronized InputStream getInputStream() throws IOException {
    return new BufferedInputStream(new FileInputStream(file));
  }

}
