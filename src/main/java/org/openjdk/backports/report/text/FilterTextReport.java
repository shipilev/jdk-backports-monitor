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
package org.openjdk.backports.report.text;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.google.common.collect.Multimap;
import org.openjdk.backports.Main;
import org.openjdk.backports.report.model.FilterModel;

import java.io.PrintStream;
import java.util.Date;

public class FilterTextReport extends AbstractTextReport {

    private final FilterModel model;

    public FilterTextReport(FilterModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
    }

    @Override
    protected void doGenerate(PrintStream out) {
        out.println("FILTER REPORT");
        printMajorDelimiterLine(out);
        out.println();
        out.println("This report shows brief list of issues matching the filter.");
        out.println();
        out.println("Report generated: " + new Date());
        out.println();
        out.println("Filter: " + model.name());
        out.println("Filter URL: " + Main.JIRA_URL + "issues/?filter=" + model.filterId());
        out.println();
        out.println("Hint: Prefix bug IDs with " + Main.JIRA_URL + "browse/ to reach the relevant JIRA entry.");
        out.println();

        Multimap<String, Issue> byComponent = model.byComponent();
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
