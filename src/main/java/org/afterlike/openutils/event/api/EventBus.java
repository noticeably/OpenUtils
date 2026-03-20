package org.afterlike.openutils.event.api;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.afterlike.openutils.event.handler.EventExecutable;
import org.afterlike.openutils.event.handler.EventHandler;
import org.afterlike.openutils.event.handler.Listener;
/*
 * Derived from AzuraClient’s EventBus
 * https://github.com/AzuraClient/Azura-Event-Bus
 *
 * MIT License
 *
 * Copyright (c) 2025 Azura
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

public final class EventBus {
	/** Map of event classes to their executables */
	private final Map<Class<? extends Event>, List<EventExecutable>> executableMap = new ConcurrentHashMap<>();
	/**
	 * Register an object in the event system
	 *
	 * @param object
	 *            the object to register
	 */
	public void subscribe(final Object object) {
		if (subscribed(object))
			return;
		for (final Method method : object.getClass().getDeclaredMethods()) {
			if (!method.isAnnotationPresent(EventHandler.class) || method.getParameterCount() <= 0)
				continue;
			final EventExecutable executable = new EventExecutable(method, object,
					method.getDeclaredAnnotation(EventHandler.class).value());
			addExecutable(executable);
		}
		for (final Field field : object.getClass().getDeclaredFields()) {
			if (!field.isAnnotationPresent(EventHandler.class)
					|| !field.getType().isAssignableFrom(Listener.class))
				continue;
			final EventExecutable executable = new EventExecutable(field, object,
					field.getDeclaredAnnotation(EventHandler.class).value());
			addExecutable(executable);
		}
	}

	private void addExecutable(final EventExecutable executable) {
		final Class<? extends Event> eventClass = executable.getEventClass();
		final List<EventExecutable> executables = executableMap.computeIfAbsent(eventClass,
				k -> new CopyOnWriteArrayList<>());
		executables.add(executable);
		executables.sort(Comparator.comparingInt(EventExecutable::getPriority));
	}

	/**
	 * Register an object in the event system
	 *
	 * @param object
	 *            the object to register
	 * @param eventClass
	 *            the event class to listen to
	 * @param listener
	 *            the listener to call
	 * @param priority
	 *            the priority of the listener
	 */
	public <U extends Event> void subscribe(final Object object, final Class<U> eventClass,
			final Listener<U> listener, final int priority) {
		addExecutable(new EventExecutable(eventClass, listener, object, priority));
	}

	/**
	 * Register an object in the event system
	 *
	 * @param object
	 *            the object to register
	 * @param eventClass
	 *            the event class to listen to
	 * @param listener
	 *            the listener to call Uses the default priority
	 */
	public <U extends Event> void subscribe(final Object object, final Class<U> eventClass,
			final Listener<U> listener) {
		this.subscribe(object, eventClass, listener, EventPriority.DEFAULT);
	}

	/**
	 * Method used for calling events
	 *
	 * @param event
	 *            the event that should be called
	 */
	public <U extends Event> U post(final U event) {
		final List<EventExecutable> executables = executableMap.get(event.getClass());
		if (executables != null) {
			for (final EventExecutable eventExecutable : executables)
				eventExecutable.call(event);
		}
		return event;
	}

	/**
	 * Unregister an object from the event system
	 *
	 * @param object
	 *            the object to unregister
	 */
	public void unsubscribe(final Object object) {
		for (final List<EventExecutable> executables : executableMap.values()) {
			executables.removeIf(e -> e.getParent().equals(object));
		}
		executableMap.values().removeIf(List::isEmpty);
	}

	/**
	 * Method used to check whether an object is registered in the event system
	 *
	 * @param object
	 *            the object to check
	 */
	public boolean subscribed(final Object object) {
		for (final List<EventExecutable> executables : executableMap.values()) {
			if (executables.stream().anyMatch(e -> e.getParent().equals(object))) {
				return true;
			}
		}
		return false;
	}
}