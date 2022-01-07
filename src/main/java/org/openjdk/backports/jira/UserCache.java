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
import io.atlassian.util.concurrent.Promise;
import org.openjdk.backports.census.Census;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserCache {
    private final UserRestClient client;
    private final Map<String, User> users;
    private final Map<String, RetryableUserPromise> userPromises;
    private final Map<String, String> displayNames;
    private final Map<String, String> affiliations;
    private List<String> censusIds;

    public UserCache(UserRestClient client) {
        this.client = client;
        this.users = new HashMap<>();
        this.userPromises = new HashMap<>();
        this.displayNames = new HashMap<>();
        this.affiliations = new HashMap<>();
    }

    public List<String> resolveCensus() {
        if (censusIds == null) {
            censusIds = Census.userIds();

            // Start async resolve for all users
            for (String uid : censusIds) {
                userPromises.put(uid, new RetryableUserPromise(client, uid));
            }
        }
        return censusIds;
    }

    private User lookupByEmail(String email) {
        for (String uid : resolveCensus()) {
            User u = getUser(uid);
            if (u != null && u.getEmailAddress().equalsIgnoreCase(email)) {
                return u;
            }
        }
        return null;
    }

    private User getUser(String id) {
        return users.computeIfAbsent(id, u -> {
            try {
                RetryableUserPromise p = userPromises.get(u);
                if (p == null) {
                    p = new RetryableUserPromise(client, u);
                    userPromises.put(u, p);
                }
                return p.claim();
            } catch (Exception e) {
                return null;
            }
        });
    }

    public String getDisplayName(String id) {
        return displayNames.computeIfAbsent(id, u -> {
            User user = getUser(u);
            if (user == null) {
                // No user with such User ID, try to fuzzy match the email
                user = lookupByEmail(u);
            }

            if (user != null) {
                return user.getDisplayName();
            } else {
                // No hits in Census.
                int email = u.indexOf("@");
                if (email != -1) {
                    // Looks like email, extract.
                    return u.substring(0, email);
                } else {
                    // No dice, report verbatim.
                    return u;
                }
            }
        });
    }

    public String getAffiliation(String id) {
        return affiliations.computeIfAbsent(id, u -> {
            User user = getUser(u);
            if (user == null) {
                // No user with such User ID, try to fuzzy match the email
                user = lookupByEmail(u);
            }

            if (user != null) {
                // Look up in Census succeeded, pick the email address.
                String email = user.getEmailAddress();
                return generifyAffiliation(user.getDisplayName(), email.substring(email.indexOf("@")));
            } else {
                // No hits in Census.
                int email = u.indexOf("@");
                if (email != -1) {
                    // Looks like email, extract.
                    return generifyAffiliation(u, u.substring(email));
                } else {
                    // No dice, report as unknown.
                    return "Unknown";
                }
            }
        });
    }

    static final String[] INDEPENDENTS = {
            "@wambold.com",
            "@yandex.ru",
            "@gmail.com",
            "@freenet.de",
            "@hollowman.ml",
            "@ludovic.dev",
            "@samersoff.net",
            "@volkhart.com",
            "@xlate.io",
            "@apache.org",
            "@users.noreply.github.com",
            "@outlook.com",
            "@j-kuhn.de",
            "@yahoo.com",
            "@ckozak.net",
            "@cs.oswego.edu",
            "@integralblue.com",
            "@gmx.de",
            "@gmx.at",
            "@gmx.ch",
            "@gmx.com",
            "@mail.ru",
            "@univ-mlv.fr",
            "@joda.org",
            "@pnnl.gov",
            "@comcast.net",
            "@thetaphi.de",
            "@haupz.de",
            "@duigou.org",
            "@bempel.fr",
            "@lagergren.net",
            "@zoulas.com",
            "@urma.com",
            "@reshnm.de",
            "@randahl.dk",
            "@reini.net",
            "@free.fr",
            "@xs4all.nl",
            "@tbee.org",
            "@tabjy.com",
            "@aaronbedra.com",
            "@carl.pro",
            "@gafter.com",
            "@jugs.org",
            "@jku.at",
            "@sterbenz.net",
            "@icloud.com",
            "@jonathangiles.net",
            "@ngmr",
            "@126.com",
            "@protonmail.com",
            "@headcrashing.eu",
            "@hotmail.com",
            "@udel.edu",
            "@javaspecialists.eu",
            "@selskabet.org",
            "@qq.com",
            "@progrm-jarvis.ru",
            "@jcornell.net",
            "@icus.se",
            "@prinzing.net",
            "@utexas.edu",
    };

    static final String[][] COMPANIES = {
            {"@oracle.com", "Oracle"},
            {"@redhat.com", "Red Hat"},
            {"@sap.com", "SAP"},
            {"@tencent.com", "Tencent"},
            {"@amazon.com", "Amazon"},
            {"@amazon.co.uk", "Amazon"},
            {"@huawei.com", "Huawei"},
            {"@bell-sw.com", "BellSoft"},
            {"@arm.com", "ARM"},
            {"@azul.com", "Azul"},
            {"@azulsystems.com", "Azul"},
            {"@intel.com", "Intel"},
            {"@microsoft.com", "Microsoft"},
            {"@alibaba-inc.com", "Alibaba"},
            {"@oss.nttdata.com", "NTT DATA"},
            {"@microdoc.com", "Microdoc"},
            {"@os.amperecomputing.com", "Ampere"},
            {"@datadoghq.com", "DataDog"},
            {"@google.com", "Google"},
            {"@skymatic.de", "Skymatic"},
            {"@gapfruit.com", "GapFruit"},
            {"@loongson.cn", "Loongson"},
            {"@tradingscreen.com", "TradingScreen"},
            {"@jetbrains.com", "JetBrains"},
            {"@twitter.com", "Twitter"},
            {"@apple.com", "Apple"},
            {"@sun.com", "Sun Microsystems"},
            {"@linaro.com", "Linaro"},
            {"@linaro.org", "Linaro"},
            {"@amd.com", "AMD"},
            {"@gluonhq.com", "Gluon"},
            {"@vmware.com", "VMWare"},
            {"@caviumnetworks.com", "Cavium"},
            {"@ubuntu.com", "Ubuntu"},
            {"@canonical.com", "Canonical"},
            {"@freebsd.org", "FreeBSD"},
            {"@suse.de", "SUSE"},
            {"@fujitsu.com", "Fujitsu"},
            {"@marvell.com", "Marvell"},
            {"@tagtraum.com", "Tagtraum"},
            {"@fb.com", "Facebook"},
            {"@dynatrace.com", "Dynatrace"},
    };

    static final String[][] SPECIAL_CASES = {
            {"Thomas Stuefe", "SAP"},
            {"Martin Buchholz", "Google"},
            {"Tagir Valeev", "JetBrains"},
            {"Volker Simonis", "Amazon"},
            {"Charles Nutter", "Red Hat"},
            {"Marcus Hirt", "DataDog"},
            {"John Paul Adrian Glaubitz", "Debian"},
            {"Lukas Eder", "DataGeekery"},
            {"Andrew Haley", "Red Hat"},
            {"Hamlin Li", "Huawei"},
            {"Wang Huang", "Huawei"},
    };

    private String generifyAffiliation(String full, String v) {
        // Pick a company first, if we can
        for (String[] kv : COMPANIES) {
            if (v.equals(kv[0])) {
                return kv[1];
            }
        }

        // Lots of prefixes here: @in.ibm.com, @linux.ibm.com etc.
        if (v.endsWith("ibm.com")) {
            return "IBM";
        }

        // Sometimes internal prefixes leak
        if (v.endsWith("oracle.com")) {
            return "Oracle";
        }

        // AWS leak
        if (v.endsWith("compute.internal")) {
            return "Amazon";
        }

        // Special cases for special people
        for (String[] kv : SPECIAL_CASES) {
            if (full.equals(kv[0])) {
                return kv[1];
            }
        }

        for (String ind : INDEPENDENTS) {
            if (v.equals(ind)) {
                return "Independent";
            }
        }

        return v;
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
