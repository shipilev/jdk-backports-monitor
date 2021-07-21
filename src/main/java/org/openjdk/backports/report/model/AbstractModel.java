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

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import org.openjdk.backports.jira.Issues;
import org.openjdk.backports.jira.UserCache;
import org.openjdk.backports.report.Common;

import java.io.PrintStream;

abstract class AbstractModel extends Common {

    protected final SearchRestClient searchCli;
    protected final IssueRestClient issueCli;
    protected final PrintStream debugOut;
    protected final Issues jiraIssues;
    protected final UserCache users;

    public AbstractModel(JiraRestClient cli, PrintStream debugOut) {
        this.searchCli = cli.getSearchClient();
        this.issueCli = cli.getIssueClient();
        this.debugOut = debugOut;
        this.jiraIssues = new Issues(debugOut, searchCli, issueCli);
        this.users = new UserCache(cli.getUserClient());
    }
}
