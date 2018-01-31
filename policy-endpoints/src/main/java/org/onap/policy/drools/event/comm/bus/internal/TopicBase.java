/*-
 * ============LICENSE_START=======================================================
 * policy-endpoints
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.event.comm.bus.internal;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.onap.policy.drools.event.comm.Topic;
import org.onap.policy.drools.event.comm.TopicListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TopicBase implements Topic {

	/**
	 * logger
	 */
	private static Logger logger = LoggerFactory.getLogger(TopicBase.class);

	/**
	 * list of servers
	 */
	protected List<String> servers;

	/**
	 * Topic
	 */
	protected String topic;

	/**
	 * event cache
	 */
	protected CircularFifoQueue<String> recentEvents = new CircularFifoQueue<>(10);

	/**
	 * Am I running?
	 * reflects invocation of start()/stop()
	 * !locked & start() => alive
	 * stop() => !alive
	 */
	protected volatile boolean alive = false;

	/**
	 * Am I locked?
	 * reflects invocation of lock()/unlock() operations
	 * locked => !alive (but not in the other direction necessarily)
	 * locked => !offer, !run, !start, !stop (but this last one is obvious
	 *                                        since locked => !alive)
	 */
	protected volatile boolean locked = false;

	/**
	 * All my subscribers for new message notifications
	 */
	protected final ArrayList<TopicListener> topicListeners = new ArrayList<>();

	/**
	 * Instantiates a new Topic Base
	 *
	 * @param servers list of servers
	 * @param topic topic name
	 *
	 * @return a Topic Base
	 * @throws IllegalArgumentException if invalid parameters are present
	 */
	public TopicBase(List<String> servers, String topic) throws IllegalArgumentException {

		if (servers == null || servers.isEmpty()) {
			throw new IllegalArgumentException("Server(s) must be provided");
		}

		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("A Topic must be provided");
		}

		this.servers = servers;
		this.topic = topic;
	}

	@Override
	public void register(TopicListener topicListener)
		throws IllegalArgumentException {

		logger.info("{}: registering {}", this, topicListener);

		synchronized(this) {
			if (topicListener == null)
				throw new IllegalArgumentException("TopicListener must be provided");

			for (TopicListener listener: this.topicListeners) {
				if (listener == topicListener) return;
			}

			this.topicListeners.add(topicListener);
		}
	}

	@Override
	public void unregister(TopicListener topicListener) {

		logger.info("{}: unregistering {}", this, topicListener);

		synchronized (this) {
			if (topicListener == null)
				throw new IllegalArgumentException("TopicListener must be provided");

			this.topicListeners.remove(topicListener);
		}
	}

	/**
	 * broadcast event to all listeners
	 *
	 * @param message the event
	 * @return true if all notifications are performed with no error, false otherwise
	 */
	protected boolean broadcast(String message) {
		List<TopicListener> snapshotListeners = this.snapshotTopicListeners();

		boolean success = true;
		for (TopicListener topicListener: snapshotListeners) {
			try {
				topicListener.onTopicEvent(this.getTopicCommInfrastructure(), this.topic, message);
			} catch (Exception e) {
				logger.warn("{}: notification error @ {} because of {}",
						    this, topicListener, e.getMessage(), e);
				success = false;
			}
		}
		return success;
	}

	/**
	 * take a snapshot of current topic listeners
	 *
	 * @return the topic listeners
	 */
	protected synchronized List<TopicListener> snapshotTopicListeners() {
		@SuppressWarnings("unchecked")
		List<TopicListener> listeners = (List<TopicListener>) topicListeners.clone();
		return listeners;
	}

	@Override
	public boolean lock() {

		logger.info("{}: locking", this);

		synchronized (this) {
			if (this.locked)
				return true;

			this.locked = true;
		}

		return this.stop();
	}

	@Override
	public boolean unlock() {
		logger.info("{}: unlocking", this);

		synchronized(this) {
			if (!this.locked)
				return true;

			this.locked = false;
		}

		try {
			return this.start();
		} catch (Exception e) {
			logger.warn("{}: cannot after unlocking because of {}", this, e.getMessage(), e);
			return false;
		}
	}

	@Override
	public boolean isLocked() {
		return this.locked;
	}

	@Override
	public String getTopic() {
		return topic;
	}

	@Override
	public boolean isAlive() {
		return this.alive;
	}

	@Override
	public List<String> getServers() {
		return servers;
	}

	@Override
	public synchronized String[] getRecentEvents() {
		String[] events = new String[recentEvents.size()];
		return recentEvents.toArray(events);
	}


	@Override
	public String toString() {
		return "TopicBase [servers=" + servers + ", topic=" + topic + ", #recentEvents=" + recentEvents.size() + ", locked="
				+ locked + ", #topicListeners=" + topicListeners.size() + "]";
	}
}
