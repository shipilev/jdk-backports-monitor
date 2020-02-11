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
import org.openjdk.backports.jira.TrackedIssue;

import java.util.Date;

public class IssueReport extends AbstractIssueReport {

    private final String issueId;
    private final boolean doCSV;

    public IssueReport(JiraRestClient restClient, String hgRepos, boolean includeDownstream, String issueId, boolean doCSV) {
        super(restClient, hgRepos, includeDownstream);
        this.issueId = issueId;
        this.doCSV = doCSV;
    }

    @Override
    public void run() {
        out.println("ISSUE REPORT: " + issueId);
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows a single issue status.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        Issue issue = issueCli.getIssue(issueId).claim();

        TrackedIssue trackedIssue = parseIssue(issue);

        if (doCSV) {
            out.println(trackedIssue.getShortOutput());
        } else {
            printDelimiterLine(out);
            out.println(trackedIssue.getOutput());
        }
    }
}
