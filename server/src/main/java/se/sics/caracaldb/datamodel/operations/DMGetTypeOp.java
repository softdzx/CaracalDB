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
package se.sics.caracaldb.datamodel.operations;

import se.sics.caracaldb.datamodel.operations.primitives.DMCRQOp;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import se.sics.caracaldb.Key;
import se.sics.caracaldb.KeyRange;
import se.sics.caracaldb.datamodel.msg.DMMessage;
import se.sics.caracaldb.datamodel.msg.GetType;
import se.sics.caracaldb.datamodel.util.ByteId;
import se.sics.caracaldb.datamodel.util.DMKeyFactory;
import se.sics.caracaldb.datamodel.util.TempTypeInfo;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class DMGetTypeOp extends DMSequentialOp {

    private final ByteId dbId;
    private final ByteId typeId;

    public DMGetTypeOp(long id, DMOperationsManager operationsMaster, ByteId dbId, ByteId typeId) {
        super(id, operationsMaster);
        this.dbId = dbId;
        this.typeId = typeId;
    }

    @Override
    public final void startHook() {
        LOG.debug("Operation {} - started", toString());

        KeyRange range;
        try {
            range = DMKeyFactory.getTMRange(dbId, typeId);
        } catch (IOException ex) {
            fail(DMMessage.ResponseCode.FAILURE);
            return;
        }
        pendingOp = new DMCRQOp(id, operationsManager, range);
        pendingOp.start();
    }

    @Override
    public void childFinished(long opId, DMOperation.Result result) {
        if(done) {
            LOG.warn("Operation {} - logical error", toString());
            return;
        }
        if (result instanceof DMCRQOp.Result) {
            if (result.responseCode.equals(DMMessage.ResponseCode.SUCCESS)) {
                TreeMap<Key, byte[]> b_results = ((DMCRQOp.Result) result).results;
                TempTypeInfo typeInfo = new TempTypeInfo(dbId, typeId);

                for (Map.Entry<Key, byte[]> e : b_results.entrySet()) {
                    try {
                        DMKeyFactory.DMKeyComponents comp = DMKeyFactory.getKeyComponents(e.getKey());
                        if (comp instanceof DMKeyFactory.TMFieldKeyComp) {
                            typeInfo.deserializeField(e.getValue());
                        } else {
                            fail(DMMessage.ResponseCode.FAILURE);
                            return;
                        }
                    } catch (IOException ex) {
                        fail(DMMessage.ResponseCode.FAILURE);
                        return;
                    }
                }
                fail(DMMessage.ResponseCode.SUCCESS);
                return;
            }
        }
        LOG.warn("Operation {} - received unknown child result {}", new Object[]{toString(), result});
        fail(DMMessage.ResponseCode.FAILURE);
    }

    //***** *****
    @Override
    public String toString() {
        return "DM_GET_TYPE " + id;
    }
    
    private void fail(DMMessage.ResponseCode respCode) {
        Result result = new Result(respCode, dbId, typeId, null);
        finish(result);
    }

    private void success(TempTypeInfo typeInfo) {
        Result result = new Result(DMMessage.ResponseCode.SUCCESS, dbId, typeId, typeInfo);
        finish(result);
    }

    public static class Result extends DMOperation.Result {
        public final ByteId dbId;
        public final ByteId typeId;
        public final TempTypeInfo typeInfo;

        public Result(DMMessage.ResponseCode respCode, ByteId dbId, ByteId typeId, TempTypeInfo typeInfo) {
            super(respCode);
            this.dbId = dbId;
            this.typeId = typeId;
            this.typeInfo = typeInfo;
        }

        @Override
        public DMMessage.Resp getMsg(long msgId) {
            return new GetType.Resp(msgId, responseCode, typeInfo);
        }
    }
}
