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
package org.openjdk.backports.report.model;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Filter;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.openjdk.backports.jira.Accessors;

import java.io.PrintStream;
import java.util.List;

public class FilterModel extends AbstractModel {

    private final long filterId;
    private final Multimap<String, Issue> byComponent;
    private final String name;
    private final List<Issue> issues;

    public FilterModel(JiraRestClient cli, PrintStream debugOut, long filterId) {
        super(cli, debugOut);
        this.filterId = filterId;

        Filter filter = searchCli.getFilter(filterId).claim();
        name = filter.getName();

        issues = jiraIssues.getBasicIssues(filter.getJql());
        issues.sort(DEFAULT_ISSUE_SORT);

        byComponent = TreeMultimap.create(String::compareTo, DEFAULT_ISSUE_SORT);
        for (Issue issue : issues) {
            byComponent.put(Accessors.extractComponents(issue), issue);
        }
    }

    public long filterId() {
        return filterId;
    }

    public Multimap<String, Issue> byComponent() {
        return byComponent;
    }

    public String name() {
        return name;
    }

    public List<Issue> issues() {
        return issues;
    }
}
