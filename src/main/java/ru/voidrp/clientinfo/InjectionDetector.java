package ru.voidrp.clientinfo;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class InjectionDetector {

    /** Known cheat library name fragments (lowercase). */
    private static final Set<String> CHEAT_LIB_KEYWORDS = Set.of(
            "wurst", "sigma", "future", "liquidbounce", "meteor", "impact",
            "riseclient", "rusherhack", "wolfram", "aristois", "inertia",
            "killaura", "noclip", "xray", "blatant", "nocom", "entropy"
    );

    /** Scan the current JVM for signs of injection. Client-side only. */
    public static DetectionResult detect() {
        List<String> agents = detectJavaAgents();
        List<String> libs   = detectSuspiciousNativeLibraries();
        return new DetectionResult(agents, libs, !agents.isEmpty());
    }

    // ── Java agent detection ─────────────────────────────────────────────────

    private static List<String> detectJavaAgents() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(arg -> arg.startsWith("-javaagent:"))
                .map(arg -> {
                    // Strip "-javaagent:" prefix and any options after '='
                    String path = arg.substring("-javaagent:".length());
                    int eq = path.indexOf('=');
                    if (eq > 0) path = path.substring(0, eq);
                    // Return only the filename, not the full path
                    int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                    return slash >= 0 ? path.substring(slash + 1) : path;
                })
                .collect(Collectors.toList());
    }

    // ── Native library injection (Linux: /proc/self/maps) ───────────────────

    private static List<String> detectSuspiciousNativeLibraries() {
        List<String> suspicious = new ArrayList<>();
        // Linux: enumerate all mapped .so files
        try {
            if (!Files.exists(Paths.get("/proc/self/maps"))) return suspicious;
            Set<String> seen = new HashSet<>();
            for (String line : Files.readAllLines(Paths.get("/proc/self/maps"))) {
                if (!line.contains(".so")) continue;
                // /proc/self/maps format: addr perm ... path
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 6) continue;
                String path = parts[parts.length - 1];
                if (!path.startsWith("/")) continue;
                int slash = path.lastIndexOf('/');
                String filename = path.substring(slash + 1).toLowerCase();
                if (seen.contains(filename)) continue;
                seen.add(filename);
                for (String kw : CHEAT_LIB_KEYWORDS) {
                    if (filename.contains(kw)) {
                        suspicious.add(filename);
                        break;
                    }
                }
            }
        } catch (IOException ignored) {}
        return suspicious;
    }

    public record DetectionResult(
            List<String> javaAgents,
            List<String> suspiciousLibraries,
            boolean agentsDetected
    ) {}
}
