/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.backports.jira;

import com.atlassian.httpclient.api.HttpClient;
import com.atlassian.httpclient.api.Response;
import com.atlassian.httpclient.api.ResponsePromise;
import org.json.JSONArray;

import javax.ws.rs.core.UriBuilder;
import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class RawRestClient {
    private final URI baseUri;
    private final HttpClient httpClient;

    public RawRestClient(URI uri, HttpClient httpClient) {
        this.baseUri = uri;
        this.httpClient = httpClient;
    }

    public Collection<String> remoteLinks(String key) {
        URI resolve = UriBuilder.fromUri(baseUri).path("/rest/api/latest/issue/" + key + "/remotelink").build(new Object[0]);
        ResponsePromise p = httpClient.newRequest(resolve).get();
        Response r = p.claim();

        if (!r.isSuccessful()) {
            return Collections.emptyList();
        }

        try (InputStream is = r.getEntityStream();
             InputStreamReader isr = new InputStreamReader(is);
             BufferedReader br = new BufferedReader(isr)) {

            Collection<String> links = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                JSONArray arr = new JSONArray(line);
                for (int a = 0; a < arr.length(); a++) {
                    String s = arr.getJSONObject(a).getJSONObject("object").getString("url");
                    links.add(s);
                }
            }
            return links;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
