# JDK Backports Monitor

## Oh God, Why.

Because making sense of the complex JIRA reports is daunting, requires super-powers
and some institutional knowledge about the project, which even the long-timers are bound
to forget at times.

This project uses JIRA REST Client to query OpenJDK bug database and produce the reports 
related to JDK Updates work. Also, there are projects that are either not tracked in the
OpenJDK bugtracker consistently (for example, because they are not in mainline for some
JDK trains), or are hosted somewhere else, so the tool is also able to parse Mercurial
repositories to cross-match the bug database with it.
 
This whole thing is really nothing more than a glorified shell script that collates data
from the OpenJDK JIRA, Mercurial and Git repos. Which is also why there are hardly any
tests, and there is this overall feeling of haphazardly put code. But hey, you should have
seen the Bash version I had before it.

## What Do I Need... Wait, Do I Really Need To Run It?

Many queries involve complex JQL statements, which requires authentication to execute. 
The tool expects the property file with your user/login for OpenJDK bug database
(comes with a glorious OpenJDK Author role, after some contributions).
By default, application uses a file "auth.props" with user/pass as authenticated method.
An empty auth.props file will switch to use anonymous authentication.

       $ cat auth.props
       user=duke
       pass=duke

Querying JIRA can take a while, especially for large reports. Not only it takes local time,
it also puts pressure on the remote JIRA instance. So, set up the CI job to generate this
once a day/week/month, and then stare at the result to your heart's content.

Generated reports from my CI jobs are here:
  https://builds.shipilev.net/backports-monitor/

## Right. Maybe I Will Just Use It A Little...

Build it like a usual Maven project:

       mvn clean verify

This would produce the runnable JAR:

       java -jar target/jdk-backports-monitor.jar -h

Some interesting one-liners:

1) Get the backporting status for all issues with a given label: 

       java -jar target/jdk-backports-monitor.jar --label redhat-openjdk
       
2) Get the pushes stats for a given release:

       java -jar target/jdk-backports-monitor.jar --pushes 11.0.3
       
3) Get the release notes for a given release:

       java -jar target/jdk-backports-monitor.jar --release-notes 11.0.3
       
4) Get the parity report for a given major JDK train: 

       java -jar target/jdk-backports-monitor.jar --parity 11
       
5) List issues that were approved, but not yet pushed to a given release:

       java -jar target/jdk-backports-monitor.jar --pending-push 11
 
6) List the issues list for a given filter id:
       
       java -jar target/jdk-backports-monitor.jar --filter 36456
       
7) Show the report for the individual issue (useful for monitor debugging):

       java -jar target/jdk-backports-monitor.jar --issue JDK-8217597

Most of these reports would generate TXT, HTML, CSV outputs at the same time.

## I Am Anxious To Contribute!

You can, but it would be easier if you describe the problem in the Issues first,
because there might be some weird thing that prevents fixing it. Or, feel free
to email me if there are issues.