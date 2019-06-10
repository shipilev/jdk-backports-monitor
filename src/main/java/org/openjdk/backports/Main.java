/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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
package org.openjdk.backports;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClientFactory;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import org.openjdk.backports.hg.HgDB;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Properties;

public class Main {

    public static final String JIRA_URL = "https://bugs.openjdk.java.net/";

    public static void main(String[] args) {
        Options options = new Options(args);

        try {
            if (options.parse()) {
                Properties p = new Properties();
                try (FileInputStream fis = new FileInputStream(options.getAuthProps())){
                    p.load(fis);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                HgDB hgDB = new HgDB();
                if (options.getHgRepos() != null) {
                    hgDB.load(options.getHgRepos());
                }

                String user = p.getProperty("user");
                String pass = p.getProperty("pass");

                if (user == null || pass == null) {
                    throw new IllegalStateException("user/pass keys are missing in auth file: " + options.getAuthProps());
                }

                JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
                JiraRestClient restClient = null;

                try {
                    restClient = factory.createWithBasicHttpAuthentication(new URI(JIRA_URL), user, pass);

                    Monitor m = new Monitor(restClient, hgDB);

                    if (options.getLabelReport() != null) {
                        m.runLabelReport(restClient, options.getLabelReport(), options.getMinLevel());
                    }

                    if (options.getPushesReport() != null) {
                        m.runPushesReport(restClient, options.getPushesReport());
                    }

                    if (options.getPendingPushReport() != null) {
                        m.runPendingPushReport(restClient, options.getPendingPushReport());
                    }

                    if (options.getFilterReport() != null) {
                        m.runFilterReport(restClient, options.getFilterReport());
                    }

                    if (options.getIssueReport() != null) {
                        m.runIssueReport(restClient, options.getIssueReport());
                    }

                    if (options.getReleaseNotesReport() != null) {
                        m.runReleaseNotesReport(restClient, options.getReleaseNotesReport());
                    }
                } finally {
                    if (restClient != null) {
                        restClient.close();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
