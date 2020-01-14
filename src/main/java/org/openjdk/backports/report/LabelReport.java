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
import org.openjdk.backports.Actionable;
import org.openjdk.backports.jira.TrackedIssue;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class LabelReport extends AbstractIssueReport {

    private final String label;
    private final Actionable minLevel;

    public LabelReport(JiraRestClient restClient, String hgRepos, boolean includeDownstream, String label, Actionable minLevel) {
        super(restClient, hgRepos, includeDownstream);
        this.label = label;
        this.minLevel = minLevel;
    }

    @Override
    public void run() {
        out.println("LABEL REPORT: " + label);
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows bugs with the given label, along with their backporting status.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();
        out.println("Minimal actionable level to display: " + minLevel);
        out.println();
        out.println("For actionable issues, search for these strings:");
        out.println("  \"" + MSG_MISSING + "\"");
        out.println("  \"" + MSG_APPROVED + "\"");
        out.println("  \"" + MSG_WARNING + "\"");
        out.println();
        out.println("For lingering issues, search for these strings:");
        out.println("  \"" + MSG_BAKING + "\"");
        out.println();

        List<Issue> found = jiraIssues.getIssues("labels = " + label +
                " AND (status in (Closed, Resolved))" +
                " AND (resolution not in (\"Won't Fix\", Duplicate, \"Cannot Reproduce\", \"Not an Issue\", Withdrawn, Other))" +
                " AND type != Backport");
        out.println();

        List<TrackedIssue> issues = found
                .parallelStream()
                .map(this::parseIssue)
                .filter(ti -> ti.getActions().getActionable().ordinal() >= minLevel.ordinal())
                .collect(Collectors.toList());

        Collections.sort(issues);

        printDelimiterLine(out);
        for (TrackedIssue i : issues) {
            out.println(i.getOutput());
            printDelimiterLine(out);
        }

        out.println();
        out.println("" + issues.size() + " issues shown.");
    }
}
