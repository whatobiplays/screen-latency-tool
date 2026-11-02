// CI-friendly auditor for actual KiCad ERC exports. Not a replacement for KiCad.
// node hardware/prototype/erc-report-gate.mjs path/to/one-sensor.rpt path/to/power-usb.rpt
import {readFileSync} from "node:fs";

export function inspectReport(body, filename="<ERC report>"){
  const included=body.match(/^Report includes:\s*(.+)$/m)?.[1]?.trim()??"missing";
  const full=/\bErrors\b/.test(included)&&/\bWarnings\b/.test(included);
  const summary=body.match(/\*\*\s+ERC messages:\s*(\d+)\s+Errors\s+(\d+)\s+Warnings\s+(\d+)/);
  const found=[...body.matchAll(/^\s*;\s*(error|warning)\s*$/gm)].map(m=>m[1]);
  const errorCount=found.filter(x=>x==="error").length;
  const warningCount=found.filter(x=>x==="warning").length;
  const codeCounts={};
  for(const m of body.matchAll(/^\[([a-z][a-z0-9_]+)\]:/gm))codeCounts[m[1]]=(codeCounts[m[1]]||0)+1;
  const reported=summary?{total:Number(summary[1]),errors:Number(summary[2]),warnings:Number(summary[3])}:null;
  const consistent=!!reported&&reported.total===found.length&&reported.errors===errorCount&&reported.warnings===warningCount;
  const ignoredBlock=body.match(/^\s*\*\* Ignored checks:\s*\n((?:\s*- [^\n]+\n?)*)/m)?.[1]??"";
  const ignoredChecks=[...ignoredBlock.matchAll(/^\s*-\s*(.+)$/gm)].map(match=>match[1]);
  const validHeader=/^ERC report \(/m.test(body);
  const passed=validHeader&&full&&consistent&&reported.total===0;
  return {filename,passed,reportIncludes:included,complete:full,validHeader,
    errors:errorCount,warnings:warningCount,codes:codeCounts,reported,consistent,ignoredChecks,scope:"enabled_erc_checks_only"};
}

const args=process.argv.slice(2);
if(args.includes("--selftest")){
  const red="ERC report (test, Encoding UTF8)\nReport includes: Errors, Warnings\n[power_pin_not_driven]: Missing source\n    ; error\n ** ERC messages: 1  Errors 1  Warnings 0\n";
  const green="ERC report (test, Encoding UTF8)\nReport includes: Errors, Warnings\n ** ERC messages: 0  Errors 0  Warnings 0\n";
  const filtered=green.replace("Errors, Warnings","Errors");
  const ignored=green+" ** Ignored checks:\n    - SPICE model issue\n";
  if(!inspectReport(ignored).passed||inspectReport(ignored).ignoredChecks[0]!=="SPICE model issue")
    throw Error("ERC ignored-check extraction regression");
  if(inspectReport(red).passed||!inspectReport(green).passed||inspectReport(filtered).passed)
    throw Error("ERC gate failed red/green/incomplete-filter regression fixtures");
  console.log("ERC gate self-test: red, green, ignored-check extraction, and incomplete-filter fixtures passed");
}else{
  if(args.length!==2){
    console.error("Usage: node hardware/prototype/erc-report-gate.mjs <one-sensor.rpt> <power-usb.rpt>");
    process.exitCode=2;
  }else{
    let failures=0;
    for(const name of args){
      let body;
      try{body=readFileSync(name,"utf8");}
      catch(error){
        failures++;
        console.error("ERC report unavailable: "+name+" ("+(error?.code??error?.message??"unknown error")+
          "). Run KiCad ERC to generate both reports before invoking this gate.");
        continue;
      }
      const report=inspectReport(body,name);
      console.log(JSON.stringify(report,null,2));
      if(!report.passed)failures++;
    }
    if(failures){console.error("ERC FAILED: "+failures+" report(s) are missing, malformed, filtered, or contain violations");process.exitCode=1;}
    else console.log("ERC PASSED: both reports show zero violations under enabled ERC checks; review ignoredChecks separately");
  }
}
