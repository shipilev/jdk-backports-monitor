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
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.TreeMultimap;
import org.openjdk.backports.Actionable;
import org.openjdk.backports.hg.HgDB;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class LabelModel extends AbstractModel {

    private final String label;
    private final Actionable minLevel;
    private final List<IssueModel> models;
    private final HashMultimap<String, IssueModel> byComponent;
    private final Integer maxVersion;
    private final Integer minVersion;

    public LabelModel(JiraRestClient cli, HgDB hgDB, PrintStream debugOut, Actionable minLevel, String label) {
        super(cli, debugOut);
        this.label = label;
        this.minLevel = minLevel;

        List<Issue> found = jiraIssues.getIssues("labels = " + label +
                " AND (status in (Closed, Resolved))" +
                " AND (resolution not in (\"Won't Fix\", Duplicate, \"Cannot Reproduce\", \"Not an Issue\", Withdrawn, Other))" +
                " AND type != Backport",
                false);

        Comparator<IssueModel> comparator = Comparator
                .comparing(IssueModel::actions)
                .thenComparing(IssueModel::components)
                .thenComparing(IssueModel::priority)
                .thenComparing(Comparator.comparing(IssueModel::daysAgo).reversed())
                .thenComparing(IssueModel::issueKey);

        models = found.parallelStream()
                .map(i -> new IssueModel(cli, hgDB, debugOut, i))
                .filter(im -> im.actions().getActionable().ordinal() >= minLevel.ordinal())
                .sorted(comparator)
                .collect(Collectors.toList());

        byComponent = HashMultimap.create();
        for (IssueModel m : models) {
            byComponent.put(m.components(), m);
        }

        maxVersion = models.stream().map(IssueModel::fixVersion).filter(i -> i != -1).reduce(Integer.MIN_VALUE, Math::max);
        minVersion = models.stream().map(IssueModel::fixVersion).filter(i -> i != -1).reduce(Integer.MAX_VALUE, Math::min);
    }

    public List<IssueModel> issues() {
        return models;
    }

    public HashMultimap<String, IssueModel> byComponent() {
        return byComponent;
    }

    public String label() {
        return label;
    }

    public Actionable minLevel() {
        return minLevel;
    }

    public int maxVersion() {
        return maxVersion;
    }

    public int minVersion() {
        return minVersion;
    }
}
