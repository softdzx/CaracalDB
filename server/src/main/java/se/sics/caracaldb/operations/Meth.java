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
package se.sics.caracaldb.operations;

import se.sics.caracaldb.Address;
import se.sics.caracaldb.KeyRange;
import se.sics.caracaldb.View;
import se.sics.kompics.Init;

/**
 *
 * @author Lars Kroll <lkroll@sics.se>
 */
public class Meth extends Init<MethCat> {

    public final Address self;
    public final KeyRange responsibility;
    public final View view;
    public final long statsPeriod;

    public Meth(Address self, KeyRange responsibility, View view, long statsPeriod) {
        this.self = self;
        this.responsibility = responsibility;
        this.view = view;
        this.statsPeriod = statsPeriod;
    }
}
