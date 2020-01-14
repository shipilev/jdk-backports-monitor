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
package org.openjdk.backports.report;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.Version;
import org.openjdk.backports.Actionable;
import org.openjdk.backports.Actions;
import org.openjdk.backports.Main;
import org.openjdk.backports.StringUtils;
import org.openjdk.backports.hg.HgDB;
import org.openjdk.backports.hg.HgRecord;
import org.openjdk.backports.jira.*;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public abstract class AbstractReport {

    protected static final String MSG_NOT_AFFECTED = "Not affected";
    protected static final String MSG_BAKING = "WAITING for patch to bake a little";
    protected static final String MSG_MISSING = "MISSING";
    protected static final String MSG_MISSING_ORACLE = "MISSING (+ on Oracle backport list)";
    protected static final String MSG_APPROVED = "APPROVED";
    protected static final String MSG_WARNING = "WARNING";

    // Sort issues by synopsis, alphabetically. This would cluster similar issues
    // together, even when they are separated by large difference in IDs.
    protected static final Comparator<Issue> DEFAULT_ISSUE_SORT = Comparator.comparing(i -> i.getSummary().trim().toLowerCase());

    protected final UserCache users;
    protected final SearchRestClient searchCli;
    protected final IssueRestClient issueCli;
    protected final PrintStream out;
    protected final Issues jiraIssues;

    public AbstractReport(JiraRestClient restClient) {
        this.out = System.out;
        this.searchCli = restClient.getSearchClient();
        this.issueCli = restClient.getIssueClient();
        this.jiraIssues = new Issues(out, searchCli, issueCli);
        this.users = new UserCache(restClient.getUserClient());
    }

    public abstract void run();

    protected void printReleaseNotes(PrintStream ps, Collection<Issue> relNotes) {
        PrintWriter pw = new PrintWriter(ps);
        printReleaseNotes(pw, relNotes);
        pw.flush();
    }

    protected void printReleaseNotes(PrintWriter pw, Collection<Issue> relNotes) {
        Set<String> dup = new HashSet<>();
        for (Issue rn : relNotes) {
            String summary = StringUtils.leftPad(rn.getKey() + ": " + rn.getSummary().replaceFirst("Release Note: ", ""), 2);
            String descr = StringUtils.leftPad(StringUtils.rewrap(StringUtils.stripNull(rn.getDescription()), StringUtils.DEFAULT_WIDTH - 6), 6);
            if (dup.add(descr)) {
                pw.println(summary);
                pw.println();
                pw.println(descr);
                pw.println();
            }
        }
    }

    protected void printDelimiterLine(PrintStream pw) {
        pw.println(StringUtils.tabLine('-'));
    }

    protected void printMajorDelimiterLine(PrintStream pw) {
        pw.println(StringUtils.tabLine('='));
    }

}
