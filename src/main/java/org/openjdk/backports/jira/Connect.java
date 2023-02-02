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
import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.auth.AnonymousAuthenticationHandler;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClient;
import com.atlassian.jira.rest.client.internal.async.AtlassianHttpClientDecorator;
import com.atlassian.jira.rest.client.internal.async.DisposableHttpClient;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;
import com.atlassian.sal.api.executor.ThreadLocalContextManager;
import org.openjdk.backports.Auth;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Connect {

    public static Clients getClients(String jiraURL, Auth auth, int maxConnections) throws URISyntaxException {
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
        opts.setLeaseTimeout(TimeUnit.MINUTES.toMillis(30));

        // And configure the cache as well. 5K x 100K = 500M cache "should be enough".
        opts.setMaxCacheEntries(5_000);
        opts.setMaxCacheObjectSize(100_000);

        // Make sure we do not overwhelm the target with too many concurrent
        // requests. Limiting the number of connections should do the trick,
        // assuming similar request rate per connection.
        opts.setMaxTotalConnections(maxConnections);

        // Make sure we have enough threads to process the requests.
        // Choose the most scalable executor availabe.
        opts.setCallbackExecutor(Executors.newWorkStealingPool());

        HttpClient client = factory.create(opts);

        AuthenticationHandler authHandler;
        if (auth.isAnonymous()) {
            authHandler = new AnonymousAuthenticationHandler();
        } else {
            authHandler = new BasicHttpAuthenticationHandler(auth.getUser(), auth.getPass());
        }
        DisposableHttpClient dispClient = new AtlassianHttpClientDecorator(client, authHandler) {
            @Override public void destroy() throws Exception { factory.dispose(client); }
        };

        return new Clients(
                new AsynchronousJiraRestClient(uri, dispClient),
                new RawRestClient(uri, dispClient)
        );
    }

    private static class MyEventPublisher implements EventPublisher {
        @Override public void publish(Object o) {}
        @Override public void register(Object o) {}
        @Override public void unregister(Object o) {}
        @Override public void unregisterAll() {}
    }

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
        public Optional<Path> getLocalHomeDirectory() {
            return Optional.empty();
        }

        @Override
        public Optional<Path> getSharedHomeDirectory() {
            return Optional.empty();
        }

        @Override
        public String getPropertyValue(final String s) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getApplicationFileEncoding() {
            return null;
        }
    }


}
