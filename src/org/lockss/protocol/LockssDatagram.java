/*
 * $Id: LockssDatagram.java,v 1.6 2003-06-20 22:34:52 claire Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.protocol;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;
import org.lockss.util.*;
import org.lockss.daemon.*;


/** LockssDatagram describes the datagram packets exchanged between LOCKSS
 * caches, and is the object passed from clients to the communications
 * layer.  Received packets are represented by the subclass, {@link
 * LockssReceivedDatagram}.
 */
public class LockssDatagram {


  /** Maximum data size */
  public static final int MAX_SIZE = 2048;
  public static final int PROTOCOL_TEST = 1;
  public static final int PROTOCOL_LCAP = 2;
  static final int HEADER_LENGTH = 4;

  byte[] pktData;			// packet data (poss. compressed)
  byte[] payload;			// Client data (uncompressed)
  int protocol;				// Client protocol

  /** Only for subclass. */
  LockssDatagram() {
  }

  /** Build a LockssDatagram to send a protocol message containing data.
   * @param protocol protocol number, one of PROTOCOL_xxx constants.
   * @param data byte array containing message data.
   */
  public LockssDatagram(int protocol, byte[] data) {
    this.protocol = protocol;
    this.payload = data;
  }

  /** Return the data to be sent in a DatagramPacket */
  public byte[] getPacketData() {
    if (pktData == null) {
      try {
	pktData = encodePacketData();
      } catch (IOException e) {
	throw new RuntimeException("Can't encode packet: " + e.toString());
      }
    }
    return pktData;
  }

  /** Return a DatagramPacket suitable for sending to a specific address.
   * The packet will be compressed if compression is configured
   * (org.lockss.comm.compress) and the payload is long enough (greater
   * than org.lockss.comm.compress.min).
   * @param addr the destination address (or multicast group)
   * @param port the destination port
   * @return the packet
   */
  public DatagramPacket makeSendPacket(InetAddress addr, int port)
      throws IOException {
    return makeSendPacket(getPacketData(), addr, port);
  }

  DatagramPacket makeSendPacket(byte[] data, InetAddress addr, int port)
      throws IOException {
    return new DatagramPacket(data, data.length, addr, port);
  }

  /** Encode the data for sending in a packet.  The packet itself contains
   * destination info, so this is the only part that can be cached.
   * The data will be compressed if compression is configured
   * (org.lockss.comm.compress) and the payload is long enough (greater
   * than org.lockss.comm.compress.min).
   */
  byte[] encodePacketData() throws IOException {
    if (isCompressed()) {
      return encodeCompressedPacketData();
    } else {
      return encodeUncompressedPacketData();
    }
  }

  /** 
   * Encode the data for sending.
   */
  byte[] encodeUncompressedPacketData() {
    byte[] data = new byte[payload.length + HEADER_LENGTH];
    ByteArray.encodeInt(protocol, data, 0);
    System.arraycopy(payload, 0, data, HEADER_LENGTH, payload.length);
    return data;
  }

  /** 
   * Compress and encode the data for sending.
   */
  public byte[] encodeCompressedPacketData()
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    OutputStream gzout = new GZIPOutputStream(baos);
    byte[] hdrData = new byte[HEADER_LENGTH];
    ByteArray.encodeInt(protocol, hdrData, 0);
    gzout.write(hdrData, 0, HEADER_LENGTH);
    gzout.write(payload, 0, payload.length);
    gzout.close();
    baos.close();
    return baos.toByteArray();
  }

  boolean isCompressed() {
    Configuration config = Configuration.getCurrentConfig();
    return (config.getBoolean(LcapComm.PARAM_COMPRESS_PACKETS,
			      LcapComm.DEFAULT_COMPRESS_PACKETS) &&
	    payload.length >= config.getInt(LcapComm.PARAM_COMPRESS_MIN,
					    LcapComm.DEFAULT_COMPRESS_MIN));
  }


  /** Return the data portion of the packet */
  public byte[] getData() {
    return payload;
  }

  /** Return the protocol under which to send the packet */
  public int getProtocol() {
    return protocol;
  }

  /** Return the size of the (possibly compressed) packet */
  public int getPacketSize() {
    return getPacketData().length;
  }

  /** Return the size of the (uncompressed) data */
  public int getDataSize() {
    return getData().length + HEADER_LENGTH;
  }

  public String toString() {
    return "[LDG: proto=" + protocol + ", size=" + payload.length + "]";
  }
}
