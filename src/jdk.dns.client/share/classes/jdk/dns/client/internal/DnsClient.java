/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.dns.client.internal;

import jdk.dns.client.ex.DnsCommunicationException;
import jdk.dns.client.ex.DnsNameNotFoundException;
import jdk.dns.client.ex.DnsResolverException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.DatagramChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import sun.net.util.IPAddressUtil;
import sun.security.jca.JCAUtil;

// Some of this code began life as part of sun.javaos.net.DnsClient
// originally by sritchie@eng 1/96.  It was first hacked up for JNDI
// use by caveh@eng 6/97.


/**
 * The DnsClient class performs DNS client operations in support of DnsContext.
 */

public class DnsClient {

    // DNS packet header field offsets
    private static final int IDENT_OFFSET = 0;
    private static final int FLAGS_OFFSET = 2;
    private static final int NUMQ_OFFSET = 4;
    private static final int NUMANS_OFFSET = 6;
    private static final int NUMAUTH_OFFSET = 8;
    private static final int NUMADD_OFFSET = 10;
    private static final int DNS_HDR_SIZE = 12;

    // DNS response codes
    private static final int NO_ERROR = 0;
    private static final int FORMAT_ERROR = 1;
    private static final int SERVER_FAILURE = 2;
    private static final int NAME_ERROR = 3;
    private static final int NOT_IMPL = 4;
    private static final int REFUSED = 5;

    private static final String[] rcodeDescription = {
            "No error",
            "DNS format error",
            "DNS server failure",
            "DNS name not found",
            "DNS operation not supported",
            "DNS service refused"
    };

    private static final int DEFAULT_PORT = 53;
    private static final int TRANSACTION_ID_BOUND = 0x10000;
    private List<InetAddress> servers;
    private List<Integer> serverPorts;
    private int timeout;                // initial timeout on UDP and TCP queries in ms
    private int retries;                // number of UDP retries

    private static final SecureRandom random;

    static {
        var pa = (PrivilegedAction<SecureRandom>) () -> JCAUtil.getSecureRandom();
        random = System.getSecurityManager() == null ? pa.run()
                : AccessController.doPrivileged(pa);
    }

    private static final DnsDatagramChannelFactory factory =
            new DnsDatagramChannelFactory(random);

    // Requests sent
    private Map<Integer, ResourceRecord> reqs;

    // Responses received
    private Map<Integer, byte[]> resps;

    //-------------------------------------------------------------------------

    /*
     * Each server is of the form "server[:port]".  IPv6 literal host names
     * include delimiting brackets.
     * "timeout" is the initial timeout interval (in ms) for queries,
     * and "retries" gives the number of retries per server.
     */
    public DnsClient(List<String> servers, int timeout, int retries) {
        this.timeout = timeout;
        this.retries = retries;
        var serversList = new ArrayList<InetAddress>();
        var serverPortsList = new ArrayList<Integer>();

        if (DEBUG) {
            System.err.println("DNS Client: servers list:" + servers);
        }

        for (String serverString : servers) {

            // Is optional port given?
            int colon = serverString.indexOf(':',
                    serverString.indexOf(']') + 1);

            int serverPort = (colon < 0) ? DEFAULT_PORT
                    : Integer.parseInt(serverString.substring(colon + 1));
            String server = (colon < 0)
                    ? serverString
                    : serverString.substring(0, colon);

            var pa = (PrivilegedAction<byte[]>) () -> {
                if (IPAddressUtil.isIPv4LiteralAddress(server)) {
                    return IPAddressUtil.textToNumericFormatV4(server);
                } else if (IPAddressUtil.isIPv6LiteralAddress(server)) {
                    return IPAddressUtil.textToNumericFormatV6(server);
                }
                return null;
            };
            byte[] addr = System.getSecurityManager() == null ?
                    pa.run() : AccessController.doPrivileged(pa);
            if (addr != null) {
                try {
                    serversList.add(InetAddress.getByAddress(server, addr));
                    serverPortsList.add(serverPort);
                } catch (UnknownHostException e) {
                    // Malformed IP address is specified - will ignore it
                }
            }
        }
        this.servers = Collections.unmodifiableList(serversList);
        this.serverPorts = Collections.unmodifiableList(serverPortsList);
        reqs = Collections.synchronizedMap(
                new HashMap<>());
        resps = Collections.synchronizedMap(new HashMap<>());
    }

    DatagramChannel getDatagramChannel() throws DnsResolverException {
        try {
            return factory.open();
        } catch (java.net.SocketException e) {
            throw new DnsResolverException("Can't create datagram channel", e);
        }
    }

    @SuppressWarnings("deprecation")
    protected void finalize() {
        close();
    }

    // A lock to access the request and response queues in tandem.
    private ReentrantLock queuesLock = new ReentrantLock();

    public void close() {
        queuesLock.lock();
        try {
            reqs.clear();
            resps.clear();
        } finally {
            queuesLock.unlock();
        }
    }

    /*
     * If recursion is true, recursion is requested on the query.
     * If auth is true, only authoritative responses are accepted; other
     * responses throw NameNotFoundException.
     */
    ResourceRecords query(DnsName fqdn, int qclass, int qtype,
                          boolean recursion, boolean auth)
            throws DnsResolverException {

        int xid;
        Packet pkt;
        ResourceRecord collision;

        do {
            // Generate a random transaction ID
            xid = random.nextInt(TRANSACTION_ID_BOUND);
            pkt = makeQueryPacket(fqdn, xid, qclass, qtype, recursion);

            // enqueue the outstanding request
            collision = reqs.putIfAbsent(xid, new ResourceRecord(pkt.getData(),
                    pkt.length(), Header.HEADER_SIZE, true, false));

        } while (collision != null);

        Exception caughtException = null;
        boolean[] doNotRetry = new boolean[servers.size()];

        try {
            //
            // The UDP retry strategy is to try the 1st server, and then
            // each server in order. If no answer, double the timeout
            // and try each server again.
            //
            for (int retry = 0; retry < retries; retry++) {

                // Try each name server.
                for (int i = 0; i < servers.size(); i++) {
                    if (doNotRetry[i]) {
                        continue;
                    }

                    // send the request packet and wait for a response.
                    try {
                        if (DEBUG) {
                            dprint("SEND ID (" + (retry + 1) + "): " + xid);
                        }

                        byte[] msg = doUdpQuery(pkt, servers.get(i), serverPorts.get(i), retry, xid);
                        //
                        // If the matching response is not got within the
                        // given timeout, check if the response was enqueued
                        // by some other thread, if not proceed with the next
                        // server or retry.
                        //
                        if (msg == null) {
                            if (resps.size() > 0) {
                                msg = lookupResponse(xid);
                            }
                            if (msg == null) { // try next server or retry
                                continue;
                            }
                        }
                        Header hdr = new Header(msg, msg.length);

                        if (auth && !hdr.authoritative) {
                            caughtException = new DnsResolverException("DNS response not authoritative");
                            doNotRetry[i] = true;
                            continue;
                        }
                        if (hdr.truncated) {  // message is truncated -- try TCP

                            // Try each server, starting with the one that just
                            // provided the truncated message.
                            int retryTimeout = (timeout * (1 << retry));
                            for (int j = 0; j < servers.size(); j++) {
                                int ij = (i + j) % servers.size();
                                if (doNotRetry[ij]) {
                                    continue;
                                }
                                try {
                                    Tcp tcp =
                                            new Tcp(servers.get(ij), serverPorts.get(ij), retryTimeout);
                                    byte[] msg2;
                                    try {
                                        msg2 = doTcpQuery(tcp, pkt);
                                    } finally {
                                        tcp.close();
                                    }
                                    Header hdr2 = new Header(msg2, msg2.length);
                                    if (hdr2.query) {
                                        throw new DnsResolverException(
                                                "DNS error: expecting response");
                                    }
                                    checkResponseCode(hdr2);

                                    if (!auth || hdr2.authoritative) {
                                        // Got a valid response
                                        hdr = hdr2;
                                        msg = msg2;
                                        break;
                                    } else {
                                        doNotRetry[ij] = true;
                                    }
                                } catch (Exception e) {
                                    // Try next server, or use UDP response
                                }
                            } // servers
                        }
                        return new ResourceRecords(msg, msg.length, hdr, false);

                    } catch (PortUnreachableException e) {
                        if (caughtException == null) {
                            caughtException = e;
                        }
                        doNotRetry[i] = true;
                    } catch (IOException e) {
                        if (DEBUG) {
                            dprint("Caught IOException:" + e);
                        }
                        if (caughtException == null) {
                            caughtException = e;
                        }
                    } catch (DnsNameNotFoundException e) {
                        // This is authoritative, so return immediately
                        throw e;
                    } catch (DnsCommunicationException e) {
                        if (caughtException == null) {
                            caughtException = e;
                        }
                    } catch (DnsResolverException e) {
                        if (caughtException == null) {
                            caughtException = e;
                        }
                        doNotRetry[i] = true;
                    }
                } // servers
            } // retries

        } finally {
            reqs.remove(xid); // cleanup
        }

        if (caughtException instanceof DnsResolverException) {
            throw (DnsResolverException) caughtException;
        }
        // A network timeout or other error occurred.
        throw new DnsResolverException("DNS error", caughtException);
    }

    /**
     * Tries to retrieve a UDP packet matching the given xid
     * received within the timeout.
     * If a packet with different xid is received, the received packet
     * is enqueued with the corresponding xid in 'resps'.
     */
    private byte[] doUdpQuery(Packet pkt, InetAddress server,
                              int port, int retry, int xid)
            throws IOException, DnsResolverException {

        int minTimeout = 50; // msec after which there are no retries.


        try (DatagramChannel dc = getDatagramChannel()) {
            DatagramPacket opkt = new DatagramPacket(pkt.getData(), pkt.length(), server, port);
            DatagramPacket ipkt = new DatagramPacket(new byte[8000], 8000);
            // Packets may only be sent to or received from this server address
            // TODO: Revisit
            var pa = (PrivilegedAction<Void>) () -> {
                dc.socket().connect(server, port);
                return null;
            };
            if (System.getSecurityManager() == null) {
                pa.run();
            } else {
                AccessController.doPrivileged(pa);
            }


            int pktTimeout = (timeout * (1 << retry));
            try {
                dc.socket().send(opkt);

                // timeout remaining after successive 'receive()'
                int timeoutLeft = pktTimeout;
                int cnt = 0;
                do {
                    if (DEBUG) {
                        cnt++;
                        dprint("Trying RECEIVE(" +
                                cnt + ") retry(" + (retry + 1) +
                                ") for:" + xid + "    sock-timeout:" +
                                timeoutLeft + " ms.");
                    }
                    dc.socket().setSoTimeout(timeoutLeft);
                    long start = System.currentTimeMillis();


                    dc.socket().receive(ipkt);
                    byte[] data = ipkt.getData();
                    int length = ipkt.getLength();
                    long end = System.currentTimeMillis();

                    if (isMatchResponse(data, length, xid)) {
                        return data;
                    }
                    timeoutLeft = pktTimeout - ((int) (end - start));
                } while (timeoutLeft > minTimeout);

            } finally {
                dc.disconnect();
            }
            return null; // no matching packet received within the timeout
        }
    }

    /*
     * Sends a TCP query, and returns the first DNS message in the response.
     */
    private byte[] doTcpQuery(Tcp tcp, Packet pkt) throws IOException {

        int len = pkt.length();
        // Send 2-byte message length, then send message.
        tcp.out.write(len >> 8);
        tcp.out.write(len);
        tcp.out.write(pkt.getData(), 0, len);
        tcp.out.flush();

        byte[] msg = continueTcpQuery(tcp);
        if (msg == null) {
            throw new IOException("DNS error: no response");
        }
        return msg;
    }

    /*
     * Returns the next DNS message from the TCP socket, or null on EOF.
     */
    private byte[] continueTcpQuery(Tcp tcp) throws IOException {

        int lenHi = tcp.read();      // high-order byte of response length
        if (lenHi == -1) {
            return null;        // EOF
        }
        int lenLo = tcp.read();      // low-order byte of response length
        if (lenLo == -1) {
            throw new IOException("Corrupted DNS response: bad length");
        }
        int len = (lenHi << 8) | lenLo;
        byte[] msg = new byte[len];
        int pos = 0;                    // next unfilled position in msg
        while (len > 0) {
            int n = tcp.read(msg, pos, len);
            if (n == -1) {
                throw new IOException(
                        "Corrupted DNS response: too little data");
            }
            len -= n;
            pos += n;
        }
        return msg;
    }

    private Packet makeQueryPacket(DnsName fqdn, int xid,
                                   int qclass, int qtype, boolean recursion) {
        int qnameLen = fqdn.getOctets();
        int pktLen = DNS_HDR_SIZE + qnameLen + 4;
        Packet pkt = new Packet(pktLen);

        short flags = recursion ? Header.RD_BIT : 0;
        // flags = (short) (flags | Header.CD_BIT | Header.AD_BIT);

        pkt.putShort(xid, IDENT_OFFSET);
        pkt.putShort(flags, FLAGS_OFFSET);
        pkt.putShort(1, NUMQ_OFFSET);
        pkt.putShort(0, NUMANS_OFFSET);
        pkt.putInt(0, NUMAUTH_OFFSET);

        makeQueryName(fqdn, pkt, DNS_HDR_SIZE);
        pkt.putShort(qtype, DNS_HDR_SIZE + qnameLen);
        pkt.putShort(qclass, DNS_HDR_SIZE + qnameLen + 2);

        return pkt;
    }

    // Builds a query name in pkt according to the RFC spec.
    private void makeQueryName(DnsName fqdn, Packet pkt, int off) {

        // Loop through labels, least-significant first.
        for (int i = fqdn.size() - 1; i >= 0; i--) {
            String label = fqdn.get(i);
            int len = label.length();

            pkt.putByte(len, off++);
            for (int j = 0; j < len; j++) {
                pkt.putByte(label.charAt(j), off++);
            }
        }
        if (!fqdn.hasRootLabel()) {
            pkt.putByte(0, off);
        }
    }

    //-------------------------------------------------------------------------

    private byte[] lookupResponse(Integer xid) throws DnsResolverException {
        //
        // Check the queued responses: some other thread in between
        // received the response for this request.
        //
        if (DEBUG) {
            dprint("LOOKUP for: " + xid +
                    "\tResponse Q:" + resps);
        }
        byte[] pkt;
        if ((pkt = resps.get(xid)) != null) {
            checkResponseCode(new Header(pkt, pkt.length));
            queuesLock.lock();
            try {
                resps.remove(xid);
                reqs.remove(xid);
            } finally {
                queuesLock.unlock();
            }

            if (DEBUG) {
                dprint("FOUND (" + Thread.currentThread() +
                        ") for:" + xid);
            }
        }
        return pkt;
    }

    /*
     * Checks the header of an incoming DNS response.
     * Returns true if it matches the given xid and throws a naming
     * exception, if appropriate, based on the response code.
     *
     * Also checks that the domain name, type and class in the response
     * match those in the original query.
     */
    private boolean isMatchResponse(byte[] pkt, int length, int xid)
            throws DnsResolverException {

        Header hdr = new Header(pkt, length);
        if (hdr.query) {
            throw new DnsResolverException("DNS error: expecting response");
        }

        if (!reqs.containsKey(xid)) { // already received, ignore the response
            return false;
        }

        // common case- the request sent matches the subsequent response read
        if (hdr.xid == xid) {
            if (DEBUG) {
                dprint("XID MATCH:" + xid);
            }
            checkResponseCode(hdr);
            if (!hdr.query && hdr.numQuestions == 1) {

                ResourceRecord rr = new ResourceRecord(pkt, length,
                        Header.HEADER_SIZE, true, false);

                // Retrieve the original query
                ResourceRecord query = reqs.get(xid);
                int qtype = query.getType();
                int qclass = query.getRrclass();
                DnsName qname = query.getName();

                // Check that the type/class/name in the query section of the
                // response match those in the original query
                if ((qtype == ResourceRecord.TYPE_ANY ||
                        qtype == rr.getType()) &&
                        (qclass == ResourceRecord.QCLASS_STAR ||
                                qclass == rr.getRrclass()) &&
                        qname.equals(rr.getName())) {

                    if (DEBUG) {
                        dprint("MATCH NAME:" + qname + " QTYPE:" + qtype +
                                " QCLASS:" + qclass);
                    }

                    // Remove the response for the xid if received by some other
                    // thread.
                    queuesLock.lock();
                    try {
                        resps.remove(xid);
                        reqs.remove(xid);
                    } finally {
                        queuesLock.unlock();
                    }
                    return true;

                } else {
                    if (DEBUG) {
                        dprint("NO-MATCH NAME:" + qname + " QTYPE:" + qtype +
                                " QCLASS:" + qclass);
                    }
                }
            }
            return false;
        }

        //
        // xid mis-match: enqueue the response, it may belong to some other
        // thread that has not yet had a chance to read its response.
        // enqueue only the first response, responses for retries are ignored.
        //
        queuesLock.lock();
        try {
            if (reqs.containsKey(hdr.xid)) { // enqueue only the first response
                resps.put(hdr.xid, Arrays.copyOf(pkt, length));
            }
        } finally {
            queuesLock.unlock();
        }

        if (DEBUG) {
            dprint("NO-MATCH SEND ID:" +
                    xid + " RECVD ID:" + hdr.xid +
                    "    Response Q:" + resps +
                    "    Reqs size:" + reqs.size());
        }
        return false;
    }

    /*
     * Throws an exception if appropriate for the response code of a
     * given header.
     */
    private void checkResponseCode(Header hdr) throws DnsResolverException {

        int rcode = hdr.rcode;
        if (rcode == NO_ERROR) {
            return;
        }
        String msg = (rcode < rcodeDescription.length)
                ? rcodeDescription[rcode]
                : "DNS error";

        msg += " [response code " + rcode + "]";
        throw new DnsResolverException(msg);
    }

    //-------------------------------------------------------------------------

    private static final boolean DEBUG = java.security.AccessController.doPrivileged(
            (PrivilegedAction<Boolean>) () -> Boolean.getBoolean("jdk.dns.client.debug"));

    private static void dprint(String mess) {
        if (DEBUG) {
            System.err.println("DNS: " + mess);
        }
    }

}

class Tcp {

    private final Socket sock;
    private final InputStream in;
    final OutputStream out;
    private int timeoutLeft;

    Tcp(InetAddress server, int port, int timeout) throws IOException {
        sock = new Socket();
        try {
            long start = System.currentTimeMillis();
            sock.connect(new InetSocketAddress(server, port), timeout);
            timeoutLeft = (int) (timeout - (System.currentTimeMillis() - start));
            if (timeoutLeft <= 0)
                throw new SocketTimeoutException();

            sock.setTcpNoDelay(true);
            out = new BufferedOutputStream(sock.getOutputStream());
            in = new BufferedInputStream(sock.getInputStream());
        } catch (Exception e) {
            try {
                sock.close();
            } catch (IOException ex) {
                e.addSuppressed(ex);
            }
            throw e;
        }
    }

    void close() throws IOException {
        sock.close();
    }

    private interface SocketReadOp {
        int read() throws IOException;
    }

    private int readWithTimeout(SocketReadOp reader) throws IOException {
        if (timeoutLeft <= 0)
            throw new SocketTimeoutException();

        sock.setSoTimeout(timeoutLeft);
        long start = System.currentTimeMillis();
        try {
            return reader.read();
        } finally {
            timeoutLeft -= System.currentTimeMillis() - start;
        }
    }

    int read() throws IOException {
        return readWithTimeout(in::read);
    }

    int read(byte b[], int off, int len) throws IOException {
        return readWithTimeout(() -> in.read(b, off, len));
    }
}

/*
 * javaos emulation -cj
 */
class Packet {
    byte[] buf;

    Packet(int len) {
        buf = new byte[len];
    }

    Packet(byte data[], int len) {
        buf = new byte[len];
        System.arraycopy(data, 0, buf, 0, len);
    }

    void putInt(int x, int off) {
        buf[off] = (byte) (x >> 24);
        buf[off + 1] = (byte) (x >> 16);
        buf[off + 2] = (byte) (x >> 8);
        buf[off + 3] = (byte) x;
    }

    void putShort(int x, int off) {
        buf[off] = (byte) (x >> 8);
        buf[off + 1] = (byte) x;
    }

    void putByte(int x, int off) {
        buf[off] = (byte) x;
    }

    void putBytes(byte src[], int src_offset, int dst_offset, int len) {
        System.arraycopy(src, src_offset, buf, dst_offset, len);
    }

    int length() {
        return buf.length;
    }

    byte[] getData() {
        return buf;
    }
}
