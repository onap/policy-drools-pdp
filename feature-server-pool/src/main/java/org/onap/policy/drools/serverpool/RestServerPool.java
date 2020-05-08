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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Info;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
@Api
@SwaggerDefinition(
    info = @Info(
        description = "PDP-D Server Pool Telemetry Service",
        version = "v1.0",
        title = "PDP-D Server Pool Telemetry"
    ),
    consumes = {MediaType.APPLICATION_JSON},
    produces = {MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN},
    schemes = {SwaggerDefinition.Scheme.HTTP},
    tags = {
        @Tag(name = "pdp-d-server-pool-telemetry", description = "Drools PDP Server Pool Telemetry Operations")
    }
    )
public class RestServerPool {
    private static Logger logger = LoggerFactory.getLogger(RestServerPool.class);

    /**
     * Handle the '/test' REST call.
     */
    @GET
    @Path("/test")
    @ApiOperation(
            value = "Perform an incoming /test request",
            notes = "Provides an acknowledge message back to requestor",
            response = String.class
            )
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
    @ApiOperation(
            value = "Perform an incoming /admin request",
            notes = "This rest call decodes incoming admin message (base-64-encoded) and "
            + "send to main thread for processing"
            )
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void adminRequest(byte[] data) {
        Server.adminRequest(data);
    }

    /**
     * Handle the '/vote' REST call.
     */
    @POST
    @Path("/vote")
    @ApiOperation(
            value = "Perform an incoming /vote request",
            notes = "The request data containing voter and vote data to be processed"
            )
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void voteData(byte[] data) {
        Leader.voteData(data);
    }

    /**
     * Handle the '/bucket/update' REST call.
     */
    @POST
    @Path("/bucket/update")
    @ApiOperation(
            value = "Perform an incoming /bucket/update request",
            notes = "The request data include owner, state, primaryBackup and secondaryBackup"
            )
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void updateBucket(byte[] data) {
        Bucket.updateBucket(data);
    }

    /**
     * Handle the '/bucket/topic' REST call.
     */
    @POST
    @Path("/bucket/topic")
    @ApiOperation(
            value = "Perform an incoming /bucket/topic request",
            notes = "Forward an incoming topic message from a remote host, the request data include "
            + "bucketNumber the bucket number calculated on the remote host, keyword the keyword "
            + "associated with the message, controllerName the controller the message was directed to "
            + "on the remote host, protocol String value of the topic value (UEB, DMAAP, NOOP, or REST "
            + "-- NOOP and REST shouldn't be used here), topic the UEB/DMAAP topic name, event this is "
            + "the JSON message"
            )
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
    @ApiOperation(
            value = "Perform an incoming /bucket/sessionData request",
            notes = "A message is received from the old owner of the bucket and send to new owner, "
            + "the request data include bucketNumber the bucket number, dest the UUID of the intended "
            + "destination, ttl controls the number of hops the message may take, data serialized data "
            + "associated with this bucket, encoded using base64"
            )
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
    @ApiOperation(
            value = "Perform an incoming /session/insertDrools request",
            notes = "An incoming /session/insertDrools message was received, the request data include "
            + "keyword the keyword associated with the incoming object, sessionName encoded session name "
            + "(groupId:artifactId:droolsSession), bucket the bucket associated with keyword, "
            + "ttl controls the number of hops the message may take, data base64-encoded serialized data "
            + "for the object"
            )
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
    @ApiOperation(
            value = "Perform an incoming /lock/lock request",
            notes = "An incoming /lock/lock REST message is received, the request data include "
            + "key string identifying the lock, which must hash to a bucket owned by the current host, "
            + "ownerKey string key identifying the owner, uuid the UUID that uniquely identifies "
            + "the original 'TargetLock', waitForLock this controls the behavior when 'key' is already "
            + "locked - 'true' means wait for it to be freed, 'false' means fail, ttl controls the number "
            + "of hops the message may take, the response is the message should be passed back to the "
            + "requestor"
            )
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
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
    @ApiOperation(
            value = "Perform an incoming /lock/free request",
            notes = "An incoming /lock/free REST message is received, the request data include "
            + "key string identifying the lock, which must hash to a bucket owned by the current host, "
            + "ownerKey string key identifying the owner, uuid the UUID that uniquely identifies "
            + "the original 'TargetLock', ttl controls the number of hops the message may take, "
            + "the response is the message should be passed back to the requestor"
            )
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
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
    @ApiOperation(
            value = "Perform an incoming /lock/locked request, (this is a callback to an earlier "
            + "requestor that the lock is now available)",
            notes = "An incoming /lock/locked REST message is received, the request data include "
            + "key string key identifying the lock, ownerKey string key identifying the owner "
            + "which must hash to a bucket owned by the current host (it is typically a 'RequestID') "
            + "uuid the UUID that uniquely identifies the original 'TargetLock', ttl controls the "
            + "number of hops the message may take, the response is the message should be passed back "
            + "to the requestor"
            )
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
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
    @ApiOperation(
            value = "Perform an incoming /lock/audit request",
            notes = "An incoming /lock/audit REST message is received, the request data include "
            + "serverUuid the UUID of the intended destination server, ttl controls the number of hops, "
            + "encodedData base64-encoded data, containing a serialized 'AuditData' instance "
            + "the response is a serialized and base64-encoded 'AuditData'"
            )
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
    @ApiOperation(
            value = "Perform an incoming /cmd/dumpHosts request",
            notes = "Dump out the current 'servers' table in a human-readable table form"
            )
    @Produces(MediaType.TEXT_PLAIN)
    public String dumpHosts() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Server.dumpHosts(new PrintStream(bos, true));
        return bos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Handle the '/cmd/dumpBuckets' REST call.
     */
    @GET
    @Path("/cmd/dumpBuckets")
    @ApiOperation(
            value = "Perform an incoming /cmd/dumpBuckets request",
            notes = "Dump out buckets information in a human-readable form"
            )
    @Produces(MediaType.TEXT_PLAIN)
    public String dumpBuckets() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bucket.dumpBuckets(new PrintStream(bos, true));
        return bos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Handle the '/cmd/ping' REST call.
     */
    @GET
    @Path("/cmd/ping")
    @ApiOperation(
            value = "Perform an incoming /cmd/ping request",
            notes = "Send information about 'thisServer' to the list of hosts"
            )
    @Produces(MediaType.TEXT_PLAIN)
    public String ping(@QueryParam("hosts") String hosts) {
        logger.info("Running '/cmd/ping', hosts={}", hosts);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Server.pingHosts(new PrintStream(bos, true), hosts);
        return bos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Handle the '/cmd/bucketMessage' REST call.
     */
    @GET
    @Path("/cmd/bucketMessage")
    @ApiOperation(
            value = "Perform an incoming /cmd/bucketMessage request",
            notes = "This is only used for testing the routing of messages between servers"
            )
    @Produces(MediaType.TEXT_PLAIN)
    public String bucketMessage(@QueryParam("keyword") String keyword,
                                @QueryParam("message") String message)
        throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bucket.bucketMessage(new PrintStream(bos, true), keyword, message);
        return bos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Handle the '/bucket/bucketResponse' REST call.
     */
    @POST
    @Path("/bucket/bucketResponse")
    @ApiOperation(
            value = "Perform an incoming /cmd/bucketResponse request",
            notes = "This runs on the destination host, and is the continuation of an operation "
            + "triggered by the /cmd/bucketMessage REST message running on the originating host"
            )
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String bucketResponse(@QueryParam("bucket") Integer bucket,
                                 @QueryParam("keyword") String keyword,
                                 byte[] data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bucket.bucketResponse(new PrintStream(bos, true), bucket, keyword, data);
        return bos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Handle the '/lock/moveBucket' REST call.
     */
    @GET
    @Path("/cmd/moveBucket")
    @ApiOperation(
            value = "Perform an incoming /cmd/moveBucket request",
            notes = "This is only used for testing bucket migration. It only works on the lead server"
            )
    @Produces(MediaType.TEXT_PLAIN)
    public String moveBucket(@QueryParam("bucket") Integer bucketNumber,
                             @QueryParam("host") String newHost) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bucket.moveBucket(new PrintStream(bos, true), bucketNumber, newHost);
        return bos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Handle the '/lock/dumpBucketAdjuncts' REST call.
     */
    @GET
    @Path("/cmd/dumpBucketAdjuncts")
    @ApiOperation(
            value = "Perform an incoming /cmd/dumpBucketAdjuncts request",
            notes = "Dump out all buckets with adjuncts"
            )
    @Produces(MediaType.TEXT_PLAIN)
    public String dumpBucketAdjuncts() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bucket.dumpAdjuncts(new PrintStream(bos, true));
        return bos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Handle the '/lock/dumpLocks' REST call.
     */
    @GET
    @Path("/cmd/dumpLocks")
    @ApiOperation(
            value = "Perform an incoming /cmd/dumpLocks request",
            notes = "Dump out locks info, detail 'true' provides additional bucket and host information"
            )
    @Produces(MediaType.TEXT_PLAIN)
    public String dumpLocks(@QueryParam("detail") boolean detail)
        throws IOException, InterruptedException, ClassNotFoundException {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        TargetLock.DumpLocks.dumpLocks(new PrintStream(bos, true), detail);
        return bos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Handle the '/lock/dumpLocksData' REST call.
     */
    @GET
    @Path("/lock/dumpLocksData")
    @ApiOperation(
            value = "Perform an incoming /cmd/dumpLocksData request",
            notes = "Generate a byte stream containing serialized 'HostData'"
            )
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public String dumpLocksData(@QueryParam("server") UUID server,
                                @QueryParam("ttl") int ttl) throws IOException {
        return new String(TargetLock.DumpLocks.dumpLocksData(server, ttl), StandardCharsets.UTF_8);
    }
}
