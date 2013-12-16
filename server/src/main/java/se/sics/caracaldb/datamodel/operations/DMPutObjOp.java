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

import java.io.IOException;
import se.sics.caracaldb.Key;
import se.sics.caracaldb.datamodel.msg.DMMessage;
import se.sics.caracaldb.datamodel.msg.PutGsonObj;
import se.sics.caracaldb.datamodel.operations.primitives.DMPutOp;
import se.sics.caracaldb.datamodel.util.ByteId;
import se.sics.caracaldb.datamodel.util.DMKeyFactory;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */


public class DMPutObjOp extends DMSequentialOp {

    private final ByteId dbId;
    private final ByteId typeId;
    private final ByteId objId;
    private final byte[] value;

    public DMPutObjOp(long id, DMOperationsManager operationsMaster, ByteId dbId, ByteId typeId, ByteId objId, byte[] value) {
        super(id, operationsMaster);
        this.dbId = dbId;
        this.typeId = typeId;
        this.objId = objId;
        this.value = value;
    }

    //*****DMOperations*****
    @Override
    public final void startHook() {
        LOG.debug("Operation {} - started", toString());

        Key key;
        try {
            key = DMKeyFactory.getDataKey(dbId, typeId, objId);
        } catch (IOException ex) {
            fail(DMMessage.ResponseCode.FAILURE);
            return;
        }
        pendingOp = new DMPutOp(id, operationsManager, key, value);
        pendingOp.start();
    }

    @Override
    public void childFinished(long opId, DMOperation.Result result) {
        if(done) {
            LOG.warn("Operation {} - logical error", toString());
            return;
        }
        if (result instanceof DMPutOp.Result) {
            if (result.responseCode.equals(DMMessage.ResponseCode.SUCCESS)) {
                success();
                return;
            }
        }
        LOG.warn("Operation {} - received unknown child result {}", new Object[]{toString(), result});
        fail(DMMessage.ResponseCode.FAILURE);
    }

    //***** *****
    @Override
    public String toString() {
        return "DM_PUT_OBJ " + id;
    }
    
    private void fail(DMMessage.ResponseCode respCode) {
        Result result = new Result(respCode, dbId, typeId, objId);
        finish(result);
    }

    private void success() {
        Result result = new Result(DMMessage.ResponseCode.SUCCESS, dbId, typeId, objId);
        finish(result);
    }

    public static class Result extends DMOperation.Result {
        public final ByteId dbId;
        public final ByteId typeId;
        public final ByteId objId;
        
        public Result(DMMessage.ResponseCode respCode, ByteId dbId, ByteId typeId, ByteId objId) {
            super(respCode);
            this.dbId = dbId;
            this.typeId = typeId;
            this.objId = objId;
        }

        @Override
        public DMMessage.Resp getMsg(long msgId) {
            return new PutGsonObj.Resp(msgId, responseCode, dbId, typeId, objId);
        }
    }
}
