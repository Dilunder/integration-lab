package dev.integrationlab;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import static dev.integrationlab.Models.*;
import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {
 ObjectMapper mapper=new ObjectMapper();
 @Test void credentialsAreBoundToExactOrigin(){
  var headers=new TargetHeaders("{\"http://demo:8090\":{\"Authorization\":\"Bearer local-test\"}}",mapper);
  var allowed=HttpRequest.newBuilder(URI.create("http://demo:8090/test"));
  headers.apply("http://demo:8090",allowed);
  assertEquals("Bearer local-test",allowed.build().headers().firstValue("Authorization").orElseThrow());
  var other=HttpRequest.newBuilder(URI.create("http://other:8090/test"));
  headers.apply("http://other:8090",other);
  assertTrue(other.build().headers().firstValue("Authorization").isEmpty());
 }
 @Test void invalidPathsAndExcessiveDeliveriesAreRejected(){
  var validator=new ScenarioValidator(new TargetPolicy("http://demo:8090"),mapper);
  assertThrows(IllegalArgumentException.class,()->validator.validate(new Scenario("Invalid","http://demo:8090",
   List.of(new Step(null,"{}",1,false,0,200,null,null)),null)));
  var many=new Step("/pay","{}",10,false,0,200,null,null);
  assertThrows(IllegalArgumentException.class,()->validator.validate(new Scenario("Too many","http://demo:8090",List.of(many,many,many,many),null)));
 }
}
