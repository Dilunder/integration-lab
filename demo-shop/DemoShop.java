import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

// Deliberately isolated demo target. It is not a production payment service.
public class DemoShop {
 public static void main(String[] args)throws Exception {
  var fixed=new ConcurrentHashMap<String,Boolean>();
  var broken=new ConcurrentHashMap<String,AtomicInteger>();
  var server=HttpServer.create(new InetSocketAddress(8090),0);
  server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
  server.createContext("/",e->{
   String path=e.getRequestURI().getPath(),body;int status=200;
   try{
    if(path.equals("/health"))body="{\"status\":\"UP\"}";
    else if(e.getRequestMethod().equals("POST")&&(path.equals("/fixed/payment")||path.equals("/broken/payment"))){
     String payload=new String(e.getRequestBody().readNBytes(32769),StandardCharsets.UTF_8);
     var match=Pattern.compile("\"paymentId\"\\s*:\\s*\"([a-zA-Z0-9-]+)\"").matcher(payload);
     if(!match.find()){status=400;body="{\"error\":\"paymentId required\"}";}
     else {String id=match.group(1);
      if(path.startsWith("/fixed"))fixed.putIfAbsent(id,true);
      else broken.computeIfAbsent(id,k->new AtomicInteger()).incrementAndGet();
      body="{\"accepted\":true}";
     }
    }else if(e.getRequestMethod().equals("GET")&&path.matches("/(fixed|broken)/orders/[a-zA-Z0-9-]+")){
     String id=path.substring(path.lastIndexOf('/')+1);
     int count=path.startsWith("/fixed")?(fixed.containsKey(id)?1:0):broken.getOrDefault(id,new AtomicInteger()).get();
     body="{\"count\":"+count+"}";
    }else{status=404;body="{\"error\":\"Not found\"}";}
   }catch(Exception ex){status=500;body="{\"error\":\"Demo error\"}";}
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().add("Content-Type","application/json");
   e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();
  });
  server.start();System.out.println("Demo shop listening on 8090");
 }
}
