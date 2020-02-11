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

import org.openjdk.backports.Actions;

public class TrackedIssue implements Comparable<TrackedIssue> {
    final String output;
    final String shortOutput;
    final long age;
    final int priority;
    final String components;
    final Actions actions;

    public TrackedIssue(String output, String shortOutput, long age, int priority, String components, Actions actions) {
        this.output = output;
        this.shortOutput = shortOutput;
        this.age = age;
        this.priority = priority;
        this.components = components;
        this.actions = actions;
    }

    public String getOutput() {
        return output;
    }

    public String getShortOutput() {
        return shortOutput;
    }

    public Actions getActions() {
        return actions;
    }

    @Override
    public int compareTo(TrackedIssue other) {
        int v1 = other.actions.compareTo(actions);
        if (v1 != 0) {
            return v1;
        }
        int v2 = this.components.compareTo(other.components);
        if (v2 != 0) {
            return v2;
        }
        int v3 = Integer.compare(this.priority, other.priority);
        if (v3 != 0) {
            return v3;
        }
        int v4 = Long.compare(other.age, this.age);
        if (v4 != 0) {
            return v4;
        }
        return this.shortOutput.compareTo(other.shortOutput);
    }
}
