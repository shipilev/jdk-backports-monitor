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
import org.openjdk.backports.jira.Connect;
import org.openjdk.backports.report.*;

import java.io.FileInputStream;
import java.io.IOException;
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

                String user = p.getProperty("user");
                String pass = p.getProperty("pass");

                if (user == null || pass == null) {
                    throw new IllegalStateException("user/pass keys are missing in auth file: " + options.getAuthProps());
                }

                try (JiraRestClient cli = Connect.getJiraRestClient(JIRA_URL, user, pass)) {
                    if (options.getLabelReport() != null) {
                        new LabelReport(cli, options.getHgRepos(), options.includeDownstream(),
                                options.getLabelReport(), options.getMinLevel(), options.doCSV()).run();
                    }

                    if (options.getPendingPushReport() != null) {
                        new PendingPushReport(cli, options.getHgRepos(), options.includeDownstream(),
                                options.getPendingPushReport()).run();
                    }

                    if (options.getIssueReport() != null) {
                        new IssueReport(cli, options.getHgRepos(), options.includeDownstream(),
                                options.getIssueReport(), options.doCSV()).run();
                    }

                    if (options.getPushesReport() != null) {
                        new PushesReport(cli, options.directOnly(), options.getPushesReport()).run();
                    }

                    if (options.getReleaseNotesReport() != null) {
                        new ReleaseNotesReport(cli, options.getReleaseNotesReport()).run();
                    }

                    if (options.getFilterReport() != null) {
                        new FilterReport(cli, options.getFilterReport()).run();
                    }

                    if (options.getAffiliationReport() != null) {
                        new AffiliationReport(cli).run();
                    }

                    if (options.getParityReport() != null) {
                        new ParityReport(cli, options.getParityReport()).run();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
