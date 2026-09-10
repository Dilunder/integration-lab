package dev.integrationlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
public final class Models {
 private Models() {}
 public record Scenario(String name, String target, List<Step> steps, Probe probe) {}
 public record Step(String path, String body, int copies, boolean parallel, int delayMs,
                    int expectedStatus, String pointer, JsonNode expected) {}
 public record Probe(String path, String pointer, JsonNode expected, int timeoutMs) {}
 public record Delivery(int step, int copy, int status, long elapsedMs, boolean passed,
                        String body, String message) {}
 public record Result(String status, List<Delivery> deliveries, String probeMessage, long elapsedMs) {}
}
