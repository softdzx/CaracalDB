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
import org.javatuples.Pair;
import se.sics.datamodel.util.ByteId;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class GetType {

    public static class Req extends DMMessage.Req {

        public final Pair<ByteId,ByteId> typeId; //<dbId, typeId>//fix later to serialize Tuples

        public Req(long id, Pair<ByteId, ByteId> typeId) {
            super(id);
            this.typeId = typeId;
        }

        @Override
        public String toString() {
            return "DM_GET_TYPE_REQ(" + id + ")";
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + (int) (this.id ^ (this.id >>> 32));
            hash = 97 * hash + Objects.hashCode(this.typeId);
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
            if (this.id != other.id) {
                return false;
            }
            if (!Objects.equals(this.typeId, other.typeId)) {
                return false;
            }
            return true;
        }
    }

    public static class Resp extends DMMessage.Resp {
        public final Pair<ByteId, ByteId> typeId;
        public final byte[] typeInfo;

        public Resp(long id, DMMessage.ResponseCode opResult, Pair<ByteId, ByteId> typeId, byte[] typeInfo) {
            super(id, opResult);
            this.typeId = typeId;
            this.typeInfo = typeInfo;
        }

        @Override
        public String toString() {
            return "DM_GET_TYPE_RESP(" + id + ")";
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 19 * hash + (int) (this.id ^ (this.id >>> 32));
            hash = 19 * hash + Objects.hashCode(this.respCode);
            hash = 19 * hash + Objects.hashCode(this.typeId);
            hash = 19 * hash + Arrays.hashCode(this.typeInfo);
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
            if (this.id != other.id) {
                return false;
            }
            if (this.respCode != other.respCode) {
                return false;
            }
            if (!Objects.equals(this.typeId, other.typeId)) {
                return false;
            }
            if (!Arrays.equals(this.typeInfo, other.typeInfo)) {
                return false;
            }
            return true;
        }
    }
}