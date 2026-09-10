package dev.integrationlab;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import java.util.*;
import static dev.integrationlab.Models.*;

@RestController
@RequestMapping("/api")
public class Api {
 private final Store store;private final Runner runner;private final ScenarioValidator validator;private final TargetPolicy policy;private final String inbox;
 public Api(Store store,Runner runner,ScenarioValidator validator,TargetPolicy policy,@Value("${lab.inbox-token}")String inbox){
  this.store=store;this.runner=runner;this.validator=validator;this.policy=policy;this.inbox=inbox;
  if(inbox.isBlank())throw new IllegalArgumentException("Inbox token must not be blank");
 }
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","UP");}
 @GetMapping("/settings") public Map<String,Object> settings(){return Map.of("origins",policy.origins());}
 @GetMapping("/scenarios") public Object scenarios(){return store.scenarios();}
 @PostMapping("/scenarios") public Object save(@RequestBody Scenario scenario){validator.validate(scenario);return Map.of("id",store.save(null,scenario));}
 @PutMapping("/scenarios/{id}") public Object update(@PathVariable UUID id,@RequestBody Scenario scenario){store.scenario(id);validator.validate(scenario);return Map.of("id",store.save(id,scenario));}
 @GetMapping("/scenarios/{id}") public Object scenario(@PathVariable UUID id){return store.scenario(id);}
 @PostMapping("/scenarios/{id}/runs") @ResponseStatus(HttpStatus.ACCEPTED)
 public Object start(@PathVariable UUID id){return Map.of("id",runner.start(id));}
 @GetMapping("/runs") public Object runs(){return store.runs();}
 @GetMapping("/runs/{id}") public Object run(@PathVariable UUID id){return store.run(id);}
 @GetMapping(value="/runs/{id}/junit",produces=MediaType.APPLICATION_XML_VALUE)
 public String junit(@PathVariable UUID id){
  var run=store.run(id);String status=(String)run.get("status");
  if(status.equals("RUNNING"))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"Run is not finished");
  boolean pass=status.equals("PASSED");
  return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"Integration Lab\" tests=\"1\" failures=\""+(pass?0:1)+"\"><testcase name=\""+id+"\">"+
    (pass?"":"<failure message=\""+status+"\">"+escape(store.json(run.get("result")))+"</failure>")+"</testcase></testsuite>";
 }
 @GetMapping("/events")public Object events(){return store.events();}
 public record EventInput(String name,String body){}
 @PostMapping("/events")public Object event(@RequestBody EventInput e){return Map.of("id",store.event(e.name(),e.body()));}
 @PostMapping("/inbox/{token}") @ResponseStatus(HttpStatus.CREATED)
 public Object capture(@PathVariable String token,@RequestBody String body){
  if(!java.security.MessageDigest.isEqual(token.getBytes(java.nio.charset.StandardCharsets.UTF_8),inbox.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
   throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
  return Map.of("id",store.event("Captured webhook",body));
 }
 @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
 public Map<String,String> invalid(IllegalArgumentException e){return Map.of("message",e.getMessage()==null?"Invalid input":e.getMessage());}
 private String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
