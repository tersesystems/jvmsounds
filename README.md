# JVM Sounds

This is a very simple demo class that plays the memory allocation rate as a sine tone, and plays minor and major GCs as percussion.

Please see [Logging vs Memory](https://tersesystems.com/blog/2020/07/09/logging-vs-memory/) and [the tweet](https://twitter.com/will_sargent/status/1281718634289573890).

## Java Agent

You can run this as a java agent, and it will play with defaults.  There are various options you can set.

* `-n napTimeMs`
* `-at allocThresholdMs` - default is 300 MB/second, below that it is silent
* `-av allocVolume 0.0 - 1.0` - volume of the allocation tone, default 0.1
* `-mv minorGCVolume 0.0 - 1.0` - volume of the minor GC event, default 0.6
* `-Mv mixedGCVolume 0.0 - 1.0` - volume of the mixed GC event, default 0.6
* `-ht hiccupThresholdMs` - default is 50 ms, below that no hiccup
* `-hv hiccupVolume 0.0 - 1.0` - volume of the hiccup, default 1.0
