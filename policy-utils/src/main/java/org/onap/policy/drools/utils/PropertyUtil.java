/*-
 * ============LICENSE_START=======================================================
 * policy-utils
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

package org.onap.policy.drools.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This class provides utilities to read properties from a properties
 * file, and optionally get notifications of future changes
 */
public class PropertyUtil
{
  /**
   * Read in a properties file
   * @param file the properties file
   * @return a Properties object, containing the associated properties
   * @throws IOException - subclass 'FileNotFoundException' if the file
   *	does not exist or can't be opened, and 'IOException' if there is
   *	a problem loading the properties file.
   */
  static public Properties getProperties(File file) throws IOException
  {
	// create an InputStream (may throw a FileNotFoundException)
	FileInputStream fis = new FileInputStream(file);
	try
	  {
		// create the properties instance
		Properties rval = new Properties();

		// load properties (may throw an IOException)
		rval.load(fis);
		return rval;
	  }
	finally
	  {
		// close input stream
		fis.close();
	  }
  }

  /**
   * Read in a properties file
   * @param fileName the properties file
   * @return a Properties object, containing the associated properties
   * @throws IOException - subclass 'FileNotFoundException' if the file
   *	does not exist or can't be opened, and 'IOException' if there is
   *	a problem loading the properties file.
   */
  static public Properties getProperties(String fileName) throws IOException
  {
	return getProperties(new File(fileName));
  }

  /* ============================================================ */

  // timer thread used for polling for property file changes
  private static Timer timer = null;

  /**
   * This is the callback interface, used for sending notifications of
   * changes in the properties file.
   */
  @FunctionalInterface
  public interface Listener
  {
	/**
	 * Notification of a properties file change
	 * @param properties the new properties
	 * @param the set of property names that have changed, including
	 *		additions and removals
	 */
	void propertiesChanged(Properties properties, Set<String> changedKeys);
  }

  // this table maps canonical file into a 'ListenerRegistration' instance
  static private HashMap<File, ListenerRegistration> registrations =
	new HashMap<>();

  /**
   * This is an internal class - one instance of this exists for each
   * property file that is being monitored. Note that multiple listeners
   * can be registered for the same file.
   */
  private static class ListenerRegistration
  {
	// the canonical path of the file being monitored
	File file;

	// the most recent value of 'file.lastModified()'
	long lastModified;

	// the most recent set of properties
	Properties properties;

	// the set of listeners monitoring this file
	LinkedList<Listener> listeners;

	// the 'TimerTask' instance, used for periodic polling
	TimerTask timerTask;

	/**
	 * Constructor - create a 'ListenerRegistration' instance for this
	 * file, but with no listeners
	 */
	ListenerRegistration(File file) throws IOException
	{
	  this.file = file;

	  // The initial value of 'lastModified' is set to 0 to ensure that we
	  // correctly handle the case where the file is modified within the
	  // same second that polling begins.
	  lastModified = 0;

	  // fetch current properties
	  properties = getProperties(file);

	  // no listeners yet
	  listeners = new LinkedList<>();

	  // add to static table, so this instance can be shared
	  registrations.put(file, this);

	  if (timer == null)
		{
		  // still need to create a timer thread
		  synchronized(PropertyUtil.class)
			{
			  // an additional check is added inside the 'synchronized' block,
			  // just in case someone beat us to it
			  if (timer == null)
				{
				  timer = new Timer("PropertyUtil-Timer", true);
				}
			}
		}

	  // create and schedule the timer task, so this is periodically polled
	  timerTask = new TimerTask()
		{
		  @Override
		  public void run()
		  {
			try
			  {
				poll();
			  }
			catch (Exception e)
			  {
				System.err.println(e);
			  }
		  }
		};
	  timer.schedule(timerTask, 10000L, 10000L);
	}

	/**
	 * Add a listener to the notification list
	 * @param listener this is the listener to add to the list
	 * @return the properties at the moment the listener was added to the list
	 */
	synchronized Properties addListener(Listener listener)
	{
	  listeners.add(listener);
	  return (Properties)properties.clone();
	}

	/**
	 * Remove a listener from the notification list
	 * @param listener this is the listener to remove
	 */
	synchronized void removeListener(Listener listener)
	{
	  listeners.remove(listener);

	  // See if we need to remove this 'ListenerRegistration' instance
	  // from the table. The 'synchronized' block is needed in case
	  // another listener is being added at about the same time that this
	  // one is being removed.
	  synchronized(registrations)
		{
		  if (listeners.isEmpty())
			{
			  timerTask.cancel();
			  registrations.remove(file);
			}
		}
	}

	/**
	 * This method is periodically called to check for property list updates
	 * @throws IOException if there is an error in reading the properties file
	 */
	synchronized void poll() throws IOException
	{
	  long timestamp = file.lastModified();
	  if (timestamp != lastModified)
		{
		  // update the record, and send out the notifications
		  lastModified = timestamp;

		  // Save old set, and initial set of changed properties.
		  Properties oldProperties = properties;
		  HashSet<String> changedProperties =
			new HashSet<>(oldProperties.stringPropertyNames());

		  // Fetch the list of listeners that we will potentially notify,
		  // and the new properties. Note that this is in a 'synchronized'
		  // block to ensure that all listeners receiving notifications
		  // actually have a newer list of properties than the one
		  // returned on the initial 'getProperties' call.
		  properties = getProperties(file);
		  
		  Set<String> newPropertyNames = properties.stringPropertyNames();
		  changedProperties.addAll(newPropertyNames);

		  // At this point, 'changedProperties' is the union of all properties
		  // in both the old and new properties files. Iterate through all
		  // of the entries in the new properties file - if the entry
		  // matches the one in the old file, remove it from
		  // 'changedProperties'.
		  for (String name : newPropertyNames)
			{
			  if (properties.getProperty(name).equals
				  (oldProperties.getProperty(name)))
				{
				  // Apparently, any property that exists must be of type
				  // 'String', and can't be null. For this reason, we don't
				  // need to worry about the case where
				  // 'properties.getProperty(name)' returns 'null'. Note that
				  // 'oldProperties.getProperty(name)' may be 'null' if the
				  // old property does not exist.
				  changedProperties.remove(name);
				}
			}

		  // 'changedProperties' should be correct at this point
		  if (!changedProperties.isEmpty())
			{
			  // there were changes - notify everyone in 'listeners'
			  for (final Listener notify : listeners)
				{
				  // Copy 'properties' and 'changedProperties', so it doesn't
				  // cause problems if the recipient makes changes.
				  final Properties tmpProperties =
					(Properties)(properties.clone());
				  final HashSet<String> tmpChangedProperties =
					new HashSet<>(changedProperties);

				  // Do the notification in a separate thread, so blocking
				  // won't cause any problems.
				  new Thread()
				  {
					@Override
					public void run()
					{
					  notify.propertiesChanged
						(tmpProperties, tmpChangedProperties);
					}
				  }.start();
				}
			}
		}
	}
  }

  /**
   * Read in a properties file, and register for update notifications.
   * NOTE: it is possible that the first callback will occur while this
   * method is still in progress. To avoid this problem, use 'synchronized'
   * blocks around this invocation and in the callback -- that will ensure
   * that the processing of the initial properties complete before any
   * updates are processed.
   *
   * @param file the properties file
   * @param notify if not null, this is a callback interface that is used for
   *	notifications of changes
   * @return a Properties object, containing the associated properties
   * @throws IOException - subclass 'FileNotFoundException' if the file
   *	does not exist or can't be opened, and 'IOException' if there is
   *	a problem loading the properties file.
   */
  static public Properties getProperties(File file, Listener listener)
	throws IOException
  {
	if (listener == null)
	  {
		// no listener specified -- just fetch the properties
		return getProperties(file);
	  }

	// Convert the file to a canonical form in order to avoid the situation
	// where different names refer to the same file.
	file = file.getCanonicalFile();

	// See if there is an existing registration. The 'synchronized' block
	// is needed to handle the case where a new listener is added at about
	// the same time that another one is being removed.
	synchronized(registrations)
	  {
		ListenerRegistration reg = registrations.get(file);
		if (reg == null)
		  {
			// a new registration is needed
			reg = new ListenerRegistration(file);
		  }
		return reg.addListener(listener);
	  }
  }

  /**
   * Read in a properties file, and register for update notifications.
   * NOTE: it is possible that the first callback will occur while this
   * method is still in progress. To avoid this problem, use 'synchronized'
   * blocks around this invocation and in the callback -- that will ensure
   * that the processing of the initial properties complete before any
   * updates are processed.
   *
   * @param fileName the properties file
   * @param notify if not null, this is a callback interface that is used for
   *	notifications of changes
   * @return a Properties object, containing the associated properties
   * @throws IOException - subclass 'FileNotFoundException' if the file
   *	does not exist or can't be opened, and 'IOException' if there is
   *	a problem loading the properties file.
   */
  static public Properties getProperties(String fileName, Listener listener)
	throws IOException
  {
	return getProperties(new File(fileName), listener);
  }

  /**
   * Stop listenening for updates
   * @param file the properties file
   * @param notify if not null, this is a callback interface that was used for
   *	notifications of changes
   */
  static public void stopListening(File file, Listener listener)
  {
	if (listener != null)
	  {
		ListenerRegistration reg = registrations.get(file);
		if (reg != null)
		  {
			reg.removeListener(listener);
		  }
	  }
  }

  /**
   * Stop listenening for updates
   * @param fileName the properties file
   * @param notify if not null, this is a callback interface that was used for
   *	notifications of changes
   */
  static public void stopListening(String fileName, Listener listener)
  {
	stopListening(new File(fileName), listener);
  }

}
