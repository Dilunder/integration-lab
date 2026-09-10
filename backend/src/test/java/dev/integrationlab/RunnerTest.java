package dev.integrationlab;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static dev.integrationlab.Models.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunnerTest {
 HttpServer server;ExecutorService pool;Runner runner;String origin;
 ObjectMapper mapper=new ObjectMapper();AtomicInteger count;Set<String> orders;
 @BeforeEach void setUp()throws Exception{
  count=new AtomicInteger();orders=ConcurrentHashMap.newKeySet();
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  pool=Executors.newFixedThreadPool(12);server.setExecutor(pool);
  server.createContext("/pay",e->{
   var id=new String(e.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   orders.add(id);count.incrementAndGet();
   byte[] response="{\"accepted\":true}".getBytes();e.sendResponseHeaders(200,response.length);e.getResponseBody().write(response);e.close();
  });
  server.createContext("/orders",e->{
   byte[] response=("{\"count\":"+orders.size()+"}").getBytes();e.sendResponseHeaders(200,response.length);e.getResponseBody().write(response);e.close();
  });
  server.createContext("/large",e->{byte[] b=new byte[70000];e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
  server.createContext("/redirect",e->{e.getResponseHeaders().add("Location","/pay");e.sendResponseHeaders(302,-1);e.close();});
  server.start();origin="http://127.0.0.1:"+server.getAddress().getPort();
  var policy=new TargetPolicy(origin);runner=new Runner(mock(Store.class),policy,new ScenarioValidator(policy,mapper),mapper,new TargetHeaders("{}",mapper));
 }
 @AfterEach void close(){runner.close();server.stop(0);pool.shutdownNow();}
 @Test void concurrentDuplicatesVerifyBusinessOutcome(){
  var step=new Step("/pay","{{runId}}",5,true,0,200,null,null);
  var probe=new Probe("/orders","/count",mapper.valueToTree(1),500);
  Result result=runner.execute(UUID.randomUUID(),new Scenario("Duplicates",origin,List.of(step),probe));
  assertEquals("PASSED",result.status());assertEquals(5,count.get());assertEquals(1,orders.size());
 }
 @Test void successfulHttpDoesNotHideBusinessFailure(){
  var step=new Step("/pay","{{runId}}",1,false,0,200,null,null);
  Result result=runner.execute(UUID.randomUUID(),new Scenario("Wrong expectation",origin,List.of(step),
   new Probe("/orders","/count",mapper.valueToTree(2),150)));
  assertEquals("FAILED",result.status());assertTrue(result.probeMessage().contains("timed out"));assertTrue(result.probeMessage().contains("received 1"));
 }
 @Test void oversizedResponseFailsInsteadOfExhaustingMemory(){
  var step=new Step("/large","{}",1,false,0,200,null,null);
  assertEquals("FAILED",runner.execute(UUID.randomUUID(),new Scenario("Size",origin,List.of(step),null)).status());
 }
 @Test void redirectsAreNeverFollowed(){
  var step=new Step("/redirect","{}",1,false,0,200,null,null);
  var result=runner.execute(UUID.randomUUID(),new Scenario("Redirect",origin,List.of(step),null));
  assertEquals("FAILED",result.status());assertEquals(302,result.deliveries().getFirst().status());assertEquals(0,count.get());
 }
 @Test void targetPolicyRejectsEscapes(){
  var policy=new TargetPolicy(origin);
  assertThrows(IllegalArgumentException.class,()->policy.resolve("https://example.com","/"));
  assertThrows(IllegalArgumentException.class,()->policy.resolve(origin,"//example.com/pay"));
  assertThrows(IllegalArgumentException.class,()->policy.resolve(origin,"/\\example.com"));
 }
}
