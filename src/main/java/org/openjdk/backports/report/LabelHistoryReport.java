/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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
import com.atlassian.jira.rest.client.api.domain.ChangelogGroup;
import com.atlassian.jira.rest.client.api.domain.ChangelogItem;
import com.atlassian.jira.rest.client.api.domain.Issue;
import org.joda.time.DateTime;

import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class LabelHistoryReport extends AbstractReport {

    private final String label;

    public LabelHistoryReport(JiraRestClient restClient, String label) {
        super(restClient);
        this.label = label;
    }

    @Override
    public void run() {
        out.println("LABEL HISTORY REPORT: " + label);
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows when the given label was added.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        List<Issue> found = jiraIssues.getIssues("labels = " + label +
                " AND type != Backport", true);
        out.println();

        SortedSet<Record> set = new TreeSet<>();
        for (Issue i : found) {
            Record rd = findUpdate(i);
            if (rd != null) {
                set.add(rd);
            } else {
                out.println("Cannot find label data for " + i.getKey());
            }
        }

        for (Record r : set) {
            out.printf("%10s, %" + users.maxDisplayName() + "s, %s: %s%n",
                    r.date.toLocalDate().toString(), r.user, r.issue.getKey(), r.issue.getSummary());
        }
    }

    private Record findUpdate(Issue i) {
        // Look in the changelog first:
        Iterable<ChangelogGroup> cl = i.getChangelog();
        if (cl != null) {
            for (ChangelogGroup cg : cl) {
                for (ChangelogItem item : cg.getItems()) {
                    if (!item.getField().equals("labels")) continue;
                    boolean noFrom = item.getFromString() == null || !item.getFromString().contains(label);
                    boolean yesTo = item.getToString() != null && item.getToString().contains(label);
                    if (noFrom && yesTo) {
                        return new Record(users.getDisplayName(cg.getAuthor().getName()), cg.getCreated(), i);
                    }
                }
            }
        }

        // No hits in changelog? Maybe it was filed with the label right away:
        if (i.getLabels().contains(label)) {
            return new Record(users.getDisplayName(i.getReporter().getName()), i.getCreationDate(), i);
        }

        return null;
    }

    private static class Record implements Comparable<Record> {
        private final String user;
        private final DateTime date;
        private final Issue issue;

        private Record(String user, DateTime date, Issue issue) {
            this.user = user;
            this.date = date;
            this.issue = issue;
        }

        @Override
        public int compareTo(Record o) {
            int c1 = o.date.compareTo(date);
            if (c1 != 0) {
                return c1;
            }
            return issue.getKey().compareTo(o.issue.getKey());
        }
    }

}
