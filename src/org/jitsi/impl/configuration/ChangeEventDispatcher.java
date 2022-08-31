/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.configuration;

import java.beans.*;
import java.util.*;

/**
 * This is a utility class that can be used by objects that support constrained
 * properties.  You can use an instance of this class as a member field and
 * delegate various work to it.
 *
 * @author Emil Ivov
 */
public class ChangeEventDispatcher
{
    /**
     * All property change listeners registered so far.
     */
    private List<PropertyChangeListener> propertyChangeListeners;

    /**
     * Hashtable for managing property change listeners registered for specific
     * properties. Maps property names to PropertyChangeSupport objects.
     */
    private Map<String, ChangeEventDispatcher> propertyChangeChildren;

    /**
     * The object to be provided as the "source" for any generated events.
     */
    private final Object source;

    /**
     * Constructs a <tt>ChangeEventDispatcher</tt> object.
     *
     * @param sourceObject  The object to be given as the source for any events.
     */
    public ChangeEventDispatcher(Object sourceObject)
    {
        if (sourceObject == null)
            throw new NullPointerException("sourceObject");

        source = sourceObject;
    }

    /**
     * Add a PropertyChangeListener to the listener list.
     * The listener is registered for all properties.
     *
     * @param listener  The PropertyChangeChangeListener to be added
     */
    public synchronized void addPropertyChangeListener(
        PropertyChangeListener listener)
    {
        if (propertyChangeListeners == null)
            propertyChangeListeners = new Vector<>();

        propertyChangeListeners.add(listener);
    }

    /**
     * Add a PropertyChangeListener for a specific property.  The listener
     * will be invoked only when a call on firePropertyChange names that
     * specific property.
     *
     * @param propertyName  The name of the property to listen on.
     * @param listener  The ConfigurationChangeListener to be added
     */
    public synchronized void addPropertyChangeListener(
        String propertyName,
        PropertyChangeListener listener)
    {
        if (propertyChangeChildren == null)
        {
            propertyChangeChildren =
                    new Hashtable<>();
        }
        ChangeEventDispatcher child = propertyChangeChildren.get(
            propertyName);
        if (child == null)
        {
            child = new ChangeEventDispatcher(source);
            propertyChangeChildren.put(propertyName, child);
        }
        child.addPropertyChangeListener(listener);
    }

    /**
     * Remove a PropertyChangeListener from the listener list.
     * This removes a ConfigurationChangeListener that was registered
     * for all properties.
     *
     * @param listener The PropertyChangeListener to be removed
     */
    public synchronized void removePropertyChangeListener(
        PropertyChangeListener listener)
    {
        if (propertyChangeListeners != null)
            propertyChangeListeners.remove(listener);
    }

    /**
     * Remove a PropertyChangeListener for a specific property.
     *
     * @param propertyName  The name of the property that was listened on.
     * @param listener  The PropertyChangeListener to be removed
     */
    public synchronized void removePropertyChangeListener(
        String propertyName,
        PropertyChangeListener listener)
    {
        if (propertyChangeChildren != null)
        {
            ChangeEventDispatcher child
                = propertyChangeChildren.get(propertyName);

            if (child != null)
                child.removePropertyChangeListener(listener);
        }
    }

    /**
     * Report a bound property update to any registered listeners.
     * No event is fired if old and new are equal and non-null.
     *
     * @param propertyName  The programmatic name of the property
     *                      that was changed.
     * @param oldValue  The old value of the property.
     * @param newValue  The new value of the property.
     */
    public void firePropertyChange(String propertyName,
                                   Object oldValue, Object newValue)
    {
        if (oldValue == null || newValue == null || !oldValue.equals(newValue))
        {
            firePropertyChange(
                new PropertyChangeEvent(
                        source,
                        propertyName,
                        oldValue, newValue));
        }
    }

    /**
     * Fire an existing PropertyChangeEvent to any registered listeners.
     * No event is fired if the given event's old and new values are
     * equal and non-null.
     * @param evt  The PropertyChangeEvent object.
     */
    public void firePropertyChange(PropertyChangeEvent evt)
    {
        Object oldValue = evt.getOldValue();
        Object newValue = evt.getNewValue();
        String propertyName = evt.getPropertyName();
        if (oldValue != null && newValue != null && oldValue.equals(newValue))
            return;

        // Copy the change listeners list to prevent concurrent modifications
        List<PropertyChangeListener> changeListeners = null;
        Map<String, ChangeEventDispatcher> changeChildren = null;
        synchronized (this)
        {
            if (propertyChangeListeners != null)
            {
                changeListeners = new ArrayList<>
                        (propertyChangeListeners);
            }
            if (propertyChangeChildren != null)
            {
                changeChildren = new HashMap<>
                        (propertyChangeChildren);
            }
        }

        if (changeListeners != null)
        {
            for (PropertyChangeListener target : changeListeners)
                target.propertyChange(evt);
        }

        if (changeChildren != null && propertyName != null)
        {
            ChangeEventDispatcher child
                = changeChildren.get(propertyName);

            if (child != null)
                child.firePropertyChange(evt);
        }
    }

    /**
     * Check if there are any listeners for a specific property. (Generic
     * listeners count as well)
     *
     * @param propertyName  the property name.
     * @return true if there are one or more listeners for the given property
     */
    public synchronized boolean hasPropertyChangeListeners(String propertyName)
    {
        if(propertyChangeListeners != null && !propertyChangeListeners.isEmpty())
        {
            // there is a generic listener
            return true;
        }
        if (propertyChangeChildren != null)
        {
            ChangeEventDispatcher child
                = propertyChangeChildren.get(propertyName);

            if (child != null && child.propertyChangeListeners != null)
                return!child.propertyChangeListeners.isEmpty();
        }
        return false;
    }
}
