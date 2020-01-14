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
package org.openjdk.backports.report;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import org.openjdk.backports.hg.HgDB;
import org.openjdk.backports.jira.TrackedIssue;

import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class PendingPushReport extends AbstractIssueReport {

    private final String release;

    public PendingPushReport(JiraRestClient restClient, String hgRepos, boolean includeDownstream, String release) {
        super(restClient, hgRepos, includeDownstream);
        this.release = release;
    }

    @Override
    public void run() {
        out.println("PENDING PUSH REPORT: " + release);
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows backports that were approved, but not yet pushed.");
        out.println("Some of them are true orphans with original backport requesters never got sponsored.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        String query = "labels = jdk" + release + "u-fix-yes AND labels != openjdk-na AND fixVersion !~ '" + release + ".*'";

        switch (release) {
            case "8":
                query += " AND fixVersion !~ 'openjdk8u*'";
                query += " AND issue not in linked-subquery(\"issue in subquery(\\\"fixVersion ~ 'openjdk8u*' AND (status = Closed OR status = Resolved)\\\")\")";
                break;
            default:
                query += " AND issue not in linked-subquery(\"issue in subquery(\\\"fixVersion ~ '" + release + ".*' AND fixVersion !~ '*oracle' AND (status = Closed OR status = Resolved)\\\")\")";
        }

        List<Issue> found = jiraIssues.getIssues(query);

        SortedSet<TrackedIssue> issues = new TreeSet<>();
        for (Issue i : found) {
            issues.add(parseIssue(i));
        }

        out.println();
        printDelimiterLine(out);
        for (TrackedIssue i : issues) {
            out.println(i.getOutput());
            printDelimiterLine(out);
        }
    }
}
