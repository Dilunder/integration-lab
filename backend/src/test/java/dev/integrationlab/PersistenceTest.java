package dev.integrationlab;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import static dev.integrationlab.Models.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PersistenceTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Test void migrationSnapshotAndRecovery(){
  var ds=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
  Flyway.configure().dataSource(ds).load().migrate();
  var db=new JdbcTemplate(ds);var store=new Store(db,new ObjectMapper());
  var original=new Scenario("Original","http://demo:8090",List.of(new Step("/pay","{}",1,false,0,200,null,null)),null);
  UUID scenario=store.save(null,original),run=UUID.randomUUID();store.begin(run,scenario,original);
  store.save(scenario,new Scenario("Edited",original.target(),original.steps(),null));
  assertEquals("Original",((Scenario)store.run(run).get("scenario")).name());
  assertEquals("Edited",store.scenario(scenario).name());
  store.interrupt();assertEquals("INTERRUPTED",store.run(run).get("status"));
  store.finish(run,new Result("PASSED",List.of(),"No probe",1));assertEquals("PASSED",store.run(run).get("status"));
  store.event("fixture","{}");assertEquals(1,store.events().size());
 }
}
