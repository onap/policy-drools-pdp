/*
 * ============LICENSE_START=======================================================
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.pooling;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.event.comm.TopicListener;
import org.onap.policy.drools.event.comm.TopicSink;
import org.onap.policy.drools.event.comm.TopicSource;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.state.StartState;
import org.onap.policy.drools.pooling.state.State;
import org.onap.policy.drools.pooling.state.StateTimerTask;
import org.onap.policy.drools.system.PolicyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

// TODO re-comment
// TODO comment: messages read by the controller are held in the rule engine
// until a bucket assignment has been made

/*
 * TODO while locked, should still forward messages. However, stuff destined
 * for this rule engine should be discarded (same behavior as
 * PolicyController.offer()).
 */

public class PoolingManagerImpl implements PoolingManager {

	private static final Logger logger = LoggerFactory.getLogger(PoolingManagerImpl.class);

	// TODO remove constants

	/**
	 * Maximum number of times a message can be forwarded.
	 */
	private static final int MAX_HOPS = 0;

	/**
	 * ID of this host.
	 */
	private final String host;

	/**
	 * Associated controller.
	 */
	private PolicyController controller;

	/**
	 * Used to inject Forwarded events.
	 */
	private TopicListener listener;

	/**
	 * Used to encode & decode request objects received from & sent to a rule
	 * engine.
	 */
	private final Serializer serializer;

	/**
	 * Internal DMaaP topic used by this controller.
	 */
	private final String topic;

	/**
	 * Topic source whose filter is to be manipulated. Initialized by
	 * {@link #afterStart()}.
	 */
	private FilteredTopicSource topicSource;

	/**
	 * Type used when delivering to the internal topic. Initialized by
	 * {@link #afterStart()}.
	 */
	private CommInfrastructure busType;

	/**
	 * Lock used while updating {@link #current}. Signaled, whenever the state
	 * object indicates that processing can begin, by invoking
	 * {@link #enableProcessing(boolean)}.
	 */
	private final Object curLocker = new Object();

	/**
	 * Current state, or {@code null} if not currently running.
	 * <p/>
	 * This uses a finite state machine, wherein the state object contains all
	 * of the data relevant to that state. Each state object has a process()
	 * method, specific to each type of {@link Message} subclass. The method
	 * returns the next state object, or {@code null} if the state is to remain
	 * the same.
	 */
	private State current = null;

	/**
	 * Default handler used when {@link #current} is {@code null}.
	 */
	private final State defaultHandler = new State(this) {
		@Override
		public Map<String, Object> getFilter() {
			return null;
		}
	};

	/**
	 * Current bucket assignments, or {@code null} if the host is not currently
	 * assigned any buckets.
	 */
	private BucketAssignments assignments = null;

	/**
	 * Pool used to execute timers.
	 */
	private ScheduledThreadPoolExecutor scheduler = null;

	/**
	 * Queue used when no bucket assignments are available.
	 */
	private EventQueue eventq;

	public PoolingManagerImpl(TypedProperties props) {
		this.host = UUID.randomUUID().toString();

		try {
			this.serializer = new Serializer();
			this.topic = props.getStrProperty(PoolingProperties.PROP_POOLING_TOPIC);
			this.eventq = new EventQueue(props.getIntProperty(PoolingProperties.PROP_OFFLINE_LIMIT),
					props.getIntProperty(PoolingProperties.PROP_OFFLINE_AGE_MS));

			logger.info("allocating host {} to controller {} for topic {}", host, controller.getName(), topic);

		} catch (PoolingFeatureException e) {
			logger.error("failed to attach internal DMaaP topic to controller {}", controller.getName());
			throw e.toRuntimeException();
		}
	}

	protected void setController(PolicyController controller) {
		this.controller = controller;
		this.listener = (TopicListener) controller;
	}

	@Override
	public ScheduledFuture<?> schedule(long delayMs, StateTimerTask task) {
		return scheduler.schedule(new TimerAction(task), delayMs, TimeUnit.MILLISECONDS);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(long initialDelayMs, long delayMs, StateTimerTask task) {
		return scheduler.scheduleWithFixedDelay(new TimerAction(task), initialDelayMs, delayMs, TimeUnit.MILLISECONDS);
	}

	@Override
	public void publishAdmin(Message msg) {
		publish(Message.ADMIN, msg);
	}

	@Override
	public void publish(String channel, Message msg) {
		msg.setChannel(channel);

		try {
			String txt = serializer.encodeMsg(msg);
			controller.deliver(busType, topic, txt);

		} catch (JsonProcessingException e) {
			logger.error("failed to serialize message for topic {} channel {}", topic, channel, e);

		} catch (RuntimeException e) {
			logger.error("failed to publish message for topic {} channel {}", topic, channel, e);
		}
	}

	private void changeState(State newState) {
		if (newState != null && current != null) {
			current.cancelTimers();
			current = newState;

			Map<String, Object> filter = current.getFilter();

			try {
				topicSource.setFilter(serializer.encodeFilter(current.getFilter()));

			} catch (JsonProcessingException e) {
				logger.error("failed to encode server-side filter for topic {}, {}", topic, filter, e);
			}
		}
	}

	public boolean beforeOffer(CommInfrastructure protocol, String topic2, String event) {
		// TODO should be a parameter
		String reqid = "";

		if (reqid == null || reqid.isEmpty()) {
			logger.warn("missing request id for topic {}", topic2);
			return false;
		}

		Forward ev = makeEvent(protocol, topic2, event, reqid);
		if (ev == null) {
			// invalid args - consume the message
			return true;
		}

		synchronized (curLocker) {
			if (topic2.equals(topic)) {
				// it's on the internal topic
				handleInternal(event);

				// we've handled the event
				return true;

			} else {
				return handleExternal(ev);
			}
		}
	}

	private Forward makeEvent(CommInfrastructure protocol, String topic2, String event, String reqid) {
		try {
			Forward ev = new Forward(host, protocol, topic2, event, reqid);
			ev.checkValidity();

			return ev;

		} catch (PoolingFeatureException e) {
			logger.error("invalid message for topic {}", topic2, e);
			return null;
		}
	}

	public void handle(Forward event) {
		synchronized (curLocker) {
			if (!handleExternal(event)) {
				inject(event);
			}
		}
	}

	private void inject(Forward event) {
		listener.onTopicEvent(event.getProtocol(), event.getTopic(), event.getPayload());
	}

	private boolean handleExternal(Forward event) {
		if (assignments == null) {
			// no bucket assignments yet - add it to the queue
			eventq.add(event);

		} else {
			int bucket = Math.abs(event.getRequestId().hashCode()) % assignments.size();
			String target = assignments.getAssignedHost(bucket);

			if (target.equals(host)) {
				// message belongs to this host - allow it to flow through
				return false;
			}

			// forward to a different host
			if (event.getNumHops() > MAX_HOPS) {
				logger.warn("message hop count {} exceeded {} for topic {}", event.getNumHops(), MAX_HOPS, topic);

			} else {
				event.bumpNumHops();
				publish(target, event);
			}
		}

		// we've handled the event
		return true;
	}

	private void handleInternal(String event) {
		Class<?> clazz = null;

		try {
			Message msg = serializer.decodeMsg(event);

			// get the class BEFORE checking the validity
			clazz = msg.getClass();

			msg.checkValidity();

			State hdlr = (current != null ? current : defaultHandler);

			Method meth = hdlr.getClass().getMethod("process", msg.getClass());
			changeState((State) meth.invoke(hdlr, msg));

		} catch (IOException e) {
			logger.warn("failed to decode message for topic {}", topic, e);

		} catch (NoSuchMethodException | SecurityException e) {
			logger.error("no processor for {} for topic {}", topic, clazz, e);

		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			logger.error("failed to process message {} for topic {}", clazz, topic, e);

		} catch (PoolingFeatureException e) {
			logger.error("failed to process message {} for topic {}", clazz, topic, e);
		}
	}

	@Override
	public void startDistributing(BucketAssignments assignments) {
		synchronized (curLocker) {
			this.assignments = assignments;

			Forward ev;
			while ((ev = eventq.poll()) != null) {
				handleEvent(ev);
			}
		}
	}

	private void handleEvent(Forward ev) {
		int bucket = Math.abs(ev.getRequestId().hashCode()) % assignments.size();
		String target = assignments.getAssignedHost(bucket);

		if (target.equals(host)) {
			// message belongs to this host
			inject(ev);

		} else {
			// forward to a different host
			publish(target, ev);
		}

	}

	public void beforeStart() {
		synchronized (curLocker) {
			if (scheduler == null) {
				scheduler = new ScheduledThreadPoolExecutor(1);

				scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
				scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
				scheduler.setMaximumPoolSize(1);
				scheduler.setRemoveOnCancelPolicy(true);
			}
		}
	}

	public void afterStart() throws PoolingFeatureException {
		synchronized (curLocker) {
			if (current == null) {
				initTopicSource();
				initTopicSink();
				current = new StartState(this);
			}
		}
	}

	private void initTopicSource() throws PoolingFeatureException {
		for (TopicSource src : controller.getTopicSources()) {
			if (topic.equals(src.getTopic())) {
				if (src instanceof FilteredTopicSource) {
					topicSource = (FilteredTopicSource) src;
					return;

				} else {
					throw new PoolingFeatureException(
							"controller " + controller.getName() + " topic source " + topic + " is not filtered");
				}
			}
		}

		throw new PoolingFeatureException("controller " + controller.getName() + " is missing topic source " + topic);
	}

	private void initTopicSink() throws PoolingFeatureException {
		for (TopicSink sink : controller.getTopicSinks()) {
			if (topic.equals(sink.getTopic())) {
				busType = sink.getTopicCommInfrastructure();
				return;
			}
		}

		throw new PoolingFeatureException("controller " + controller.getName() + " is missing topic sink " + topic);
	}

	public void beforeStop() {
		ScheduledThreadPoolExecutor sched;

		synchronized (curLocker) {
			sched = scheduler;
			scheduler = null;

			if (current != null) {
				State st = current;
				current = null;

				st.stop();
			}
		}

		if (sched != null) {
			sched.shutdownNow();
		}
	}

	public void afterStop() {
		synchronized (curLocker) {
			if (!eventq.isEmpty()) {
				logger.warn("discarded messages after stopping topic {}", topic);
			}
		}
	}

	public String getHost() {
		return host;
	}

	private class TimerAction implements Runnable {
		private State origState;
		private StateTimerTask task;

		public TimerAction(StateTimerTask task) {
			this.origState = current;
			this.task = task;
		}

		@Override
		public void run() {
			synchronized (curLocker) {
				if (origState == current) {
					changeState(task.fire(null));
				}
			}
		}
	}

	private static class EventQueue {
		private int maxEvents;
		private int maxAgeMs;
		private Deque<Forward> events = new LinkedList<>();

		public EventQueue(int maxEvents, int maxAgeMs) {
			this.maxEvents = maxEvents;
			this.maxAgeMs = maxAgeMs;
		}

		public boolean isEmpty() {
			return events.isEmpty();
		}

		public void add(Forward ev) {
			events.add(ev);

			if (events.size() >= maxEvents) {
				events.remove();
			}
		}

		public Forward poll() {
			long tmin = System.currentTimeMillis() - maxAgeMs;
			Forward ev;
			while ((ev = events.poll()) != null) {
				if (!ev.isExpired(tmin)) {
					// hasn't expired yet
					break;
				}
			}

			return ev;
		}
	}
}
