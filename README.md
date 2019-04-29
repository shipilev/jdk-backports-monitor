# JDK Backports Monitor

## Oh God, Why.

Because making sense of the complex JIRA reports is daunting, requires super-powers
and some institutional knowledge about the project, which even the long-timers are bound
to forget at times.

This project uses JIRA REST Client to query OpenJDK bug database and produce the reports 
related to JDK Updates work. Also, there are projects that are either not tracked in the
OpenJDK bugtracker consistently (for example, because they are not in mainline in some
releases), or are hosted somewhere else, so the tool is also able to parse Mercurial
repositories to cross-match the bug database with it.
 
This whole thing is really nothing more than a glorified shell script that 
collates data around the OpenJDK JIRA and Mercurial repos. Which is also why there are
hardly any tests, and there is this overall feeling of haphazardly put code. But hey,
you should have seen the Bash version I had before it.

## What Do I Need... Wait, Do I Really Need To Run It?

Many queries involve complex JQL statements, which requires authentication to execute. 
The tool expects the property file with your user/login for OpenJDK bug database
(comes with a glorious OpenJDK Author role, after some contributions):

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

       mvn clean install

Some interesting one-liners:

1) Print the backporting status for all issues with a given label: 

       java -jar target/jdk-backports-monitor.jar --label redhat-openjdk
       
2) Print the pushes stats for a given release

       java -jar target/jdk-backports-monitor.jar --pushes 11.0.3
       
3) List issues that were approved, but not yet pushed to a given release

       java -jar target/jdk-backports-monitor.jar --pending-push 11
 
4) Display the issues list for a given filter id
       
       java -jar target/jdk-backports-monitor.jar --filter 36456
       
5) Show the report for the individual issue (useful for monitor debugging)

       java -jar target/jdk-backports-monitor.jar --issue JDK-8217597

## I Am Anxious To Contribute!

You don't really want to. Read the source to feel despair. Email me if there are issues.