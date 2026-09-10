package dev.integrationlab;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TargetPolicy {
 private final Set<String> allowed;
 public TargetPolicy(@Value("${lab.allowed-origins}") String origins) {
  allowed = Arrays.stream(origins.split(",")).map(String::trim).collect(Collectors.toUnmodifiableSet());
 }
 public URI resolve(String origin, String path) {
  if (origin == null || !allowed.contains(origin)) throw new IllegalArgumentException("Target origin is not allowed");
  URI base = URI.create(origin);
  if (!Set.of("http","https").contains(base.getScheme()) || base.getHost() == null ||
      base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null ||
      !(base.getPath().isEmpty() || base.getPath().equals("/")))
   throw new IllegalArgumentException("Target must be an HTTP origin");
  if (path == null || !path.startsWith("/") || path.startsWith("//") ||
      path.contains("\\") || path.contains("#") || path.contains("\r") || path.contains("\n"))
   throw new IllegalArgumentException("Path must be a relative absolute-path without fragment");
  URI resolved = base.resolve(path);
  if (!base.getScheme().equals(resolved.getScheme()) || !base.getRawAuthority().equals(resolved.getRawAuthority()))
   throw new IllegalArgumentException("Path escapes target origin");
  return resolved;
 }
 public Set<String> origins() { return allowed; }
}
