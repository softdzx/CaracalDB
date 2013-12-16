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
package se.sics.caracaldb.datamodel.operations.primitives;

import se.sics.caracaldb.Key;
import se.sics.caracaldb.datamodel.msg.DMMessage;
import se.sics.caracaldb.datamodel.operations.DMOperation;
import se.sics.caracaldb.datamodel.operations.DMOperationsManager;
import se.sics.caracaldb.operations.CaracalOp;
import se.sics.caracaldb.operations.CaracalResponse;
import se.sics.caracaldb.operations.GetRequest;
import se.sics.caracaldb.operations.GetResponse;
import se.sics.caracaldb.operations.ResponseCode;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class DMGetOp extends DMOperation {

    private final DMOperationsManager operationsManager;
    private final Key key;

    public DMGetOp(long id, DMOperationsManager operationsManager, Key key) {
        super(id);
        this.operationsManager = operationsManager;
        this.key = key;
        this.done = false;
    }

    //***** DMOperation *****
    @Override
    protected void startHook() {
        LOG.debug("Operation DM_GET {} - started", id);
        CaracalOp req = new GetRequest(id, key);
        operationsManager.send(id, req.id, req);
    }

    @Override
    public void handleMessage(CaracalResponse resp) {
        if(done) {
            LOG.debug("Operation {} - finished and will not process further messages", toString());
            operationsManager.droppedMessage(resp);
            return;
        }
        if (resp instanceof GetResponse) {
            if (resp.code.equals(ResponseCode.SUCCESS)) {
                success(((GetResponse)resp).data);
            } else {
                fail(DMMessage.ResponseCode.FAILURE);
            }
        } else {
            operationsManager.droppedMessage(resp);
        }
    }
    
    //***** *****
    @Override
    public String toString() {
        return "DM_GET " + id; 
    }
    
    private void finish(Result result) {
        LOG.debug("Operation {} - finished {}", new Object[]{toString(), result.responseCode});
        done = true;
        operationsManager.childFinished(id, result);
    }
    
    private void fail(DMMessage.ResponseCode respCode) {
        Result result = new Result(respCode, key, null);
        finish(result);
    }

    private void success(byte[] value) {
        Result result = new Result(DMMessage.ResponseCode.SUCCESS, key, value);
        finish(result);
    }

    
    public static class Result extends DMOperation.Result {
        public final Key key;
        public final byte[] value;
        
        public Result(DMMessage.ResponseCode respCode, Key key, byte[] value) {
            super(respCode);
            this.key = key;
            this.value = value;
        }

        @Override
        public DMMessage.Resp getMsg(long msgId) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
