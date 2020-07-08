/*
 * ============LICENSE_START=======================================================
 * feature-server-pool
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.serverpool;

import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_HTTPS;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_SELF_SIGNED_CERTIFICATES;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_SERVER_ADAPTIVE_GAP_ADJUST;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_SERVER_CONNECT_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_SERVER_INITIAL_ALLOWED_GAP;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_SERVER_IP_ADDRESS;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_SERVER_PORT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_SERVER_READ_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_SERVER_THREADS_CORE_POOL_SIZE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_SERVER_THREADS_KEEP_ALIVE_TIME;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_SERVER_THREADS_MAXIMUM_POOL_SIZE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.HOST_LIST;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SERVER_ADAPTIVE_GAP_ADJUST;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SERVER_CONNECT_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SERVER_HTTPS;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SERVER_INITIAL_ALLOWED_GAP;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SERVER_IP_ADDRESS;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SERVER_PORT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SERVER_READ_TIMEOUT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SERVER_SELF_SIGNED_CERTIFICATES;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SERVER_THREADS_CORE_POOL_SIZE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SERVER_THREADS_KEEP_ALIVE_TIME;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SERVER_THREADS_MAXIMUM_POOL_SIZE;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SITE_IP_ADDRESS;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.SITE_PORT;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.getProperty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;
import org.onap.policy.common.endpoints.event.comm.bus.internal.BusTopicParams;
import org.onap.policy.common.endpoints.http.client.HttpClient;
import org.onap.policy.common.endpoints.http.client.HttpClientConfigException;
import org.onap.policy.common.endpoints.http.client.HttpClientFactoryInstance;
import org.onap.policy.common.endpoints.http.server.HttpServletServer;
import org.onap.policy.common.endpoints.http.server.HttpServletServerFactoryInstance;
import org.onap.policy.drools.utils.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server implements Comparable<Server> {
    private static Logger logger = LoggerFactory.getLogger(Server.class);

    // maps UUID to Server object for all known servers
    private static TreeMap<UUID, Server> servers =
        new TreeMap<>(Util.uuidComparator);

    // maps UUID to Server object for all failed servers
    // (so they aren't accidentally restored, due to updates from other hosts)
    private static TreeMap<UUID, Server> failedServers =
        new TreeMap<>(Util.uuidComparator);

    // subset of servers to be notified (null means it needs to be rebuilt)
    private static LinkedList<Server> notifyList = null;

    // data to be sent out to notify list
    private static TreeSet<Server> updatedList = new TreeSet<>();

    // the server associated with the current host
    private static Server thisServer = null;

    // the current REST server
    private static HttpServletServer restServer;

    /*==================================================*/
    /* Some properties extracted at initialization time */
    /*==================================================*/

    // initial value of gap to allow between pings
    private static long initialAllowedGap;

    // used in adaptive calculation of allowed gap between pings
    private static long adaptiveGapAdjust;

    // time to allow for TCP connect (long)
    private static String connectTimeout;

    // time to allow before TCP read timeout (long)
    private static String readTimeout;

    // outgoing per-server thread pool parameters
    private static int corePoolSize;
    private static int maximumPoolSize;
    private static long keepAliveTime;

    // https-related parameters
    private static boolean useHttps;
    private static boolean useSelfSignedCertificates;

    // list of remote host names
    private static String[] hostList = new String[0];

    /*=========================================================*/
    /* Fields included in every 'ping' message between servers */
    /*=========================================================*/

    // unique id for this server
    private UUID uuid;

    // counter periodically incremented to indicate the server is "alive"
    private int count;

    // 16 byte MD5 checksum over additional data that is NOT included in
    // every 'ping' message -- used to determine whether the data is up-to-date
    private byte[] checksum;

    /*========================================================================*/
    /* The following data is included in the checksum, and doesn't change too */
    /* frequently (some fields may change as servers go up and down)          */
    /*========================================================================*/

    // IP address and port of listener
    private InetSocketAddress socketAddress;

    // site IP address and port
    private InetSocketAddress siteSocketAddress = null;

    /*============================================*/
    /* Local information not included in checksum */
    /*============================================*/

    // destination socket information
    private InetSocketAddress destSocketAddress;
    private String destName;

    // REST client fields
    private HttpClient client;
    private WebTarget target;
    private ThreadPoolExecutor sendThreadPool = null;

    // time when the 'count' field was last updated
    private long lastUpdateTime;

    // calculated field indicating the maximum time between updates
    private long allowedGap = initialAllowedGap;

    // indicates whether the 'Server' instance is active or not (synchronized)
    private boolean active = true;

    /*
     * Tags for encoding of server data
     */
    static final int END_OF_PARAMETERS_TAG = 0;
    static final int SOCKET_ADDRESS_TAG = 1;
    static final int SITE_SOCKET_ADDRESS_TAG = 2;

    // 'pingHosts' error
    static final String PINGHOSTS_ERROR = "Server.pingHosts error";

    // a string for print
    static final String PRINTOUT_DASHES = "-------";

    /*==============================*/
    /* Comparable<Server> interface */
    /*==============================*/

    /**
     * Compare this instance to another one by comparing the 'uuid' field.
     */
    @Override
    public int compareTo(Server other) {
        return Util.uuidComparator.compare(uuid, other.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Server)) {
            return false;
        }
        Server other = (Server) obj;
        return Objects.equals(uuid, other.uuid);
    }

    /**
     * This method may be invoked from any thread, and is used as the main
     * entry point when testing.
     *
     * @param args arguments containing an '=' character are interpreted as
     *     a property, other arguments are presumed to be a property file.
     */
    public static void main(String[] args) throws IOException {
        Properties prop = new Properties();

        for (String arg : args) {
            // arguments with an equals sign in them are a property definition;
            // otherwise, they are a properties file name

            if (arg.contains("=")) {
                prop.load(new StringReader(arg));
            } else {
                prop.putAll(PropertyUtil.getProperties(arg));
            }
        }

        String rval = startup(prop);
        if (rval != null) {
            logger.error("Server.startup failed: {}", rval);
        }
    }

    /**
     * This method may be invoked from any thread, and performs initialization.
     *
     * @param propertiesFile the name of a property file
     */
    public static String startup(String propertiesFile) {
        Properties properties;
        try {
            properties = PropertyUtil.getProperties(propertiesFile);
        } catch (IOException e) {
            logger.error("Server.startup: exception reading properties", e);
            properties = new Properties();
        }
        return startup(properties);
    }

    /**
     * This method may be invoked from any thread, and performs initialization.
     *
     * @param properties contains properties used by the server
     */
    public static String startup(Properties properties) {
        ServerPoolProperties.setProperties(properties);
        logger.info("startup: properties={}", properties);

        // fetch some static properties
        initialAllowedGap = getProperty(SERVER_INITIAL_ALLOWED_GAP,
                                        DEFAULT_SERVER_INITIAL_ALLOWED_GAP);
        adaptiveGapAdjust = getProperty(SERVER_ADAPTIVE_GAP_ADJUST,
                                        DEFAULT_SERVER_ADAPTIVE_GAP_ADJUST);
        connectTimeout =
            String.valueOf(getProperty(SERVER_CONNECT_TIMEOUT,
                                       DEFAULT_SERVER_CONNECT_TIMEOUT));
        readTimeout = String.valueOf(getProperty(SERVER_READ_TIMEOUT,
                                     DEFAULT_SERVER_READ_TIMEOUT));
        corePoolSize = getProperty(SERVER_THREADS_CORE_POOL_SIZE,
                                   DEFAULT_SERVER_THREADS_CORE_POOL_SIZE);
        maximumPoolSize = getProperty(SERVER_THREADS_MAXIMUM_POOL_SIZE,
                                      DEFAULT_SERVER_THREADS_MAXIMUM_POOL_SIZE);
        keepAliveTime = getProperty(SERVER_THREADS_KEEP_ALIVE_TIME,
                                    DEFAULT_SERVER_THREADS_KEEP_ALIVE_TIME);
        useHttps = getProperty(SERVER_HTTPS, DEFAULT_HTTPS);
        useSelfSignedCertificates = getProperty(SERVER_SELF_SIGNED_CERTIFICATES,
                                                DEFAULT_SELF_SIGNED_CERTIFICATES);
        String hostListNames = getProperty(HOST_LIST, null);
        if (hostListNames != null) {
            hostList = hostListNames.split(",");
        }

        String possibleError = null;
        try {
            // fetch server information
            String ipAddressString =
                getProperty(SERVER_IP_ADDRESS, DEFAULT_SERVER_IP_ADDRESS);
            int port = getProperty(SERVER_PORT, DEFAULT_SERVER_PORT);

            possibleError = "Unknown Host: " + ipAddressString;
            InetAddress address = InetAddress.getByName(ipAddressString);
            InetSocketAddress socketAddress = new InetSocketAddress(address, port);

            possibleError = "HTTP server initialization error";
            restServer = HttpServletServerFactoryInstance.getServerFactory().build(
                         "SERVER-POOL",                                 // name
                         useHttps,                                      // https
                         socketAddress.getAddress().getHostAddress(),   // host (maybe 0.0.0.0)
                         port,                                          // port (can no longer be 0)
                         null,                                          // contextPath
                         false,                                         // swagger
                         false);                                        // managed
            restServer.addServletClass(null, RestServerPool.class.getName());

            // add any additional servlets
            for (ServerPoolApi feature : ServerPoolApi.impl.getList()) {
                Collection<Class<?>> classes = feature.servletClasses();
                if (classes != null) {
                    for (Class<?> clazz : classes) {
                        restServer.addServletClass(null, clazz.getName());
                    }
                }
            }

            // we may not know the port until after the server is started
            possibleError = "HTTP server start error";
            restServer.start();
            possibleError = null;

            // determine the address to use
            if (DEFAULT_SERVER_IP_ADDRESS.contentEquals(address.getHostAddress())) {
                address = InetAddress.getLocalHost();
            }

            thisServer = new Server(new InetSocketAddress(address, port));

            // TBD: is this really appropriate?
            thisServer.newServer();

            // start background thread
            MainLoop.startThread();
            MainLoop.queueWork(() -> {
                // run this in the 'MainLoop' thread
                Leader.startup();
                Bucket.startup();
            });
            logger.info("Listening on port {}", port);

            return null;
        } catch (UnknownHostException e) {
            logger.error("Server.startup: exception start server", e);
            if (possibleError == null) {
                possibleError = e.toString();
            }
            return possibleError;
        }
    }

    /**
     * Shut down all threads associate with server pool.
     */
    public static void shutdown() {
        Discovery.stopDiscovery();
        MainLoop.stopThread();
        TargetLock.shutdown();
        Util.shutdown();

        HashSet<Server> allServers = new HashSet<>();
        allServers.addAll(servers.values());
        allServers.addAll(failedServers.values());

        for (Server server : allServers) {
            if (server.sendThreadPool != null) {
                server.sendThreadPool.shutdown();
            }
        }
        if (restServer != null) {
            restServer.shutdown();
        }
    }

    /**
     * Return the Server instance associated with the current host.
     *
     * @return the Server instance associated with the current host
     */
    public static Server getThisServer() {
        return thisServer;
    }

    /**
     * Return the first Server instance in the 'servers' list.
     *
     * @return the first Server instance in the 'servers' list
     *     (the one with the lowest UUID)
     */
    public static Server getFirstServer() {
        return servers.firstEntry().getValue();
    }

    /**
     * Lookup a Server instance associated with a UUID.
     *
     * @param uuid the key to the lookup
     @ @return the associated 'Server' instance, or 'null' if none
     */
    public static Server getServer(UUID uuid) {
        return servers.get(uuid);
    }

    /**
     * Return a count of the number of servers.
     *
     * @return a count of the number of servers
     */
    public static int getServerCount() {
        return servers.size();
    }

    /**
     * Return the complete list of servers.
     *
     * @return the complete list of servers
     */
    public static Collection<Server> getServers() {
        return servers.values();
    }

    /**
     * This method is invoked from the 'startup' thread, and creates a new
     * 'Server' instance for the current server.
     *
     * @param socketAddress the IP address and port the listener is bound to
     */
    private Server(InetSocketAddress socketAddress) {
        this.uuid = UUID.randomUUID();
        this.count = 1;
        this.socketAddress = socketAddress;
        this.lastUpdateTime = System.currentTimeMillis();

        // site information

        String siteIp = getProperty(SITE_IP_ADDRESS, null);
        int sitePort = getProperty(SITE_PORT, 0);
        if (siteIp != null && sitePort != 0) {
            // we do have site information specified
            try {
                siteSocketAddress = new InetSocketAddress(siteIp, sitePort);
                if (siteSocketAddress.getAddress() == null) {
                    logger.error("Couldn't resolve site address: {}", siteIp);
                    siteSocketAddress = null;
                }
            } catch (IllegalArgumentException e) {
                logger.error("Illegal 'siteSocketAddress'", e);
                siteSocketAddress = null;
            }
        }

        // TBD: calculate checksum
    }

    /**
     * Initialize a 'Server' instance from a 'DataInputStream'. If it is new,
     * it may get inserted in the table. If it is an update, fields in an
     * existing 'Server' may be updated.
     *
     * @param is the 'DataInputStream'
     */
    Server(DataInputStream is) throws IOException {
        // read in 16 byte UUID
        uuid = Util.readUuid(is);

        // read in 4 byte counter value
        count = is.readInt();

        // read in 16 byte MD5 checksum
        checksum = new byte[16];
        is.readFully(checksum);

        // optional parameters
        int tag;
        while ((tag = is.readUnsignedByte()) != END_OF_PARAMETERS_TAG) {
            switch (tag) {
                case SOCKET_ADDRESS_TAG:
                    socketAddress = readSocketAddress(is);
                    break;
                case SITE_SOCKET_ADDRESS_TAG:
                    siteSocketAddress = readSocketAddress(is);
                    break;
                default:
                    // ignore tag
                    logger.error("Illegal tag: {}", tag);
                    break;
            }
        }
    }

    /**
     * Read an 'InetSocketAddress' from a 'DataInputStream'.
     *
     * @param is the 'DataInputStream'
     * @return the 'InetSocketAddress'
     */
    private static InetSocketAddress readSocketAddress(DataInputStream is) throws IOException {

        byte[] ipAddress = new byte[4];
        is.read(ipAddress, 0, 4);
        int port = is.readUnsignedShort();
        return new InetSocketAddress(InetAddress.getByAddress(ipAddress), port);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Server[" + uuid + "]";
    }

    /**
     * Return the UUID associated with this Server.
     *
     * @return the UUID associated with this Server
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Return the external InetSocketAddress of the site.
     *
     * @return the external InetSocketAddress of the site
     *     ('null' if it doesn't exist)
     */
    public InetSocketAddress getSiteSocketAddress() {
        return siteSocketAddress;
    }

    /**
     * This method may be called from any thread.
     *
     * @return 'true' if the this server is active, and 'false' if not
     */
    public synchronized boolean isActive() {
        return active;
    }

    /**
     * This method writes out the data associated with the current Server
     * instance.
     *
     * @param os outout stream that should receive the data
     */
    void writeServerData(DataOutputStream os) throws IOException {
        // write out 16 byte UUID
        Util.writeUuid(os, uuid);

        // write out 4 byte counter value
        os.writeInt(count);

        // write out 16 byte MD5 checksum
        // TBD: should this be implemented?
        os.write(checksum == null ? new byte[16] : checksum);

        if (socketAddress != null) {
            // write out socket address
            os.writeByte(SOCKET_ADDRESS_TAG);
            os.write(socketAddress.getAddress().getAddress(), 0, 4);
            os.writeShort(socketAddress.getPort());
        }

        if (siteSocketAddress != null) {
            // write out socket address
            os.writeByte(SITE_SOCKET_ADDRESS_TAG);
            os.write(siteSocketAddress.getAddress().getAddress(), 0, 4);
            os.writeShort(siteSocketAddress.getPort());
        }

        os.writeByte(END_OF_PARAMETERS_TAG);
    }

    /**
     * Do any processing needed to create a new server. This method is invoked
     * from the 'MainLoop' thread in every case except for the current server,
     * in which case it is invoked in 'startup' prior to creating 'MainLoop'.
     */
    private void newServer() {
        Server failed = failedServers.get(uuid);
        if (failed != null) {
            // this one is on the failed list -- see if the counter has advanced
            if ((count - failed.count) <= 0) {
                // the counter has not advanced -- ignore
                return;
            }

            // the counter has advanced -- somehow, this server has returned
            failedServers.remove(uuid);
            synchronized (this) {
                active = true;
            }
            logger.error("Server reawakened: {} ({})", uuid, socketAddress);
        }

        lastUpdateTime = System.currentTimeMillis();
        servers.put(uuid, this);
        updatedList.add(this);

        // notify list will need to be rebuilt
        notifyList = null;

        if (socketAddress != null && this != thisServer) {
            // initialize 'client' and 'target' fields
            if (siteSocketAddress != null
                    && !siteSocketAddress.equals(thisServer.siteSocketAddress)) {
                // destination is on a remote site
                destSocketAddress = siteSocketAddress;
            } else {
                // destination is on the local site -- use direct addressing
                destSocketAddress = socketAddress;
            }
            destName = socketAddressToName(destSocketAddress);
            try {
                // 'client' is used for REST messages to the destination
                client = buildClient(uuid.toString(), destSocketAddress, destName);

                // initialize the 'target' field
                target = getTarget(client);
            } catch (NoSuchFieldException | IllegalAccessException | HttpClientConfigException e) {
                logger.error("Server.newServer: problems creating 'client'", e);
            }
        }
        logger.info("New server: {} ({})", uuid, socketAddress);
        for (Events listener : Events.getListeners()) {
            listener.newServer(this);
        }
    }

    /**
     * Check the server state in response to some issue. At present, only the
     * 'destName' information is checked.
     */
    private void checkServer() {
        // recalculate 'destName' (we have seen DNS issues)
        String newDestName = socketAddressToName(destSocketAddress);
        if (newDestName.equals(destName)) {
            return;
        }
        logger.warn("Remote host name for {} has changed from {} to {}",
                    destSocketAddress, destName, newDestName);

        // shut down old client, and rebuild
        client.shutdown();
        client = null;
        target = null;

        // update 'destName', and rebuild the client
        destName = newDestName;
        try {
            // 'client' is used for REST messages to the destination
            client = buildClient(uuid.toString(), destSocketAddress, destName);

            // initialize the 'target' field
            target = getTarget(client);
        } catch (NoSuchFieldException | IllegalAccessException | HttpClientConfigException e) {
            logger.error("Server.checkServer: problems recreating 'client'", e);
        }
    }

    /**
     * Update server data.
     *
     * @param serverData this is a temporary 'Server' instance created from
     *     an incoming message, which is used to update fields within the
     *     'Server' instance identified by 'this'
     */
    private void updateServer(Server serverData) {
        if (serverData.count > count) {
            // an update has occurred
            count = serverData.count;

            // TBD: calculate and verify checksum, more fields may be updated

            // adjust 'allowedGap' accordingly
            long currentTime = System.currentTimeMillis();
            long gap = currentTime - lastUpdateTime;

            // adjust 'allowedGap' accordingly
            // TBD: need properties to support overrides
            gap = gap * 3 / 2 + adaptiveGapAdjust;
            if (gap > allowedGap) {
                // update 'allowedGap' immediately
                allowedGap = gap;
            } else {
                // gradually pull the allowed gap down
                // TBD: need properties to support overrides
                allowedGap = (allowedGap * 15 + gap) / 16;
            }
            lastUpdateTime = currentTime;

            updatedList.add(this);
        }
    }

    /**
     * a server has failed.
     */
    private void serverFailed() {
        // mark as inactive
        synchronized (this) {
            active = false;
        }

        // remove it from the table
        servers.remove(uuid);

        // add it to the failed servers table
        failedServers.put(uuid, this);

        // clean up client information
        if (client != null) {
            client.shutdown();
            client = null;
            target = null;
        }

        // log an error message
        logger.error("Server failure: {} ({})", uuid, socketAddress);
        for (Events listener : Events.getListeners()) {
            listener.serverFailed(this);
        }
    }

    /**
     * Fetch, and possibly calculate, the "notify list" associated with this
     * server. This is the list of servers to forward a server and bucket
     * information to, and is approximately log2(n) in length, where 'n' is
     * the total number of servers.
     * It is calculated by starting with all of the servers sorted by UUID --
     * let's say the current server is at position 's'. The notify list will
     * contain the server at positions:
     *     (s + 1) % n
     *     (s + 2) % n
     *     (s + 4) % n
     *          ...
     * Using all powers of 2 less than 'n'. If the total server count is 50,
     * this list has 6 entries.
     * @return the notify list
     */
    static Collection<Server> getNotifyList() {
        // The 'notifyList' value is initially 'null', and it is reset to 'null'
        // every time a new host joins, or one leaves. That way, it is marked for
        // recalculation, but only when needed.
        if (notifyList == null) {
            // next index we are looking for
            int dest = 1;

            // our current position in the Server table -- starting at 'thisServer'
            UUID current = thisServer.uuid;

            // site socket address of 'current'
            InetSocketAddress thisSiteSocketAddress = thisServer.siteSocketAddress;

            // hash set of all site socket addresses located
            HashSet<InetSocketAddress> siteSocketAddresses = new HashSet<>();
            siteSocketAddresses.add(thisSiteSocketAddress);

            // the list we are building
            notifyList = new LinkedList<>();

            int index = 1;
            for ( ; ; ) {
                // move to the next key (UUID) -- if we hit the end of the table,
                // wrap to the beginning
                current = servers.higherKey(current);
                if (current == null) {
                    current = servers.firstKey();
                }
                if (current.equals(thisServer.uuid)) {
                    // we have looped through the entire list
                    break;
                }

                // fetch associated server & site socket address
                Server server = servers.get(current);
                InetSocketAddress currentSiteSocketAddress =
                    server.siteSocketAddress;

                if (Objects.equals(thisSiteSocketAddress,
                                   currentSiteSocketAddress)) {
                    // same site -- see if we should add this one
                    if (index == dest) {
                        // this is the next index we are looking for --
                        // add the server
                        notifyList.add(server);

                        // advance to the next offset (current-offset * 2)
                        dest = dest << 1;
                    }
                    index += 1;
                } else if (!siteSocketAddresses.contains(currentSiteSocketAddress)) {
                    // we need at least one member from each site
                    notifyList.add(server);
                    siteSocketAddresses.add(currentSiteSocketAddress);
                }
            }
        }
        return notifyList;
    }

    /**
     * See if there is a host name associated with a destination socket address.
     *
     * @param dest the socket address of the destination
     * @return the host name associated with the IP address, or the IP address
     *     if no associated host name is found.
     */
    private static String socketAddressToName(InetSocketAddress dest) {
        // destination IP address
        InetAddress inetAddress = dest.getAddress();
        String destName = null;

        // go through the 'hostList' to see if there is a matching name
        for (String hostName : hostList) {
            try {
                if (inetAddress.equals(InetAddress.getByName(hostName))) {
                    // this one matches -- use the name instead of the IP address
                    destName = hostName;
                    break;
                }
            } catch (UnknownHostException e) {
                logger.debug("Server.socketAddressToName error", e);
            }
        }

        // default name = string value of IP address
        return destName == null ? inetAddress.getHostAddress() : destName;
    }

    /**
     * Create an 'HttpClient' instance for a particular host.
     *
     * @param name of the host (currently a UUID or host:port string)
     * @param dest the socket address of the destination
     * @param destName the string name to use for the destination
     */
    static HttpClient buildClient(String name, InetSocketAddress dest, String destName)
        throws HttpClientConfigException {

        return HttpClientFactoryInstance.getClientFactory().build(
            BusTopicParams.builder()
                .clientName(name)                               // name
                .useHttps(useHttps)                             // https
                .allowSelfSignedCerts(useSelfSignedCertificates)// selfSignedCerts
                .hostname(destName)                             // host
                .port(dest.getPort())                           // port
                .managed(false)                                 // managed
                .build());
    }

    /**
     * Extract the 'WebTarget' information from the 'HttpClient'.
     *
     * @param client the associated HttpClient instance
     * @return a WebTarget referring to the previously-specified 'baseUrl'
     */
    static WebTarget getTarget(HttpClient client)
        throws NoSuchFieldException, IllegalAccessException {
        // need access to the internal field 'client'
        // TBD: We need a way to get this information without reflection
        Field field = client.getClass().getDeclaredField("client");
        field.setAccessible(true);
        Client rsClient = (Client) field.get(client);
        field.setAccessible(false);

        rsClient.property(ClientProperties.CONNECT_TIMEOUT, connectTimeout);
        rsClient.property(ClientProperties.READ_TIMEOUT, readTimeout);

        // For performance reasons, the root 'WebTarget' is generated only once
        // at initialization time for each remote host.
        return rsClient.target(client.getBaseUrl());
    }

    /**
     * This method may be invoked from any thread, and is used to send a
     * message to the destination server associated with this 'Server' instance.
     *
     * @param path the path relative to the base URL
     * @param entity the "request entity" containing the body of the
     *     HTTP POST request
     */
    public void post(final String path, final Entity<?> entity) {
        post(path, entity, null);
    }

    /**
     * This method may be invoked from any thread, and is used to send a
     * message to the destination server associated with this 'Server' instance.
     *
     * @param path the path relative to the base URL
     * @param entity the "request entity" containing the body of the
     *     HTTP POST request (if 'null', an HTTP GET is used instead)
     * @param responseCallback if non-null, this callback may be used to
     *     modify the WebTarget, and/or receive the POST response message
     */
    public void post(final String path, final Entity<?> entity,
                     PostResponse responseCallback) {
        if (target == null) {
            return;
        }

        getThreadPool().execute(() -> {
            /*
             * This method is running within the 'MainLoop' thread.
             */
            try {
                WebTarget webTarget = target.path(path);
                if (responseCallback != null) {
                    // give callback a chance to modify 'WebTarget'
                    webTarget = responseCallback.webTarget(webTarget);

                    // send the response to the callback
                    Response response;
                    if (entity == null) {
                        response = webTarget.request().get();
                    } else {
                        response = webTarget.request().post(entity);
                    }
                    responseCallback.response(response);
                } else {
                    // just do the invoke, and ignore the response
                    if (entity == null) {
                        webTarget.request().get();
                    } else {
                        webTarget.request().post(entity);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to send to {} ({}, {})",
                             uuid, destSocketAddress, destName);
                if (responseCallback != null) {
                    responseCallback.exceptionResponse(e);
                }
                // this runs in the 'MainLoop' thread

                // the DNS cache may have been out-of-date when this server
                // was first contacted -- fix the problem, if needed
                MainLoop.queueWork(this::checkServer);
            }
        });
    }

    /**
     * This method may be invoked from any thread.
     *
     * @return the 'ThreadPoolExecutor' associated with this server
     */
    public synchronized ThreadPoolExecutor getThreadPool() {
        if (sendThreadPool == null) {
            // build a thread pool for this Server
            sendThreadPool =
                new ThreadPoolExecutor(corePoolSize, maximumPoolSize,
                                       keepAliveTime, TimeUnit.MILLISECONDS,
                                       new LinkedTransferQueue<>());
            sendThreadPool.allowCoreThreadTimeOut(true);
        }
        return sendThreadPool;
    }

    /**
     * Lower-level method supporting HTTP, which requires that the caller's
     * thread tolerate blocking. This method may be called from any thread.
     *
     * @param path the path relative to the base URL
     * @return a 'WebTarget' instance pointing to this path
     */
    public WebTarget getWebTarget(String path) {
        return target == null ? null : target.path(path);
    }

    /**
     * This method may be invoked from any thread, but its real intent is
     * to decode an incoming 'admin' message (which is Base-64-encoded),
     * and send it to the 'MainLoop' thread for processing.
     *
     * @param data the base-64-encoded data
     */
    static void adminRequest(byte[] data) {
        final byte[] packet = Base64.getDecoder().decode(data);
        Runnable task = () -> {
            try {
                ByteArrayInputStream bis = new ByteArrayInputStream(packet);
                DataInputStream dis = new DataInputStream(bis);

                while (dis.available() != 0) {
                    Server serverData = new Server(dis);

                    // TBD: Compare with current server

                    Server server = servers.get(serverData.uuid);
                    if (server == null) {
                        serverData.newServer();
                    } else {
                        server.updateServer(serverData);
                    }
                }
            } catch (Exception e) {
                logger.error("Server.adminRequest: can't decode packet", e);
            }
        };
        MainLoop.queueWork(task);
    }

    /**
     * Send out information about servers 'updatedList' to all servers
     * in 'notifyList' (may need to build or rebuild 'notifyList').
     */
    static void sendOutData() throws IOException {
        // include 'thisServer' in the data -- first, advance the count
        thisServer.count += 1;
        if (thisServer.count == 0) {
            /*
             * counter wrapped (0 is a special case) --
             * actually, we could probably leave this out, because it would take
             * more than a century to wrap if the increment is 1 second
             */
            thisServer.count = 1;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        thisServer.lastUpdateTime = System.currentTimeMillis();
        thisServer.writeServerData(dos);

        // include all hosts in the updated list
        for (Server server : updatedList) {
            server.writeServerData(dos);
        }
        updatedList.clear();

        // create an 'Entity' that can be sent out to all hosts in the notify list
        Entity<String> entity = Entity.entity(
            new String(Base64.getEncoder().encode(bos.toByteArray()), StandardCharsets.UTF_8),
            MediaType.APPLICATION_OCTET_STREAM_TYPE);
        for (Server server : getNotifyList()) {
            server.post("admin", entity);
        }
    }

    /**
     * Search for servers which have taken too long to respond.
     */
    static void searchForFailedServers() {
        long currentTime = System.currentTimeMillis();

        // used to build a list of newly-failed servers
        LinkedList<Server> failed = new LinkedList<>();
        for (Server server : servers.values()) {
            if (server == thisServer) {
                continue;
            }
            long gap = currentTime - server.lastUpdateTime;
            if (gap > server.allowedGap) {
                // add it to the failed list -- we don't call 'serverFailed' yet,
                // because this updates the server list, and leads to a
                // 'ConcurrentModificationException'
                failed.add(server);
            }
        }

        // remove servers from our list
        if (!failed.isEmpty()) {
            for (Server server : failed) {
                server.serverFailed();
            }
            notifyList = null;
        }
    }

    /**
     * This method may be invoked from any thread:
     * Send information about 'thisServer' to the list of hosts.
     *
     * @param out the 'PrintStream' to use for displaying information
     * @param hosts a comma-separated list of entries containing
     *     'host:port' or just 'port' (current host is implied in this case)
     */
    static void pingHosts(PrintStream out, String hosts) {
        LinkedList<InetSocketAddress> addresses = new LinkedList<>();
        boolean error = false;

        for (String host : hosts.split(",")) {
            try {
                String[] segs = host.split(":");

                switch (segs.length) {
                    case 1:
                        addresses.add(new InetSocketAddress(InetAddress.getLocalHost(),
                                Integer.parseInt(segs[0])));
                        break;
                    case 2:
                        addresses.add(new InetSocketAddress(segs[0],
                                Integer.parseInt(segs[1])));
                        break;
                    default:
                        out.println(host + ": Invalid host/port value");
                        error = true;
                        break;
                }
            } catch (NumberFormatException e) {
                out.println(host + ": Invalid port value");
                logger.error(PINGHOSTS_ERROR, e);
                error = true;
            } catch (UnknownHostException e) {
                out.println(host + ": Unknown host");
                logger.error(PINGHOSTS_ERROR, e);
                error = true;
            }
        }
        if (!error) {
            pingHosts(out, addresses);
        }
    }

    /**
     * This method may be invoked from any thread:
     * Send information about 'thisServer' to the list of hosts.
     *
     * @param out the 'PrintStream' to use for displaying information
     * @param hosts a collection of 'InetSocketAddress' instances, which are
     *     the hosts to send the information to
     */
    static void pingHosts(final PrintStream out,
                          final Collection<InetSocketAddress> hosts) {
        FutureTask<Integer> ft = new FutureTask<>(() -> {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);

            // add information for this server only
            try {
                thisServer.writeServerData(dos);

                // create an 'Entity' that can be sent out to all hosts
                Entity<String> entity = Entity.entity(
                    new String(Base64.getEncoder().encode(bos.toByteArray()),
                        StandardCharsets.UTF_8),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE);
                pingHostsLoop(entity, out, hosts);
            } catch (IOException e) {
                out.println("Unable to generate 'ping' data: " + e);
                logger.error(PINGHOSTS_ERROR, e);
            }
            return 0;
        });

        MainLoop.queueWork(ft);
        try {
            ft.get(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Server.pingHosts: interrupted waiting for queued work", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            logger.error("Server.pingHosts: error waiting for queued work", e);
        }
    }

    /**
     * This method is used for pingHosts method to reduce its Cognitive Complexity.
     *
     * @param entity for sending out to all hosts
     * @param out the 'PrintStream' to use for displaying information
     * @param hosts a collection of 'InetSocketAddress' instances, which are
     *     the hosts to send the information to
     */
    static void pingHostsLoop(final Entity<String> entity,
                              final PrintStream out,
                              final Collection<InetSocketAddress> hosts) {
        // loop through hosts
        for (InetSocketAddress host : hosts) {
            HttpClient httpClient = null;

            try {
                httpClient = buildClient(host.toString(), host,
                                     socketAddressToName(host));
                getTarget(httpClient).path("admin").request().post(entity);
                httpClient.shutdown();
                httpClient = null;
            } catch (NoSuchFieldException | IllegalAccessException e) {
                out.println(host + ": Unable to get link to target");
                logger.error(PINGHOSTS_ERROR, e);
            } catch (Exception e) {
                out.println(host + ": " + e);
                logger.error(PINGHOSTS_ERROR, e);
            }
            if (httpClient != null) {
                httpClient.shutdown();
            }
        }
    }

    /**
     * This method may be invoked from any thread:
     * Dump out the current 'servers' table in a human-readable table form.
     *
     * @param out the 'PrintStream' to dump the table to
     */
    public static void dumpHosts(final PrintStream out) {
        FutureTask<Integer> ft = new FutureTask<>(() -> {
            dumpHostsInternal(out);
            return 0;
        });
        MainLoop.queueWork(ft);
        try {
            ft.get(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Server.dumpHosts: interrupted waiting for queued work", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            logger.error("Server.dumpHosts: error waiting for queued work", e);
        }
    }

    /**
     * Dump out the current 'servers' table in a human-readable table form.
     *
     * @param out the 'PrintStream' to dump the table to
     */
    private static void dumpHostsInternal(PrintStream out) {
        // modifications to 'servers.values()' and 'notifyList'.
        HashSet<Server> localNotifyList = new HashSet<>(getNotifyList());

        // see if we have any site information
        boolean siteData = false;
        for (Server server : servers.values()) {
            if (server.siteSocketAddress != null) {
                siteData = true;
                break;
            }
        }

        String format = "%1s %-36s %-15s %5s %5s %12s %7s %7s\n";
        SimpleDateFormat dateFormat = new SimpleDateFormat("kk:mm:ss.SSS");

        if (siteData) {
            format = "%1s %-36s %-15s %5s %-15s %5s %5s %12s %7s %7s\n";
            // @formatter:off
            out.printf(format, "", "UUID", "IP Address", "Port",
                       "Site IP Address", "Port",
                       "Count", "Update Time", "Elapsed", "Allowed");
            out.printf(format, "", "----", "----------", "----",
                       "---------------", "----",
                       "-----", "-----------", PRINTOUT_DASHES, PRINTOUT_DASHES);
            // @formatter:on
        } else {
            // @formatter:off
            out.printf(format, "", "UUID", "IP Address", "Port",
                       "Count", "Update Time", "Elapsed", "Allowed");
            out.printf(format, "", "----", "----------", "----",
                       "-----", "-----------", PRINTOUT_DASHES, PRINTOUT_DASHES);
            // @formatter:on
        }

        long currentTime = System.currentTimeMillis();
        for (Server server : servers.values()) {
            String thisOne = "";

            if (server == thisServer) {
                thisOne = "*";
            } else if (localNotifyList.contains(server)) {
                thisOne = "n";
            }

            if (siteData) {
                String siteIp = "";
                String sitePort = "";
                if (server.siteSocketAddress != null) {
                    siteIp =
                        server.siteSocketAddress.getAddress().getHostAddress();
                    sitePort = String.valueOf(server.siteSocketAddress.getPort());
                }

                out.printf(format, thisOne, server.uuid,
                           server.socketAddress.getAddress().getHostAddress(),
                           server.socketAddress.getPort(),
                           siteIp, sitePort, server.count,
                           dateFormat.format(new Date(server.lastUpdateTime)),
                           currentTime - server.lastUpdateTime,
                           server.allowedGap);
            } else {
                out.printf(format, thisOne, server.uuid,
                           server.socketAddress.getAddress().getHostAddress(),
                           server.socketAddress.getPort(), server.count,
                           dateFormat.format(new Date(server.lastUpdateTime)),
                           currentTime - server.lastUpdateTime,
                           server.allowedGap);
            }
        }
        out.println("Count: " + servers.size());
    }

    /* ============================================================ */

    /**
     * This interface supports the 'post' method, and provides the opportunity
     * to change the WebTarget and/or receive the POST response message.
     */
    interface PostResponse {
        /**
         * Callback that can be used to modify 'WebTarget', and do things like
         * add query parameters.
         *
         * @param webTarget the current WebTarget
         * @return the updated WebTarget
         */
        public default WebTarget webTarget(WebTarget webTarget) {
            return webTarget;
        }

        /**
         * Callback that passes the POST response.
         *
         * @param response the POST response
         */
        public default void response(Response response) {
        }

        /**
         * Callback that passes the POST exception response.
         *
         */
        public default void exceptionResponse(Exception exception) {
            Response.ResponseBuilder response;
            response = Response.serverError();
            this.response(response.build());
        }
    }
}
