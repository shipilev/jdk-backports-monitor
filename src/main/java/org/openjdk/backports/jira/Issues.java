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
package org.openjdk.backports.jira;

import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import org.apache.commons.lang3.text.WordUtils;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class Issues {

    private static final int PAGE_SIZE = 50;

    private final PrintStream out;
    private final SearchRestClient searchCli;
    private final IssueRestClient issueCli;

    public Issues(PrintStream out, SearchRestClient searchCli, IssueRestClient issueCli) {
        this.out = out;
        this.searchCli = searchCli;
        this.issueCli = issueCli;
    }

    /**
     * Reply with basic issues for a given JIRA query.
     * Basic issues have only a few populated fields, and are much faster to acquire.
     *
     * @param query query
     * @return list of issues
     */
    public List<Issue> getBasicIssues(String query) {
        out.println("JIRA Query: " + WordUtils.wrap(query, 80));
        out.println();

        SearchResult poll = new RetryableSearchPromise(searchCli, query, 1, 0).claim();
        int total = poll.getTotal();

        List<RetryableSearchPromise> searchPromises = new ArrayList<>();
        for (int cnt = 0; cnt < total; cnt += PAGE_SIZE) {
            out.println("Acquiring page [" + cnt + ", " + (cnt + PAGE_SIZE) + "] (total: " + total + ")");
            searchPromises.add(new RetryableSearchPromise(searchCli, query, PAGE_SIZE, cnt));
        }

        List<Issue> issues = new ArrayList<>();
        for (RetryableSearchPromise sp : searchPromises) {
            for (Issue i : sp.claim().getIssues()) {
                issues.add(i);
            }
            out.println("Loaded " + issues.size() + "/" + total + " matching issues.");
        }

        return issues;
    }

    /**
     * Reply with resolved issues for a given JIRA query.
     * Resolved issues have all their fields filled in.
     *
     * @param query query
     * @return list of issues
     */
    public List<Issue> getIssues(String query) {
        List<Issue> basicIssues = getBasicIssues(query);
        int total = basicIssues.size();

        List<RetryableIssuePromise> batch = new ArrayList<>();
        for (Issue i : basicIssues) {
            batch.add(new RetryableIssuePromise(issueCli, i.getKey()));
        }

        int count = 0;
        List<Issue> issues = new ArrayList<>();
        for (RetryableIssuePromise ip : batch) {
            issues.add(ip.claim());
            if ((++count % PAGE_SIZE) == 0) {
                out.println("Resolved " + issues.size() + "/" + total + " matching issues.");
            }
        }
        out.println("Resolved " + issues.size() + "/" + total + " matching issues.");

        return issues;
    }

    /**
     * Reply with parent issues for a given JIRA query.
     * For every issue that has a parent, its parent is returned. If issue has no
     * parents, the issue itself is replied.
     *
     * @param query query
     * @return list issues
     */
    public List<Issue> getParentIssues(String query) {
        List<Issue> basicIssues = getBasicIssues(query);
        int totalSize = basicIssues.size();

        List<RetryableIssuePromise> basicPromises = new ArrayList<>();
        for (Issue i : basicIssues) {
            basicPromises.add(new RetryableIssuePromise(issueCli, i.getKey()));
        }

        int c1 = 0;
        List<RetryableIssuePromise> parentPromises = new ArrayList<>();
        for (RetryableIssuePromise ip : basicPromises) {
            RetryableIssuePromise parent = Accessors.getParent(issueCli, ip.claim());
            parentPromises.add(parent != null ? parent : ip);
            if ((++c1 % PAGE_SIZE) == 0) {
                out.println("Resolved " + parentPromises.size() + "/" + totalSize + " matching issues.");
            }
        }
        out.println("Resolved " + parentPromises.size() + "/" + totalSize + " matching issues.");

        int c2 = 0;
        List<Issue> issues = new ArrayList<>();
        for (RetryableIssuePromise ip : parentPromises) {
            issues.add(ip.claim());
            if ((++c2 % PAGE_SIZE) == 0) {
                out.println("Resolved parents for " + issues.size() + "/" + totalSize + " matching issues.");
            }
        }
        out.println("Resolved parents for " + issues.size() + "/" + totalSize + " matching issues.");

        return issues;
    }

}
