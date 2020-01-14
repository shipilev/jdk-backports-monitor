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
import org.openjdk.backports.census.Census;
import org.openjdk.backports.hg.HgDB;

import java.util.Date;
import java.util.List;

public class AffiliationReport extends AbstractReport {

    public AffiliationReport(JiraRestClient restClient, HgDB hgDB, boolean includeDownstream) {
        super(restClient, hgDB, includeDownstream);
    }

    @Override
    public void run() {
        out.println("AFFILIATION REPORT");
        printMajorDelimiterLine(out);
        out.println();
        out.println("Report generated: " + new Date());
        out.println();

        List<String> userIds = Census.userIds();

        // Start async resolve
        for (String uid : userIds) {
            users.getUserAsync(uid);
        }

        // Get all data and compute column widths
        int maxUid = 0;
        for (String uid : userIds) {
            users.getDisplayName(uid);
            users.getAffiliation(uid);
            maxUid = Math.max(maxUid, uid.length());
        }

        int maxDisplayName = users.maxDisplayName();
        int maxAffiliation = users.maxAffiliation();
        for (String uid : userIds) {
            out.printf("%" + maxUid + "s, %" + maxDisplayName + "s, %" + maxAffiliation + "s%n",
                    uid, users.getDisplayName(uid), users.getAffiliation(uid));
        }
    }
}
