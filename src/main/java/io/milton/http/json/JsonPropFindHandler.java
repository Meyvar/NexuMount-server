/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.milton.http.json;

import io.milton.http.values.ValueAndType;
import io.milton.http.webdav.PropFindPropertyBuilder;
import io.milton.http.webdav.PropFindResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.*;
import java.util.Map.Entry;

/**
 *
 * @author brad
 */
public class JsonPropFindHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonPropFindHandler.class);
    private final PropFindPropertyBuilder propertyBuilder;
    private final Helper helper;

    public JsonPropFindHandler(PropFindPropertyBuilder propertyBuilder) {
        this.propertyBuilder = propertyBuilder;
        helper = new Helper();
    }

    static class Helper {

        private List<Map<String, Object>> toMap(List<PropFindResponse> props, Map<QName, String> aliases) {
            List<Map<String, Object>> list = new ArrayList<>();
            Object val;
            for (PropFindResponse prop : props) {
                Map<String, Object> map = new HashMap<>();
                list.add(map);
                for (Entry<QName, ValueAndType> p : prop.getKnownProperties().entrySet()) {
                    String name = aliases.get(p.getKey());
                    if (name == null) {
                        name = p.getKey().getLocalPart();
                    }
                    val = p.getValue().getValue();
                    map.put(name, val);
                }
            }
            return list;
        }
    }
}
