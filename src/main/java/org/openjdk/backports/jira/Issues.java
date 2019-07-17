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

import java.util.ArrayList;
import java.util.List;

public class Issues {

    private static final int PAGE_SIZE = 50;

    private final SearchRestClient searchCli;
    private final IssueRestClient issueCli;

    public Issues(SearchRestClient searchCli, IssueRestClient issueCli) {
        this.searchCli = searchCli;
        this.issueCli = issueCli;
    }

    public List<Issue> getIssues(String query) {
        List<Issue> basicIssues = getBasicIssues(query);

        List<RetryableIssuePromise> batch = new ArrayList<>();
        for (Issue i : basicIssues) {
            batch.add(new RetryableIssuePromise(issueCli, i.getKey()));
        }

        int count = 0;
        List<Issue> issues = new ArrayList<>();
        for (RetryableIssuePromise ip : batch) {
            issues.add(ip.claim());
            if ((++count % 50) == 0) {
                System.out.println("Resolved " + issues.size() + "/" + basicIssues.size() + " matching issues.");
            }
        }
        System.out.println("Resolved " + issues.size() + "/" + basicIssues.size() + " matching issues.");

        return issues;
    }

    public List<Issue> getParentIssues(String query) {
        List<Issue> basicIssues = getBasicIssues(query);

        List<RetryableIssuePromise> layer1 = new ArrayList<>();
        for (Issue i : basicIssues) {
            layer1.add(new RetryableIssuePromise(issueCli, i.getKey()));
        }

        int c1 = 0;
        List<RetryableIssuePromise> layer2 = new ArrayList<>();
        for (RetryableIssuePromise issue1 : layer1) {
            RetryableIssuePromise parent = Accessors.getParent(issueCli, issue1.claim());
            layer2.add(parent != null ? parent : issue1);
            if ((++c1 % 50) == 0) {
                System.out.println("Resolved " + layer2.size() + "/" + basicIssues.size() + " matching issues.");
            }
        }
        System.out.println("Resolved " + layer2.size() + "/" + basicIssues.size() + " matching issues.");

        int c2 = 0;
        List<Issue> issues = new ArrayList<>();
        for (RetryableIssuePromise issue2 : layer2) {
            issues.add(issue2.claim());
            if ((++c2 % 50) == 0) {
                System.out.println("Resolved parents for " + issues.size() + "/" + basicIssues.size() + " matching issues.");
            }
        }
        System.out.println("Resolved parents for " + issues.size() + "/" + basicIssues.size() + " matching issues.");

        return issues;
    }

    public List<Issue> getBasicIssues(String query) {
        List<Issue> issues = new ArrayList<>();

        System.out.println("JIRA Query: " + WordUtils.wrap(query, 80));
        System.out.println();

        SearchResult poll = new RetryableSearchPromise(searchCli, query, 1, 0).claim();
        int total = poll.getTotal();

        List<RetryableSearchPromise> searchPromises = new ArrayList<>();
        for (int cnt = 0; cnt < total; cnt += PAGE_SIZE) {
            searchPromises.add(new RetryableSearchPromise(searchCli, query, PAGE_SIZE, cnt));
            System.out.println("Acquiring page [" + cnt + ", " + (cnt + PAGE_SIZE) + "] (total: " + total + ")");
        }

        for (RetryableSearchPromise sp : searchPromises) {
            SearchResult found = sp.claim();
            for (Issue i : found.getIssues()) {
                issues.add(i);
            }
            System.out.println("Loaded " + issues.size() + "/" + total + " matching issues.");
        }

        return issues;
    }


}
