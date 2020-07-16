package com.tersesystems.jvmsounds;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.sun.management.GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION;
import static com.sun.management.GarbageCollectionNotificationInfo.from;

/**
 * Print out some GC statistics.  Maybe we can make record scratches and notes on GC events.
 */
public class GCEventProducer {

    // MemoryPoolMXBean.getCollectionUsage() does not works with -XX:+UseG1GC -XX:+ExplicitGCInvokesConcurrent
    // https://bugs.openjdk.java.net/browse/JDK-8175375

    // G1 Old Gen MemoryPool CollectionUsage.used values don't reflect mixed GC results
    // https://bugs.openjdk.java.net/browse/JDK-8195115

    private final NotificationListener listener = new GCListener();
    private final Consumer<GcInfo> minorConsumer;
    private final Consumer<GcInfo> majorConsumer;

    public GCEventProducer(Consumer<GcInfo> minorConsumer, Consumer<GcInfo> majorConsumer) {
        this.minorConsumer = minorConsumer;
        this.majorConsumer = majorConsumer;
    }

    public void start() {
        List<GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcbean : gcbeans) {
            NotificationEmitter emitter = (NotificationEmitter) gcbean;
            emitter.addNotificationListener(listener, null, null);
        }
    }

    public void stop() {
        List<GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcbean : gcbeans) {
            NotificationEmitter emitter = (NotificationEmitter) gcbean;
            try {
                emitter.removeNotificationListener(listener);
            } catch (ListenerNotFoundException e) {
                // don't care
            }
        }
    }

    class GCListener implements NotificationListener {

        @Override
        public void handleNotification(Notification notification, Object handback) {
            if (notification.getType().equals(GARBAGE_COLLECTION_NOTIFICATION)) {
                //get the information associated with this notification
                GarbageCollectionNotificationInfo info = from((CompositeData) notification.getUserData());

                //prettyPrint(info);

                String gctype = info.getGcAction();
                if ("end of minor GC".equals(gctype)) {
                    minorConsumer.accept(info.getGcInfo());
                } else {
                    majorConsumer.accept(info.getGcInfo());
                }
            }
        }

        private void prettyPrint(GarbageCollectionNotificationInfo info) {
            //get all the info and pretty print it
            long duration = info.getGcInfo().getDuration();
            String gctype = info.getGcAction();
            if ("end of minor GC".equals(gctype)) {
                gctype = "Young Gen GC";
            } else if ("end of major GC".equals(gctype)) {
                gctype = "Old Gen GC";
            }
            System.out.println();
            System.out.println(gctype + ": - " + info.getGcInfo().getId() + " " + info.getGcName() + " (from " + info.getGcCause() + ") " + duration + " milliseconds; start-end times " + info.getGcInfo().getStartTime() + "-" + info.getGcInfo().getEndTime());

            prettyPrintMemorySpace(info);
        }

        private void prettyPrintMemorySpace(GarbageCollectionNotificationInfo info) {
            //Get the information about each memory space, and pretty print it
            Map<String, MemoryUsage> membefore = info.getGcInfo().getMemoryUsageBeforeGc();
            Map<String, MemoryUsage> mem = info.getGcInfo().getMemoryUsageAfterGc();
            for (Map.Entry<String, MemoryUsage> entry : mem.entrySet()) {
                String name = entry.getKey();
                MemoryUsage memdetail = entry.getValue();
                long memInit = memdetail.getInit();
                long memCommitted = memdetail.getCommitted();
                long memMax = memdetail.getMax();
                long memUsed = memdetail.getUsed();
                MemoryUsage before = membefore.get(name);
                long used = before.getUsed();
                if (before.getCommitted() > 0) {
                    long beforepercent = (used > 0) ? ((used * 1000L) / before.getCommitted()) : 0;
                    long percent = (memUsed > 0) ? ((memUsed * 1000L) / before.getCommitted()) : 0; //>100% when it gets expanded
                    System.out.print(name + (memCommitted == memMax ? "(fully expanded)" : "(still expandable)") + "used: " + (beforepercent / 10) + "." + (beforepercent % 10) + "%->" + (percent / 10) + "." + (percent % 10) + "%(" + ((memUsed / 1048576) + 1) + "MB) / ");
                }
            }
            System.out.println();
            long totalGcDuration = info.getGcInfo().getDuration();
            long percent = totalGcDuration * 1000L / info.getGcInfo().getEndTime();
            System.out.println("GC cumulated overhead " + (percent / 10) + "." + (percent % 10) + "%");
        }
    }

}