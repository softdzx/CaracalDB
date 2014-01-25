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
package se.sics.caracaldb.datamodel.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import se.sics.caracaldb.datamodel.util.gsonextra.ClientGetObjGson;
import se.sics.caracaldb.datamodel.util.gsonextra.ClientGetTypeGson;
import se.sics.caracaldb.datamodel.util.gsonextra.ClientPutObjGson;
import se.sics.caracaldb.datamodel.util.gsonextra.ClientQueryObjGson;

/**
 *
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class GsonHelper {

    private static Gson gson = null;

    public static Gson getGson() {
        if (gson == null) {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(TempTypeInfo.class, new TempTypeInfo.GsonTypeAdapter());
            gsonBuilder.registerTypeAdapter(TempObject.Value.class, new TempObject.Value.GsonTypeAdapter());
            gsonBuilder.registerTypeAdapter(TempObject.ValueHolder.class, new TempObject.ValueHolder.GsonTypeAdapter());
            gsonBuilder.registerTypeAdapter(ClientGetTypeGson.class, new ClientGetTypeGson.GsonTypeAdapter());
            gsonBuilder.registerTypeAdapter(ClientGetObjGson.class, new ClientGetObjGson.GsonTypeAdapter());
            gsonBuilder.registerTypeAdapter(ClientQueryObjGson.class, new ClientQueryObjGson.GsonTypeAdapter());
            gsonBuilder.registerTypeAdapter(ClientPutObjGson.class, new ClientPutObjGson.GsonTypeAdapter());
            gson = gsonBuilder.create();
        }
        return gson;
    }
}