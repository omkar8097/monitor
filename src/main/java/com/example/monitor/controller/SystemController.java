package com.example.monitor.controller;

import com.example.monitor.filter.RequestCountFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @GetMapping("/status")
    public Map<String, Object> getSystemStatus() {
        Runtime runtime = Runtime.getRuntime();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        java.lang.management.OperatingSystemMXBean baseOsBean = ManagementFactory.getOperatingSystemMXBean();

        Map<String, Object> response = new LinkedHashMap<>();
        
        // General Info
        response.put("status", "UP");
        response.put("jvmVersion", System.getProperty("java.version"));
        response.put("jvmVendor", System.getProperty("java.vendor"));
        response.put("osName", System.getProperty("os.name"));
        response.put("osVersion", System.getProperty("os.version"));
        response.put("osArch", System.getProperty("os.arch"));
        response.put("availableProcessors", runtime.availableProcessors());

        // Uptime
        long uptimeMs = runtimeMXBean.getUptime();
        response.put("uptimeMs", uptimeMs);
        response.put("uptimeFormatted", formatUptime(uptimeMs));

        // 1. CPU Monitoring (System vs JVM Process)
        Map<String, Object> cpuMap = new LinkedHashMap<>();
        if (baseOsBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) baseOsBean;
            double systemCpu = osBean.getCpuLoad();
            double processCpu = osBean.getProcessCpuLoad();
            
            cpuMap.put("systemCpuLoad", systemCpu);
            cpuMap.put("processCpuLoad", processCpu);
            cpuMap.put("systemCpuPercentage", systemCpu >= 0 ? String.format("%.2f%%", systemCpu * 100) : "N/A");
            cpuMap.put("processCpuPercentage", processCpu >= 0 ? String.format("%.2f%%", processCpu * 100) : "N/A");
        } else {
            cpuMap.put("systemCpuPercentage", "N/A (Non-HotSpot JVM)");
            cpuMap.put("processCpuPercentage", "N/A (Non-HotSpot JVM)");
        }
        response.put("cpu", cpuMap);

        // 2. RAM Memory Monitoring (System Total/Free vs JVM Heap Usage)
        Map<String, Object> ramMap = new LinkedHashMap<>();
        
        // JVM Specific RAM usage
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        ramMap.put("jvmUsedMb", usedMemory / (1024 * 1024));
        ramMap.put("jvmAllocatedMb", totalMemory / (1024 * 1024));
        ramMap.put("jvmMaxMb", maxMemory / (1024 * 1024));
        ramMap.put("jvmUsagePercentage", String.format("%.2f%%", ((double) usedMemory / totalMemory) * 100));

        // System Physical RAM usage
        if (baseOsBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) baseOsBean;
            long totalPhysicalMemory = osBean.getTotalMemorySize();
            long freePhysicalMemory = osBean.getFreeMemorySize();
            long usedPhysicalMemory = totalPhysicalMemory - freePhysicalMemory;

            ramMap.put("systemTotalGb", String.format("%.2f GB", (double) totalPhysicalMemory / (1024.0 * 1024.0 * 1024.0)));
            ramMap.put("systemFreeGb", String.format("%.2f GB", (double) freePhysicalMemory / (1024.0 * 1024.0 * 1024.0)));
            ramMap.put("systemUsedGb", String.format("%.2f GB", (double) usedPhysicalMemory / (1024.0 * 1024.0 * 1024.0)));
            ramMap.put("systemUsagePercentage", String.format("%.2f%%", ((double) usedPhysicalMemory / totalPhysicalMemory) * 100));
        }
        response.put("ram", ramMap);

        // 3. Disk Space Monitoring
        Map<String, Object> diskMap = new LinkedHashMap<>();
        try {
            File root = new File(".");
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            long usableSpace = root.getUsableSpace();
            long usedSpace = totalSpace - freeSpace;

            diskMap.put("totalGb", String.format("%.2f GB", (double) totalSpace / (1024.0 * 1024.0 * 1024.0)));
            diskMap.put("freeGb", String.format("%.2f GB", (double) freeSpace / (1024.0 * 1024.0 * 1024.0)));
            diskMap.put("usableGb", String.format("%.2f GB", (double) usableSpace / (1024.0 * 1024.0 * 1024.0)));
            diskMap.put("usedGb", String.format("%.2f GB", (double) usedSpace / (1024.0 * 1024.0 * 1024.0)));
            diskMap.put("usagePercentage", totalSpace > 0 ? String.format("%.2f%%", ((double) usedSpace / totalSpace) * 100) : "0.00%");
        } catch (Exception e) {
            diskMap.put("error", "Failed to retrieve disk metrics: " + e.getMessage());
        }
        response.put("disk", diskMap);

        // Threads
        response.put("threadCount", threadMXBean.getThreadCount());
        response.put("peakThreadCount", threadMXBean.getPeakThreadCount());

        // Request Statistics
        response.put("totalRequestsHandled", RequestCountFilter.getRequestCount());

        return response;
    }

    @GetMapping("/cpu-details")
    public Map<String, Object> getCpuDetails() {
        java.lang.management.OperatingSystemMXBean baseOsBean = ManagementFactory.getOperatingSystemMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        Runtime runtime = Runtime.getRuntime();

        Map<String, Object> response = new LinkedHashMap<>();

        // Ensure thread CPU timing is enabled
        if (threadMXBean.isThreadCpuTimeSupported() && !threadMXBean.isThreadCpuTimeEnabled()) {
            threadMXBean.setThreadCpuTimeEnabled(true);
        }

        // Basic CPU & Core Stats
        response.put("availableProcessors", runtime.availableProcessors());
        response.put("systemLoadAverage", baseOsBean.getSystemLoadAverage());

        if (baseOsBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) baseOsBean;
            double systemCpu = osBean.getCpuLoad();
            double processCpu = osBean.getProcessCpuLoad();
            
            response.put("systemCpuLoad", systemCpu);
            response.put("processCpuLoad", processCpu);
            response.put("systemCpuPercentage", systemCpu >= 0 ? String.format("%.2f%%", systemCpu * 100) : "N/A");
            response.put("processCpuPercentage", processCpu >= 0 ? String.format("%.2f%%", processCpu * 100) : "N/A");
            response.put("processCpuTimeNs", osBean.getProcessCpuTime());
            response.put("processCpuTimeFormatted", formatCpuTime(osBean.getProcessCpuTime()));
        }

        // Detail list of threads and their CPU timing
        List<Map<String, Object>> threadsList = new ArrayList<>();
        long[] threadIds = threadMXBean.getAllThreadIds();
        
        for (long id : threadIds) {
            ThreadInfo info = threadMXBean.getThreadInfo(id);
            if (info == null) continue;

            Map<String, Object> threadData = new LinkedHashMap<>();
            threadData.put("id", id);
            threadData.put("name", info.getThreadName());
            threadData.put("state", info.getThreadState().toString());
            
            long cpuTimeNs = -1;
            long userTimeNs = -1;
            if (threadMXBean.isThreadCpuTimeEnabled()) {
                cpuTimeNs = threadMXBean.getThreadCpuTime(id);
                userTimeNs = threadMXBean.getThreadUserTime(id);
            }
            
            threadData.put("cpuTimeMs", cpuTimeNs >= 0 ? cpuTimeNs / 1_000_000.0 : -1);
            threadData.put("userTimeMs", userTimeNs >= 0 ? userTimeNs / 1_000_000.0 : -1);
            threadData.put("cpuTimeFormatted", cpuTimeNs >= 0 ? formatCpuTime(cpuTimeNs) : "N/A");
            
            threadsList.add(threadData);
        }
        
        response.put("threads", threadsList);
        return response;
    }

    private String formatCpuTime(long cpuTimeNs) {
        if (cpuTimeNs < 0) return "N/A";
        double ms = cpuTimeNs / 1_000_000.0;
        if (ms < 1000) {
            return String.format("%.2f ms", ms);
        }
        double sec = ms / 1000.0;
        if (sec < 60) {
            return String.format("%.2f s", sec);
        }
        double min = sec / 60.0;
        return String.format("%.2f m", min);
    }

    private String formatUptime(long uptimeMs) {
        long seconds = (uptimeMs / 1000) % 60;
        long minutes = (uptimeMs / (1000 * 60)) % 60;
        long hours = (uptimeMs / (1000 * 60 * 60)) % 24;
        long days = uptimeMs / (1000 * 60 * 60 * 24);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString();
    }
}
