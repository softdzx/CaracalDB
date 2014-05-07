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
package se.sics.caracaldb.flow;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import se.sics.kompics.Direct;
import se.sics.kompics.address.Address;

/**
 * The <code>ClearToSend</code> class.
 *
 * @author Lars Kroll <lkroll@sics.se>
 * @version $$
 *
 */
public class ClearToSend implements Direct.Response, Cloneable {

    private Address destination;
    private Address source;
    private int quota;
    private UUID flowId;
    private int clearId;

    public ClearToSend(Address src, Address dest, UUID flowId) {
        this.destination = dest;
        this.source = src;
        this.flowId = flowId;
    }

    /**
     * Used by Network implementation!
     *
     */
    void setQuota(int quota) {
        this.quota = quota;
    }

    /**
     * Used by Network implementation!
     *
     */
    void setClearId(int clearId) {
        this.clearId = clearId;
    }

    public Address getDestination() {
        return this.destination;
    }

    public Address getSource() {
        return this.source;
    }

    public int getQuota() {
        return this.quota;
    }

    public UUID getFlowId() {
        return this.flowId;
    }

    public int getClearId() {
        return this.clearId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ClearToSend(");
        sb.append(source.toString());
        sb.append(", ");
        sb.append(destination.toString());
        sb.append(", ");
        sb.append(quota);
        sb.append(", ");
        sb.append(flowId);
        //sb.append(", ");
        //sb.append(requestId);
        sb.append(')');
        return sb.toString();
    }

    /* (non-Javadoc)
     * @see se.sics.kompics.Response#clone()
     */
    @Override
    public final Object clone() throws CloneNotSupportedException {
        ClearToSend cts = (ClearToSend) super.clone();
        cts.destination = this.destination;
        cts.source = this.source;
        cts.quota = this.quota;
        cts.flowId = this.flowId;
        cts.clearId = this.clearId;
        return cts;
    }

    public DataMessage constructMessage(byte[] data) {
        return new DataMessage(flowId, clearId, data);
    }

    public DataMessage constructFinalMessage(byte[] data) {
        return new DataMessage(flowId, clearId, data, true);
    }
}