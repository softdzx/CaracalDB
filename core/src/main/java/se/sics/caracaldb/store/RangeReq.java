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
package se.sics.caracaldb.store;

import com.google.common.io.Closer;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import org.javatuples.Pair;
import se.sics.caracaldb.Key;
import se.sics.caracaldb.KeyRange;
import se.sics.caracaldb.persistence.Persistence;
import se.sics.caracaldb.persistence.StoreIterator;
import se.sics.caracaldb.store.Limit.LimitTracker;
import com.larskroll.common.ByteArrayRef;

/**
 *
 * @author Lars Kroll <lkroll@sics.se>
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class RangeReq extends StorageRequest {

    public final KeyRange range;
    private final TransformationFilter transFilter;
    private final LimitTracker limit;
    private final RangeAction action;
    private int maxVersionId = Ints.MAX_POWER_OF_TWO;
    private int actionVersionId;

    public RangeReq(KeyRange range, LimitTracker limit, TransformationFilter transFilter, RangeAction action, int actionVersionId) {
        this.range = range;
        if (limit != null) {
            this.limit = limit;
        } else {
            this.limit = Limit.noLimit();
        }
        if (transFilter != null) {
            this.transFilter = transFilter;
        } else {
            this.transFilter = TFFactory.noTF();
        }
        if (action != null) {
            this.action = action;
        } else {
            this.action = ActionFactory.noop();
        }
        this.actionVersionId = actionVersionId;
    }

    public void setMaxVersionId(int id) {
        this.maxVersionId = id;
    }

    public int getMaxVersionId() {
        return this.maxVersionId;
    }

    @Override
    public StorageResponse execute(Persistence store) throws IOException {
        TreeMap<Key, byte[]> results = new TreeMap<Key, byte[]>();

        long lengthDiff = 0;
        long keyNumDiff = 0;
        Diff diff = null;

        Closer closer = Closer.create();
        try {
            action.prepare(store);
            byte[] begin = range.begin.getArray();
            for (StoreIterator it = closer.register(store.iterator(begin)); it.hasNext(); it.next()) {
                byte[] key = it.peekKey();
                SortedMap<Integer, ByteArrayRef> oldVals = it.peekAllValues();
                ByteArrayRef oldVal = null;
                for (Entry<Integer, ByteArrayRef> e : oldVals.entrySet()) {
                    if (e.getKey() <= maxVersionId) {
                        oldVal = e.getValue();
                        break;
                    }
                }
                if (range.contains(key)) {
                    Pair<Boolean, ByteArrayRef> res = transFilter.execute(oldVal);
                    if (res.getValue0()) {
                        if (limit.read(res.getValue1())) {
                            results.put(new Key(key), res.getValue1().dereference());
                            long newSize = action.process(key, res.getValue1(), actionVersionId);
                            if (oldVal != null) {
                                if (newSize == 0) {
                                    lengthDiff -= oldVal.length;
                                    keyNumDiff--;
                                } else if (newSize > 0) {
                                    lengthDiff -= oldVal.length - newSize;
                                }
                            }
                            if (!limit.canRead()) {
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                } else {
                    //special case (a,b) and key is a
                    if (Key.compare(begin, key) != 0) {
                        break; // reached end of range
                    }
                }
            }
            action.commit();
            diff = new Diff(lengthDiff, keyNumDiff);
        } catch (Throwable e) {
            action.abort();
            closer.rethrow(e);
        } finally {
            closer.close();
        }
        return new RangeResp(this, results, !limit.canRead(), diff);
    }
}
