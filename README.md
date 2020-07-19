# JVM Sounds

This is a very simple JVM library that plays JVM events as audio sounds.  
 
* Memory allocation rate as a sine tone
* Minor GC is a wood percussion
* Major GC is a noise hit 
* Hiccups (underlying JVM timejumps) are played as sample hiccups.

Please see [Logging vs Memory](https://tersesystems.com/blog/2020/07/09/logging-vs-memory/) and [the tweet](https://twitter.com/will_sargent/status/1281718634289573890).

## Library

The library has dependencies on jSyn and on `jvm-alloc-rate-meter` and `jvm-hiccup-meter`.  It is plain Java, no Scala library required.

```scala
libraryDependencies += "com.tersesystems.jvmsounds" % "jvmsounds" % "1.0-SNAPSHOT"
```

## Java Agent

You can run this as a java agent.  I like using [sbt-javaagent](https://github.com/sbt/sbt-javaagent).

```scala
javaAgents += "com.tersesystems.jvmsounds" % "jvmsounds" % "1.0-SNAPSHOT"
```

## Options

* `-n napTimeMs`
* `-at allocThresholdMs` - default is 300 MB/second, below that it is silent
* `-av allocVolume 0.0 - 1.0` - volume of the allocation tone, default 0.1
* `-mv minorGCVolume 0.0 - 1.0` - volume of the minor GC event, default 0.6
* `-Mv mixedGCVolume 0.0 - 1.0` - volume of the mixed GC event, default 0.6
* `-ht hiccupThresholdMs` - default is 50 ms, below that no hiccup
* `-hv hiccupVolume 0.0 - 1.0` - volume of the hiccup, default 1.0
