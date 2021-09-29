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

import org.openjdk.backports.hg.HgDB;
import org.openjdk.backports.jira.Clients;
import org.openjdk.backports.jira.Connect;
import org.openjdk.backports.report.csv.*;
import org.openjdk.backports.report.html.*;
import org.openjdk.backports.report.model.*;
import org.openjdk.backports.report.text.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
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

                try (Clients cli = Connect.getClients(JIRA_URL, user, pass)) {
                    PrintStream debugLog = System.out;
                    String logPrefix = options.getLogPrefix();

                    HgDB hgDB = new HgDB();
                    if (options.getHgRepos() != null) {
                        hgDB.load(options.getHgRepos());
                    }

                    if (options.getLabelReport() != null) {
                        LabelModel m = new LabelModel(cli, hgDB, debugLog, options.getMinLevel(), options.getLabelReport());
                        new LabelTextReport(m, debugLog, logPrefix).generate();
                        new LabelCSVReport (m, debugLog, logPrefix).generate();
                        new LabelHTMLReport(m, debugLog, logPrefix).generate();
                    }

                    if (options.getLabelHistoryReport() != null) {
                        LabelHistoryModel m = new LabelHistoryModel(cli, debugLog, options.getLabelHistoryReport());
                        new LabelHistoryTextReport(m, debugLog, logPrefix).generate();
                        new LabelHistoryCSVReport (m, debugLog, logPrefix).generate();
                        new LabelHistoryHTMLReport(m, debugLog, logPrefix).generate();
                    }

                    if (options.getPendingPushReport() != null) {
                        PendingPushModel m = new PendingPushModel(cli, hgDB, debugLog, options.getPendingPushReport());
                        new PendingPushTextReport(m, debugLog, logPrefix).generate();
                        new PendingPushCSVReport (m, debugLog, logPrefix).generate();
                        new PendingPushHTMLReport(m, debugLog, logPrefix).generate();
                    }

                    if (options.getIssueReport() != null) {
                        IssueModel m = new IssueModel(cli, hgDB, debugLog, options.getIssueReport());
                        new IssueTextReport(m, debugLog, logPrefix).generate();
                        new IssueCSVReport (m, debugLog, logPrefix).generate();
                        new IssueHTMLReport(m, debugLog, logPrefix).generate();
                    }

                    if (options.getPushesReport() != null) {
                        PushesModel m = new PushesModel(cli, debugLog, options.directOnly(), options.getPushesReport());
                        new PushesTextReport(m, debugLog, logPrefix).generate();
                        new PushesCSVReport (m, debugLog, logPrefix).generate();
                        new PushesHTMLReport(m, debugLog, logPrefix).generate();
                    }

                    if (options.getReleaseNotesReport() != null) {
                        ReleaseNotesModel m = new ReleaseNotesModel(cli, debugLog, options.includeCarryovers(), options.getReleaseNotesReport());
                        new ReleaseNotesTextReport(m, debugLog, logPrefix).generate();
                        // No CSV report for this, it is not supposed to be machine-readable
                        new ReleaseNotesHTMLReport(m, debugLog, logPrefix).generate();
                    }

                    if (options.getFilterReport() != null) {
                        FilterModel m = new FilterModel(cli, debugLog, options.getFilterReport());
                        new FilterTextReport(m, debugLog, logPrefix).generate();
                        new FilterCSVReport (m, debugLog, logPrefix).generate();
                        new FilterHTMLReport(m, debugLog, logPrefix).generate();
                    }

                    if (options.getAffiliationReport() != null) {
                        AffiliationModel m = new AffiliationModel(cli, debugLog);
                        new AffiliationTextReport(m, debugLog, logPrefix).generate();
                        new AffiliationCSVReport (m, debugLog, logPrefix).generate();
                        new AffiliationHTMLReport(m, debugLog, logPrefix).generate();
                    }

                    if (options.getParityReport() != null) {
                        ParityModel m = new ParityModel(cli, debugLog, options.getParityReport());
                        new ParityTextReport(m, debugLog, logPrefix).generate();
                        new ParityCSVReport (m, debugLog, logPrefix).generate();
                        new ParityHTMLReport(m, debugLog, logPrefix).generate();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
