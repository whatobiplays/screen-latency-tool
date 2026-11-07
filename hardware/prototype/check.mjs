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
  const selectedCapacitorMpn="KGM15BR71H104KM";
  const selectedCapacitorUrl="https://www.digikey.com/en/products/detail/kyocera-avx/KGM15BR71H104KM/6797442";
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
  const opticalJ1=optical.byRef.get("J1");
  check(prop(opticalJ1,"MPN")==="B4B-PH-K-S(LF)(SN)"&&
    prop(opticalJ1,"Manufacturer")==="JST"&&
    prop(opticalJ1,"Source URL")==="https://www.tme.com/us/en-us/details/b4b-ph-k-s/raster-signal-connectors-2-00mm/jst/b4b-ph-k-s-lf-sn/"&&
    prop(opticalJ1,"Datasheet")==="https://www.pololu.com/file/0J373/JST_ePH.pdf"&&
    prop(opticalJ1,"Footprint")==="Connector_JST:JST_PH_B4B-PH-K_1x04_P2.00mm_Vertical",
    "Optical J1 exact JST full-suffix MPN, source, drawing and 2.00 mm footprint");
  const power=checkSchematic(powerPath,"power-usb",14,{
    "J1:1":"5V_IN","J1:2":"GND","F1:1":"5V_IN","F1:2":"5V_LIMITED",
    "M1:1":"5V_LIMITED","M1:2":"5V_SAFE","M1:3":"GND",
    "U1:2":"VUSB_BUFFER_IN","U1:3":"GND","U1:4":"VBUS_PRESENT","U1:5":"3V3",
    "J2:1":"5V_SAFE","J2:2":"GND","J6:1":"5V_SAFE","J6:2":"GND",
    "J3:1":"VUSB_RAW","J4:3":"VBUS_PRESENT","J5:1":"LED_GPIO","J5:2":"GND",
    "R1:2":"VUSB_BUFFER_IN","R3:1":"VUSB_BUFFER_IN","R3:2":"GND"
  });
  const powerJ1=power.byRef.get("J1"),powerJ1At=xy(powerJ1,"at");
  check(one(powerJ1,"lib_id")?.[1]==="Proto:JACK3"&&
    prop(powerJ1,"MPN")==="L721A"&&prop(powerJ1,"Manufacturer")==="Switchcraft"&&
    prop(powerJ1,"Source URL")==="https://www.digikey.com/en/products/detail/switchcraft-inc/L721A/8571719"&&
    prop(powerJ1,"Datasheet")==="https://www.switchcraft.com/assets/1/24/L721A_CD.PDF",
    "Power J1 uses the exact Switchcraft L721A and source drawing");
  const powerJ1Shunt=posKey(powerJ1At[0]-10.16,powerJ1At[1]+5.08);
  const noConnects=kids(power.root,"no_connect").map(node=>posKey(...xy(node,"at")));
  const bufferU1=power.byRef.get("U1"),bufferU1At=xy(bufferU1,"at");
  const bufferLib=kids(one(power.root,"lib_symbols"),"symbol")
    .find(symbol=>symbol[1]==="Proto:BUFFER");
  const bufferUnit=kids(bufferLib,"symbol").find(symbol=>kids(symbol,"pin").length>0);
  const bufferPin=number=>kids(bufferUnit,"pin")
    .find(pin=>one(pin,"number")?.[1]===number);
  const pinEndpoint=number=>{
    const pin=bufferPin(number),local=xy(pin,"at");
    return local&&bufferU1At?posKey(bufferU1At[0]+local[0],bufferU1At[1]-local[1]):"";
  };
  const bufferNcPin=bufferPin("1"),bufferInputPin=bufferPin("2");
  const bufferNc=pinEndpoint("1"),bufferInput=pinEndpoint("2");
  check(noConnects.length===2&&noConnects.includes(powerJ1Shunt),
    "L721A sleeve shunt is marked NC; power schematic has two NC markers");
  check(one(bufferNcPin,"name")?.[1]==="NC"&&noConnects.includes(bufferNc)&&
    one(bufferInputPin,"name")?.[1]==="A"&&!noConnects.includes(bufferInput),
    "U1:1 NC pin is marked NC; U1:2 A remains connected to VUSB_BUFFER_IN");
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
  const selectedOpticalCaps=footprints.filter(fp=>["C3","C5"].includes(prop(fp,"Reference")));
  check(selectedOpticalCaps.length===2&&selectedOpticalCaps.every(fp=>
    prop(fp,"MPN")==="KGM15BR71H104KM"&&
    prop(fp,"Source URL")===selectedCapacitorUrl),
    "Optical PCB C3/C5 metadata uses the approved KM packaging variant");
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
      const angle=((Number(one(footprint,"at")?.[3]??0)%360)+360)%360;
      const rotate=(x,y)=>angle===0?[x,y]:angle===90?[y,-x]:
        angle===180?[-x,-y]:angle===270?[-y,x]:[x,y];
      const [rx,ry]=rotate(offset[0],offset[1]);
      padPositions.push({ref,pin:pad[1],net:netName(pad)??"",
        x:origin[0]+rx,y:origin[1]+ry,w:size[0],h:size[1]});
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
  const opticalJ1Board=footprints.find(fp=>prop(fp,"Reference")==="J1");
  const opticalJ1At=one(opticalJ1Board,"at"),opticalJ1Origin=xy(opticalJ1Board,"at");
  const opticalJ1Pads=new Map(kids(opticalJ1Board,"pad").map(p=>[p[1],p]));
  // KiCad serializes this orientation as either 270 or -90 degrees.
  check(opticalJ1Board[1]==="Connector_JST:JST_PH_B4B-PH-K_1x04_P2.00mm_Vertical"&&
    prop(opticalJ1Board,"MPN")==="B4B-PH-K-S(LF)(SN)"&&
    ((Number(opticalJ1At?.[3])%360)+360)%360===270,
    "Native optical J1 is the full-suffix JST PH header, rotated to preserve pin 1");
  for(const [number,net,expectedY] of [["1","/3V3",108],["2","/GND",110],
    ["3","/EDGE",112],["4","/MON",114]]){
    const pad=opticalJ1Pads.get(number),at=xy(pad,"at");
    check(!!pad&&netName(pad)===net,"Optical J1 pad "+number+" maps to "+net);
    check(!!pad&&Math.abs(opticalJ1Origin[0]-at[1]-145)<1e-6&&
      Math.abs(opticalJ1Origin[1]+at[0]-expectedY)<1e-6,
      "Optical J1 pad "+number+" center follows JST 2.00 mm pitch and pin-1 orientation");
    check(!!pad&&one(pad,"drill")?.[1]==="0.75",
      "Optical J1 pad "+number+" matches the library's JST-compatible 0.75 mm drill");
  }
  const j1Courtyard=kids(opticalJ1Board,"fp_rect").find(x=>one(x,"layer")?.[1]==="F.CrtYd");
  const j1CrtStart=xy(j1Courtyard,"start"),j1CrtEnd=xy(j1Courtyard,"end");
  const j1CourtyardBounds={
    minX:opticalJ1Origin[0]-j1CrtEnd[1],maxX:opticalJ1Origin[0]-j1CrtStart[1],
    minY:opticalJ1Origin[1]+j1CrtStart[0],maxY:opticalJ1Origin[1]+j1CrtEnd[0]
  };
  check(j1CourtyardBounds.minX>=100&&j1CourtyardBounds.maxX<=147.2&&
    j1CourtyardBounds.minY>=100&&j1CourtyardBounds.maxY<=144,
    "JST J1 courtyard remains inside the board with at least 2.8 mm right-edge clearance");
  const trackEndpoint=(net,x,y)=>segments.some(segment=>netName(segment)===net&&
    [one(segment,"start"),one(segment,"end")].some(point=>
      Math.abs(Number(point?.[1])-x)<1e-6&&Math.abs(Number(point?.[2])-y)<1e-6));
  check(trackEndpoint("/EDGE",145,112)&&trackEndpoint("/MON",145,114),
    "EDGE and MON fanouts terminate on the remapped JST J1 pads");
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
  check(prop(sD1,"Value")==="SFH 203"&&prop(sD1,"MPN")==="Q62702P0955"&&
    prop(sD1,"Manufacturer")==="ams OSRAM"&&
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
  check(rows.length===45,"BOM header + 44 component rows after direct Type-C cable selection");
  check(rows.slice(1).every(row=>row.length===rows[0].length),"BOM consistent columns");
  check(rows.slice(1).filter(row=>row[0]==="optical sensor").length===23,"BOM optical count 23");
  check(rows.slice(1).filter(row=>row[0]==="external power + USB").length===14,"BOM power count 14");
  check(rows.slice(1).some(row=>row[0]==="optical sensor"&&row[1]==="R4"&&row[9]==="YES"),"BOM R4 DNP");
  check(rows[0][11]==="lifecycle_status","BOM lifecycle status column");
  check(rows.slice(1).some(row=>row[0]==="optical sensor"&&row[1]==="D1"&&
    row[2]==="SFH 203"&&row[3]==="Q62702P0955"&&row[11]==="ACTIVE_VERIFIED_2026-10-31"),
    "BOM active SFH 203 display and Q62702P0955 MPN required");
  check(rows.slice(1).some(row=>row[0]==="external power + USB"&&row[1]==="R3"&&
    row[2]==="47k VBUS_PD"),"BOM VBUS sense pull-down present");
  check(rows.slice(1).every(row=>!/(DISCONTINUED|LAST_TIME_BUY|OBSOLETE)/.test(row[11])),
    "No lifecycle-disallowed part in BOM");
  const bomRow=(ref,group)=>rows.slice(1).find(row=>row[1]===ref&&(!group||row[0]===group));
  for(const [ref,group,schematic,footprint] of [
    ["C3","optical sensor",optical,"Capacitor_SMD:C_0603_1608Metric"],
    ["C5","optical sensor",optical,"Capacitor_SMD:C_0603_1608Metric"],
    ["C1","external power + USB",power,"offboard/non-PCB"]
  ]){
    const symbol=schematic.byRef.get(ref),row=bomRow(ref,group);
    check(!!symbol&&prop(symbol,"MPN")===selectedCapacitorMpn&&
      prop(symbol,"Source URL")===selectedCapacitorUrl&&
      prop(symbol,"Lifecycle")==="UNVERIFIED_BEFORE_ORDER"&&
      prop(symbol,"Footprint")===(ref==="C1"?undefined:footprint),
      "KiCad "+ref+" uses the approved exact KYOCERA AVX capacitor and lifecycle evidence");
    check(!!row&&row[3]===selectedCapacitorMpn&&row[4]==="1"&&
      row[5]===footprint&&row[6]==="0.10"&&row[7]==="0.10"&&
      row[8]===selectedCapacitorUrl&&row[11]==="UNVERIFIED_BEFORE_ORDER"&&
      /2026-11-06/.test(row[10])&&/5,996/.test(row[10])&&/USD 0\.10/.test(row[10]),
      "BOM "+ref+" records one exact KM capacitor at the current dated unit price and source");
  }
  check(![files[opticalPath],files[powerPath],files[pcbPath],files[bomPath]]
    .some(text=>text.includes("KGM15BR71H104KT")),
    "Generated CAD and BOM contain no superseded KT capacitor selection");
  const specificationUrl="https://www.china-amass.com/public/upload/20240608/810c62b3f7ace2287989748538eb2a6b.pdf";
  for(const [ref,mpn,role,price,exactPrice,sourceUrl] of [
    ["J2","XT30U-F.G.Y","SOURCE FEMALE","0.35","0.35031","https://store.comet.bg/en/Catalogue/Product/5521133/"],
    ["J6","XT30U-M.G.Y","LOAD MALE","0.39","0.38990","https://store.comet.bg/en/Catalogue/Product/5521134/"]
  ]){
    const symbol=power.byRef.get(ref),row=bomRow(ref);
    check(!!symbol&&prop(symbol,"MPN")===mpn&&prop(symbol,"Manufacturer")==="AMASS"&&
      prop(symbol,"Lifecycle")==="UNVERIFIED_BEFORE_ORDER"&&prop(symbol,"Source URL")===sourceUrl&&
      prop(symbol,"Datasheet")===specificationUrl,"KiCad "+ref+" exact MPN and source metadata");
    check(!!symbol&&one(symbol,"on_board")?.[1]==="no"&&prop(symbol,"Footprint")===undefined,
      "KiCad "+ref+" remains offboard without a footprint");
    check(!!row&&row[0]==="external power + USB"&&row[2].includes(role)&&row[3]===mpn&&
      row[4]==="1"&&row[5]==="offboard/non-PCB"&&row[6]===price&&row[7]===price&&row[8]===sourceUrl&&
      row[11]==="UNVERIFIED_BEFORE_ORDER"&&row[10].includes("USD "+exactPrice+" each")&&
      /procurement HOLD/.test(row[10]),
      "BOM "+ref+" exact-MPN dated price, source, offboard status, and lifecycle HOLD");
  }
  check(power.byRef.has("J2")&&power.byRef.has("J6")&&
    !power.byRef.has("J2A")&&!power.byRef.has("J2B"),
    "XT30U source J2 and load J6 use distinct numeric references");
  const opticalJ1Row=bomRow("J1","optical sensor");
  check(!!opticalJ1Row&&opticalJ1Row[3]==="B4B-PH-K-S(LF)(SN)"&&
    opticalJ1Row[5]==="Connector_JST:JST_PH_B4B-PH-K_1x04_P2.00mm_Vertical"&&
    opticalJ1Row[6]==="0.24"&&opticalJ1Row[8]===prop(opticalJ1,"Source URL")&&
    opticalJ1Row[11]==="UNVERIFIED_BEFORE_ORDER",
    "BOM optical J1 has the exact JST part, footprint, dated price and lifecycle hold");
  const powerJ1Row=bomRow("J1","external power + USB");
  check(!!powerJ1Row&&powerJ1Row[3]==="L721A"&&powerJ1Row[6]==="6.28"&&
    powerJ1Row[8]===prop(powerJ1,"Source URL")&&powerJ1Row[5]==="offboard/non-PCB"&&
    /center.*5V_IN.*sleeve.*GND.*shunt.*(NC|insulat)/i.test(powerJ1Row[10])&&
    /767K\/768K/.test(powerJ1Row[10])&&/unverified Stage-B checks/i.test(powerJ1Row[10])&&
    /owner-approved.*direct.*nonlocking.*design assumption/i.test(powerJ1Row[10])&&
    /physical engagement.*retention/i.test(powerJ1Row[10])&&
    /no electrical.contact failure is asserted/i.test(powerJ1Row[10])&&
    !/resolve the mate before Stage B/i.test(powerJ1Row[10]),
    "BOM power J1 records the approved direct path and preserves Stage-B fit and safety gates");
  for(const [ref,mpn,value,unitPrice,sourceUrl,role,netDescription] of [
    ["J4","CAB-14575","TEENSY_LOGIC","1.95","https://www.digikey.com/en/products/detail/sparkfun-electronics/14575/8543395","3-position","1=3V3, 2=GND, 3=VBUS_PRESENT"],
    ["J5","CAB-14574","SLOW_LED_GPIO","1.38","https://www.digikey.com/en/products/detail/sparkfun-electronics/14574/8543394","2-position","1=LED_GPIO, 2=GND"]
  ]){
    const symbol=power.byRef.get(ref),row=bomRow(ref,"external power + USB");
    check(!!symbol&&prop(symbol,"MPN")===mpn&&prop(symbol,"Manufacturer")==="SparkFun"&&
      prop(symbol,"Source URL")===sourceUrl&&prop(symbol,"Lifecycle")==="UNVERIFIED_BEFORE_ORDER"&&
      prop(symbol,"BOM Comments")?.includes("female")&&prop(symbol,"BOM Comments")?.includes("male"),
      "KiCad "+ref+" exact SparkFun pair, owner-selected gender orientation and lifecycle hold");
    check(!!row&&row[2]===value&&row[3]===mpn&&row[4]==="1"&&
      row[5]==="offboard/non-PCB"&&row[6]===unitPrice&&row[8]===sourceUrl&&
      row[11]==="UNVERIFIED_BEFORE_ORDER"&&row[10].includes(role)&&
      new RegExp(netDescription.replace(/[.*+?^${}()|[\]\\]/g,"\\$&")).test(row[10])&&
      /female[\s\S]*Teensy[\s\S]*male[\s\S]*external/i.test(row[10]),
      "BOM "+ref+" preserves pin map and documents the female Teensy-side half");
  }
  const cableRow=bomRow("EXT6","offboard kit");
  check(!!cableRow&&cableRow[3]==="CABLE-PH04"&&cableRow[4]==="1"&&
    cableRow[5]==="offboard/non-PCB"&&cableRow[6]==="2.71"&&
    cableRow[8]==="https://www.digikey.com/en/products/detail/analog-devices-inc-maxim-integrated/CABLE-PH04/9381900"&&
    /pin colors 1 black, 2 red, 3 blue, 4 gray/i.test(cableRow[10])&&
    /black=3V3.*red=GND/i.test(cableRow[10])&&
    /continuity/i.test(cableRow[10])&&cableRow[11]==="UNVERIFIED_BEFORE_ORDER",
    "CABLE-PH04 source, current price, color/pin evidence and offline continuity gate");
  const directUsbCable=bomRow("EXT4","offboard kit");
  check(!!directUsbCable&&directUsbCable[3]==="Adafruit 3878"&&directUsbCable[6]==="3.50"&&
    directUsbCable[8]==="https://www.adafruit.com/product/3878"&&
    /5.1 kohm CC pull-down/.test(directUsbCable[10])&&/VCC jumper CLOSED/.test(directUsbCable[10])&&
    /shield-to-GND jumper OPEN/.test(directUsbCable[10])&&
    directUsbCable[11]==="UNVERIFIED_BEFORE_ORDER"&&!bomRow("EXT5","offboard kit")&&
    !rows.slice(1).some(row=>["Adafruit 5030","Adafruit 592"].includes(row[3])),
    "Owner-selected Adafruit 3878 replaces the two-piece USB adapter stack and remains lifecycle-unverified");
  const usbBreakoutRow=bomRow("EXT7","offboard kit");
  check(!!usbBreakoutRow&&usbBreakoutRow[2]==="eLabGuy USB Micro-B inline pass-through breakout"&&
    usbBreakoutRow[3]==="USB-uBM-uBF-V1A"&&usbBreakoutRow[4]==="1"&&
    usbBreakoutRow[5]==="offboard/non-PCB"&&usbBreakoutRow[6]==="9.28"&&
    usbBreakoutRow[8]==="https://elabbay.myshopify.com/products/usb-bm-bf-v1a-micro-usb2-0-type-b-male-to-micro-usb2-0-type-b-female-pass-through-breakout-board"&&
    usbBreakoutRow[10].includes("https://www.elabguy.com/datasheet/USB-uBM-uBF-V1A%20Rev1.1.pdf")&&
    /Micro-B male-to-female/i.test(usbBreakoutRow[10])&&/D\+ and D− connector pins pass directly/i.test(usbBreakoutRow[10])&&
    /VBUS is open between sides unless its jumper is installed/i.test(usbBreakoutRow[10])&&
    /Figure 2 caption.*pin spacing only.*cautions against breadboard use/i.test(usbBreakoutRow[10])&&
    usbBreakoutRow[11]==="UNVERIFIED_BEFORE_ORDER",
    "PAC-2 inline USB breakout is exact-MPN, sourced, VBUS-isolated by default, and lifecycle-held");
  const dmmLeadsRow=bomRow("EXT8","offboard kit");
  check(!!dmmLeadsRow&&dmmLeadsRow[2]==="DMM test leads (Fluke TL75)"&&
    dmmLeadsRow[3]==="855705"&&dmmLeadsRow[4]==="1"&&
    dmmLeadsRow[5]==="offboard/non-PCB"&&dmmLeadsRow[6]==="39.99"&&
    dmmLeadsRow[8]==="https://www.digikey.com/en/products/detail/fluke-electronics/TL75/1801786"&&
    /48.?in/i.test(dmmLeadsRow[10])&&/banana/i.test(dmmLeadsRow[10])&&
    /owner.*DMM banana-jack compatibility/i.test(dmmLeadsRow[10])&&
    /suitable leads are already owned/i.test(dmmLeadsRow[10])&&
    /low.voltage.*continuity/i.test(dmmLeadsRow[10])&&
    dmmLeadsRow[11]==="UNVERIFIED_BEFORE_ORDER",
    "PAC-2 DMM leads are exact-MPN, sourced, low-voltage scoped, and inventory/lifecycle-held");
  check(!rows.slice(1).some(row=>["Adafruit 368","TSW-104-07-G-S","TSW-103-07-G-S","TSW-102-07-G-S"].includes(row[3])),
    "Superseded connectors are absent from the active BOM");
  const purchasableMpns=new Set(rows.slice(1).filter(row=>row[3]&&row[9]!=="YES"&&
    !/^TP[1-5]$/.test(row[1])&&row[3]!=="Teensy OEM copper test pads").map(row=>row[3]));
  check(purchasableMpns.size===31,"31 distinct purchasable MPNs after Adafruit 3878 replaces 5030/592");
  check(new Set(rows.slice(1).map(row=>row[3]).filter(Boolean)).size===34,
    "34 distinct manifest part identities after direct Type-C cable selection");
  check(rows.slice(1).filter(row=>row[0]==="external power + USB").length===14,
    "BOM power row count matches schematic");
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
