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

import com.atlassian.jira.rest.client.api.UserRestClient;
import com.atlassian.jira.rest.client.api.domain.User;

import java.util.HashMap;
import java.util.Map;

public class UserCache {
    private final UserRestClient client;
    private final Map<String, User> users;
    private final Map<String, String> displayNames;
    private final Map<String, String> affiliations;

    public UserCache(UserRestClient client) {
        this.client = client;
        this.users = new HashMap<>();
        this.displayNames = new HashMap<>();
        this.affiliations = new HashMap<>();
    }

    public User getUser(String id) {
        return users.computeIfAbsent(id, u -> client.getUser(u).claim());
    }

    public String getDisplayName(String id) {
        return displayNames.computeIfAbsent(id, u -> getUser(u).getDisplayName());
    }

    public String getAffiliation(String id) {
        return affiliations.computeIfAbsent(id, u -> {
            String email = getUser(u).getEmailAddress();
            return email.substring(email.indexOf("@"));
        });
    }

    public int maxAffiliation() {
        int r = 0;
        for (String v : affiliations.values()) {
            r = Math.max(r, v.length());
        }
        return r;
    }

    public int maxDisplayName() {
        int r = 0;
        for (String v : displayNames.values()) {
            r = Math.max(r, v.length());
        }
        return r;
    }
}
