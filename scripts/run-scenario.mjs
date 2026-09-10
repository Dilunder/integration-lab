import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
const base=process.env.LAB_URL||'http://127.0.0.1:8088';
const key=process.env.LAB_API_KEY;
const fixture=process.argv[2];
if(!key||!fixture){console.error('Usage: LAB_API_KEY=... node scripts/run-scenario.mjs examples/duplicate-fixed.json');process.exit(2);}
async function api(endpoint,method='GET',body){
 const response=await fetch(base+'/api'+endpoint,{method,headers:{'Content-Type':'application/json','X-Lab-Key':key},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(10000)});
 if(!response.ok)throw Error('API HTTP '+response.status);
 return response.json();
}
try{
 const scenario=JSON.parse(await readFile(fixture,'utf8'));
 const {id}=await api('/scenarios','POST',scenario);
 const run=await api('/scenarios/'+id+'/runs','POST');
 const deadline=Date.now()+180000;
 let result;
 do{
  result=await api('/runs/'+run.id);
  if(result.status!=='RUNNING')break;
  await new Promise(resolve=>setTimeout(resolve,300));
 }while(Date.now()<deadline);
 if(result.status==='RUNNING')throw Error('Run exceeded CI timeout; inspect '+run.id);
 const folder=process.env.LAB_REPORT_DIR||'artifacts';await mkdir(folder,{recursive:true});
 await writeFile(path.join(folder,run.id+'.json'),JSON.stringify(result,null,2));
 const xml=await fetch(base+'/api/runs/'+run.id+'/junit',{headers:{'X-Lab-Key':key},signal:AbortSignal.timeout(10000)});
 if(!xml.ok)throw Error('JUnit report HTTP '+xml.status);
 await writeFile(path.join(folder,run.id+'.xml'),await xml.text());
 console.log(result.status+' '+scenario.name+' ('+run.id+')');
 console.log(result.result?.probeMessage||'No completed result');
 process.exitCode=result.status==='PASSED'?0:1;
}catch(error){console.error(error.message);process.exitCode=2;}
