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
package se.sics.caracaldb.datamodel;

import se.sics.caracaldb.datamodel.msg.DMNetworkMessage;
import se.sics.caracaldb.datamodel.msg.DMMessage;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import se.sics.kompics.network.Network;

/**
 *
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class ServerInterface extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(ServerInterface.class);

    Positive<Network> network = requires(Network.class);
    Positive<DataModelPort> dataModel = requires(DataModelPort.class);

    private final Map<Long, Address> pendingRequests; //<reqId, src>
    private final Address self;

    public ServerInterface(ServerInterfaceInit init) {
        this.pendingRequests = new HashMap<Long, Address>();
        this.self = init.self;
        
        subscribe(networkRequestHandler, network);
        subscribe(localResponseHandler, dataModel);
    }

    Handler<DMNetworkMessage.Req> networkRequestHandler = new Handler<DMNetworkMessage.Req>() {
        @Override
        public void handle(DMNetworkMessage.Req netReq) {
            DMMessage.Req localReq = netReq.message;
            pendingRequests.put(localReq.id, netReq.getSource());

            LOG.debug("{}: received message {} from {}", new Object[]{self, localReq, netReq.getSource()});
            trigger(localReq, dataModel);
        }
    };

    Handler<DMMessage.Resp> localResponseHandler = new Handler<DMMessage.Resp>() {
        @Override
        public void handle(DMMessage.Resp localResp) {
            Address reqSrc = pendingRequests.remove(localResp.id);

            LOG.debug("{}: sent response {} to {}", new Object[]{self, localResp, reqSrc});
            trigger(new DMNetworkMessage.Resp(self, reqSrc, localResp), network);
        }
    };
}
