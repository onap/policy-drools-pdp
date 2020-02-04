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

import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_LEADER_STABLE_IDLE_CYCLES;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.DEFAULT_LEADER_STABLE_VOTING_CYCLES;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.LEADER_STABLE_IDLE_CYCLES;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.LEADER_STABLE_VOTING_CYCLES;
import static org.onap.policy.drools.serverpool.ServerPoolProperties.getProperty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handles the election of the lead server. The lead server
 * handles bucket assignments, and also is the server running the
 * 'Discovery' procedure long-term (other servers do run the procedure
 * until a leader is elected).
 * Note that everything in this class is run under the 'MainLoop' thread,
 * with the exception of the invocation and first two statements of the
 * 'voteData' method.
 */
public class Leader {
    private static Logger logger = LoggerFactory.getLogger(Leader.class);

    // Listener class to handle state changes that may lead to a new election
    private static EventHandler eventHandler = new EventHandler();

    static {
        Events.register(eventHandler);
    }

    // Server currently in the leader roll
    private static Server leader = null;

    // Vote state machine -- it is null, unless a vote is in progress
    private static VoteCycle voteCycle = null;

    private static UUID emptyUUID = new UUID(0L, 0L);

    /*==================================================*/
    /* Some properties extracted at initialization time */
    /*==================================================*/

    // how many cycles of stability before voting starts
    private static int stableIdleCycles;

    // how may cycles of stability before declaring a winner
    private static int stableVotingCycles;

    /**
     * Invoked at startup, or after some events -- immediately start a new vote.
     */
    static void startup() {
        // fetch some static properties
        stableIdleCycles = getProperty(LEADER_STABLE_IDLE_CYCLES,
                                       DEFAULT_LEADER_STABLE_IDLE_CYCLES);
        stableVotingCycles = getProperty(LEADER_STABLE_VOTING_CYCLES,
                                         DEFAULT_LEADER_STABLE_VOTING_CYCLES);

        startVoting();
    }

    /**
     * start, or restart voting.
     */
    private static void startVoting() {
        if (voteCycle == null) {
            voteCycle = new VoteCycle();
            MainLoop.addBackgroundWork(voteCycle);
        } else {
            voteCycle.serverChanged();
        }
    }

    /**
     * Return the current leader.
     *
     * @return the current leader ('null' if none has been selected)
     */
    public static Server getLeader() {
        return leader;
    }

    /**
     * Handle an incoming /vote REST message.
     *
     * @param data base64-encoded data, containing vote data
     */
    public static void voteData(byte[] data) {
        // decode base64 data
        final byte[] packet = Base64.getDecoder().decode(data);

        MainLoop.queueWork(new Runnable() {
            /**
             * This method is running within the 'MainLoop' thread.
             */
            @Override
            public void run() {
                // create the 'VoteCycle' state machine, if needed
                if (voteCycle == null) {
                    voteCycle = new VoteCycle();
                    MainLoop.addBackgroundWork(voteCycle);
                }
                try {
                    // pass data to 'VoteCycle' state machine
                    voteCycle.packetReceived(packet);
                } catch (IOException e) {
                    logger.error("Exception in 'Leader.voteData", e);
                }
            }
        });
    }

    /* ============================================================ */

    /**
     * There is a single instance of this class (Leader.eventHandler), which
     * is registered to listen for notifications of state transitions. Note
     * that all of these methods are running within the 'MainLoop' thread.
     */
    private static class EventHandler implements Events {
        /**
         * {@inheritDoc}
         */
        @Override
        public void newServer(Server server) {
            // a new server has joined -- start/restart the VoteCycle state machine
            startVoting();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void serverFailed(Server server) {
            if (server == leader) {
                // the lead server has failed --
                // start/restart the VoteCycle state machine
                leader = null;
                startVoting();

                // send out a notification that the lead server has failed
                for (Events listener : Events.getListeners()) {
                    listener.leaderFailed(server);
                }
            } else if (voteCycle != null) {
                // a vote is in progress -- restart the state machine
                // (don't do anything if there is no vote in progress)
                voteCycle.serverChanged();
            }
        }
    }

    /* ============================================================ */

    /**
     * This is the 'VoteCycle' state machine -- it runs as background work
     * on the 'MainLoop' thread, and goes away when a leader is elected.
     */
    private static class VoteCycle implements Runnable {
        enum State {
            // server just started up -- 5 second grace period
            STARTUP,

            // voting in progress -- changes have occurred in the last cycle
            VOTING,
        }

        // maps UUID voted for into the associated data
        private final TreeMap<UUID, VoteData> uuidToVoteData =
            new TreeMap<>(Util.uuidComparator);

        // maps voter UUID into the associated data
        private final TreeMap<UUID, VoterData> uuidToVoterData =
            new TreeMap<>(Util.uuidComparator);

        // sorted list of 'VoteData' (most preferable to least)
        private final TreeSet<VoteData> voteData = new TreeSet<>();

        // data to send out next cycle
        private final HashSet<VoterData> updatedVotes = new HashSet<>();

        private State state = State.STARTUP;
        private int cycleCount = stableIdleCycles;

        /**
         * Constructor - if there is no leader, or this server is the leader,
         * start the 'Discovery' thread.
         */
        VoteCycle() {
            if (leader == null || leader == Server.getThisServer()) {
                Discovery.startDiscovery();
            }
        }

        /**
         * A state change has occurred that invalidates any vote in progress --
         * restart the VoteCycle state machine.
         */
        void serverChanged() {
            // clear all of the tables
            uuidToVoteData.clear();
            uuidToVoterData.clear();
            voteData.clear();
            updatedVotes.clear();

            // wait for things to stabilize before continuing
            state = State.STARTUP;
            cycleCount = stableIdleCycles;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            switch (state) {
                case STARTUP: {
                    /**
                     * 5-second grace period -- wait for things to stablize before
                     * starting the vote
                     */
                    if ((cycleCount -= 1) <= 0) {
                        logger.info("VoteCycle: {} seconds have passed",
                                    stableIdleCycles);
                        //MainLoop.removeBackgroundWork(this);
                        updateMyVote();
                        sendOutUpdates();
                        state = State.VOTING;
                        cycleCount = stableVotingCycles;
                    }
                    break;
                }

                case VOTING: {
                    /**
                     * need to be in the VOTING state without any vote changes
                     * for 5 seconds -- once this happens, the leader is chosen
                     */
                    if (sendOutUpdates()) {
                        // changes have occurred -- set the grace period to 5 seconds
                        cycleCount = stableVotingCycles;
                    } else if ((cycleCount -= 1) <= 0) {
                        /**
                         * 5 second grace period has passed -- the leader is one with
                         * the most votes, which is the first entry in 'voteData
                         */
                        Server oldLeader = leader;
                        leader = Server.getServer(voteData.first().uuid);
                        if (leader != oldLeader) {
                            // the leader has changed -- send out notifications
                            for (Events listener : Events.getListeners()) {
                                listener.newLeader(leader);
                            }
                        } else {
                            // the election is over, and the leader has been confirmed
                            for (Events listener : Events.getListeners()) {
                                listener.leaderConfirmed(leader);
                            }
                        }
                        if (leader == Server.getThisServer()) {
                            /**
                             * this is the lead server --
                             * make sure the 'Discovery' threads are running
                             */
                            Discovery.startDiscovery();
                        } else {
                            // this is not the lead server -- stop 'Discovery' threads
                            Discovery.stopDiscovery();
                        }

                        // we are done with voting -- clean up, and report results
                        MainLoop.removeBackgroundWork(this);
                        voteCycle = null;

                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        PrintStream out = new PrintStream(bos);

                        out.println("Voting results:");

                        // x(36) xxxxx x(36)
                        // UUID  Votes Voter
                        String format = "%-36s %5s %-36s\n";
                        out.format(format, "UUID", "Votes", "Voter(s)");
                        out.format(format, "----", "-----", "--------");

                        for (VoteData vote : voteData) {
                            if (vote.voters.isEmpty()) {
                                out.format(format, vote.uuid, 0, "");
                            } else {
                                boolean headerNeeded = true;
                                for (VoterData voter : vote.voters) {
                                    if (headerNeeded) {
                                        out.format(format, vote.uuid,
                                                   vote.voters.size(), voter.uuid);
                                        headerNeeded = false;
                                    } else {
                                        out.format(format, "", "", voter.uuid);
                                    }
                                }
                            }
                        }

                        logger.info(bos.toString());
                    }
                    break;
                }
                default:
                    logger.error("Unknown state: {}", state);
                    break;
            }
        }

        /**
         * Process an incoming /vote REST message.
         *
         * @param packet vote data, containing one or more votes
         */
        private void packetReceived(byte[] packet) throws IOException {
            DataInputStream dis =
                new DataInputStream(new ByteArrayInputStream(packet));

            while (dis.available() != 0) {
                /**
                 * message is a series of:
                 * 16-bytes voter UUID
                 * 16-bytes vote UUID
                 * 8-bytes timestamp
                 */
                long tmp = dis.readLong(); // most significant bits
                UUID voter = new UUID(tmp, dis.readLong());

                tmp = dis.readLong();
                UUID vote = new UUID(tmp, dis.readLong());

                long timestamp = dis.readLong();

                // process the single vote
                processVote(voter, vote, timestamp);
            }
        }

        /**
         * Process a single incoming vote.
         *
         * @param UUID voter the UUID of the Server making this vote
         * @param UUID vote the UUID of the Server that 'voter' voted for
         * @param timestamp the time when the vote was made
         */
        private void processVote(UUID voter, UUID vote, long timestamp) {
            // fetch old data for this voter
            VoterData voterData = uuidToVoterData.computeIfPresent(voter,
                (key, val) -> (val == null) ? new VoterData(voter, timestamp) : val);
            if (voterData == null) {
                // no data available for this voter -- create a new entry
                voterData = new VoterData(voter, timestamp);
                uuidToVoterData.putIfAbsent(voter, voterData);
            } else if (timestamp >= voterData.timestamp) {
                // this is a new vote for this voter -- update the timestamp
                voterData.timestamp = timestamp;
            } else {
                // already processed vote, and it may even be obsolete
                return;
            }

            // fetch the old vote, if any, for this voter
            VoteData oldVoteData = voterData.vote;
            VoteData newVoteData = null;

            if (vote != null
                    && (newVoteData = uuidToVoteData.get(vote)) == null) {
                // need to create a new 'voteToVoteData' entry
                newVoteData = new VoteData(vote);
                uuidToVoteData.putIfAbsent(vote, newVoteData);
            }

            if (oldVoteData != newVoteData) {
                /**
                 * the vote has changed -- update the 'voterData' entry,
                 * and include this in the next set of outgoing messages
                 */
                logger.info("{} voting for {}", voter, vote);
                voterData.vote = newVoteData;
                updatedVotes.add(voterData);

                if (oldVoteData != null) {
                    // remove the old vote data
                    voteData.remove(oldVoteData);
                    oldVoteData.voters.remove(voterData);
                    if (oldVoteData.voters.isEmpty()) {
                        // no voters left -- remove the entry
                        uuidToVoteData.remove(oldVoteData.uuid);
                    } else {
                        // reinsert in a new position
                        voteData.add(oldVoteData);
                    }
                }

                if (newVoteData != null) {
                    // update the new vote data
                    voteData.remove(newVoteData);
                    newVoteData.voters.add(voterData);
                    voteData.add(newVoteData);
                }
            }
        }

        /**
         * If any updates have occurred, send then out to all servers on
         * the "notify list".
         *
         * @return 'true' if one or more votes have changed, 'false' if not
         */
        private boolean sendOutUpdates() {
            try {
                if (updatedVotes.isEmpty()) {
                    // no changes to send out
                    return false;
                }

                // possibly change vote based on current information
                updateMyVote();

                // generate message to send out
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);

                // go through all of the updated votes
                for (VoterData voterData : updatedVotes) {
                    // voter UUID
                    dos.writeLong(voterData.uuid.getMostSignificantBits());
                    dos.writeLong(voterData.uuid.getLeastSignificantBits());

                    // vote UUID
                    UUID vote =
                        (voterData.vote == null ? emptyUUID : voterData.vote.uuid);
                    dos.writeLong(vote.getMostSignificantBits());
                    dos.writeLong(vote.getLeastSignificantBits());

                    // timestamp
                    dos.writeLong(voterData.timestamp);
                }
                updatedVotes.clear();

                // create an 'Entity' that can be sent out to all hosts
                Entity<String> entity = Entity.entity(
                    new String(Base64.getEncoder().encode(bos.toByteArray()),
                    StandardCharsets.UTF_8), MediaType.APPLICATION_OCTET_STREAM_TYPE);

                // send out to all servers on the notify list
                for (Server server : Server.getNotifyList()) {
                    server.post("vote", entity);
                }
                return true;
            } catch (IOException e) {
                logger.error("Exception in VoteCycle.sendOutUpdates", e);
                return false;
            }
        }

        /**
         * (Possibly) change this servers vote, based upon votes of other voters.
         */
        private void updateMyVote() {
            UUID myVote = null;

            if (uuidToVoterData.size() * 2 < Server.getServerCount()) {
                // fewer than half of the nodes have voted
                if (leader != null) {
                    // choose the current leader
                    myVote = leader.getUuid();
                } else {
                    // choose the first entry in the servers list
                    myVote = Server.getFirstServer().getUuid();
                }
            } else {
                // choose the first entry we know about
                for (VoteData vote : voteData) {
                    if (Server.getServer(vote.uuid) != null) {
                        myVote = vote.uuid;
                        break;
                    }
                }
            }
            if (myVote != null) {
                // update the vote for this host, and include it in the list
                processVote(Server.getThisServer().getUuid(), myVote,
                            System.currentTimeMillis());
            }
        }
    }

    /* ============================================================ */

    /**
     * This class corresponds to a single vote recipient --
     * the Server being voted for.
     */
    private static class VoteData implements Comparable<VoteData> {
        // uuid voted for
        private UUID uuid;

        // the set of all voters that voted for this server
        private HashSet<VoterData> voters = new HashSet<>();

        /**
         * Constructor -- set the UUID.
         */
        private VoteData(UUID uuid) {
            this.uuid = uuid;
        }

        /*================================*/
        /* Comparable<VoteData> interface */
        /*================================*/

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(VoteData other) {
            // favor highest vote count
            // in case of a tie, compare UUIDs (favor smallest)

            int rval = other.voters.size() - voters.size();
            if (rval == 0) {
                // vote counts equal -- favor the smaller UUID
                rval = Util.uuidComparator.compare(uuid, other.uuid);
            }
            return rval;
        }
    }

    /* ============================================================ */

    /**
     * This class corresponds to the vote of a single server.
     */
    private static class VoterData {
        // voter UUID
        private UUID uuid;

        // most recently cast vote from this voter
        private VoteData vote = null;

        // time when the vote was cast
        private long timestamp = 0;

        /**
         * Constructor - store the UUID and timestamp.
         */
        private VoterData(UUID uuid, long timestamp) {
            this.uuid = uuid;
            this.timestamp = timestamp;
        }
    }
}
