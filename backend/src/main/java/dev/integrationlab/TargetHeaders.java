package dev.integrationlab;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.http.HttpRequest;
import java.util.Map;

// Credentials are bound to an exact origin and never included in scenario exports.
@Component
public class TargetHeaders {
 private final Map<String,Map<String,String>> headers;
 public TargetHeaders(@Value("${LAB_TARGET_HEADERS:{}}")String json,ObjectMapper mapper) {
  try{headers=mapper.readValue(json,new TypeReference<>(){});}
  catch(Exception e){throw new IllegalArgumentException("LAB_TARGET_HEADERS must be an origin-to-headers JSON object");}
  if(headers==null)throw new IllegalArgumentException("LAB_TARGET_HEADERS cannot be null");
 }
 public void apply(String origin,HttpRequest.Builder request){
  var values=headers.get(origin);
  if(values!=null) values.forEach(request::header);
 }
}
