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
package se.sics.caracaldb.system;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import se.sics.caracaldb.Address;
import se.sics.caracaldb.BaseMessage;
import se.sics.caracaldb.TestUtil;
import se.sics.caracaldb.global.SchemaData;
import se.sics.caracaldb.system.Configuration.NodePhase;
import se.sics.caracaldb.system.Configuration.SystemPhase;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.ComponentProxy;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;

/**
 *
 * @author Lars Kroll <lkroll@sics.se>
 */
@RunWith(JUnit4.class)
public class LauncherTest {

    private static final String MSG_SENT = "Message Sent";
    private static final String MSG_RECEIVED = "Message Received";
    private static final String SPAWN_SENT = "Spawn Sent";
    private static final String NODE_DOWN = "Node Tearing Down";
    private static final String SYSTEM_DOWN = "System Tearing Down";
    private static final byte NUM_SPAWNS = 1;
    private InetAddress localHost;
    private int port;
    private static Address netAddr;
    private static Address virtAddr1;
    private static Address virtAddr2;
    private static boolean spawn2;
    private static SchemaData.SingleSchema schema;

    static {
        byte[] idB = new byte[]{0};
        ByteBuffer id = ByteBuffer.wrap(idB);
        schema = new SchemaData.SingleSchema(null, "test", ImmutableMap.of("db", "memory"));
    }

    @Before
    public void setUp() {
        try {
            localHost = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException ex) {
            fail(ex.getMessage());
        }

        try {
            ServerSocket s = new ServerSocket(0); // try to find a free port
            port = s.getLocalPort();
            s.close();
        } catch (IOException ex) {
            System.out.println("No free port available!");
            System.exit(1);
        }

        netAddr = new Address(localHost, port, null);
        virtAddr1 = netAddr.newVirtual((byte) 0);
        virtAddr2 = netAddr.newVirtual((byte) 1);

        Launcher.reset();
        Launcher.config().setBoot(false); // don't actually boot up

    }

    @Test
    public void basic() {
        Launcher.config()
                .setIp(netAddr.getIp())
                .setPort(netAddr.getPort())
                .addHostHook(SystemPhase.INIT, new ComponentHook() {
                    @Override
                    public void setUp(HostSharedComponents shared, ComponentProxy parent) {
                        System.out.println("Setting up (" + shared.getSelf() + ").");
                        Component sender = parent.create(SenderComponent.class, new SenderInit(shared.getSelf()));
                        Component receiver = parent.create(ReceiverComponent.class, Init.NONE);
                        shared.connectNetwork(sender);
                        shared.connectNetwork(receiver);
                    }

                    @Override
                    public void tearDown(HostSharedComponents shared, ComponentProxy parent) {
                        System.out.println("Tearing down.");
                    }
                });

        System.out.println("Starting up.");
        TestUtil.reset();
        Launcher.start();
        TestUtil.waitFor(MSG_SENT);
        TestUtil.waitFor(MSG_RECEIVED);
        System.out.println("Stopping.");
        Launcher.stop();
        System.out.println("Done.");

    }

    @Test
    public void virtualBasic() {
        Launcher.config()
                .setIp(netAddr.getIp())
                .setPort(netAddr.getPort())
                .addHostHook(SystemPhase.INIT, new ComponentHook() {
                    @Override
                    public void setUp(HostSharedComponents shared, ComponentProxy parent) {
                        System.out.println("Setting up (" + shared.getSelf() + ").");
                        Component spawner = parent.create(SpawnerComponent.class, Init.NONE);
                        shared.connectNetwork(spawner);
                    }

                    @Override
                    public void tearDown(HostSharedComponents shared, ComponentProxy parent) {
                        System.out.println("Tearing down.");
                    }
                })
                .addVirtualHook(NodePhase.INIT, new VirtualComponentHook() {
                    @Override
                    public void setUp(VirtualSharedComponents shared, ComponentProxy parent) {
                        System.out.println("Setting up (" + shared.getSelf() + ").");
                        Component sender = parent.create(SenderComponent.class, new SenderInit(shared.getSelf()));
                        Component receiver = parent.create(ReceiverComponent.class, Init.NONE);
                        shared.connectNetwork(sender);
                        shared.connectNetwork(receiver);
                    }

                    @Override
                    public void tearDown(VirtualSharedComponents shared, ComponentProxy parent) {
                        System.out.println("Tearing down.");
                    }
                });
        spawn2 = true;

        System.out.println("Starting up.");
        TestUtil.reset();
        Launcher.start();

        TestUtil.waitFor(SPAWN_SENT);
        TestUtil.waitForAll(MSG_SENT, MSG_SENT, MSG_RECEIVED, MSG_RECEIVED);
        System.out.println("Stopping.");

        Launcher.stop();
        System.out.println("Done.");

    }

    @Test
    public void startStop() {
        Launcher.config()
                .setIp(netAddr.getIp())
                .setPort(netAddr.getPort())
                .addHostHook(SystemPhase.INIT, new ComponentHook() {
                    @Override
                    public void setUp(HostSharedComponents shared, ComponentProxy parent) {
                        System.out.println("Setting up (" + shared.getSelf() + ").");
                        Component spawner = parent.create(SpawnerComponent.class, Init.NONE);
                        shared.connectNetwork(spawner);
                    }

                    @Override
                    public void tearDown(HostSharedComponents shared, ComponentProxy parent) {
                        System.out.println(SYSTEM_DOWN);
                        TestUtil.submit(SYSTEM_DOWN);
                    }
                })
                .addVirtualHook(NodePhase.INIT, new VirtualComponentHook() {
                    private Component respawner;

                    @Override
                    public void setUp(VirtualSharedComponents shared, ComponentProxy parent) {
                        System.out.println("Setting up (" + shared.getSelf() + ").");
                        respawner = parent.create(RespawnerComponent.class, new RespawnerInit(shared.getSelf()));
                        shared.connectNetwork(respawner);
                    }

                    @Override
                    public void tearDown(VirtualSharedComponents shared, ComponentProxy parent) {
                        System.out.println("Tearing down " + shared.getSelf());
                        shared.disconnectNetwork(respawner);
                        parent.destroy(respawner);
                        TestUtil.submit(NODE_DOWN);
                    }
                });
        spawn2 = false;

        System.out.println("Starting up.");
        TestUtil.reset();
        Launcher.start();

        //TestUtil.waitFor(SPAWN_SENT);
        String[] waits = new String[NUM_SPAWNS * 2];
        for (byte i = 0; i < NUM_SPAWNS; i++) {
            waits[2 * i] = NODE_DOWN;
            waits[2 * i + 1] = SPAWN_SENT;
        }
        System.out.println("Wait: " + Arrays.toString(waits));
        TestUtil.waitForAll(waits);
        System.out.println("Stopping.");

        Launcher.stop();
        TestUtil.waitFor(SYSTEM_DOWN);
        System.out.println("Done.");

    }

    public static class SenderComponent extends ComponentDefinition {

        private Address self;

        public SenderComponent(SenderInit init) {
            final Positive<Network> net = requires(Network.class);

            self = init.self;

            Handler<Start> startHandler = new Handler<Start>() {
                @Override
                public void handle(Start event) {
                    System.out.println("Started (" + self + ").");
                    trigger(new TestMsg(self, self), net);
                    TestUtil.submit(MSG_SENT);
                }
            };
            subscribe(startHandler, control);
        }
    }

    public static class ReceiverComponent extends ComponentDefinition {

        {
            Positive<Network> net = requires(Network.class);
            Handler<TestMsg> msgHandler = new Handler<TestMsg>() {
                @Override
                public void handle(TestMsg event) {
                    System.out.println("Message arrived: " + event.toString());
                    TestUtil.submit(MSG_RECEIVED);
                }
            };
            subscribe(msgHandler, net);
        }
    }

    public static class SpawnerComponent extends ComponentDefinition {

        {
            final Positive<Network> net = requires(Network.class);

            Handler<Start> startHandler = new Handler<Start>() {
                @Override
                public void handle(Start event) {
                    System.out.println("Started (" + netAddr + ").");
                    trigger(new StartVNode(netAddr, netAddr, virtAddr1.getId(), schema), net);
                    if (spawn2) {
                        trigger(new StartVNode(netAddr, netAddr, virtAddr2.getId(), schema), net);
                    }
                    TestUtil.submit(SPAWN_SENT);
                }
            };
            subscribe(startHandler, control);
        }
    }

    public static class RespawnerComponent extends ComponentDefinition {

        private Address self;

        public RespawnerComponent(RespawnerInit init) {
            final Positive<Network> net = requires(Network.class);

            self = init.self;

            Handler<Start> startHandler = new Handler<Start>() {
                @Override
                public void handle(Start event) {
                    System.out.println("Started (" + netAddr + ").");
                    byte id = self.getId()[0];
                    byte newId = (byte) (id + 1);
                    if (newId < NUM_SPAWNS) {
                        trigger(new StartVNode(self, netAddr, new byte[]{newId}, schema), net);
                        TestUtil.submit(SPAWN_SENT);
                    }
                    trigger(new StopVNode(self, self), net);
                }
            };
            subscribe(startHandler, control);
        }
    }

    public static class SenderInit extends Init<SenderComponent> {

        public final Address self;

        public SenderInit(Address self) {
            this.self = self;
        }
    }

    public static class RespawnerInit extends Init<RespawnerComponent> {

        public final Address self;

        public RespawnerInit(Address self) {
            this.self = self;
        }
    }

    public static class TestMsg extends BaseMessage {

        public TestMsg(Address from, Address to) {
            super(from, to, Transport.TCP);
        }
    }
}
