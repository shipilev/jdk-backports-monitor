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

import org.openjdk.backports.jira.UserCache;
import org.openjdk.backports.report.model.AffiliationModel;

import java.io.PrintStream;
import java.util.Date;
import java.util.List;

public class AffiliationHTMLReport extends AbstractHTMLReport {

    private final AffiliationModel model;

    public AffiliationHTMLReport(AffiliationModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
    }

    @Override
    public void doGenerate(PrintStream out) {
        out.println("<h1>AFFILIATION REPORT</h1>");
        out.println("<p>Report generated: " + new Date() + "</p>");

        List<String> userIds = model.userIds();
        UserCache users = model.users();

        out.println("<table>");
        out.println("<tr>");
        out.println("<th nowrap>ID</th>");
        out.println("<th nowrap>Name</th>");
        out.println("<th nowrap>Organization</th>");
        out.println("</tr>");
        for (String uid : userIds) {
            out.println("<tr>");
            out.println("<td nowrap><a href='https://openjdk.org/census#" + uid + "'>" + uid + "</a></td>");
            out.println("<td nowrap>" + users.getDisplayName(uid) + "</td>");
            out.println("<td nowrap>" + users.getAffiliation(uid) + "</td>");
            out.println("</tr>");
        }
        out.println("</table>");
    }
}
