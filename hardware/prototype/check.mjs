// Independent structural checks for the unqualified generated prototype CAD.
// Run: node hardware/prototype/check.mjs
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

function verifyPrototype(files) {
  const errors=[], observations=[];
  const opticalPath="hardware/prototype/optical/one-sensor.kicad_sch";
  const pcbPath="hardware/prototype/optical/one-sensor.kicad_pcb";
  const powerPath="hardware/prototype/power-usb/power-usb.kicad_sch";
  const bomPath="hardware/prototype/BOM.csv";
  const check=(condition,description)=>{if(condition)observations.push(description);else errors.push(description);};
  function parse(s) {
    let pos=0;
    function spaces(){while(pos<s.length && /\s/.test(s[pos]))pos++;}
    function token(){
      spaces();if(pos>=s.length)throw Error("Unexpected EOF");
      const c=s[pos];
      if(c==="("){pos++;const out=[];while(true){spaces();if(pos>=s.length)throw Error("Unclosed expression");if(s[pos]===")"){pos++;return out;}out.push(token());}}
      if(c===")")throw Error("Unmatched right parenthesis");
      if(c==='"'){const start=pos++;let escaping=false;while(pos<s.length){const next=s[pos++];if(escaping){escaping=false;continue;}if(next==="\\"){escaping=true;continue;}if(next==='"')return JSON.parse(s.slice(start,pos));}throw Error("Unterminated quoted string");}
      const start=pos;while(pos<s.length && !/\s|\(|\)/.test(s[pos]))pos++;
      return s.slice(start,pos);
    }
    const result=token();spaces();if(pos!==s.length)throw Error("Trailing content");return result;
  }
  const kids=(node,name)=>Array.isArray(node)?node.filter(v=>Array.isArray(v)&&v[0]===name):[];
  const one=(node,name)=>kids(node,name)[0];
  const prop=(node,name)=>kids(node,"property").find(x=>x[1]===name)?.[2];
  const netName=node=>{const net=one(node,"net");return net?.length===2?net[1]:net?.[2];};
  const xy=(node,name)=>{const x=one(node,name);return x?[Number(x[1]),Number(x[2])]:null;};
  const posKey=(x,y)=>[x,y].map(n=>Number(n).toFixed(4)).join(",");
  const symbolUuid=node=>one(node,"uuid")?.[1];
  function checkSchematic(path,projectName,expectedCount,expectedPinNets){
    const root=parse(files[path]);
    check(root[0]==="kicad_sch",path+" schematic root token");
    const allSymbols=kids(root,"symbol").filter(s=>one(s,"lib_id"));
    const flags=allSymbols.filter(s=>one(s,"lib_id")?.[1]==="Proto:PWR_FLAG");
    const symbols=allSymbols.filter(s=>one(s,"lib_id")?.[1]!=="Proto:PWR_FLAG");
    check(symbols.length===expectedCount,path+" physical component count");
    check(flags.length===2,path+" exactly two offboard power flags");
    const pointLabels=new Map(kids(root,"label").map(l=>[posKey(...xy(l,"at")),l[1]]));
    const declared=new Set(flags.map(flag=>pointLabels.get(posKey(...xy(flag,"at")))));
    check(declared.size===2&&declared.has("3V3")&&declared.has("GND"),
      path+" PWR_FLAG anchors attached to external 3V3 and GND");
    for(const flag of flags){
      check(one(flag,"in_bom")?.[1]==="no"&&one(flag,"on_board")?.[1]==="no",
        path+" PWR_FLAG excluded from physical BOM and PCB");
      check(/^#FLG0[12]$/.test(prop(flag,"Reference")||""),path+" #FLG0x reference");
    }
    const sheetId=symbolUuid(root),byRef=new Map();
    for(const symbol of symbols){
      const ref=prop(symbol,"Reference"),proj=one(one(symbol,"instances"),"project");
      check(proj?.[1]===projectName,path+" "+ref+" project matches filename");
      check(one(proj,"path")?.[1]==="/"+sheetId,path+" "+ref+" root sheet path");
      check(!byRef.has(ref),path+" "+ref+" distinct reference");
      byRef.set(ref,symbol);
    }
    const libraries=new Map(kids(one(root,"lib_symbols"),"symbol").map(s=>[s[1],s]));
    const wires=new Map();
    for(const w of kids(root,"wire")){
      const pts=kids(one(w,"pts"),"xy");
      if(pts.length!==2)continue;
      const a=posKey(pts[0][1],pts[0][2]),b=posKey(pts[1][1],pts[1][2]);
      for(const [from,to] of [[a,b],[b,a]]){if(!wires.has(from))wires.set(from,[]);wires.get(from).push(to);}
    }
    const labels=new Map(kids(root,"label").map(l=>[posKey(...xy(l,"at")),l[1]]));
    for(const [id,net] of Object.entries(expectedPinNets)){
      const [ref,pinNumber]=id.split(":"),symbol=byRef.get(ref);
      if(!symbol){errors.push(path+" missing "+ref);continue;}
      const library=libraries.get(one(symbol,"lib_id")?.[1]);
      if(!library){errors.push(path+" missing library symbol "+ref);continue;}
      const pin=kids(library,"symbol").flatMap(unit=>kids(unit,"pin")).find(p=>one(p,"number")?.[1]===pinNumber);
      if(!pin){errors.push(path+" missing "+id);continue;}
      const local=xy(pin,"at"),origin=xy(symbol,"at");
      const endpoint=posKey(origin[0]+local[0],origin[1]-local[1]);
      const seen=(wires.get(endpoint)||[]).map(end=>labels.get(end)).filter(Boolean);
      check(seen.includes(net),path+" "+id+" -> "+net+" ["+seen.join(",")+"]");
    }
    check(new Set(allSymbols.map(symbolUuid)).size===allSymbols.length,path+" unique symbol UUIDs");
    return {root,symbols,byRef,sheetId};
  }
  const optical=checkSchematic(opticalPath,"one-sensor",23,{
    "U1:1":"TIA_OUT","U1:2":"GND","U1:3":"VREF","U1:4":"SUM","U1:5":"3V3",
    "U2:1":"TIA_OUT","U2:2":"GND","U2:3":"THRESH","U2:4":"3V3","U2:5":"CMP_OUT","U2:6":"GND",
    "D1:1":"SUM","D1:2":"3V3","R3:1":"SUM","R3:2":"TIA_OUT",
    "C2:1":"SUM","C2:2":"TIA_OUT",
    "J1:1":"3V3","J1:2":"GND","J1:3":"EDGE","J1:4":"MON"
  });
  const power=checkSchematic(powerPath,"power-usb",13,{
    "J1:1":"5V_IN","J1:2":"GND","F1:1":"5V_IN","F1:2":"5V_LIMITED",
    "M1:1":"5V_LIMITED","M1:2":"5V_SAFE","M1:3":"GND",
    "U1:2":"VUSB_BUFFER_IN","U1:3":"GND","U1:4":"VBUS_PRESENT","U1:5":"3V3",
    "J2:1":"5V_SAFE","J3:1":"VUSB_RAW","J4:3":"VBUS_PRESENT",
    "R1:2":"VUSB_BUFFER_IN","R3:1":"VUSB_BUFFER_IN","R3:2":"GND"
  });
  check(kids(power.root,"no_connect").length===1,"VBUS buffer NC explicitly marked");
  // Validate registered library definitions; embedded cache-only symbols cause ERC warnings.
  const proto=parse(files["hardware/prototype/Proto.kicad_sym"]);
  check(proto[0]==="kicad_symbol_lib","Proto local library parse");
  const registered=new Map(kids(proto,"symbol").map(s=>[s[1],s]));
  for(const [path,root] of [[opticalPath,optical.root],[powerPath,power.root]]){
    for(const embedded of kids(one(root,"lib_symbols"),"symbol")){
      if(!embedded[1].startsWith("Proto:"))continue;
      const name=embedded[1].slice(6),library=registered.get(name);
      if(!library){errors.push("Unregistered Proto:"+name+" in "+path);continue;}
      const clone=JSON.parse(JSON.stringify(embedded));clone[1]=name;
      check(JSON.stringify(clone)===JSON.stringify(library),path+" registered Proto:"+name+" matches embedded copy");
    }
  }
  const flagDef=registered.get("PWR_FLAG");
  check(!!flagDef&&kids(flagDef,"symbol").flatMap(u=>kids(u,"pin")).some(p=>p[1]==="power_out"),
    "custom ERC flag has power_out pin and does not weaken input-power checks");
  for(const directory of ["optical","power-usb"]){
    const table=parse(files["hardware/prototype/"+directory+"/sym-lib-table"]);
    const entries=kids(table,"lib"),item=entries[0];
    check(table[0]==="sym_lib_table"&&entries.length===1,directory+" project symbol table parses");
    check(one(item,"name")?.[1]==="Proto",directory+" project registers Proto");
    check(one(item,"uri")?.[1]===String.fromCharCode(36)+"{KIPRJMOD}/../Proto.kicad_sym",
      directory+" project uses shared relative library");
  }
  for(const name of ["one-sensor","power-usb"]){
    const file="hardware/prototype/"+(name==="one-sensor"?"optical":"power-usb")+"/"+name+".kicad_pro";
    const project=JSON.parse(files[file]);
    check(project.meta?.filename===name+".kicad_pro"&&project.meta?.version>=3,
      name+" native KiCad project manifest");
  }

  const board=parse(files[pcbPath]);
  check(board[0]==="kicad_pcb","PCB root token");
  const footprints=kids(board,"footprint");
  check(footprints.length===23,"23 PCB footprints for 23 optical parts");
  const segments=kids(board,"segment"),vias=kids(board,"via"),zones=kids(board,"zone");
  check(segments.length>0&&vias.length>0&&zones.length===1,
    "Routed PCB contains tracks, vias and one ground zone");
  check(segments.every(x=>netName(x)?.startsWith("/")),
    "Every PCB track uses a root-sheet net name");
  check(vias.every(x=>netName(x)?.startsWith("/")),
    "Every PCB via uses a root-sheet net name");
  check(segments.filter(x=>netName(x)==="/EDGE").every(x=>one(x,"layer")?.[1]==="F.Cu"),
    "/EDGE is routed entirely on F.Cu");
  check(!vias.some(x=>netName(x)==="/EDGE"),
    "/EDGE has no layer-transition vias");
  check(zones.every(x=>netName(x)==="/GND"&&one(x,"layer")?.[1]==="B.Cu"),
    "PCB ground zone is on B.Cu");
  check(new Set(segments.map(x=>one(x,"layer")?.[1])).has("F.Cu")&&
    new Set(segments.map(x=>one(x,"layer")?.[1])).has("B.Cu"),
    "Routed PCB uses both copper layers");
  check(!files["hardware/prototype/generate.mjs"].includes('"hardware/prototype/optical/one-sensor.kicad_pcb":board'),
    "Generator preserves native routed PCB source of truth");
  const padPositions=[],present=new Set();
  for(const footprint of footprints){
    const ref=prop(footprint,"Reference"),origin=xy(footprint,"at"),s=optical.byRef.get(ref);
    check(Boolean(s),"PCB "+ref+" exists in schematic");
    if(!s)continue;
    present.add(ref);
    check(one(footprint,"path")?.[1]==="/"+optical.sheetId+"/"+symbolUuid(s),
      "PCB "+ref+" schematic UUID path");
    const pads=kids(footprint,"pad"),schPins=new Set(kids(s,"pin").map(x=>x[1])),pcbPins=new Set(pads.map(x=>x[1]));
    check(schPins.size===pcbPins.size&&[...schPins].every(x=>pcbPins.has(x)),
      "PCB "+ref+" pin numbers match schematic");
    if(ref==="R4")check((one(footprint,"attr")||[]).includes("dnp"),"R4 is DNP on PCB");
    check(origin[0]>=100&&origin[0]<=150&&origin[1]>=100&&origin[1]<=144,
      "PCB "+ref+" location inside draft outline");
    for(const pad of pads){
      const offset=xy(pad,"at"),size=xy(pad,"size");
      padPositions.push({ref,pin:pad[1],net:netName(pad)??"",
        x:origin[0]+offset[0],y:origin[1]+offset[1],w:size[0],h:size[1]});
    }
  }
  check(present.size===23,"All 23 footprints uniquely referenced");
  let overlaps=0;
  for(let i=0;i<padPositions.length;i++)for(let j=i+1;j<padPositions.length;j++){
    const a=padPositions[i],b=padPositions[j];
    if(a.ref===b.ref||a.net===b.net)continue;
    if(Math.abs(a.x-b.x)<(a.w+b.w)/2 && Math.abs(a.y-b.y)<(a.h+b.h)/2){
      errors.push("Overlapping different-net pads: "+a.ref+"."+a.pin+" "+a.net+" / "+b.ref+"."+b.pin+" "+b.net);overlaps++;
    }
  }
  check(overlaps===0,"No different-net pad area overlaps");
  const diodeFP=footprints.find(fp=>prop(fp,"Reference")==="D1");
  const diodePads=new Map(kids(diodeFP,"pad").map(p=>[p[1],p]));
  check(prop(diodeFP,"Value")==="SFH 203"&&diodeFP[1]==="ProtoOpto:Photodiode_SFH203",
    "D1 PCB uses active SFH 203 instead of last-time-buy SFH 203 P");
  for(const [number,net,expectedX] of [["1","/SUM",0],["2","/3V3",2.54]]){
    const pad=diodePads.get(number);
    check(!!pad&&netName(pad)===net,"D1 pad "+number+" maps to "+net);
    check(!!pad&&xy(pad,"at")?.[0]===expectedX&&xy(pad,"at")?.[1]===0,
      "D1 pad "+number+" location follows polarity view");
    check(!!pad&&xy(pad,"size")?.[0]===2&&xy(pad,"size")?.[1]===2&&one(pad,"drill")?.[1]==="1",
      "D1 pad "+number+" is 2.0mm land, 1.0mm drill");
  }
  const customFP=parse(files["hardware/prototype/optical/ProtoOpto.pretty/Photodiode_SFH203.kicad_mod"]);
  const customPads=new Map(kids(customFP,"pad").map(p=>[p[1],p]));
  check(customFP[0]==="footprint"&&customFP[1]==="Photodiode_SFH203"&&customPads.size===2,
    "Sourced active diode custom footprint exists");
  const footprintGeometry=footprint=>{
    const silk=kids(footprint,"fp_circle").find(c=>one(c,"layer")?.[1]==="F.SilkS");
    const fab=kids(footprint,"fp_circle").find(c=>one(c,"layer")?.[1]==="F.Fab");
    const courtyard=kids(footprint,"fp_rect").find(c=>one(c,"layer")?.[1]==="F.CrtYd");
    const center=xy(silk,"center"),silkEnd=xy(silk,"end"),fabEnd=xy(fab,"end");
    return {silkRadius:silkEnd[0]-center[0],fabRadius:fabEnd[0]-center[0],
      courtyardStart:xy(courtyard,"start"),courtyardEnd:xy(courtyard,"end")};
  };
  const customGeometry=footprintGeometry(customFP),boardGeometry=footprintGeometry(diodeFP);
  for(const [name,geometry] of [["custom",customGeometry],["PCB",boardGeometry]]){
    check(Math.abs(geometry.silkRadius*2-5.9)<1e-6,
      name+" SFH 203 silk shows 5.90 mm maximum flange");
    check(Math.abs(geometry.fabRadius*2-5.85)<1e-6,
      name+" SFH 203 fabrication outline shows 5.85 mm nominal flange");
    check(Math.abs((geometry.courtyardEnd[0]-geometry.courtyardStart[0])-6.4)<1e-6&&
      Math.abs((geometry.courtyardEnd[1]-geometry.courtyardStart[1])-6.4)<1e-6,
      name+" SFH 203 courtyard is 6.40 mm square for 0.25 mm radial fit clearance");
  }
  check(JSON.stringify(customGeometry)===JSON.stringify(boardGeometry),
    "D1 PCB footprint body and courtyard match custom footprint");
  for(const number of ["1","2"]){
    const a=diodePads.get(number),b=customPads.get(number);
    check(!!a&&!!b&&JSON.stringify(xy(a,"at"))===JSON.stringify(xy(b,"at"))&&
      JSON.stringify(xy(a,"size"))===JSON.stringify(xy(b,"size")),
      "D1 pad "+number+" PCB matches custom footprint physical geometry");
  }
  const sD1=optical.byRef.get("D1"),pR3=power.byRef.get("R3");
  check(prop(sD1,"MPN")==="SFH 203"&&prop(sD1,"Manufacturer")==="ams OSRAM"&&
    prop(sD1,"Datasheet")?.endsWith(".pdf"),"Native D1 MPN, manufacturer, PDF metadata");
  check(prop(sD1,"Lifecycle")==="ACTIVE_VERIFIED_2026-10-31",
    "D1 lifecycle is active and checked, not LTB");
  check(prop(pR3,"MPN")==="RC0603FR-0747KL"&&prop(pR3,"Value")==="47k VBUS_PD",
    "VBUS pull-down exact MPN and value source-linked");
  check(optical.symbols.filter(s=>!/^TP[1-5]$/.test(prop(s,"Reference")||"")).every(s=>!!prop(s,"MPN")),
    "All purchasable optical components have KiCad MPN field");
  check(power.symbols.filter(s=>prop(s,"Reference")!=="J3").every(s=>!!prop(s,"MPN")),
    "All purchasable power/USB schematic items have KiCad MPN field");

  const lines=files[bomPath].trim().split(/\r?\n/);
  check(!files[bomPath].includes('"SFH 203 P"'),"Current BOM does not specify discontinued SFH 203 P");
  check(files["hardware/prototype/optical/fp-lib-table"].includes("ProtoOpto"),
    "KiCad local photodiode footprint library is registered");
  const rows=lines.map(line=>line.split(/","/).map(value=>value.replace(/^"|"$/g,"")));
  check(rows[0][0]==="group"&&rows[0][1]==="ref","BOM header");
  check(rows.length===42,"BOM header + 41 component rows");
  check(rows.slice(1).every(row=>row.length===rows[0].length),"BOM consistent columns");
  check(rows.slice(1).filter(row=>row[0]==="optical sensor").length===23,"BOM optical count 23");
  check(rows.slice(1).filter(row=>row[0]==="external power + USB").length===13,"BOM power count 13");
  check(rows.slice(1).some(row=>row[0]==="optical sensor"&&row[1]==="R4"&&row[9]==="YES"),"BOM R4 DNP");
  check(rows[0][11]==="lifecycle_status","BOM lifecycle status column");
  check(rows.slice(1).some(row=>row[0]==="optical sensor"&&row[1]==="D1"&&
    row[3]==="SFH 203"&&row[11]==="ACTIVE_VERIFIED_2026-10-31"),
    "BOM active verified SFH 203 required");
  check(rows.slice(1).some(row=>row[0]==="external power + USB"&&row[1]==="R3"&&
    row[2]==="47k VBUS_PD"),"BOM VBUS sense pull-down present");
  check(rows.slice(1).every(row=>!/(DISCONTINUED|LAST_TIME_BUY|OBSOLETE)/.test(row[11])),
    "No lifecycle-disallowed part in BOM");
  check(rows.slice(1).filter(row=>row[0]==="external power + USB").length===13,
    "BOM accounts for added power-sense resistor");
  return {status:errors.length?"failed":"passed",checks:observations.length,errors,counts:{
    opticalSymbols:optical.symbols.length,powerSymbols:power.symbols.length,
    pcbFootprints:footprints.length,pcbPads:padPositions.length,bomRows:rows.length-1
  }};
}
const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const paths=["hardware/prototype/generate.mjs","hardware/prototype/optical/one-sensor.kicad_sch","hardware/prototype/optical/one-sensor.kicad_pcb","hardware/prototype/optical/ProtoOpto.pretty/Photodiode_SFH203.kicad_mod","hardware/prototype/optical/fp-lib-table","hardware/prototype/power-usb/power-usb.kicad_sch","hardware/prototype/BOM.csv","hardware/prototype/Proto.kicad_sym","hardware/prototype/optical/sym-lib-table","hardware/prototype/power-usb/sym-lib-table","hardware/prototype/optical/one-sensor.kicad_pro","hardware/prototype/power-usb/power-usb.kicad_pro"];
const files=Object.fromEntries(paths.map(path=>[path,readFileSync(resolve(root,path),"utf8")]));
const result=verifyPrototype(files);
console.log(JSON.stringify(result,null,2));
if(result.status!=="passed")process.exitCode=1;
