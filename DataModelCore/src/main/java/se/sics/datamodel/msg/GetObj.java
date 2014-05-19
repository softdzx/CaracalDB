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
package se.sics.datamodel.msg;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import org.javatuples.Triplet;
import se.sics.datamodel.util.ByteId;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class GetObj {

    public static class Req extends DMMessage.Req {

        public final Triplet<ByteId, ByteId, ByteId> objId; //revert once support for serializing Tuples
        
        public Req(UUID id, Triplet<ByteId, ByteId, ByteId> objId) {
            super(id);
            this.objId = objId;
        }

        @Override
        public String toString() {
            return "DM_GET_GSONOBJ_REQ(" + id + ")";
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 79 * hash + Objects.hashCode(this.id);
            hash = 79 * hash + Objects.hashCode(this.objId);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Req other = (Req) obj;
            if (!Objects.equals(this.id, other.id)) {
                return false;
            }
            if (!Objects.equals(this.objId, other.objId)) {
                return false;
            }
            return true;
        }
    }

    public static class Resp extends DMMessage.Resp {
        public final Triplet<ByteId, ByteId, ByteId> objId; //revert once support for serializing Tuples
        
        public final byte[] value;

        public Resp(UUID id, DMMessage.ResponseCode opResult, Triplet<ByteId, ByteId, ByteId> objId, byte[] value) {
            super(id, opResult);
            this.objId = objId;
            this.value = value;
        }

        @Override
        public String toString() {
            return "DM_GET_GSONOBJ_RESP(" + id + ")";
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 53 * hash + Objects.hashCode(this.id);
            hash = 53 * hash + Objects.hashCode(this.respCode);
            hash = 53 * hash + Objects.hashCode(this.objId);
            hash = 53 * hash + Arrays.hashCode(this.value);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Resp other = (Resp) obj;
            if (!Objects.equals(this.id, other.id)) {
                return false;
            }
            if (this.respCode != other.respCode) {
                return false;
            }
            if (!Objects.equals(this.objId, other.objId)) {
                return false;
            }
            if (!Arrays.equals(this.value, other.value)) {
                return false;
            }
            return true;
        }
        
        
    }
}
