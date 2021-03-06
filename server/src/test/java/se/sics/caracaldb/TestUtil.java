/* 
 * This file is part of the CaracalDB distributed storage system.
 *
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) 
 * Copyright (C) 2009 Royal Institute of Technology (KTH)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.caracaldb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Event;

/**
 *
 * @author Lars Kroll <lkroll@sics.se>
 */
public abstract class TestUtil {

    private static final Logger log = LoggerFactory.getLogger(TestUtil.class);
    private static BlockingQueue<String> stringQ;
    private static BlockingQueue<Event> eventQ;
    private static long timeout = 5000;
    private static final TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    public static void reset() {
        stringQ = new LinkedBlockingQueue<String>();
        eventQ = new LinkedBlockingQueue<Event>();
    }

    public static void reset(long timeout) {
        TestUtil.timeout = timeout;
        reset();
    }

    public static void submit(String s) {
        try {
            if (!stringQ.offer(s, timeout, timeUnit)) {
                Assert.fail("Timeout");
            }
        } catch (InterruptedException ex) {
            log.debug("Failed on putting String: " + s, ex);
        }
    }

    public static void submit(Event e) {
        try {
            if (!eventQ.offer(e, timeout, timeUnit)) {
                Assert.fail("Timeout");
            }
        } catch (InterruptedException ex) {
            log.debug("Failed on putting Event: " + e, ex);
        }
    }

    public static void waitFor(Event e) {
        try {
            Event qEvent = eventQ.poll(timeout, timeUnit);
            if (qEvent == null) {
                Assert.fail("Timeout");
            }
            Assert.assertEquals(e, qEvent);
        } catch (InterruptedException ex) {
            log.debug("Failed waiting for Event: " + e, ex);
        }
    }

    public static void waitFor(Class<? extends Event> eventType) {
        try {
            Event qEvent = eventQ.poll(timeout, timeUnit);
            if (qEvent == null) {
                Assert.fail("Timeout");
            }
            Assert.assertTrue(eventType.isInstance(qEvent));
        } catch (InterruptedException ex) {
            log.debug("Failed waiting for Event of Type: " + eventType, ex);
        }
    }

    public static void waitFor(String s) {
        try {
            String qString = stringQ.poll(timeout, timeUnit);
            if (qString == null) {
                Assert.fail("Timeout");
            }
            Assert.assertEquals(s, qString);
        } catch (InterruptedException ex) {
            log.debug("Failed waiting for String: " + s, ex);
        }
    }

    public static void waitForAll(Event... events) {
        ArrayList<Event> el = new ArrayList<Event>();
        for (Event e : events) {
            el.add(e);
        }
        try {
            while (!el.isEmpty()) {
                Event qEvent = eventQ.poll(timeout, timeUnit);
                if (qEvent == null) {
                    Assert.fail("Timeout");
                }
                if (el.contains(qEvent)) {
                    el.remove(qEvent);
                } else {
                    Assert.fail("Unexpected event: " + qEvent);
                }
            }
        } catch (InterruptedException ex) {
            log.debug("Failed waiting for Events.", ex);
        }
    }

    public static void waitForAll(Class<? extends Event>... eventTypes) {
        ArrayList<Class<? extends Event>> el = new ArrayList<Class<? extends Event>>();
        for (Class<? extends Event> e : eventTypes) {
            el.add(e);
        }
        try {
            while (!el.isEmpty()) {
                Event qEvent = eventQ.poll(timeout, timeUnit);
                if (qEvent == null) {
                    Assert.fail("Timeout");
                }
                Iterator<Class<? extends Event>> it = el.iterator();
                boolean found = false;
                while (it.hasNext()) {
                    Class<? extends Event> eventType = it.next();
                    if (eventType.isInstance(qEvent)) {
                        it.remove();
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Assert.fail("Unexpected event: " + qEvent);
                }
            }
        } catch (InterruptedException ex) {
            log.debug("Failed waiting for Events.", ex);
        }
    }
    
    public static void waitForAll(String... strings) {
        ArrayList<String> sl = new ArrayList<String>();
        for (String s : strings) {
            sl.add(s);
        }
        try {
            while (!sl.isEmpty()) {
                String qString = stringQ.poll(timeout, timeUnit);
                if (qString == null) {
                    Assert.fail("Timeout");
                }
                if (sl.contains(qString)) {
                    sl.remove(qString);
                    System.out.println("Got " + qString);
                } else {
                    Assert.fail("Unexpected key: " + qString);
                }
            }
        } catch (InterruptedException ex) {
            log.debug("Failed waiting for Events.", ex);
        }
    }
}
