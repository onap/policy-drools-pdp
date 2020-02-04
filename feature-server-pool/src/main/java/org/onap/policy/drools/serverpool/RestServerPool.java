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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains methods for processing incoming REST messages.
 */

@Path("/")
public class RestServerPool {
    private static Logger logger = LoggerFactory.getLogger(RestServerPool.class);

    /**
     * Handle the '/test' REST call.
     */
    @GET
    @Path("/test")
    @Produces(MediaType.TEXT_PLAIN)
    public String test() {
        return "RestServerPool.test()";
    }

    /* ============================================================ */

    /**
     * Handle the '/admin' REST call.
     */
    @POST
    @Path("/admin")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void adminRequest(byte[] data) {
        Server.adminRequest(data);
    }

    /**
     * Handle the '/vote' REST call.
     */
    @POST
    @Path("/vote")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void voteData(byte[] data) {
        Leader.voteData(data);
    }

    /**
     * Handle the '/bucket/update' REST call.
     */
    @POST
    @Path("/bucket/update")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void updateBucket(byte[] data) {
        Bucket.updateBucket(data);
    }

    /**
     * Handle the '/bucket/topic' REST call.
     */
    @POST
    @Path("/bucket/topic")
    @Consumes(MediaType.APPLICATION_JSON)
    public void topicMessage(@QueryParam("bucket") Integer bucket,
                             @QueryParam("keyword") String keyword,
                             @QueryParam("controller") String controllerName,
                             @QueryParam("protocol") String protocol,
                             @QueryParam("topic") String topic,
                             String event) {
        FeatureServerPool.topicMessage(bucket, keyword, controllerName, protocol, topic, event);
    }

    /**
     * Handle the '/bucket/sessionData' REST call.
     */
    @POST
    @Path("/bucket/sessionData")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void sessionData(@QueryParam("bucket") Integer bucket,
                            @QueryParam("dest") UUID dest,
                            @QueryParam("ttl") int ttl,
                            byte[] data) {
        Bucket.sessionData(bucket, dest, ttl, data);
    }

    /**
     * Handle the '/session/insertDrools' REST call.
     */
    @POST
    @Path("/session/insertDrools")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void insertDrools(@QueryParam("keyword") String keyword,
                             @QueryParam("session") String sessionName,
                             @QueryParam("bucket") int bucket,
                             @QueryParam("ttl") int ttl,
                             byte[] data) {
        FeatureServerPool.incomingInsertDrools(keyword, sessionName, bucket, ttl, data);
    }

    /**
     * Handle the '/lock/lock' REST call.
     */
    @GET
    @Path("/lock/lock")
    public Response lock(@QueryParam("key") String key,
                         @QueryParam("owner") String keyOwner,
                         @QueryParam("uuid") UUID uuid,
                         @QueryParam("wait") boolean waitForLock,
                         @QueryParam("ttl") int ttl) {
        return TargetLock.incomingLock(key, keyOwner, uuid, waitForLock, ttl);
    }

    /**
     * Handle the '/lock/free' REST call.
     */
    @GET
    @Path("/lock/free")
    public Response free(@QueryParam("key") String key,
                         @QueryParam("owner") String keyOwner,
                         @QueryParam("uuid") UUID uuid,
                         @QueryParam("ttl") int ttl) {
        return TargetLock.incomingFree(key, keyOwner, uuid, ttl);
    }

    /**
     * Handle the '/lock/locked' REST call.
     */
    @GET
    @Path("/lock/locked")
    public Response locked(@QueryParam("key") String key,
                           @QueryParam("owner") String keyOwner,
                           @QueryParam("uuid") UUID uuid,
                           @QueryParam("ttl") int ttl) {
        return TargetLock.incomingLocked(key, keyOwner, uuid, ttl);
    }

    /**
     * Handle the '/lock/audit' REST call.
     */
    @POST
    @Path("/lock/audit")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] lockAudit(@QueryParam("server") UUID server,
                            @QueryParam("ttl") int ttl,
                            byte[] data) {
        return TargetLock.Audit.incomingAudit(server, ttl, data);
    }

    /* ============================================================ */

    /**
     * Handle the '/cmd/dumpHosts' REST call.
     */
    @GET
    @Path("/cmd/dumpHosts")
    @Produces(MediaType.TEXT_PLAIN)
    public byte[] dumpHosts() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Server.dumpHosts(new PrintStream(bos, true));
        return bos.toByteArray();
    }

    /**
     * Handle the '/cmd/dumpBuckets' REST call.
     */
    @GET
    @Path("/cmd/dumpBuckets")
    @Produces(MediaType.TEXT_PLAIN)
    public byte[] dumpBuckets() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bucket.dumpBuckets(new PrintStream(bos, true));
        return bos.toByteArray();
    }

    /**
     * Handle the '/cmd/ping' REST call.
     */
    @GET
    @Path("/cmd/ping")
    @Produces(MediaType.TEXT_PLAIN)
    public byte[] ping(@QueryParam("hosts") String hosts) {
        logger.info("Running '/cmd/ping', hosts={}", hosts);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Server.pingHosts(new PrintStream(bos, true), hosts);
        return bos.toByteArray();
    }

    /**
     * Handle the '/cmd/bucketMessage' REST call.
     */
    @GET
    @Path("/cmd/bucketMessage")
    @Produces(MediaType.TEXT_PLAIN)
    public byte[] bucketMessage(@QueryParam("keyword") String keyword,
                                @QueryParam("message") String message) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bucket.bucketMessage(new PrintStream(bos, true), keyword, message);
        return bos.toByteArray();
    }

    /**
     * Handle the '/bucket/bucketResponse' REST call.
     */
    @POST
    @Path("/bucket/bucketResponse")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public byte[] bucketResponse(@QueryParam("bucket") Integer bucket,
                                 @QueryParam("keyword") String keyword,
                                 byte[] data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bucket.bucketResponse(new PrintStream(bos, true), bucket, keyword, data);
        return bos.toByteArray();
    }

    /**
     * Handle the '/lock/moveBucket' REST call.
     */
    @GET
    @Path("/cmd/moveBucket")
    @Produces(MediaType.TEXT_PLAIN)
    public byte[] moveBucket(@QueryParam("bucket") Integer bucketNumber,
                             @QueryParam("host") String newHost) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bucket.moveBucket(new PrintStream(bos, true), bucketNumber, newHost);
        return bos.toByteArray();
    }

    /**
     * Handle the '/lock/dumpBucketAdjuncts' REST call.
     */
    @GET
    @Path("/cmd/dumpBucketAdjuncts")
    @Produces(MediaType.TEXT_PLAIN)
    public byte[] dumpBucketAdjuncts() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bucket.dumpAdjuncts(new PrintStream(bos, true));
        return bos.toByteArray();
    }

    /**
     * Handle the '/lock/dumpLocks' REST call.
     */
    @GET
    @Path("/cmd/dumpLocks")
    @Produces(MediaType.TEXT_PLAIN)
    public byte[] dumpLocks(@QueryParam("detail") boolean detail)
        throws IOException, InterruptedException, ClassNotFoundException {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TargetLock.DumpLocks.dumpLocks(new PrintStream(bos, true), detail);
        return bos.toByteArray();
    }

    /**
     * Handle the '/lock/dumpLocksData' REST call.
     */
    @GET
    @Path("/lock/dumpLocksData")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] dumpLocksData(@QueryParam("server") UUID server,
                                @QueryParam("ttl") int ttl) throws IOException {
        return TargetLock.DumpLocks.dumpLocksData(server, ttl);
    }
}
