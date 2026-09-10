package dev.integrationlab;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import static dev.integrationlab.Models.*;

@Repository
public class Store {
 private final JdbcTemplate db;
 private final ObjectMapper mapper;
 public Store(JdbcTemplate db,ObjectMapper mapper){this.db=db;this.mapper=mapper;}
 public String json(Object value) {
  try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException("Cannot serialize document",e);}
 }
 public List<Map<String,Object>> scenarios(){
  return db.query("select id,document::text,updated_at from scenarios order by updated_at desc",
   (rs,n)->Map.of("id",rs.getString(1),"scenario",read(rs.getString(2),Scenario.class),"updatedAt",rs.getTimestamp(3).toInstant().toString()));
 }
 public UUID save(UUID id,Scenario s){
  UUID key=id==null?UUID.randomUUID():id;
  db.update("insert into scenarios(id,document) values (?,?::jsonb) on conflict(id) do update set document=excluded.document,updated_at=now()",key,json(s));
  return key;
 }
 public Scenario scenario(UUID id){
  var rows=db.query("select document::text from scenarios where id=?",(rs,n)->read(rs.getString(1),Scenario.class),id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Scenario not found");
  return rows.getFirst();
 }
 public void begin(UUID id,UUID scenarioId,Scenario s){
  db.update("insert into runs(id,scenario_id,snapshot,status) values(?,?,?::jsonb,'RUNNING')",id,scenarioId,json(s));
 }
 public void finish(UUID id,Result r){ db.update("update runs set status=?,result=?::jsonb where id=?",r.status(),json(r),id); }
 public void interrupt(){db.update("update runs set status='INTERRUPTED' where status='RUNNING'");}
 public List<Map<String,Object>> runs(){
  return db.query("select id,status,created_at,snapshot->>'name' from runs order by created_at desc limit 100",
   (rs,n)->Map.of("id",rs.getString(1),"status",rs.getString(2),"createdAt",rs.getTimestamp(3).toInstant().toString(),"name",rs.getString(4)));
 }
 public Map<String,Object> run(UUID id){
  var rows=db.query("select status,snapshot::text,result::text,created_at from runs where id=?",(rs,n)->{
   Map<String,Object> m=new LinkedHashMap<>();
   m.put("id",id);m.put("status",rs.getString(1));m.put("scenario",read(rs.getString(2),Scenario.class));
   m.put("result",rs.getString(3)==null?null:read(rs.getString(3),Result.class));
   m.put("createdAt",rs.getTimestamp(4).toInstant().toString());return m;
  },id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Run not found");
  return rows.getFirst();
 }
 public List<Map<String,Object>> events(){
  return db.query("select id,name,body,created_at from events order by created_at desc limit 100",
   (rs,n)->Map.of("id",rs.getString(1),"name",rs.getString(2),"body",rs.getString(3),"createdAt",rs.getTimestamp(4).toInstant().toString()));
 }
 public UUID event(String name,String body){
  if(name==null||name.isBlank()||name.length()>120||body==null||body.length()>32000)
   throw new IllegalArgumentException("Event requires name (1–120) and body (up to 32000 characters)");
  UUID id=UUID.randomUUID();db.update("insert into events(id,name,body) values(?,?,?)",id,name,body);return id;
 }
 private <T>T read(String value,Class<T> type){
  try{return mapper.readValue(value,type);}catch(JsonProcessingException e){throw new IllegalStateException("Stored document is invalid",e);}
 }
}
