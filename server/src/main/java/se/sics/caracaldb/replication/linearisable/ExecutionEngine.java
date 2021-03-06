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
package se.sics.caracaldb.replication.linearisable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.caracaldb.Address;
import se.sics.caracaldb.KeyRange;
import se.sics.caracaldb.View;
import se.sics.caracaldb.datatransfer.Completed;
import se.sics.caracaldb.datatransfer.DataReceiver;
import se.sics.caracaldb.datatransfer.DataReceiverInit;
import se.sics.caracaldb.datatransfer.DataSender;
import se.sics.caracaldb.datatransfer.DataSenderInit;
import se.sics.caracaldb.datatransfer.DataTransfer;
import se.sics.caracaldb.datatransfer.InitiateTransfer;
import se.sics.caracaldb.datatransfer.TransferFilter;
import se.sics.caracaldb.operations.CaracalOp;
import se.sics.caracaldb.operations.CaracalResponse;
import se.sics.caracaldb.operations.GetRequest;
import se.sics.caracaldb.operations.GetResponse;
import se.sics.caracaldb.operations.MultiOpRequest;
import se.sics.caracaldb.operations.MultiOpResponse;
import se.sics.caracaldb.operations.PutRequest;
import se.sics.caracaldb.operations.PutResponse;
import se.sics.caracaldb.operations.RangeQuery;
import se.sics.caracaldb.operations.ResponseCode;
import se.sics.caracaldb.replication.log.Decide;
import se.sics.caracaldb.replication.log.Noop;
import se.sics.caracaldb.replication.log.Propose;
import se.sics.caracaldb.replication.log.Prune;
import se.sics.caracaldb.replication.log.Reconfigure;
import se.sics.caracaldb.replication.log.ReplicatedLog;
import se.sics.caracaldb.replication.log.Value;
import se.sics.caracaldb.store.CleanupReq;
import se.sics.caracaldb.store.GetReq;
import se.sics.caracaldb.store.GetResp;
import se.sics.caracaldb.store.MultiOp;
import se.sics.caracaldb.store.Put;
import se.sics.caracaldb.store.RangeReq;
import se.sics.caracaldb.store.RangeResp;
import se.sics.caracaldb.store.SizeScan;
import se.sics.caracaldb.store.StorageRequest;
import se.sics.caracaldb.store.StorageResponse;
import se.sics.caracaldb.store.Store;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.Stopped;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;

/**
 *
 * @author Lars Kroll <lkroll@sics.se>
 */
public class ExecutionEngine extends ComponentDefinition {

    public static enum State {

        PASSIVE, // wait for installation of first view
        TRANSFERING, // transfer snapshot in the background and handle incoming requests
        CATCHING_UP, // receiving snapshot in the background but ignore/forward incoming requests
        ACTIVE; // handle requests
    }
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionEngine.class);
    Negative<Replication> rep = provides(Replication.class);
    Positive<ReplicatedLog> rLog = requires(ReplicatedLog.class);
    Positive<Store> store = requires(Store.class);
    Positive<Network> net = requires(Network.class);
    Positive<Timer> timer = requires(Timer.class);
    // Instance
    private State state;
    private View view;
    private KeyRange range;
    private Address self;
    private ExecutionEngineInit init;
    private final Actions actions = new Actions();
    private OperationsLog opLog = new InMemoryLog();
    private long lastSnapshotId = -1;
    private int versionId = -1;

    public ExecutionEngine(ExecutionEngineInit event) {
        this.init = event;

        this.view = init.view;
        this.self = init.self;
        this.range = init.range;

        state = State.PASSIVE;

        if (view == null) {
            LOG.debug("{}: Starting in passive mode", self);
            subscribe(installHandler, rLog);
        } else {
            LOG.debug("{}: Starting in active mode", self);
            subscribe(startHandler, control);
        }

    }
    /*
     * HANDLERS
     */
    Handler<Start> startHandler = new Handler<Start>() {
        @Override
        public void handle(Start event) {

            state = State.ACTIVE;

            subCoreHandlers();

            trigger(Synced.EVENT, rep);
        }
    };
    /*
     * Core
     */
    Handler<CaracalOp> opHandler = new Handler<CaracalOp>() {
        @Override
        public void handle(CaracalOp event) {
            if (!actions.contains(state, event)) {
                trigger(new CaracalResponse(event.id, ResponseCode.UNSUPPORTED_OP), rep);
                return;
            }
            trigger(new Propose(new SMROp(event.id, event)), rLog);
        }
    };
    Handler<ReplicationSetInfo> infoHandler = new Handler<ReplicationSetInfo>() {

        @Override
        public void handle(ReplicationSetInfo event) {
            trigger(new Propose(new Scan(event.range)), rLog);

            LOG.info("{}: Scheduling SizeScan. State: {}, Log-size: {}, View: {}, lastSnapshot: {}",
                    new Object[]{self, state, opLog.size(), view, lastSnapshotId});
        }
    };
    Handler<GetResp> getHandler = new Handler<GetResp>() {
        @Override
        public void handle(GetResp event) {
            trigger(new GetResponse(event.getId(), event.key, event.value), rep);
        }
    };
    Handler<MultiOp.Resp> mopHandler = new Handler<MultiOp.Resp>() {

        @Override
        public void handle(MultiOp.Resp event) {
            trigger(new MultiOpResponse(event.getId(), ResponseCode.SUCCESS, event.success), rep);
        }
    };
    Handler<RangeResp> rangeHandler = new Handler<RangeResp>() {
        @Override
        public void handle(RangeResp resp) {
            trigger(new RangeQuery.InternalResponse(resp), rep);
        }
    };
    Handler<StorageResponse> diffHandler = new Handler<StorageResponse>() {

        @Override
        public void handle(StorageResponse event) {
            if (event.diff != null) {
                trigger(event.diff, rep);
            }
        }
    };
    Handler<ViewChange> viewChangeHandler = new Handler<ViewChange>() {
        @Override
        public void handle(ViewChange event) {
            if ((view != null) && (view.id >= event.view.id)) {
                LOG.info("Ignoring view change from {} to {}: Local is at least as recent.",
                        view, event.view);
                return;
            }
            Reconfigure reconf = new Reconfigure(UUID.randomUUID(), event.view, event.quorum, versionId, event.range);
            trigger(new Propose(reconf), rLog);
        }
    };
    Handler<Decide> decideHandler = new Handler<Decide>() {
        @Override
        public void handle(Decide e) {
            List<Pair<Long, Value>> values = opLog.insert(e.position, e.value);
            for (Pair<Long, Value> t : values) {
                Value v = t.getValue1();
                Long pos = t.getValue0();
                if (v instanceof Reconfigure) {
                    doReconf((Reconfigure) v);
                    continue;
                }
                if (v instanceof SMROp) {
                    executeOp(pos, (SMROp) v);
                    continue;
                }
                if (v instanceof SyncedUp) {
                    goActive();
                    continue;
                }
                if (v instanceof Scan) {
                    Scan s = (Scan) v;
                    trigger(new SizeScan(s.range), store);
                    continue;
                }
                if (!(v instanceof Noop)) {
                    LOG.error("Unkown decision value: {}", v);
                }
            }
            trigger(new Prune(lastSnapshotId), rLog);
        }
    };
    Handler<SnapshotResp> snapshotHandler = new Handler<SnapshotResp>() {
        @Override
        public void handle(SnapshotResp event) {
            // Don't use the on in the id in the response
            // There might be new updates already
            trigger(new Prune(lastSnapshotId), rLog);
        }
    };
    /*
     * PASSIVE only
     */
    Handler<Decide> installHandler = new Handler<Decide>() {
        @Override
        public void handle(Decide e) {
            if (e.value instanceof Reconfigure) {
                Reconfigure event = (Reconfigure) e.value;
                unsubscribe(this, rLog);
                view = event.view;
                state = State.CATCHING_UP;
                versionId = event.versionId + 1;

                Handler<InitiateTransfer> transferHandler = new Handler<InitiateTransfer>() {
                    @Override
                    public void handle(InitiateTransfer event) {
                        final Component dataTransfer = create(DataReceiver.class, new DataReceiverInit(event));
                        connect(dataTransfer.getNegative(Network.class), net, new TransferFilter(event.id));
                        connect(dataTransfer.getNegative(Timer.class), timer);
                        connect(dataTransfer.getNegative(Store.class), store);

                        final Long snapshotId = (Long) event.metadata.get("snapshotId");
                        final Integer versionId = (Integer) event.metadata.get("versionId");
                        if ((snapshotId == null) || (versionId == null)) {
                            LOG.error("Data transfer didn't provide snapshotId or versionId. "
                                    + "Shutting down to avoid inconsistencies.");
                            System.exit(1);
                        }

                        final Handler<Completed> completedHandler = new Handler<Completed>() {
                            @Override
                            public void handle(Completed event) {
                                unsubscribe(this, dataTransfer.getPositive(DataTransfer.class));
                                trigger(Stop.event, dataTransfer.control());
                                Handler<Stopped> cleanupHandler = new Handler<Stopped>() {
                                    @Override
                                    public void handle(Stopped event) {
                                        disconnect(dataTransfer.getNegative(Network.class), net);
                                        disconnect(dataTransfer.getNegative(Timer.class), timer);
                                        disconnect(dataTransfer.getNegative(Store.class), store);
                                        destroy(dataTransfer);
                                    }
                                };
                                subscribe(cleanupHandler, dataTransfer.control());

                                lastSnapshotId = snapshotId;
                                ExecutionEngine.this.versionId = versionId;
                                trigger(new Propose(new SyncedUp()), rLog);
                                trigger(Synced.EVENT, rep);
                            }
                        };
                        subscribe(completedHandler, dataTransfer.getPositive(DataTransfer.class));
                        trigger(Start.event, dataTransfer.control());
                        unsubscribe(this, net); // Only allow one instance
                    }
                };
                subscribe(transferHandler, net);

                subCoreHandlers();
            } else {
                LOG.warn("{}: Got {} while waiting for initial reconfigure decision", self, e.value);
            }
        }
    };

    private void goActive() {
        State oldState = state;
        state = State.ACTIVE;

        if (oldState == State.CATCHING_UP) {
            Pair<Long, List<CaracalOp>> diff = opLog.getSnapshotDiff(lastSnapshotId);
            SnapshotReq req = new SnapshotReq(diff.getValue0());
            for (CaracalOp op : diff.getValue1()) {
                StorageRequest sreq = actions.get(state, op).prepareSnapshot(op);
                if (sreq != null) {
                    req.addReq(sreq);
                }
            }
            trigger(req, store);
            lastSnapshotId = diff.getValue0();
        }
        CleanupReq cr = new CleanupReq(range, versionId);
        trigger(cr, store);
    }

    private void doReconf(Reconfigure rconf) {
        if ((view != null) && (view.id >= rconf.view.id)) {
            LOG.warn("Ignoring reconfiguration from {} to {}: Local is at least as recent.",
                    view, rconf.view);
            return;
        }

        View oldView = view;
        view = rconf.view;
        LOG.info("Moved to view {}", view);
        //TODO update MethCat view as well

        state = State.TRANSFERING;
        versionId++;

        // transfer data
        transferDataMaybe(oldView, view);
    }

    private void executeOp(long pos, SMROp smrOp) {
        LOG.debug("{}: Decided {}", self, smrOp);
        Action a = actions.get(state, smrOp.op);
        if (a == null) {
            LOG.error("No action for {}. This is weird...", smrOp.op);
            return;
        }
        a.initiate(smrOp.op, pos);
    }

    private void subCoreHandlers() {
        subscribe(decideHandler, rLog);
        subscribe(viewChangeHandler, rep);
        subscribe(opHandler, rep);
        subscribe(getHandler, store);
        subscribe(rangeHandler, store);
        subscribe(mopHandler, store);
        subscribe(snapshotHandler, store);
        subscribe(diffHandler, store);
        subscribe(infoHandler, rep);
    }

    private class Actions {

        private Map<Class<? extends CaracalOp>, Action> passive = new HashMap<Class<? extends CaracalOp>, Action>();
        private Map<Class<? extends CaracalOp>, Action> catchingUp = new HashMap<Class<? extends CaracalOp>, Action>();
        private Map<Class<? extends CaracalOp>, Action> active = new HashMap<Class<? extends CaracalOp>, Action>();

        public Action get(State s, CaracalOp op) {
            switch (s) {
                case PASSIVE:
                    return passive.get(op.getClass());
                case TRANSFERING:
                    return active.get(op.getClass());
                case CATCHING_UP:
                    return catchingUp.get(op.getClass());
                case ACTIVE:
                    return active.get(op.getClass());
            }
            return null;
        }

        public boolean contains(State s, CaracalOp op) {
            switch (s) {
                case PASSIVE:
                    return passive.containsKey(op.getClass());
                case TRANSFERING:
                    return active.containsKey(op.getClass());
                case CATCHING_UP:
                    return catchingUp.containsKey(op.getClass());
                case ACTIVE:
                    return active.containsKey(op.getClass());
            }
            return false;
        }

        /*
         * ACTIONS
         */
        {
            /*
             * PASSIVE
             */
            passive.put(CaracalOp.class, new Action<CaracalOp>() {
                @Override
                public void initiate(CaracalOp op, long pos) {
                    // ignore all ops in passive mode
                    LOG.debug("{}: Got an op {} in PASSIVE mode.", self, op);
                }

                @Override
                public StorageRequest prepareSnapshot(CaracalOp op) {
                    return null;
                }
            });
            /*
             * CATCHING_UP
             */
            catchingUp.put(GetRequest.class, new Action<GetRequest>() {
                @Override
                public void initiate(GetRequest op, long pos) {
                    /*
                     * Can't answer this during catch-up. Simply ignore it and
                     * let another replica reply.
                     */
                }

                @Override
                public StorageRequest prepareSnapshot(GetRequest op) {
                    return null;
                }
            });
            catchingUp.put(PutRequest.class, new Action<PutRequest>() {
                @Override
                public void initiate(PutRequest op, long pos) {
                    // Just reply. Will be applied when the next snapshot is taken.
                    trigger(new PutResponse(op.id, op.key), rep);
                }

                @Override
                public StorageRequest prepareSnapshot(PutRequest op) {
                    return null;
                }
            });
            catchingUp.put(RangeQuery.Request.class, new Action<RangeQuery.Request>() {
                @Override
                public void initiate(RangeQuery.Request op, long pos) {
                    /*
                     * Can't answer this during catch-up. Simply ignore it and
                     * let another replica reply.
                     */
                }

                @Override
                public StorageRequest prepareSnapshot(RangeQuery.Request op) {
                    return null;
                }
            });

            /*
             * ACTIVE
             */
            active.put(GetRequest.class, new Action<GetRequest>() {
                @Override
                public void initiate(GetRequest op, long pos) {
                    GetReq request = new GetReq(op.key);
                    request.setId(op.id);
                    trigger(request, store);
                }

                @Override
                public StorageRequest prepareSnapshot(GetRequest op) {
                    // Nothing to store for a GET
                    return null;
                }
            });
            active.put(PutRequest.class, new Action<PutRequest>() {
                @Override
                public void initiate(PutRequest op, long pos) {
                    Put request = new Put(op.key, op.data, versionId);
                    trigger(request, store);
                    trigger(new PutResponse(op.id, op.key), rep);
                    lastSnapshotId = pos;
                }

                @Override
                public StorageRequest prepareSnapshot(PutRequest op) {
                    return new Put(op.key, op.data, versionId);
                }
            });
            active.put(RangeQuery.Request.class, new Action<RangeQuery.Request>() {
                @Override
                public void initiate(RangeQuery.Request op, long pos) {
                    RangeReq request = new RangeReq(op.subRange, op.limitTracker, op.transFilter, op.action, versionId);
                    request.setId(op.id);
                    trigger(request, store);
                }

                @Override
                public StorageRequest prepareSnapshot(RangeQuery.Request op) {
                    // Nothing to store for a RangeReq
                    return null;
                }
            });
            active.put(MultiOpRequest.class, new Action<MultiOpRequest>() {

                @Override
                public void initiate(MultiOpRequest op, long pos) {
                    MultiOp.Req request = new MultiOp.Req(op.conditions, op.successPuts, op.failurePuts, versionId);
                    request.setId(op.id);
                    trigger(request, store);
                }

                @Override
                public StorageRequest prepareSnapshot(MultiOpRequest op) {
                    MultiOp.Req request = new MultiOp.Req(op.conditions, op.successPuts, op.failurePuts, versionId);
                    request.setId(op.id);
                    return request;
                }
            });
        }
    }

    private void transferDataMaybe(View oldView, View newView) {
        SortedSet<Address> responsible = new TreeSet<Address>();
        Address higher = oldView.members.ceiling(self);
        boolean last = higher.equals(self);
        Address lower = oldView.members.floor(self);
        boolean first = lower.equals(self);
        for (Address adr : newView.members) {
            if (!oldView.members.contains(adr) // it's new
                    && ((!first && (lower.compareTo(adr) < 0)) // it's between my predecessor (if exists)
                    && (self.compareTo(adr) > 0)) // and me
                    || (last // or I'm last in oldView
                    && (self.compareTo(adr) < 0))) { // and it's comes after me in newView
                responsible.add(adr);
            }
        }
        for (Address adr : responsible) {
            final Address dst = adr;
            UUID id = UUID.randomUUID(); // TODO there's certainly better ways...
            Map<String, Object> metadata = new HashMap<String, Object>();
            metadata.put("snapshotId", lastSnapshotId);
            metadata.put("versionId", versionId);
//            final Component sender = create(DataSender.class,
//                    new DataSenderInit(id, init.range, self, dst,
//                            2 * init.keepAlivePeriod, metadata));
            final Component sender = create(DataSender.class,
                    new DataSenderInit(id, self, dst,
                            2 * init.keepAlivePeriod, metadata));

            //FIXME add data source
            connect(sender.getNegative(Network.class), net, new TransferFilter(id));
            connect(sender.getNegative(Timer.class), timer);
            connect(sender.getNegative(Store.class), store);
            final Handler<Completed> completedHandler = new Handler<Completed>() {
                @Override
                public void handle(Completed event) {
                    LOG.info("{}: Completed transfer to {}", self, dst);
                    unsubscribe(this, sender.getPositive(DataTransfer.class));
                    trigger(Stop.event, sender.control());
                    Handler<Stopped> cleanupHandler = new Handler<Stopped>() {
                        @Override
                        public void handle(Stopped event) {
                            disconnect(sender.getNegative(Network.class), net);
                            disconnect(sender.getNegative(Timer.class), timer);
                            disconnect(sender.getNegative(Store.class), store);
                            destroy(sender);
                        }
                    };
                    subscribe(cleanupHandler, sender.control());
                }
            };
            subscribe(completedHandler, sender.getPositive(DataTransfer.class));
            trigger(Start.event, sender.control());
        }

    }

    public static class SMROp extends Value {

        public final CaracalOp op;

        public SMROp(UUID id, CaracalOp op) {
            super(id);
            this.op = op;
        }

        @Override
        public int compareTo(Value o) {
            if (o instanceof SMROp) {
                SMROp that = (SMROp) o;
                int ids = this.op.id.compareTo(that.op.id);
                if (ids != 0) {
                    return ids;
                }
                return 0;
            }
            return super.baseCompareTo(o);
        }

        @Override
        public String toString() {
            return "SMROp(" + op.toString() + ")";
        }
    }

    public static class SyncedUp extends Value {

        public SyncedUp() {
            super(UUID.randomUUID());
        }

        /**
         * Serialization use only
         */
        SyncedUp(UUID id) {
            super(id);
        }

        @Override
        public int compareTo(Value o) {
            return super.baseCompareTo(o);
        }
    }

    public static class Scan extends Value {

        public final KeyRange range;

        public Scan(KeyRange range) {
            super(UUID.randomUUID());

            this.range = range;
        }

        /**
         * Serialization use only
         */
        Scan(UUID id, KeyRange range) {
            super(id);
            this.range = range;
        }

        @Override
        public int compareTo(Value o) {
            return super.baseCompareTo(o);
        }
    }
}
