/*
 *
 * Copyright 2014 McEvoy Software Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.milton.http.annotated;

import io.milton.resource.CollectionResource;
import io.milton.resource.Resource;

import java.util.*;

/**
 * @author brad
 */
public class ResourceList extends ArrayList<CommonResource> {

    private static final long serialVersionUID = 1L;
    private final Map<String, CommonResource> map = new HashMap<>();

    public ResourceList() {
    }

    public ResourceList(AnnoResource[] array) {
        addAll(Arrays.asList(array));
    }

    public ResourceList(ResourceList copyFrom) {
        super(copyFrom);
    }

    public ResourceList getDirs() {
        ResourceList list = new ResourceList();
        for (CommonResource cr : this) {
            if (cr instanceof CollectionResource) {
                list.add(cr);
            }
        }
        return list;
    }

    public ResourceList getFiles() {
        ResourceList list = new ResourceList();
        for (CommonResource cr : this) {
            if (!(cr instanceof CollectionResource)) {
                list.add(cr);
            }
        }
        return list;
    }

    @Override
    public boolean add(CommonResource e) {
        if (e == null) {
            throw new NullPointerException("Attempt to add null node");
        }
        if (e.getName() == null) {
            throw new NullPointerException("Attempt to add resource with null name: " + e.getClass().getName());
        }
        map.put(e.getName(), e);
        return super.add(e);
    }

    /**
     * Just adds the elements in the given list to this list and returns list to
     * make it suitable for chaining and use from velocity
     *
     * @param otherList
     * @return
     */
    public ResourceList add(ResourceList otherList) {
        addAll(otherList);
        return this;
    }

    public CommonResource get(String name) {
        return map.get(name);
    }

    public Resource remove(String name) {
        CommonResource r = map.remove(name);
        if (r != null) {
            super.remove(r);
        }
        return r;
    }

    public boolean hasChild(String name) {
        return get(name) != null;
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof Resource) {
            Resource e = (Resource) o;
            map.remove(e.getName());
        }
        return super.remove(o);
    }

    @Override
    public CommonResource getFirst() {
        if (isEmpty()) {
            return null;
        }
        return this.get(0);
    }

    @Override
    public CommonResource getLast() {
        if (this.size() > 0) {
            return this.get(this.size() - 1);
        } else {
            return null;
        }
    }


    /**
     * Returns a new list where elements satisfy is(s)
     *
     * @param s
     * @return
     */
    public ResourceList ofType(String s) {
        ResourceList newList = new ResourceList(this);
        newList.removeIf(ct -> !ct.is(s));
        return newList;
    }



    public Map<String, ResourceList> getOfType() {
        return new ChildrenOfTypeMap(this);
    }

    /**
     * Returns the next item after the one given. If the given argument is null,
     * returns the first item in the list
     *
     * @param from
     * @return
     */
    public Resource next(Resource from) {
        if (from == null) {
            return getFirst();
        } else {
            boolean found = false;
            for (Resource r : this) {
                if (found) {
                    return r;
                }
                if (r == from) {
                    found = true;
                }
            }
            return null;
        }
    }



    public Map<String, CommonResource> getMap() {
        return map;
    }

}
