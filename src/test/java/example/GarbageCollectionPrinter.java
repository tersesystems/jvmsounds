/**
 * License
 * -------
 * Written in 2020 by Will Sargent will@tersesystems.com
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
 * You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>
 */
package example;

import com.sun.management.GarbageCollectionNotificationInfo;

import java.lang.management.MemoryUsage;
import java.util.Map;

public class GarbageCollectionPrinter {

    public void printGarbageCollectionInfo(GarbageCollectionNotificationInfo info) {
        //get all the info and pretty print it
        long duration = info.getGcInfo().getDuration();
        String gctype = info.getGcAction();
        if ("end of minor GC".equals(gctype)) {
            gctype = "Young Gen GC";
        } else if ("end of major GC".equals(gctype)) {
            gctype = "Old Gen GC";
        }
        printGcType(info, duration, gctype);

        prettyPrintMemorySpace(info);
    }

    public void printGcType(GarbageCollectionNotificationInfo info, long duration, String gctype) {
        System.out.println();
        System.out.println(gctype + ": - " +
                info.getGcInfo().getId() +
                " " +
                info.getGcName() +
                " (from " +
                info.getGcCause() +
                ") " +
                duration +
                " milliseconds; start-end times " +
                info.getGcInfo().getStartTime() +
                "-" +
                info.getGcInfo().getEndTime());
    }

    public void prettyPrintMemorySpace(GarbageCollectionNotificationInfo info) {
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
                printMemorySpace(name, memCommitted, memMax, memUsed, beforepercent, percent);
            }
        }
        System.out.println();
        long totalGcDuration = info.getGcInfo().getDuration();
        long percent = totalGcDuration * 1000L / info.getGcInfo().getEndTime();
        System.out.println("GC cumulated overhead " + (percent / 10) + "." + (percent % 10) + "%");
    }

    public void printMemorySpace(String name, long memCommitted, long memMax, long memUsed, long beforepercent, long percent) {
        String s1 = memCommitted == memMax ? "(fully expanded)" : "(still expandable)";
        String s3 = "used: " +
                (beforepercent / 10) +
                "." +
                (beforepercent % 10) +
                "%->" +
                (percent / 10) +
                "." +
                (percent % 10) +
                "%(" +
                ((memUsed / 1048576) + 1) +
                "MB) / ";
        String s = name + s1 + s3;
        System.out.print(s);
    }
}
