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
package org.openjdk.backports.report.html;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.TreeMultimap;
import org.openjdk.backports.report.model.IssueModel;
import org.openjdk.backports.report.model.LabelModel;

import java.io.PrintStream;
import java.util.Date;
import java.util.TreeSet;

public class LabelHTMLReport extends AbstractHTMLReport {

    private final LabelModel model;

    public LabelHTMLReport(LabelModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
    }

    @Override
    protected void doGenerate(PrintStream out) {
        out.println("<h1>LABEL REPORT: " + model.label() + "</h1>");
        out.println();
        out.println("<p>This report shows bugs with the given label, along with their backporting status.</p>");
        out.println();
        out.println("<p>Report generated: " + new Date() + "</p>");
        out.println();
        out.println("<p>Minimal actionable level to display: " + model.minLevel() + "</p>");
        out.println();

        HashMultimap<String, IssueModel> map = model.byComponent();
        for (String comp : new TreeSet<>(map.keySet())) {
            out.println("<h3>" + comp + "</h3>");

            out.println("<table>");
            out.println("<tr>");
            out.println("<th nowrap>sh/8</th>");
            int minV = model.minVersion();
            int maxV = model.maxVersion();
            for (int r = minV; r <= maxV; r++) {
                out.println("<th nowrap>" + r + "</th>");
            }
            out.println("<th nowrap>Bug</th>");
            out.println("<th nowrap>Synopsis</th>");
            out.println("</tr>");
            for (IssueModel im : map.get(comp)) {
                new IssueHTMLReport(im, debugLog, logPrefix).generateTableLine(out, minV, maxV);
            }
            out.println("</table>");

        }
    }

}
