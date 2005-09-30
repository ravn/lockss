/*
 * $Id: IcpUtil.java,v 1.2 2005-09-30 22:04:28 thib_gc Exp $
 */

/*

Copyright (c) 2000-2005 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.proxy.icp;

import java.io.UnsupportedEncodingException;

import org.lockss.util.Constants;

/**
 * <p>Provides utility methods to deal with ICP.</p>
 * @author Thib Guicherd-Callin
 */
public class IcpUtil {

  /**
   * <p>An array of valid RFC2186 opcode, for fast lookup.</p>
   * @see #isValidOpcode
   */
  private static final boolean[] validOpcode = {
    false, // 00: ICP_OP_INVALID (named by RFC but not valid, obviously)
    true,  // 01: ICP_OP_QUERY
    true,  // 02: ICP_OP_HIT
    true,  // 03: ICP_OP_MISS
    true,  // 04: ICP_OP_ERR
    false,
    false,
    false,
    false,
    false,
    true,  // 10: ICP_OP_SECHO
    true,  // 11: ICP_OP_DECHO
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    false,
    true,  // 21: ICP_OP_MISS_NOFETCH
    true,  // 22: ICP_OP_DENIED
    true   // 23: ICP_OP_HIT_OBJ 
  };
  
  /**
   * <p>Computes the number of bytes corresponding to a given ICP
   * message.</p>
   * @param message An ICP message.
   * @return The number of bytes the ICP message represents.
   */
  public static short computeLength(IcpMessage message) {
    try {
      int result =
        21 // 20-byte header + null terminator
        + message.getPayloadUrl().getBytes(Constants.URL_ENCODING).length; // URL
      if (message.isQuery()) {
        result += 4; // requester field
      }
      if (message.getOpcode() == IcpMessage.ICP_OP_HIT_OBJ) {
        byte[] obj = message.getPayloadObject();
        if (obj != null) {
          result += obj.length;
        }
      }
      return (short)result;
    }
    catch (UnsupportedEncodingException exc) {
      return -1; // should never happen; US-ASCII guaranteed
    }
  }
  
  /**
   * <p>Determines if an opcode is meaningful.</p>
   * @param opcode A potential opcode.
   * @return False if the opcode is unused in the ICP specification,
   *         version 2 (RFC2186); true otherwise.
   * @see #validOpcode
   */
  public static boolean isValidOpcode(int opcode) {
    try {
      return validOpcode[opcode];
    }
    catch (IndexOutOfBoundsException ioobe) {
      return false;
    }
  }
  
  public static int stringLength(String str) {
    return str.toCharArray().length;
  }
  
}
