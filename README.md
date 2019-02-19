JDK Backports Monitor

This powers:
  https://builds.shipilev.net/backports-monitor/

Usage:

1) Print the backporting status for all issues with a given label: 

       java -jar target/jdk-backports-monitor.jar --label redhat-openjdk
       
2) Print the release logs for a given release

       java -jar target/jdk-backports-monitor.jar --release 11.0.3