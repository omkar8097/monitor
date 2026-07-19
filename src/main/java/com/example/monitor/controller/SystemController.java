package com.example.monitor.controller;

import com.example.monitor.filter.RequestCountFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
import java.util.Random;

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

    @GetMapping("/battery")
    public Map<String, Object> getBatteryStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        
        try {
            // PowerShell query to fetch battery properties (comma-separated output)
            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", 
                "-Command", 
                "$b = Get-CimInstance -ClassName Win32_Battery; if ($b) { echo \"$($b.EstimatedChargeRemaining),$($b.BatteryStatus),$($b.DesignCapacity),$($b.FullChargeCapacity),$($b.EstimatedRunTime)\" }"
            );
            Process process = pb.start();
            
            // Read output
            java.io.InputStream is = process.getInputStream();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            process.waitFor();
            
            String output = sb.toString().trim();
            if (output.isEmpty() || output.equals("null")) {
                response.put("present", false);
                response.put("message", "No physical battery detected (System is running on AC mains or in VM)");
                addMockBatteryData(response);
            } else {
                response.put("present", true);
                response.put("simulated", false);
                parsePowerShellOutput(output, response);
            }
        } catch (Exception e) {
            response.put("present", false);
            response.put("error", "Failed to query system battery status: " + e.getMessage());
            addMockBatteryData(response);
        }
        
        return response;
    }

    // --- Benchmarking Endpoints ---

    @PostMapping("/benchmark/cpu")
    public Map<String, Object> benchmarkCpu() {
        int cores = Runtime.getRuntime().availableProcessors();
        long startTime = System.nanoTime();
        
        // Use Java parallel stream utilizing the ForkJoin common pool (utilizing all available logical CPU cores)
        // to execute heavy prime calculations as a computation load.
        long primeCount = java.util.stream.LongStream.rangeClosed(2, 2_500_000)
                .parallel()
                .filter(SystemController::isPrime)
                .count();
        
        long endTime = System.nanoTime();
        double durationSec = (endTime - startTime) / 1_000_000_000.0;
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coresUsed", cores);
        result.put("primesCalculated", primeCount);
        result.put("durationSeconds", String.format("%.3f s", durationSec));
        
        // Calculate score (operations per second metric)
        double score = (2_500_000.0 / durationSec) / 1000.0;
        result.put("score", Math.round(score));
        return result;
    }

    @PostMapping("/benchmark/ram")
    public Map<String, Object> benchmarkRam() {
        int sizeBytes = 64 * 1024 * 1024; // 64 MB block
        byte[] array = new byte[sizeBytes];
        
        // Write benchmark
        long writeStart = System.nanoTime();
        for (int i = 0; i < sizeBytes; i++) {
            array[i] = (byte) (i % 256);
        }
        long writeEnd = System.nanoTime();
        double writeSec = (writeEnd - writeStart) / 1_000_000_000.0;
        double writeSpeedGbs = ((double) sizeBytes / (1024.0 * 1024.0 * 1024.0)) / writeSec;
        
        // Read benchmark (including checksum addition to prevent JVM optimization from eliding the read loop)
        long readStart = System.nanoTime();
        long checksum = 0;
        for (int i = 0; i < sizeBytes; i++) {
            checksum += array[i];
        }
        long readEnd = System.nanoTime();
        double readSec = (readEnd - readStart) / 1_000_000_000.0;
        double readSpeedGbs = ((double) sizeBytes / (1024.0 * 1024.0 * 1024.0)) / readSec;
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blockSizeMb", 64);
        result.put("writeSpeedGbs", String.format("%.2f GB/s", writeSpeedGbs));
        result.put("readSpeedGbs", String.format("%.2f GB/s", readSpeedGbs));
        result.put("checksum", checksum);
        
        return result;
    }

    @PostMapping("/benchmark/disk")
    public Map<String, Object> benchmarkDisk() {
        File tempFile = new File("temp_benchmark.bin");
        int sizeBytes = 32 * 1024 * 1024; // 32 MB file
        byte[] block = new byte[8192]; // 8 KB buffer block
        new Random().nextBytes(block);
        
        // Write Speed test
        long writeStart = System.nanoTime();
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
            int written = 0;
            while (written < sizeBytes) {
                fos.write(block);
                written += block.length;
            }
            // Sync forces all system file buffers to synchronize with the underlying physical storage medium
            fos.getFD().sync(); 
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Disk write benchmark failed: " + e.getMessage());
            return err;
        }
        long writeEnd = System.nanoTime();
        double writeSec = (writeEnd - writeStart) / 1_000_000_000.0;
        double writeSpeedMbs = ((double) sizeBytes / (1024.0 * 1024.0)) / writeSec;
        
        // Read Speed test
        long readStart = System.nanoTime();
        try (java.io.FileInputStream fis = new java.io.FileInputStream(tempFile)) {
            byte[] readBlock = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(readBlock)) != -1) {
                // Read operation
            }
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Disk read benchmark failed: " + e.getMessage());
            return err;
        }
        long readEnd = System.nanoTime();
        double readSec = (readEnd - readStart) / 1_000_000_000.0;
        double readSpeedMbs = ((double) sizeBytes / (1024.0 * 1024.0)) / readSec;
        
        // Clean up temp file
        if (tempFile.exists()) {
            tempFile.delete();
        }
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("testFileMb", 32);
        result.put("writeSpeedMbs", String.format("%.2f MB/s", writeSpeedMbs));
        result.put("readSpeedMbs", String.format("%.2f MB/s", readSpeedMbs));
        
        return result;
    }

    private static boolean isPrime(long n) {
        if (n <= 1) return false;
        if (n <= 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        for (long i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }

    private void parsePowerShellOutput(String output, Map<String, Object> response) {
        try {
            String[] parts = output.split(",");
            if (parts.length >= 4) {
                int charge = Integer.parseInt(parts[0].trim());
                int statusNum = Integer.parseInt(parts[1].trim());
                long design = parts[2].trim().isEmpty() || parts[2].trim().equals("null") ? 0 : Long.parseLong(parts[2].trim());
                long full = parts[3].trim().isEmpty() || parts[3].trim().equals("null") ? 0 : Long.parseLong(parts[3].trim());
                
                response.put("chargePercentage", charge);
                response.put("status", getBatteryStatusText(statusNum));
                response.put("designCapacityMwh", design);
                response.put("fullChargeCapacityMwh", full);
                response.put("chemistry", "Lithium Ion (Li-Ion)");
                
                if (design > 0 && full > 0) {
                    double health = ((double) full / design) * 100.0;
                    response.put("healthPercentage", Math.min(100, Math.round(health)));
                } else {
                    response.put("healthPercentage", 100);
                }

                if (parts.length >= 5 && !parts[4].trim().isEmpty() && !parts[4].trim().equals("null")) {
                    long runTimeMin = Long.parseLong(parts[4].trim());
                    // estimated runtime returns 71582788 minutes if fully charged and on AC
                    if (runTimeMin > 0 && runTimeMin < 100000) {
                        response.put("estimatedRunTimeMinutes", runTimeMin);
                        response.put("estimatedRunTimeFormatted", formatRunTime(runTimeMin));
                    } else {
                        response.put("estimatedRunTimeMinutes", -1);
                        response.put("estimatedRunTimeFormatted", "AC Power / Charged");
                    }
                } else {
                    response.put("estimatedRunTimeFormatted", "AC Power");
                }
            }
        } catch (Exception e) {
            response.put("parseError", "Failed to parse: " + e.getMessage());
            addMockBatteryData(response);
        }
    }

    private String getBatteryStatusText(int code) {
        switch (code) {
            case 1: return "Discharging";
            case 2: return "Unknown";
            case 3: return "Fully Charged";
            case 4: return "Low";
            case 5: return "Critical";
            case 6: return "Charging";
            case 7: return "Charging (High)";
            case 8: return "Charging (Low)";
            case 9: return "Charging (Critical)";
            case 10: return "Undefined";
            case 11: return "Partially Charged";
            default: return "AC Power Connected";
        }
    }

    private void addMockBatteryData(Map<String, Object> response) {
        response.put("simulated", true);
        response.put("chargePercentage", 85);
        response.put("status", "Discharging");
        response.put("statusDescription", "Running on Battery Power");
        response.put("healthPercentage", 94);
        response.put("designCapacityMwh", 57008);
        response.put("fullChargeCapacityMwh", 53587);
        response.put("remainingCapacityMwh", 45548);
        response.put("estimatedRunTimeMinutes", 185);
        response.put("estimatedRunTimeFormatted", "3h 5m");
        response.put("chemistry", "Lithium Ion (Li-Ion)");
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

    private String formatRunTime(long min) {
        if (min < 0) return "N/A";
        if (min < 60) return min + "m";
        long h = min / 60;
        long m = min % 60;
        return h + "h " + m + "m";
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
