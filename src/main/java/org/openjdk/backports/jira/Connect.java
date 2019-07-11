/*
 * Copyright (c) 2019, Red Hat, Inc. All rights reserved.
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

import com.atlassian.event.api.EventPublisher;
import com.atlassian.httpclient.apache.httpcomponents.DefaultHttpClientFactory;
import com.atlassian.httpclient.api.HttpClient;
import com.atlassian.httpclient.api.factory.HttpClientOptions;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClient;
import com.atlassian.jira.rest.client.internal.async.AtlassianHttpClientDecorator;
import com.atlassian.jira.rest.client.internal.async.DisposableHttpClient;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;
import com.atlassian.sal.api.executor.ThreadLocalContextManager;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Connect {

    public static JiraRestClient getJiraRestClient(String jiraURL, String user, String pass) throws URISyntaxException {
        final URI uri = new URI(jiraURL);

        DefaultHttpClientFactory factory = new DefaultHttpClientFactory(
                new MyEventPublisher(),
                new MyApplicationProperties(uri),
                new ThreadLocalContextManager() {
                    @Override public Object getThreadLocalContext() { return null; }
                    @Override public void setThreadLocalContext(Object context) {}
                    @Override public void clearThreadLocalContext() {}
                });

        HttpClientOptions opts = new HttpClientOptions();

        // All this mess is needed to change timeouts:
        opts.setRequestTimeout(2, TimeUnit.MINUTES);
        opts.setSocketTimeout(2, TimeUnit.MINUTES);

        HttpClient client = factory.create(opts);

        DisposableHttpClient dispClient = new AtlassianHttpClientDecorator(
                client,
                new BasicHttpAuthenticationHandler(user, pass)) {
                    @Override public void destroy() throws Exception { factory.dispose(client); }
        };

        return new AsynchronousJiraRestClient(uri, dispClient);
    }

    private static class MyEventPublisher implements EventPublisher {
        @Override public void publish(Object o) {}
        @Override public void register(Object o) {}
        @Override public void unregister(Object o) {}
        @Override public void unregisterAll() {}
    }

    @SuppressWarnings("deprecation")
    private static class MyApplicationProperties implements ApplicationProperties {
        private final String url;

        private MyApplicationProperties(URI uri) {
            this.url = uri.getPath();
        }

        @Override
        public String getBaseUrl() {
            return url;
        }

        @Override
        public String getBaseUrl(UrlMode mode) {
            return url;
        }

        @Override
        public String getDisplayName() {
            return "Atlassian JIRA Rest Java Client";
        }

        @Override
        public String getPlatformId() {
            return ApplicationProperties.PLATFORM_JIRA;
        }

        @Override
        public String getVersion() {
            return "4.0.0-custom";
        }

        @Override
        public Date getBuildDate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getBuildNumber() {
            return String.valueOf(0);
        }

        @Override
        public File getHomeDirectory() {
            return new File(".");
        }

        @Override
        public String getPropertyValue(final String s) {
            throw new UnsupportedOperationException();
        }
    }


}
