package dev.integrationlab;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import static dev.integrationlab.Models.*;

@Component
public class ScenarioValidator {
 private final TargetPolicy policy;
 private final ObjectMapper mapper;
 public ScenarioValidator(TargetPolicy policy, ObjectMapper mapper) { this.policy=policy; this.mapper=mapper; }
 public void validate(Scenario s) {
  if (s == null || s.name()==null || s.name().isBlank() || s.name().length()>120)
   throw new IllegalArgumentException("Name must contain 1–120 characters");
  if (s.steps()==null || s.steps().isEmpty() || s.steps().size()>20)
   throw new IllegalArgumentException("Use 1–20 steps");
  int deliveries=0, delays=0;
  for (Step step:s.steps()) {
   if(step==null) throw new IllegalArgumentException("Step cannot be null");
   policy.resolve(s.target(), step.path().replace("{{runId}}","validation"));
   if(step.copies()<1 || step.copies()>10 || step.delayMs()<0 || step.delayMs()>5000)
    throw new IllegalArgumentException("Use 1–10 copies and delay 0–5000 ms");
   if(step.expectedStatus()<100 || step.expectedStatus()>599)
    throw new IllegalArgumentException("Invalid expected HTTP status");
   if(step.body()==null || step.body().length()>32000) throw new IllegalArgumentException("Body exceeds 32000 characters");
   deliveries+=step.copies(); delays+=step.delayMs();
   pointer(step.pointer(), step.expected());
  }
  if(deliveries>30 || delays>10000) throw new IllegalArgumentException("Maximum 30 deliveries and 10 seconds of delays");
  if(s.probe()!=null) {
   Probe p=s.probe();
   policy.resolve(s.target(), p.path().replace("{{runId}}","validation"));
   if(p.timeoutMs()<100 || p.timeoutMs()>10000 || p.expected()==null)
    throw new IllegalArgumentException("Probe requires expected value and timeout 100–10000 ms");
   pointer(p.pointer(), p.expected());
  }
 }
 private void pointer(String pointer, com.fasterxml.jackson.databind.JsonNode expected) {
  if(expected==null) return;
  try { mapper.createObjectNode().at(pointer==null?"":pointer); }
  catch(IllegalArgumentException e) { throw new IllegalArgumentException("Invalid JSON Pointer"); }
 }
}
