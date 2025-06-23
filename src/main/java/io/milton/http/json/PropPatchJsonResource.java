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

import io.milton.http.Auth;
import io.milton.http.FileItem;
import io.milton.http.Range;
import io.milton.http.Request;
import io.milton.http.Request.Method;
import io.milton.http.Response.Status;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.http.webdav.PropFindResponse;
import io.milton.http.webdav.PropFindResponse.NameAndError;
import io.milton.resource.PostableResource;
import io.milton.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PropPatchJsonResource extends JsonResource implements PostableResource {
    private static final Logger log = LoggerFactory.getLogger(PropPatchJsonResource.class);
    private final Resource wrappedResource;
    private final JsonPropPatchHandler patchHandler;
    private final String encodedUrl;
    private PropFindResponse resp;

    public PropPatchJsonResource( Resource wrappedResource, JsonPropPatchHandler patchHandler, String encodedUrl ) {
        super( wrappedResource, Method.PROPPATCH.code, null );
        this.wrappedResource = wrappedResource;
        this.encodedUrl = encodedUrl;
        this.patchHandler = patchHandler;
    }

    public void sendContent( OutputStream out, Range range, Map<String, String> params, String contentType ) throws IOException, NotAuthorizedException {

    }

    @Override
    public boolean authorise( Request request, Method method, Auth auth ) {
        // leave authorisation to the proppatch processing
        return true;
    }



    public String processForm( Map<String, String> parameters, Map<String, FileItem> files ) throws BadRequestException, NotAuthorizedException, ConflictException {
        resp = patchHandler.process( wrappedResource, encodedUrl, parameters );
        return null;
    }

    @Override
    public Method applicableMethod() {
        return Method.PROPPATCH;
    }

}
