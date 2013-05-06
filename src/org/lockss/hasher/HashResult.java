/*
 * $Id: HashResult.java,v 1.2 2013-05-06 16:35:58 barry409 Exp $
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

package org.lockss.hasher;

import java.security.MessageDigest;
import java.util.Arrays;

import org.lockss.util.ByteArray;

/**
 * Wrap the byte array returned by {@link MessageDigest#digest}
 * to force equality and sorting by content of the byte array.
 */
final public class HashResult {
  /** An internal byte array, never shared with clients. */
  private final byte[] bytes;

  private HashResult(byte[] bytes) {
    this.bytes = bytes;
  }

  /**
   * Thrown by {@link #make} for byte arrays which are not permitted.
   */
  public static class IllegalByteArray extends IllegalArgumentException {
    public IllegalByteArray(String msg) {
      super(msg);
    }
  }

  /**
   * Wrap the given bytes.
   * @param bytes The bytes to wrap; probably the output from
   * MessageDigest.digest().
   * @return A HashResult for these bytes.
   * @throws {@link IllegalByteArray} if <code>bytes</code> is null or
   * zero-length.
   */
  public static HashResult make(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new IllegalByteArray("null or zero-length byte array is not allowed.");
    }
    // Keep a safe copy, so someone mucking with the bytes won't break
    // our hash.
    return new HashResult(Arrays.copyOf(bytes, bytes.length));
  }

  /** 
   * @return A copy of the underlying bytes. This will never be null
   * or zero-length.
   */
  public byte[] getBytes() {
    return Arrays.copyOf(bytes, bytes.length);
  }

  /** Indicates whether some other object is "equal to" this one. */
  @Override public boolean equals(Object other) {
    if (!(other instanceof HashResult)) {
      return false;
    }
    return MessageDigest.isEqual(this.bytes, ((HashResult)other).bytes);
  }

  /** Returns a hash code value for the object, based on the content. */
  @Override public int hashCode() {
    return Arrays.hashCode(bytes);
  }
}
