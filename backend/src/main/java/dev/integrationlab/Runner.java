package dev.integrationlab;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static dev.integrationlab.Models.*;

@Service
public class Runner {
 private final Store store;
 private final TargetPolicy policy;
 private final ScenarioValidator validator;
 private final ObjectMapper mapper;
 private final ExecutorService runs=Executors.newFixedThreadPool(2);
 private final ExecutorService deliveries=Executors.newFixedThreadPool(10);
 private final Semaphore capacity=new Semaphore(2);
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
    .followRedirects(HttpClient.Redirect.NEVER).build();
 public Runner(Store store,TargetPolicy policy,ScenarioValidator validator,ObjectMapper mapper){
  this.store=store;this.policy=policy;this.validator=validator;this.mapper=mapper;
 }
 @PostConstruct void recover(){store.interrupt();}
 @PreDestroy void close(){runs.shutdownNow();deliveries.shutdownNow();client.close();}
 public UUID start(UUID scenarioId){
  Scenario s=store.scenario(scenarioId);validator.validate(s);
  if(!capacity.tryAcquire())throw new org.springframework.web.server.ResponseStatusException(
      org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"Two runs are already active");
  UUID id=UUID.randomUUID();
  try{
   store.begin(id,scenarioId,s);
   runs.submit(()->{try{store.finish(id,execute(id,s));}finally{capacity.release();}});
  }catch(RuntimeException e){capacity.release();throw e;}
  return id;
 }
 Result execute(UUID id,Scenario s){
  long started=System.nanoTime();List<Delivery> results=new ArrayList<>();String probe="No business probe configured";
  try{
   for(int i=0;i<s.steps().size();i++){
    Step step=s.steps().get(i);Thread.sleep(step.delayMs());
    final int index=i+1;
    if(step.parallel()){
     List<Callable<Delivery>> tasks=new ArrayList<>();
     for(int j=1;j<=step.copies();j++){final int copy=j;tasks.add(()->send(id,s.target(),step,index,copy));}
     for(Future<Delivery> f:deliveries.invokeAll(tasks))results.add(f.get());
    }else for(int j=1;j<=step.copies();j++) results.add(send(id,s.target(),step,index,j));
   }
   boolean passed=results.stream().allMatch(Delivery::passed);
   if(s.probe()!=null){
    probe=probe(id,s.target(),s.probe());
    passed &= probe.equals("Business assertion passed");
   }
   return new Result(passed?"PASSED":"FAILED",results,probe,elapsed(started));
  }catch(InterruptedException e){
   Thread.currentThread().interrupt();return new Result("INTERRUPTED",results,"Run interrupted; delivery outcome may be unknown",elapsed(started));
  }catch(Exception e){
   return new Result("ERROR",results,"Executor failed: "+e.getClass().getSimpleName(),elapsed(started));
  }
 }
 private Delivery send(UUID id,String target,Step s,int index,int copy){
  long begin=System.nanoTime();
  try{
   HttpRequest request=HttpRequest.newBuilder(policy.resolve(target,expand(s.path(),id)))
    .timeout(Duration.ofSeconds(3)).header("Content-Type","application/json")
    .POST(HttpRequest.BodyPublishers.ofString(expand(s.body(),id))).build();
   var response=fetch(request);
   String mismatch=assertion(response.body(),s.pointer(),s.expected());
   boolean pass=response.status()==s.expectedStatus() && mismatch==null;
   String message=response.status()!=s.expectedStatus()?"Expected HTTP "+s.expectedStatus()+", received "+response.status():
       mismatch==null?"Assertions passed":mismatch;
   return new Delivery(index,copy,response.status(),elapsed(begin),pass,response.body(),message);
  }catch(Exception e){
   if(e instanceof InterruptedException)Thread.currentThread().interrupt();
   return new Delivery(index,copy,0,elapsed(begin),false,"","Delivery failed: "+e.getClass().getSimpleName());
  }
 }
 private String probe(UUID id,String target,Probe p)throws Exception{
  long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(p.timeoutMs());String last="No response";
  do{
   long remaining=Math.max(1,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));
   try{
    var request=HttpRequest.newBuilder(policy.resolve(target,expand(p.path(),id)))
     .timeout(Duration.ofMillis(Math.min(3000,remaining))).GET().build();
    var response=fetch(request);
    last=response.status()!=200?"Expected HTTP 200, received "+response.status():assertion(response.body(),p.pointer(),p.expected());
    if(last==null)return "Business assertion passed";
   }catch(InterruptedException e){throw e;}catch(Exception e){last=e.getClass().getSimpleName();}
   remaining=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());
   if(remaining>0)Thread.sleep(Math.min(200,remaining));
  }while(System.nanoTime()<deadline);
  return "Business assertion timed out: "+last;
 }
 String assertion(String body,String pointer,com.fasterxml.jackson.databind.JsonNode expected){
  if(expected==null)return null;
  try{
   var root=mapper.readTree(body);
   if(root==null)return "Response is not JSON";
   var actual=root.at(pointer==null?"":pointer);
   return expected.equals(actual)?null:"At "+pointer+": expected "+expected+", received "+actual;
  }catch(Exception e){return "Response is not valid JSON";}
 }
 private record Response(int status,String body){}
 private Response fetch(HttpRequest request)throws Exception{
  // The subscriber enforces a byte cap while reading, not after allocating the full body.
  var handler=(HttpResponse.BodyHandler<byte[]>) info -> new LimitedBodySubscriber(65536);
  var response=client.send(request,handler);
  return new Response(response.statusCode(),new String(response.body(),java.nio.charset.StandardCharsets.UTF_8));
 }
 private String expand(String text,UUID id){return text.replace("{{runId}}",id.toString());}
 private long elapsed(long start){return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);}
}
