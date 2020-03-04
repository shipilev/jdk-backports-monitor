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
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.IssueLinkType;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.text.WordUtils;
import org.openjdk.backports.StringUtils;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Issues {

    private static final int PAGE_SIZE = 50;

    private final PrintStream out;
    private final SearchRestClient searchCli;
    private final IssueRestClient issueCli;
    private final Map<String, IssuePromise> issueCache;

    public Issues(PrintStream out, SearchRestClient searchCli, IssueRestClient issueCli) {
        this.out = out;
        this.searchCli = searchCli;
        this.issueCli = issueCli;
        this.issueCache = new ConcurrentHashMap<>();
    }

    public IssuePromise getIssue(String key) {
        return getIssue(key, false);
    }

    public IssuePromise getIssue(String key, boolean full) {
        IssuePromise i = issueCache.get(key);
        if (i != null) {
            return i;
        }

        return issueCache.computeIfAbsent(key,
                k -> new RetryableIssuePromise(this, issueCli, k, full));
    }

    void registerIssueCache(String key, Issue issue) {
        issueCache.put(key, new ResolvedIssuePromise(issue));
    }

    /**
     * Reply with basic issues for a given JIRA query.
     * Basic issues have only a few populated fields, and are much faster to acquire.
     *
     * @param query query
     * @return list of issues
     */
    public List<Issue> getBasicIssues(String query) {
        out.println("JIRA Query:");
        out.println(WordUtils.wrap(query, StringUtils.DEFAULT_WIDTH));
        out.println();

        SearchResult poll = new RetryableSearchPromise(searchCli, query, 1, 0).claim();
        int total = poll.getTotal();

        out.print("Acquiring pages (" + total + " total): ");
        List<RetryableSearchPromise> searchPromises = new ArrayList<>();
        for (int cnt = 0; cnt < total; cnt += PAGE_SIZE) {
            searchPromises.add(new RetryableSearchPromise(searchCli, query, PAGE_SIZE, cnt));
            out.print(".");
            out.flush();
        }
        out.println(" done");

        out.print("Loading issues (" + total + " total): ");
        List<Issue> issues = new ArrayList<>();
        for (RetryableSearchPromise sp : searchPromises) {
            for (Issue i : sp.claim().getIssues()) {
                issues.add(i);
            }
            out.print(".");
            out.flush();
        }
        out.println(" done");

        return issues;
    }

    /**
     * Reply with resolved issues for a given JIRA query.
     * Resolved issues have all their fields filled in.
     *
     * @param query query
     * @param full load all metadata
     * @return list of issues
     */
    public List<Issue> getIssues(String query, boolean full) {
        List<Issue> basicIssues = getBasicIssues(query);
        int total = basicIssues.size();

        List<IssuePromise> batch = new ArrayList<>();
        for (Issue i : basicIssues) {
            batch.add(getIssue(i.getKey(), full));
        }

        int count = 0;
        out.print("Resolving issues (" + total + " total): ");
        List<Issue> issues = new ArrayList<>();
        for (IssuePromise ip : batch) {
            issues.add(ip.claim());
            if ((++count % PAGE_SIZE) == 0) {
                out.print(".");
                out.flush();
            }
        }
        out.println(" done");

        return issues;
    }

    public IssuePromise getParent(Issue start) {
        for (IssueLink link : start.getIssueLinks()) {
            IssueLinkType type = link.getIssueLinkType();
            if (type.getName().equals("Backport") && type.getDirection() == IssueLinkType.Direction.INBOUND) {
                return getIssue(link.getTargetIssueKey());
            }
        }
        return null;
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

        List<IssuePromise> basicPromises = new ArrayList<>();
        for (Issue i : basicIssues) {
            basicPromises.add(getIssue(i.getKey()));
        }

        int c1 = 0;
        out.print("Resolving issues (" + totalSize + " total): ");
        List<IssuePromise> parentPromises = new ArrayList<>();
        for (IssuePromise ip : basicPromises) {
            IssuePromise parent = getParent(ip.claim());
            parentPromises.add(parent != null ? parent : ip);
            if ((++c1 % PAGE_SIZE) == 0) {
                out.print(".");
                out.flush();
            }
        }
        out.println(" done");

        int c2 = 0;
        out.print("Resolving parents (" + totalSize + " total): ");
        List<Issue> issues = new ArrayList<>();
        for (IssuePromise ip : parentPromises) {
            issues.add(ip.claim());
            if ((++c2 % PAGE_SIZE) == 0) {
                out.print(".");
                out.flush();
            }
        }
        out.println(" done");

        return issues;
    }

    public Multimap<Issue, Issue> getIssuesWithBackports(String query) {
        List<Issue> parents = getParentIssues(query);
        int totalSize = parents.size();

        Multimap<Issue, IssuePromise> promises = HashMultimap.create();
        for (Issue parent : parents) {
            if (parent.getIssueLinks() != null) {
                for (IssueLink link : parent.getIssueLinks()) {
                    if (link.getIssueLinkType().getName().equals("Backport")) {
                        String linkKey = link.getTargetIssueKey();
                        promises.put(parent, getIssue(linkKey));
                    }
                }
            }

        }

        int c1 = 0;
        out.print("Resolving backports (" + totalSize + " total): ");
        Multimap<Issue, Issue> result = HashMultimap.create();
        for (Issue parent : parents) {
            for (IssuePromise ip : promises.get(parent)) {
                result.put(parent, ip.claim());
            }
            if ((++c1 % PAGE_SIZE) == 0) {
                out.print(".");
                out.flush();
            }
        }
        out.println(" done");

        return result;
    }

    public List<Issue> getParentIssues(List<Issue> basics) {
        int c1 = 0;
        out.print("Resolving issues (" + basics.size() + " total): ");
        List<IssuePromise> parentPromises = new ArrayList<>();
        for (Issue ip : basics) {
            IssuePromise parent = getParent(ip);
            parentPromises.add(parent);
            if ((++c1 % PAGE_SIZE) == 0) {
                out.print(".");
                out.flush();
            }
        }
        out.println(" done");

        out.print("Resolving parents (" + basics.size() + " total): ");
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < parentPromises.size(); i++) {
            IssuePromise ip = parentPromises.get(i);
            if (ip != null) {
                issues.add(ip.claim());
            } else {
                issues.add(basics.get(i));
            }
            if ((i % PAGE_SIZE) == 0) {
                out.print(".");
                out.flush();
            }
        }
        out.println(" done");
        return issues;
    }

}
