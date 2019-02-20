/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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
package org.openjdk.backports;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.IOException;

public class Options {
    private final String[] args;
    private String authProps;
    private int maxIssues;
    private String labelReport;
    private String pushesReport;
    private String orphansReport;

    public Options(String[] args) {
        this.args = args;
    }

    public boolean parse() throws IOException {
        OptionParser parser = new OptionParser();

        OptionSpec<String> optAuthProps = parser.accepts("auth", "Property file with user/pass authentication pair")
                .withRequiredArg().ofType(String.class).describedAs("file").defaultsTo("auth.props");

        OptionSpec<Integer> optMaxIssues = parser.accepts("max", "Max issues to show")
                .withRequiredArg().ofType(Integer.class).describedAs("max").defaultsTo(1000);

        OptionSpec<String> optLabelReport = parser.accepts("label", "Report status of closed bugs for given label")
                .withRequiredArg().ofType(String.class).describedAs("tag");

        OptionSpec<String> optPushesReport = parser.accepts("pushes", "Report backport pushes by release")
                .withRequiredArg().ofType(String.class).describedAs("pushes");

        OptionSpec<String> optOrphansReport = parser.accepts("orphans", "Report backports that were approved, but not pushed")
                .withRequiredArg().ofType(String.class).describedAs("orphans");

        parser.accepts("h", "Print this help.");

        OptionSet set;
        try {
            set = parser.parse(args);
        } catch (OptionException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.err.println();
            parser.printHelpOn(System.err);
            return false;
        }

        if (set.has("h")) {
            parser.printHelpOn(System.out);
            return false;
        }

        maxIssues = optMaxIssues.value(set);
        authProps = optAuthProps.value(set);
        labelReport = optLabelReport.value(set);
        pushesReport = optPushesReport.value(set);
        orphansReport = optOrphansReport.value(set);

        return true;
    }

    public String getAuthProps() {
        return authProps;
    }

    public int getMaxIssues() {
        return maxIssues;
    }

    public String getLabelReport() {
        return labelReport;
    }

    public String getPushesReport() {
        return pushesReport;
    }

    public String getOrphansReport() {
        return orphansReport;
    }

}
