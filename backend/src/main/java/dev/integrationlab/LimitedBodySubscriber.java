package dev.integrationlab;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;

final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
 private final int limit;private final ByteArrayOutputStream data=new ByteArrayOutputStream();
 private final CompletableFuture<byte[]> result=new CompletableFuture<>();private Flow.Subscription subscription;
 LimitedBodySubscriber(int limit){this.limit=limit;}
 public CompletionStage<byte[]> getBody(){return result;}
 public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
 public void onNext(List<ByteBuffer> buffers){
  for(ByteBuffer b:buffers){
   if(data.size()+b.remaining()>limit){subscription.cancel();result.completeExceptionally(new IllegalStateException("Response exceeds size limit"));return;}
   byte[] bytes=new byte[b.remaining()];b.get(bytes);data.writeBytes(bytes);
  }
  subscription.request(1);
 }
 public void onError(Throwable error){result.completeExceptionally(error);}
 public void onComplete(){result.complete(data.toByteArray());}
}
