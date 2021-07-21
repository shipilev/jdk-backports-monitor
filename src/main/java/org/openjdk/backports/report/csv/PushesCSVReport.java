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
package org.openjdk.backports.report.csv;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.openjdk.backports.jira.Accessors;
import org.openjdk.backports.jira.UserCache;
import org.openjdk.backports.report.model.PushesModel;

import java.io.PrintStream;

public class PushesCSVReport extends AbstractCSVReport {

    private final PushesModel model;

    public PushesCSVReport(PushesModel model, PrintStream debugLog, String logPrefix) {
        super(debugLog, logPrefix);
        this.model = model;
    }

    protected void doGenerate(PrintStream out) {
        UserCache users = model.users();

        for (Issue i : model.byTime()) {
            String pushUser = Accessors.getPushUser(i);
            out.printf("\"%s\", \"%s\", \"%s\", \"%s\"%n",
                    i.getKey(),
                    i.getSummary(),
                    users.getDisplayName(pushUser),
                    users.getAffiliation(pushUser));
        }
        for (Issue i : model.noChangesets()) {
            out.printf("\"%s\", \"%s\", \"N/A\", \"N/A\"%n",
                    i.getKey(),
                    i.getSummary());
        }
    }

}
