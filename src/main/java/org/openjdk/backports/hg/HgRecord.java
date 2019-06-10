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
package org.openjdk.backports.hg;

public class HgRecord {
    final String repo;
    final String hash;
    final String[] synopsis;
    final String author;

    HgRecord(String repo, String hash, String synopsis, String author) {
        this.repo = repo;
        this.hash = hash;
        this.synopsis = synopsis.split("<br/> ");
        this.author = author;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HgRecord record = (HgRecord) o;

        if (!repo.equals(record.repo)) return false;
        return hash.equals(record.hash);
    }

    @Override
    public int hashCode() {
        int result = repo.hashCode();
        result = 31 * result + hash.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return repo + "/rev/" + hash;
    }

    public boolean synopsisStartsWith(String needle) {
        for (String syn : synopsis) {
            if (syn.startsWith(needle)) return true;
        }
        return false;
    }
}
