package dev.integrationlab;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AccessFilter extends OncePerRequestFilter {
 private final String key;
 public AccessFilter(@Value("${lab.api-key}")String key){
  if(key.isBlank())throw new IllegalArgumentException("LAB_API_KEY must not be blank");this.key=key;
 }
 protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  response.setHeader("X-Content-Type-Options","nosniff");
  if(request.getContentLengthLong()>262144){response.sendError(413);return;}
  String path=request.getRequestURI();
  if(path.startsWith("/api/")&&!path.startsWith("/api/inbox/")&&!path.equals("/api/health")){
   String given=request.getHeader("X-Lab-Key");
   if(given==null||!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),given.getBytes(StandardCharsets.UTF_8))){
    response.sendError(401);return;
   }
  }
  // Also cap chunked bodies with no Content-Length.
  if(SetOfMethods.contains(request.getMethod())){
   byte[] body=request.getInputStream().readNBytes(262145);
   if(body.length>262144){response.sendError(413);return;}
   chain.doFilter(new HttpServletRequestWrapper(request){
    public ServletInputStream getInputStream(){
     var input=new java.io.ByteArrayInputStream(body);
     return new ServletInputStream(){
      public int read(){return input.read();}
      public boolean isFinished(){return input.available()==0;}
      public boolean isReady(){return true;}
      public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}
     };
    }
    public java.io.BufferedReader getReader(){return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
   },response);
  }else chain.doFilter(request,response);
 }
 private static final java.util.Set<String> SetOfMethods=java.util.Set.of("POST","PUT","PATCH");
}
