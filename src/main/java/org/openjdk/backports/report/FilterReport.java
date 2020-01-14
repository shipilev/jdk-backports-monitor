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
import com.atlassian.jira.rest.client.api.domain.Filter;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.openjdk.backports.Main;
import org.openjdk.backports.hg.HgDB;
import org.openjdk.backports.jira.Accessors;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class FilterReport extends AbstractReport {

    private final long filterId;

    public FilterReport(JiraRestClient restClient, long filterId) {
        super(restClient);
        this.filterId = filterId;
    }

    @Override
    public void run() {
        Filter filter = searchCli.getFilter(filterId).claim();

        out.println("FILTER REPORT");
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows brief list of issues matching the filter.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();
        out.println("Filter: " + filter.getName());
        out.println("Filter URL: " + Main.JIRA_URL + "issues/?filter=" + filterId);
        out.println();

        List<Issue> issues = jiraIssues.getBasicIssues(filter.getJql());

        out.println();
        out.println("Hint: Prefix bug IDs with " + Main.JIRA_URL + "browse/ to reach the relevant JIRA entry.");
        out.println();

        Collections.sort(issues, DEFAULT_ISSUE_SORT);

        Multimap<String, Issue> byComponent = TreeMultimap.create(String::compareTo, DEFAULT_ISSUE_SORT);

        for (Issue issue : issues) {
            byComponent.put(Accessors.extractComponents(issue), issue);
        }

        for (String component : byComponent.keySet()) {
            out.println(component + ":");
            for (Issue i : byComponent.get(component)) {
                out.println("  " + i.getKey() + ": " + i.getSummary());
            }
            out.println();
        }
        out.println();
    }
}
