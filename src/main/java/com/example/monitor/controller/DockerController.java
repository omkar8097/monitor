package com.example.monitor.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1/docker")
public class DockerController {

    private boolean isDockerAvailable = false;
    private final ConcurrentHashMap<String, Map<String, String>> mockContainers = new ConcurrentHashMap<>();
    private final List<Map<String, String>> mockImages = new ArrayList<>();
    private final AtomicLong containerIdGen = new AtomicLong(100);

    @PostConstruct
    public void init() {
        checkDockerAvailability();
        seedMockData();
    }

    private void checkDockerAvailability() {
        try {
            Process process = Runtime.getRuntime().exec("docker info");
            int exitCode = process.waitFor();
            isDockerAvailable = (exitCode == 0);
        } catch (Exception e) {
            isDockerAvailable = false;
        }
    }

    private void seedMockData() {
        // Pre-populate mock containers
        createMockContainer("mysql-db", "mysql:8.0", "Up 3 hours", "3 hours ago");
        createMockContainer("redis-cache", "redis:alpine", "Exited (0) 2 hours ago", "2 hours ago");
        createMockContainer("nginx-proxy", "nginx:latest", "Up 45 minutes", "45 minutes ago");
        createMockContainer("spring-app", "monitor-app:latest", "Exited (137) 10 minutes ago", "1 hour ago");

        // Pre-populate mock images
        createMockImage("mysql", "8.0", "sha256:d82e21a", "512 MB");
        createMockImage("redis", "alpine", "sha256:4a8c9b2", "32.4 MB");
        createMockImage("nginx", "latest", "sha256:2b9f3d8", "142 MB");
        createMockImage("monitor-app", "latest", "sha256:e9a0c12", "185 MB");
    }

    private void createMockContainer(String name, String image, String status, String runningFor) {
        String id = "c" + containerIdGen.getAndIncrement();
        Map<String, String> container = new LinkedHashMap<>();
        container.put("id", id);
        container.put("name", name);
        container.put("image", image);
        container.put("status", status);
        container.put("runningFor", runningFor);
        mockContainers.put(id, container);
    }

    private void createMockImage(String repo, String tag, String id, String size) {
        Map<String, String> img = new LinkedHashMap<>();
        img.put("repository", repo);
        img.put("tag", tag);
        img.put("id", id);
        img.put("size", size);
        mockImages.add(img);
    }

    @GetMapping("/status")
    public Map<String, Object> getDockerStatus() {
        checkDockerAvailability(); // Refresh status
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("available", isDockerAvailable);
        response.put("mode", isDockerAvailable ? "Production (Daemon Active)" : "Simulation Mode (Offline Fallback)");
        
        if (isDockerAvailable) {
            try {
                Process p = Runtime.getRuntime().exec("docker version --format \"{{.Server.Version}}\"");
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String version = reader.readLine();
                response.put("version", version != null ? version.trim() : "Unknown");
            } catch (Exception e) {
                response.put("version", "Error reading version");
            }
        } else {
            response.put("version", "Sim-Daemon v26.0.0");
        }
        return response;
    }

    @GetMapping("/containers")
    public List<Map<String, String>> getContainers() {
        if (!isDockerAvailable) {
            return new ArrayList<>(mockContainers.values());
        }

        List<Map<String, String>> list = new ArrayList<>();
        try {
            // Fetch running & stopped containers
            Process p = Runtime.getRuntime().exec("docker ps -a --format \"{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}|{{.RunningFor}}\"");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    Map<String, String> container = new LinkedHashMap<>();
                    container.put("id", parts[0]);
                    container.put("name", parts[1]);
                    container.put("image", parts[2]);
                    container.put("status", parts[3]);
                    container.put("runningFor", parts.length >= 5 ? parts[4] : "N/A");
                    list.add(container);
                }
            }
            p.waitFor();
        } catch (Exception e) {
            // Fallback to simulation if process execution fails
            return new ArrayList<>(mockContainers.values());
        }
        return list;
    }

    @PostMapping("/containers/{id}/start")
    public Map<String, Object> startContainer(@PathVariable String id) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        
        if (!isDockerAvailable) {
            Map<String, String> container = mockContainers.get(id);
            if (container != null) {
                container.put("status", "Up 1 second");
                response.put("status", "success");
                response.put("message", "Simulated container " + container.get("name") + " started.");
            } else {
                response.put("status", "error");
                response.put("message", "Container " + id + " not found.");
            }
            return response;
        }

        try {
            Process p = Runtime.getRuntime().exec("docker start " + id);
            int code = p.waitFor();
            if (code == 0) {
                response.put("status", "success");
            } else {
                response.put("status", "error");
                response.put("message", "Docker exit code: " + code);
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    @PostMapping("/containers/{id}/stop")
    public Map<String, Object> stopContainer(@PathVariable String id) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        
        if (!isDockerAvailable) {
            Map<String, String> container = mockContainers.get(id);
            if (container != null) {
                container.put("status", "Exited (0) 1 second ago");
                response.put("status", "success");
                response.put("message", "Simulated container " + container.get("name") + " stopped.");
            } else {
                response.put("status", "error");
                response.put("message", "Container " + id + " not found.");
            }
            return response;
        }

        try {
            Process p = Runtime.getRuntime().exec("docker stop " + id);
            int code = p.waitFor();
            if (code == 0) {
                response.put("status", "success");
            } else {
                response.put("status", "error");
                response.put("message", "Docker exit code: " + code);
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    @PostMapping("/containers/{id}/restart")
    public Map<String, Object> restartContainer(@PathVariable String id) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        
        if (!isDockerAvailable) {
            Map<String, String> container = mockContainers.get(id);
            if (container != null) {
                container.put("status", "Up 1 second (Restarted)");
                response.put("status", "success");
                response.put("message", "Simulated container " + container.get("name") + " restarted.");
            } else {
                response.put("status", "error");
                response.put("message", "Container " + id + " not found.");
            }
            return response;
        }

        try {
            Process p = Runtime.getRuntime().exec("docker restart " + id);
            int code = p.waitFor();
            if (code == 0) {
                response.put("status", "success");
            } else {
                response.put("status", "error");
                response.put("message", "Docker exit code: " + code);
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    @DeleteMapping("/containers/{id}")
    public Map<String, Object> removeContainer(@PathVariable String id) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        
        if (!isDockerAvailable) {
            Map<String, String> removed = mockContainers.remove(id);
            if (removed != null) {
                response.put("status", "success");
                response.put("message", "Simulated container " + removed.get("name") + " removed.");
            } else {
                response.put("status", "error");
                response.put("message", "Container " + id + " not found.");
            }
            return response;
        }

        try {
            // docker rm -f forces removal of running container
            Process p = Runtime.getRuntime().exec("docker rm -f " + id);
            int code = p.waitFor();
            if (code == 0) {
                response.put("status", "success");
            } else {
                response.put("status", "error");
                response.put("message", "Docker exit code: " + code);
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    @GetMapping("/images")
    public List<Map<String, String>> getImages() {
        if (!isDockerAvailable) {
            return mockImages;
        }

        List<Map<String, String>> list = new ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec("docker images --format \"{{.Repository}}|{{.Tag}}|{{.ID}}|{{.Size}}\"");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    Map<String, String> img = new LinkedHashMap<>();
                    img.put("repository", parts[0]);
                    img.put("tag", parts[1]);
                    img.put("id", parts[2]);
                    img.put("size", parts[3]);
                    list.add(img);
                }
            }
            p.waitFor();
        } catch (Exception e) {
            return mockImages;
        }
        return list;
    }

    @PostMapping("/images/pull")
    public Map<String, Object> pullImage(@RequestParam String name) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("image", name);
        
        if (!isDockerAvailable) {
            // Simulated pull
            String repo = name;
            String tag = "latest";
            if (name.contains(":")) {
                String[] split = name.split(":");
                repo = split[0];
                tag = split[1];
            }
            createMockImage(repo, tag, "sha256:" + Integer.toHexString(new Random().nextInt(1000000)), "120 MB");
            response.put("status", "success");
            response.put("message", "Simulated pulling image: " + name);
            return response;
        }

        try {
            // Run docker pull
            Process p = Runtime.getRuntime().exec("docker pull " + name);
            int code = p.waitFor();
            if (code == 0) {
                response.put("status", "success");
            } else {
                response.put("status", "error");
                response.put("message", "Docker exit code: " + code);
            }
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }
}
