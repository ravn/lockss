/*
 * $Id: EasySSLProtocolSocketFactory.java,v 1.1 2008-05-19 07:38:58 tlipkis Exp $
 */
/*

/*

Copyright (c) 2000-2008 Board of Trustees of Leland Stanford Jr. University,
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

/* Portions of this code are
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.lockss.util.urlconn;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.HttpClientError;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ControllerThreadSocketFactory;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;
import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
//import org.apache.commons.httpclient.contrib.ssl.*;

import com.sun.net.ssl.SSLContext;
import com.sun.net.ssl.TrustManager;

/**
 * <p>
 * EasySSLProtocolSocketFactory can be used to creats SSL {@link Socket}s 
 * that accept self-signed certificates. 
 * </p>
 * <p>
 * This socket factory SHOULD NOT be used for productive systems 
 * due to security reasons, unless it is a concious decision and 
 * you are perfectly aware of security implications of accepting 
 * self-signed certificates
 * </p>
 *
 * <p>
 * Example of using custom protocol socket factory for a specific host:
 *     <pre>
 *     Protocol easyhttps = new Protocol("https", new EasySSLProtocolSocketFactory(), 443);
 *
 *     HttpClient client = new HttpClient();
 *     client.getHostConfiguration().setHost("localhost", 443, easyhttps);
 *     // use relative url only
 *     GetMethod httpget = new GetMethod("/");
 *     client.executeMethod(httpget);
 *     </pre>
 * </p>
 * <p>
 * Example of using custom protocol socket factory per default instead of the standard one:
 *     <pre>
 *     Protocol easyhttps = new Protocol("https", new EasySSLProtocolSocketFactory(), 443);
 *     Protocol.registerProtocol("https", easyhttps);
 *
 *     HttpClient client = new HttpClient();
 *     GetMethod httpget = new GetMethod("https://localhost/");
 *     client.executeMethod(httpget);
 *     </pre>
 * </p>
 * 
 * @author <a href="mailto:oleg -at- ural.ru">Oleg Kalnichevski</a>
 * 
 * <p>
 * DISCLAIMER: HttpClient developers DO NOT actively support this component.
 * The component is provided as a reference material, which may be inappropriate
 * for use without additional customization.
 * </p>
 */

public class EasySSLProtocolSocketFactory implements SecureProtocolSocketFactory {

    /** Log object for this class. */
    private static final Log LOG = LogFactory.getLog(EasySSLProtocolSocketFactory.class);

    private SSLContext sslcontext = null;

    /**
     * Constructor for EasySSLProtocolSocketFactory.
     */
    public EasySSLProtocolSocketFactory() {
        super();
    }

    private static SSLContext createEasySSLContext() {
        try {
            SSLContext context = SSLContext.getInstance("SSL");
            context.init(
              null, 
              new TrustManager[] {new EasyX509TrustManager(null)}, 
              null);
            return context;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new HttpClientError(e.toString());
        }
    }

    private SSLContext getSSLContext() {
        if (this.sslcontext == null) {
            this.sslcontext = createEasySSLContext();
        }
        return this.sslcontext;
    }

    /**
     * @see SecureProtocolSocketFactory#createSocket(java.lang.String,int,java.net.InetAddress,int)
     */
    public Socket createSocket(
        String host,
        int port,
        InetAddress clientHost,
        int clientPort)
        throws IOException, UnknownHostException {

        return getSSLContext().getSocketFactory().createSocket(
            host,
            port,
            clientHost,
            clientPort
        );
    }

    /**
     * Attempts to get a new socket connection to the given host within the given time limit.
     * <p>
     * To circumvent the limitations of older JREs that do not support connect timeout a 
     * controller thread is executed. The controller thread attempts to create a new socket 
     * within the given limit of time. If socket constructor does not return until the 
     * timeout expires, the controller terminates and throws an {@link ConnectTimeoutException}
     * </p>
     *  
     * @param host the host name/IP
     * @param port the port on the host
     * @param clientHost the local host name/IP to bind the socket to
     * @param clientPort the port on the local machine
     * @param params {@link HttpConnectionParams Http connection parameters}
     * 
     * @return Socket a new socket
     * 
     * @throws IOException if an I/O error occurs while creating the socket
     * @throws UnknownHostException if the IP address of the host cannot be
     * determined
     */
    public Socket createSocket(
        final String host,
        final int port,
        final InetAddress localAddress,
        final int localPort,
        final HttpConnectionParams params
    ) throws IOException, UnknownHostException, ConnectTimeoutException {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        int timeout = params.getConnectionTimeout();
        if (timeout == 0) {
            return createSocket(host, port, localAddress, localPort);
        } else {
            // To be eventually deprecated when migrated to Java 1.4 or above
            return ControllerThreadSocketFactory.createSocket(
                    this, host, port, localAddress, localPort, timeout);
        }
    }

    /**
     * @see SecureProtocolSocketFactory#createSocket(java.lang.String,int)
     */
    public Socket createSocket(String host, int port)
        throws IOException, UnknownHostException {
        return getSSLContext().getSocketFactory().createSocket(
            host,
            port
        );
    }

    /**
     * @see SecureProtocolSocketFactory#createSocket(java.net.Socket,java.lang.String,int,boolean)
     */
    public Socket createSocket(
        Socket socket,
        String host,
        int port,
        boolean autoClose)
        throws IOException, UnknownHostException {
        return getSSLContext().getSocketFactory().createSocket(
            socket,
            host,
            port,
            autoClose
        );
    }

    public boolean equals(Object obj) {
        return ((obj != null) && obj.getClass().equals(EasySSLProtocolSocketFactory.class));
    }

    public int hashCode() {
        return EasySSLProtocolSocketFactory.class.hashCode();
    }

}
