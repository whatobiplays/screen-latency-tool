// Reproducible source for the prototype schematic, symbol, footprint and BOM drafts.
// The routed PCB is authoritative native KiCad source and is never written by this generator.
// Run: node hardware/prototype/generate.mjs
import { mkdirSync, writeFileSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
const generatePrototype=function generatePrototype() {
  let seq=1; const uid=()=>`9a3f1b20-99ce-4cf2-8a30-${(seq++).toString(16).padStart(12,"0")}`;
  const q=x=>JSON.stringify(String(x??"")), f=x=>(+x).toFixed(4).replace(/\.?0+$/,"")||"0";
  const R="Resistor_SMD:R_0603_1608Metric", C="Capacitor_SMD:C_0603_1608Metric";
  const parts=[
    ["U1","opa","OPA320AIDBVR","OPA320AIDBVR",3.06,"Package_TO_SOT_SMD:SOT-23-5",{1:"TIA_OUT",2:"GND",3:"VREF",4:"SUM",5:"3V3"},[113,112],"https://www.digikey.com/en/products/detail/texas-instruments/OPA320AIDBVR/2747411"],
    ["U2","cmp","TLV3501AIDBVR","TLV3501AIDBVR",4.34,"Package_TO_SOT_SMD:SOT-23-6",{1:"TIA_OUT",2:"GND",3:"THRESH",4:"3V3",5:"CMP_OUT",6:"GND"},[132,114],"https://www.digikey.com/en/products/detail/texas-instruments/TLV3501AIDBVR/4135047"],
    ["D1","diode","SFH 203","SFH 203",.84,"ProtoOpto:Photodiode_SFH203",{1:"SUM",2:"3V3"},[119,113],"https://www.digikey.com/en/products/detail/ams-osram-usa-inc/SFH-203/2205881"],
    ["R1","res","18k","RC0603FR-0718KL",.10,R,{1:"3V3",2:"VREF"},[106,123],"https://www.digikey.com/en/products/detail/yageo/RC0603FR-0718KL/726989"],
    ["R2","res","10k","ERJ-3EKF1002V",.10,R,{1:"VREF",2:"GND"},[106,126],"https://www.digikey.com/en/products/detail/panasonic-industry/ERJ-3EKF1002V/198102"],
    ["R3","res","47k","RC0603FR-0747KL",.10,R,{1:"SUM",2:"TIA_OUT"},[115,117],"https://www.digikey.com/en/products/detail/yageo/RC0603FR-0747KL/727253"],
    ["R4","res","2.2M DNP","RC0603FR-072M2L",.11,R,{1:"CMP_OUT",2:"THRESH"},[126,120],"https://www.digikey.com/en/products/detail/yageo/RC0603FR-072M2L/5282195"],
    ["R5","res","100R","RC0603FR-07100RL",.10,R,{1:"CMP_OUT",2:"EDGE"},[137,120],"https://www.digikey.com/en/products/detail/yageo/RC0603FR-07100RL/726888"],
    ["R6","res","1k","RC0603FR-071KL",.10,R,{1:"TIA_OUT",2:"MON"},[137,123],"https://www.digikey.com/en/products/detail/yageo/RC0603FR-071KL/726843"],
    ["R7","res","1M","RC0603FR-071ML",.10,R,{1:"THRESH",2:"VREF"},[122,125],"https://www.digikey.com/en/products/detail/yageo/RC0603FR-071ML/729791"],
    ["C1","cap","1u VREF","CL10A105KP8NNNC",.11,C,{1:"VREF",2:"GND"},[106,130],"https://www.digikey.com/en/products/detail/samsung-electro-mechanics/CL10A105KP8NNNC/3886840"],
    ["C2","cap","2.2p C0G","CC0603CRNPO9BN2R2",.10,C,{1:"SUM",2:"TIA_OUT"},[115,120],"https://www.digikey.com/en/products/detail/yageo/CC0603CRNPO9BN2R2/5195641"],
    ["C3","cap","100n U1","KGM15BR71H104KT",.10,C,{1:"3V3",2:"GND"},[110,108],"https://www.digikey.com/en/products/detail/kyocera-avx/KGM15BR71H104KT/1600046"],
    ["C4","cap","1u U1","CL10A105KP8NNNC",.11,C,{1:"3V3",2:"GND"},[110,105],"https://www.digikey.com/en/products/detail/samsung-electro-mechanics/CL10A105KP8NNNC/3886840"],
    ["C5","cap","100n U2","KGM15BR71H104KT",.10,C,{1:"3V3",2:"GND"},[135,109],"https://www.digikey.com/en/products/detail/kyocera-avx/KGM15BR71H104KT/1600046"],
    ["C6","cap","2.2u U2","CL10A225KP8NNNC",.10,C,{1:"3V3",2:"GND"},[135,106],"https://www.digikey.com/en/products/detail/samsung-electro-mechanics/CL10A225KP8NNNC/3886743"],
    ["RV1","pot","10k 3296W","3296W-1-103LF",2.39,"Potentiometer_THT:Potentiometer_Bourns_3296W_Vertical",{1:"3V3",2:"THRESH",3:"GND"},[133,133],"https://www.digikey.com/en/products/detail/bourns-inc/3296W-1-103LF/1088045"],
    ["J1","conn4","SENSOR_IO","TSW-104-07-G-S",.38,"Connector_PinHeader_2.54mm:PinHeader_1x04_P2.54mm_Vertical",{1:"3V3",2:"GND",3:"EDGE",4:"MON"},[142,121],"https://www.digikey.com/en/products/detail/samtec-inc/TSW-104-07-G-S/1101323"],
    ...[["TP1","TIA_OUT",119,137],["TP2","THRESH",140,136],["TP3","VREF",111,136],["TP4","GND",105,138],["TP5","3V3",142,139]].map(([ref,net,x,y])=>[ref,"tp",net,"PCB_TEST_PAD",0,"TestPoint:TestPoint_Pad_D1.5mm",{1:net},[x,y],""])
  ].map(([ref,kind,value,mpn,price,fp,pins,at,url])=>({ref,kind,value,mpn,price,fp,pins,at,url,dnp:ref==="R4"}));
  const power=[
    ["J1","conn2","5V_DC_IN","Adafruit 368",2,{1:"5V_IN",2:"GND"},"https://www.adafruit.com/product/368"],
    ["F1","res","MF-R050 PTC","MF-R050",.47,{1:"5V_IN",2:"5V_LIMITED"},"https://www.digikey.com/en/products/detail/bourns-inc/MF-R050/259965"],
    ["M1","module3","Pololu 5383","5383",4.95,{1:"5V_LIMITED",2:"5V_SAFE",3:"GND"},"https://www.pololu.com/product/5383"],
    ["J2","conn2","TEENSY_VIN_GND","TSW-102-07-G-S",.21,{1:"5V_SAFE",2:"GND"},"https://www.digikey.com/en/products/detail/samtec-inc/TSW-102-07-G-S/1101325"],
    ["J3","conn2","ISOLATED_VUSB_PAD","Teensy OEM copper test pads",0,{1:"VUSB_RAW",2:"GND"},"https://www.pjrc.com/store/teensy41.html"],
    ["R1","res","1k VBUS","RC0603FR-071KL",.10,{1:"VUSB_RAW",2:"VUSB_BUFFER_IN"},"https://www.digikey.com/en/products/detail/yageo/RC0603FR-071KL/726843"],
    ["R3","res","47k VBUS_PD","RC0603FR-0747KL",.10,{1:"VUSB_BUFFER_IN",2:"GND"},"https://www.digikey.com/en/products/detail/yageo/RC0603FR-0747KL/727253"],
    ["U1","buffer","SN74LVC1G17DBVRG4","SN74LVC1G17DBVRG4",.71,{1:null,2:"VUSB_BUFFER_IN",3:"GND",4:"VBUS_PRESENT",5:"3V3"},"https://www.digikey.com/en/products/detail/texas-instruments/SN74LVC1G17DBVRG4/4833886"],
    ["C1","cap","100n VBUS","KGM15BR71H104KT",.10,{1:"3V3",2:"GND"},"https://www.digikey.com/en/products/detail/kyocera-avx/KGM15BR71H104KT/1600046"],
    ["J4","conn3","TEENSY_LOGIC","TSW-103-07-G-S",.30,{1:"3V3",2:"GND",3:"VBUS_PRESENT"},"https://www.digikey.com/en/products/detail/samtec-inc/TSW-103-07-G-S/1101324"],
    ["J5","conn2","SLOW_LED_GPIO","TSW-102-07-G-S",.21,{1:"LED_GPIO",2:"GND"},"https://www.digikey.com/en/products/detail/samtec-inc/TSW-102-07-G-S/1101325"],
    ["R2","res","1k LED","RC0603FR-071KL",.10,{1:"LED_GPIO",2:"LED_ANODE"},"https://www.digikey.com/en/products/detail/yageo/RC0603FR-071KL/726843"],
    ["D1","led","RED_LED slow","WP7113ID",.21,{1:"LED_ANODE",2:"GND"},"https://www.digikey.com/en/products/detail/kingbright/WP7113ID/1747663"]
  ].map(([ref,kind,value,mpn,price,pins,url])=>({ref,kind,value,mpn,price,pins,url}));
  const styles={
    opa:[["3","+IN","L",5.08,"input"],["4","-IN","L",-2.54,"input"],["1","OUT","R",5.08,"output"],["2","V-","R",0,"power_in"],["5","V+","R",-5.08,"power_in"]],
    cmp:[["1","-IN","L",5.08,"input"],["3","+IN","L",0,"input"],["6","SHDN","L",-5.08,"input"],["4","V+","R",5.08,"power_in"],["5","OUT","R",0,"output"],["2","V-","R",-5.08,"power_in"]],
    diode:[["1","A","L",0,"passive"],["2","K","R",0,"passive"]],led:[["1","A","L",0,"passive"],["2","K","R",0,"passive"]],
    res:[["1","1","L",0,"passive"],["2","2","R",0,"passive"]],cap:[["1","1","L",0,"passive"],["2","2","R",0,"passive"]],
    pot:[["1","1","L",5.08,"passive"],["2","W","L",0,"passive"],["3","3","R",0,"passive"]],
    conn2:[["1","1","L",2.54,"passive"],["2","2","L",-2.54,"passive"]],
    conn3:[["1","1","L",5.08,"passive"],["2","2","L",0,"passive"],["3","3","L",-5.08,"passive"]],
    conn4:[["1","1","L",5.08,"passive"],["2","2","L",2.54,"passive"],["3","3","L",-2.54,"passive"],["4","4","L",-5.08,"passive"]],
    module3:[["1","VIN","L",5.08,"input"],["2","VOUT","R",5.08,"output"],["3","GND","L",-5.08,"power_in"]],
    buffer:[["1","NC","L",5.08,"passive"],["2","A","L",0,"input"],["3","GND","L",-5.08,"power_in"],["4","Y","R",2.54,"output"],["5","VCC","R",-2.54,"power_in"]],
    tp:[["1","TP","L",0,"passive"]]
  };
  const symbolDef=(kind,embedded=true)=>{
    const name=kind.toUpperCase(),ref=kind==="res"?"R":kind==="cap"?"C":kind==="tp"?"TP":kind==="diode"||kind==="led"?"D":kind.startsWith("conn")?"J":kind==="module3"?"M":kind==="pot"?"RV":"U";
    const pins=styles[kind].map(([num,label,side,y,type])=>`(pin ${type} line (at ${side==="L"?"-10.16":"10.16"} ${f(y)} ${side==="L"?0:180}) (length 2.54) (name ${q(label)} (effects (font (size 1.016 1.016)))) (number ${q(num)} (effects (font (size 1.016 1.016)))))`).join("\n");
    return `(symbol ${q(embedded?"Proto:"+name:name)} (pin_names (offset 0.508)) (in_bom yes) (on_board yes) (property "Reference" ${q(ref)} (at 0 10.16 0) (effects (font (size 1.27 1.27)))) (property "Value" ${q(name)} (at 0 -10.16 0) (effects (font (size 1.27 1.27)))) (symbol ${q(name+"_0_1")} (rectangle (start -7.62 7.62) (end 7.62 -7.62) (stroke (width 0.254) (type default)) (fill (type background)))) (symbol ${q(name+"_1_1")} ${pins}))`;
  };

  // Project-local KiCad power-out annotation, not a physical part.
  const powerFlagDef=`(symbol "Proto:PWR_FLAG" (power) (pin_numbers (hide yes)) (pin_names (offset 0) (hide yes))
    (in_bom no) (on_board no)
    (property "Reference" "#FLG" (at 0 1.905 0) (effects (font (size 1.27 1.27)) (hide yes)))
    (property "Value" "PWR_FLAG" (at 0 3.81 0) (effects (font (size 1.27 1.27))))
    (property "Footprint" "" (at 0 0 0) (effects (font (size 1.27 1.27)) (hide yes)))
    (symbol "PWR_FLAG_0_0" (pin power_out line (at 0 0 90) (length 0)
      (name "pwr" (effects (font (size 1.27 1.27)))) (number "1" (effects (font (size 1.27 1.27))))))
    (symbol "PWR_FLAG_0_1" (polyline (pts (xy 0 0) (xy 0 1.27) (xy -1.016 1.905) (xy 0 2.54) (xy 1.016 1.905) (xy 0 1.27))
      (stroke (width 0.254) (type default)) (fill (type none)))))`;
  const commonKinds=[...new Set([...parts,...power].map(p=>p.kind))];
  const protoSymbolLibrary=`(kicad_symbol_lib (version 20241209) (generator "screen_latency_prototype")
${commonKinds.map(kind=>symbolDef(kind,false)).join("\n")}
${powerFlagDef.replace('(symbol "Proto:PWR_FLAG"', '(symbol "PWR_FLAG"')}
)
`;
  const projectSymbolTable='(sym_lib_table (lib (name "Proto") (type "KiCad") (uri "'+
    String.fromCharCode(36)+'{KIPRJMOD}/../Proto.kicad_sym") (options "") (descr "Generated Proto symbol library")))\n';

  // BOM and KiCad symbol properties are generated from the same sourced part rows.
  // Unchecked lifecycle is not silently treated as active or orderable.
  const datasheets={
    "OPA320AIDBVR":"https://www.ti.com/lit/ds/symlink/opa320.pdf",
    "TLV3501AIDBVR":"https://www.ti.com/lit/ds/symlink/tlv3501.pdf",
    "SFH 203":"https://media.digikey.com/pdf/Data%20Sheets/Osram%20PDFs/SFH_203_Ver1.3_12-23-15.pdf",
    "SN74LVC1G17DBVRG4":"https://www.ti.com/lit/ds/symlink/sn74lvc1g17.pdf"
  };
  const manufacturers={
    "OPA320AIDBVR":"Texas Instruments","TLV3501AIDBVR":"Texas Instruments",
    "SN74LVC1G17DBVRG4":"Texas Instruments","SFH 203":"ams OSRAM",
    "5383":"Pololu","MF-R050":"Bourns"
  };
  const verifiedActive=new Set(["OPA320AIDBVR","TLV3501AIDBVR","SN74LVC1G17DBVRG4","SFH 203","5383"]);
  const nonPurchasable=p=>p.kind==="tp"||p.mpn==="Teensy OEM copper test pads";
  const lifecycle=p=>nonPurchasable(p)?"N/A":verifiedActive.has(p.mpn)?"ACTIVE_VERIFIED_2026-10-31":"UNVERIFIED_BEFORE_ORDER";
  if(parts.some(p=>p.mpn==="SFH 203 P"||p.fp?.includes("SFH203P")))throw Error("Last-time-buy photodiode forbidden");
  const schematic=(rows,project)=>{
    const root=uid(),defs=[...new Set(rows.map(p=>p.kind))].map(kind=>symbolDef(kind,true)).join("\n")+"\n"+powerFlagDef,instances=[],wires=[],labels=[],nc=[];
    for(let i=0;i<rows.length;i++){
      const p=rows[i],x=[53.34,149.86,246.38,342.90][i%4],y=41.91+Math.floor(i/4)*38.1,id=uid();p.schUUID=id;
      const prop=(name,value,dy,size)=>`(property ${q(name)} ${q(value)} (at ${f(x)} ${f(y+dy)} 0) (effects (font (size ${size} ${size}))))`;

      const meta=[];
      if(!nonPurchasable(p)&&p.mpn)meta.push(["MPN",p.mpn]);
      if(manufacturers[p.mpn])meta.push(["Manufacturer",manufacturers[p.mpn]]);
      if(p.url)meta.push(["Source URL",p.url]);
      if(datasheets[p.mpn])meta.push(["Datasheet",datasheets[p.mpn]]);
      if(!nonPurchasable(p))meta.push(["Lifecycle",lifecycle(p)]);
      if(project==="one-sensor"&&p.ref==="D1")meta.push(["BOM Comments","SFH 203 active, A1=SUM K2=3V3; 40deg angle and boot require calibration"]);
      if(project==="power-usb"&&p.ref==="R3")meta.push(["BOM Comments","47k bias to GND; VBUS disconnected state and Ioff require bench verification"]);
      if(p.dnp)meta.push(["BOM Comments","DNP by default: comparator intrinsic hysteresis not characterized"]);
      const metaProps=meta.map(([name,value],j)=>`(property ${q(name)} ${q(value)} (at ${f(x)} ${f(y+19.05+j*2.54)} 0) (effects (font (size 1 1)) (hide yes)))`).join(" ");
      instances.push(`(symbol (lib_id ${q("Proto:"+p.kind.toUpperCase())}) (at ${f(x)} ${f(y)} 0) (unit 1) (in_bom ${p.kind==="tp"?"no":"yes"}) (on_board yes) (dnp ${p.dnp?"yes":"no"}) (uuid ${q(id)}) ${prop("Reference",p.ref,-12.7,1.27)} ${prop("Value",p.value,12.7,1)} ${p.fp?`(property "Footprint" ${q(p.fp)} (at ${f(x)} ${f(y+15.24)} 0) (effects (font (size 1 1)) (hide yes)))`:""} ${metaProps} ${styles[p.kind].map(([number])=>`(pin ${q(number)} (uuid ${q(uid())}))`).join("")} (instances (project ${q(project)} (path ${q("/"+root)} (reference ${q(p.ref)}) (unit 1)))))`);
      for(const [num,,side,dy] of styles[p.kind]){const net=p.pins[num],sx=x+(side==="L"?-10.16:10.16),sy=y-dy,wx=sx+(side==="L"?-5.08:5.08);if(net==null){nc.push(`(no_connect (at ${f(sx)} ${f(sy)}) (uuid ${q(uid())}))`);continue;}wires.push(`(wire (pts (xy ${f(sx)} ${f(sy)}) (xy ${f(wx)} ${f(sy)})) (stroke (width 0) (type default)) (uuid ${q(uid())}))`);labels.push(`(label ${q(net)} (at ${f(wx)} ${f(sy)} 0) (effects (font (size 1.016 1.016)) (justify left bottom)) (uuid ${q(uid())}))`);}
    }

    // ERC assumption: externally provided 3V3 and common ground at J1/J4.
    const externalRails=project==="one-sensor"
      ? [["3V3",53.34,266.7],["GND",149.86,266.7]]
      : [["3V3",53.34,194.31],["GND",149.86,194.31]];
    for(let i=0;i<externalRails.length;i++){
      const [net,x,y]=externalRails[i],ref="#FLG0"+(i+1);
      instances.push(`(symbol (lib_id "Proto:PWR_FLAG") (at ${f(x)} ${f(y)} 0) (unit 1)
        (in_bom no) (on_board no) (uuid ${q(uid())})
        (property "Reference" ${q(ref)} (at ${f(x)} ${f(y-2.54)} 0) (effects (font (size 1.27 1.27)) (hide yes)))
        (property "Value" "PWR_FLAG" (at ${f(x)} ${f(y-5.08)} 0) (effects (font (size 1.27 1.27))))
        (pin "1" (uuid ${q(uid())}))
        (instances (project ${q(project)} (path ${q("/"+root)} (reference ${q(ref)}) (unit 1)))))`);
      labels.push(`(label ${q(net)} (at ${f(x)} ${f(y)} 0)
        (effects (font (size 1.016 1.016)) (justify left bottom)) (uuid ${q(uid())}))`);
    }
    const title=project==="one-sensor"?"One-sensor optical front end - unqualified prototype":"Teensy external power + VUSB sense - candidate wiring";
    const txt=`(kicad_sch (version 20250114) (generator "screen_latency_prototype") (generator_version "0.1") (uuid ${q(root)}) (paper "A3") (title_block (title ${q(title)}) (date "2026-10-31") (rev "P0") (comment 1 "DRAFT - ERC and electrical review required; NO FABRICATION")) (lib_symbols ${defs}) ${wires.join("\n")} ${labels.join("\n")} ${nc.join("\n")} ${instances.join("\n")} (sheet_instances (path "/" (page "1"))))\n`;
    return {txt,root};
  };
  const os=schematic(parts,"one-sensor"),ps=schematic(power,"power-usb");
  // This placement seed is retained only for schematic-to-pad consistency checks. It is not
  // emitted: copper, zones and routes in one-sensor.kicad_pcb must survive regeneration.
  const pads={
    opa:[["1",-1.1375,-.95,"smd"],["2",-1.1375,0,"smd"],["3",-1.1375,.95,"smd"],["4",1.1375,.95,"smd"],["5",1.1375,-.95,"smd"]],
    cmp:[["1",-1.1375,-.95,"smd"],["2",-1.1375,0,"smd"],["3",-1.1375,.95,"smd"],["4",1.1375,.95,"smd"],["5",1.1375,0,"smd"],["6",1.1375,-.95,"smd"]],
    diode:[["1",0,0,"thru_hole"],["2",2.54,0,"thru_hole"]],
    res:[["1",-.775,0,"smd"],["2",.775,0,"smd"]],cap:[["1",-.775,0,"smd"],["2",.775,0,"smd"]],
    pot:[["1",0,0,"thru_hole"],["2",-2.54,0,"thru_hole"],["3",-5.08,0,"thru_hole"]],
    conn4:[["1",0,0,"thru_hole"],["2",0,2.54,"thru_hole"],["3",0,5.08,"thru_hole"],["4",0,7.62,"thru_hole"]],
    tp:[["1",0,0,"smd"]]
  };
  const nets=[...new Set(parts.flatMap(p=>Object.values(p.pins).filter(Boolean)))].sort(),netMap=new Map(nets.map((n,i)=>[n,i+1]));
  const footprints=parts.map(p=>{
    const k=p.kind,bbox=k==="pot"?[-7.305,-2.41,2.225,2.42]:k==="diode"?[-1.25,-2.6,3.79,2.6]:k==="conn4"?[-1.25,-1.25,1.25,8.87]:k==="tp"?[-1,-1,1,1]:k==="opa"||k==="cmp"?[-1.5,-.75,1.5,.75]:[-1.65,-.68,1.65,.68];
    const pp=pads[k].map(([num,x,y,type])=>{const net=p.pins[num],n=net?` (net ${netMap.get(net)} ${q(net)})`:"";if(type==="thru_hole")return `(pad ${q(num)} thru_hole ${k==="diode"&&num==="1"?"rect":"circle"} (at ${f(x)} ${f(y)}) (size ${k==="diode"?"2 2":"1.5 1.5"}) (drill ${k==="diode"?"1":"0.85"}) (layers "*.Cu" "*.Mask")${n} (uuid ${q(uid())}))`;if(k==="tp")return `(pad ${q(num)} smd circle (at 0 0) (size 1.5 1.5) (layers "F.Cu" "F.Mask")${n} (uuid ${q(uid())}))`;return `(pad ${q(num)} smd roundrect (at ${f(x)} ${f(y)}) (size ${k==="res"||k==="cap"?"0.9 0.9":"1.2 0.65"}) (layers "F.Cu" "F.Paste" "F.Mask") (roundrect_rratio 0.18)${n} (uuid ${q(uid())}))`;}).join("\n");
    const refLoc=bbox[1]-1.6,valLoc=bbox[3]+1.8;
    return `(footprint ${q(p.fp)} (layer "F.Cu") (uuid ${q(uid())}) (at ${p.at[0]} ${p.at[1]} 0) (property "Reference" ${q(p.ref)} (at 0 ${f(refLoc)} 0) (layer "F.SilkS") (effects (font (size 0.85 0.85) (thickness 0.12))) (uuid ${q(uid())})) (property "Value" ${q(p.value)} (at 0 ${f(valLoc)} 0) (layer "F.Fab") (effects (font (size 0.7 0.7) (thickness 0.1))) (uuid ${q(uid())})) (path ${q("/"+os.root+"/"+p.schUUID)}) (attr ${["diode","pot","conn4"].includes(k)?"through_hole":"smd"}${p.dnp?" dnp":""}) (fp_rect (start ${bbox[0]} ${bbox[1]}) (end ${bbox[2]} ${bbox[3]}) (stroke (width 0.1) (type default)) (fill none) (layer "F.Fab") (uuid ${q(uid())})) ${k==="diode"?`(fp_text user "K" (at 3.6 -3.2) (layer "F.SilkS") (effects (font (size 0.75 0.75) (thickness 0.12))) (uuid ${q(uid())}))`:""} ${pp})`;
  }).join("\n");
  const edges=[[100,100,150,100],[150,100,150,144],[150,144,100,144],[100,144,100,100]].map(([x1,y1,x2,y2])=>`(gr_line (start ${x1} ${y1}) (end ${x2} ${y2}) (stroke (width 0.05) (type default)) (layer "Edge.Cuts") (uuid ${q(uid())}))`).join("\n");
  const board=`(kicad_pcb (version 20240108) (generator "screen_latency_prototype") (general (thickness 1.6)) (paper "A4") (layers (0 "F.Cu" signal) (31 "B.Cu" signal) (32 "B.Adhes" user) (33 "F.Adhes" user) (34 "B.Paste" user) (35 "F.Paste" user) (36 "B.SilkS" user) (37 "F.SilkS" user) (38 "B.Mask" user) (39 "F.Mask" user) (40 "Dwgs.User" user) (41 "Cmts.User" user) (44 "Edge.Cuts" user) (46 "B.CrtYd" user) (47 "F.CrtYd" user) (48 "B.Fab" user) (49 "F.Fab" user)) (setup (pad_to_mask_clearance 0)) (net 0 "") ${nets.map(n=>`(net ${netMap.get(n)} ${q(n)})`).join("\n")} ${footprints} ${edges} (gr_text "PLACE ONLY - NO COPPER ROUTING" (at 125 141) (layer "F.SilkS") (effects (font (size 1 1) (thickness 0.15))) (uuid ${q(uid())})) (gr_text "DRAFT P0 - NO GERBERS" (at 125 103) (layer "F.SilkS") (effects (font (size 1 1) (thickness 0.15))) (uuid ${q(uid())})))\n`;
  const fp=`(footprint "Photodiode_SFH203" (version 20240108) (generator "screen_latency_prototype") (layer "F.Cu") (descr "SFH 203 radial photodiode; 2.54 mm pitch, A1/K2, 2.0 mm lands, 1.0 mm drills; 5.90 mm maximum flange shown, 6.40 mm courtyard; verify installed cathode marking and provisional boot fit") (property "Reference" "D1" (at 1.27 -4.15 0) (layer "F.SilkS") (effects (font (size 1 1) (thickness 0.15)))) (property "Value" "SFH 203" (at 1.27 4.2 0) (layer "F.Fab") (effects (font (size 0.85 0.85) (thickness 0.12)))) (attr through_hole) (fp_circle (center 1.27 0) (end 4.22 0) (stroke (width 0.12) (type default)) (fill none) (layer "F.SilkS")) (fp_circle (center 1.27 0) (end 4.195 0) (stroke (width 0.1) (type solid)) (fill none) (layer "F.Fab")) (fp_rect (start -1.93 -3.2) (end 4.47 3.2) (stroke (width 0.05) (type default)) (fill none) (layer "F.CrtYd")) (fp_text user "K" (at 4.2 3) (layer "F.SilkS") (effects (font (size 0.85 0.85) (thickness 0.12)))) (pad "1" thru_hole rect (at 0 0) (size 2 2) (drill 1.0) (layers "*.Cu" "*.Mask")) (pad "2" thru_hole circle (at 2.54 0) (size 2 2) (drill 1.0) (layers "*.Cu" "*.Mask")))\n`;
  const fpTable=`(fp_lib_table (lib (name "ProtoOpto") (type "KiCad") (uri "\${KIPRJMOD}/ProtoOpto.pretty") (options "") (descr "Draft SFH203 A1/K2 2.54mm footprint; 5.90mm maximum flange, 6.40mm courtyard for fit review")))\n`;
  const ext=[
    {group:"offboard kit",ref:"EXT1",value:"Teensy 4.1 with headers",mpn:"DEV-20360",price:33.60,url:"https://www.sparkfun.com/teensy-4-1-without-ethernet-headers.html"},
    {group:"offboard kit",ref:"EXT2",value:"5V/3A adapter",mpn:"GST18U05-P1J",price:14.10,url:"https://www.digikey.com/en/products/detail/mean-well-usa-inc/GST18U05-P1J/7703631"},
    {group:"offboard kit",ref:"EXT3",value:"digital breadboard",mpn:"Adafruit 239",price:5.95,url:"https://www.adafruit.com/product/239"},
    {group:"offboard kit",ref:"EXT4",value:"USB C to USB A adapter",mpn:"Adafruit 5030",price:.95,url:"https://www.adafruit.com/product/5030"},
    {group:"offboard kit",ref:"EXT5",value:"USB A to Micro B data cable",mpn:"Adafruit 592",price:2.95,url:"https://www.adafruit.com/product/592"}
  ];
  const rowObj=[
    ...parts.map(p=>({...p,group:"optical sensor"})),
    ...power.map(p=>({...p,group:"external power + USB"})),
    ...ext
  ];
  const cols=["group","ref","value","mpn","qty","footprint","unit_usd_snapshot","extended_usd_snapshot","source_url","dnp","notes","lifecycle_status"];
  const csvQuote=x=>q(x??"").replace(/\\n/g," ");
  const bom=[cols.map(csvQuote).join(","),...rowObj.map(p=>{const o={group:p.group,ref:p.ref,value:p.value,mpn:p.mpn,qty:1,footprint:p.fp??"offboard/non-PCB",unit_usd_snapshot:p.price==null?"":p.price.toFixed(2),extended_usd_snapshot:p.price==null?"":p.price.toFixed(2),source_url:p.url??"",dnp:p.dnp?"YES":"NO",notes:p.kind==="tp"?"bare pad; no purchased part":p.price==null?"price not verified":p.dnp?"not populated by default":"2026-10-31 product listing; recheck before purchase",lifecycle_status:p.group==="offboard kit"?"UNVERIFIED_BEFORE_ORDER":lifecycle(p)};return cols.map(k=>csvQuote(o[k])).join(",");})].join("\n")+"\n";
  function balanced(t){let n=0,inQ=false,esc=false;for(const c of t){if(inQ){if(esc)esc=false;else if(c==="\\")esc=true;else if(c==='"')inQ=false;}else if(c==='"')inQ=true;else if(c==="(")n++;else if(c===")")n--;if(n<0)throw Error("bad s-exp close");}if(n||inQ)throw Error("bad s-exp balance");}
  const files={
    "hardware/prototype/optical/one-sensor.kicad_sch":os.txt,
    "hardware/prototype/Proto.kicad_sym":protoSymbolLibrary,
    "hardware/prototype/optical/sym-lib-table":projectSymbolTable,
    "hardware/prototype/power-usb/sym-lib-table":projectSymbolTable,
    "hardware/prototype/optical/ProtoOpto.pretty/Photodiode_SFH203.kicad_mod":fp,
    "hardware/prototype/optical/fp-lib-table":fpTable,
    "hardware/prototype/power-usb/power-usb.kicad_sch":ps.txt,
    "hardware/prototype/BOM.csv":bom
  };
  for(const [path,t] of Object.entries(files))if(!path.endsWith(".csv"))balanced(t);
  const expected={"U1:4":"SUM","D1:1":"SUM","D1:2":"3V3","U2:6":"GND","U2:4":"3V3","U2:5":"CMP_OUT"};
  for(const [refPin,net] of Object.entries(expected)){const [ref,pin]=refPin.split(":"),p=parts.find(x=>x.ref===ref);if(p.pins[pin]!==net)throw Error("pin/net mismatch "+refPin);}
  for(const p of parts){const a=Object.keys(p.pins).sort().join(","),b=pads[p.kind].map(x=>x[0]).sort().join(",");if(a!==b)throw Error("schematic/PCB mismatch "+p.ref);}
  if(power.find(p=>p.ref==="U1").pins[4]!=="VBUS_PRESENT")throw Error("VBUS buffer output pin failure");
   if(power.find(p=>p.ref==="R3")?.pins[1]!=="VUSB_BUFFER_IN"||power.find(p=>p.ref==="R3")?.pins[2]!=="GND")throw Error("VBUS pull-down missing");
  return {files,stats:{opticalParts:parts.length,powerParts:power.length,nets:nets.length,nativeBoardAuthoritative:true,sourcePaths:Object.keys(files),totalBytes:Object.values(files).reduce((n,x)=>n+x.length,0)}};
};
const root=resolve(dirname(fileURLToPath(import.meta.url)),"../..");
const result=generatePrototype();
const checkOnly=process.argv.includes("--check");
let stale=0;
for(const [path,contents] of Object.entries(result.files)) {
  const absolute=resolve(root,path);
  if(checkOnly) {
    let onDisk;
    try { onDisk=readFileSync(absolute,"utf8"); } catch { onDisk=null; }
    if(onDisk!==contents) { console.error(`STALE or missing generated file: ${path}`); stale++; }
  } else { mkdirSync(dirname(absolute),{recursive:true});writeFileSync(absolute,contents,"utf8"); }
}
if(checkOnly && stale) process.exitCode=1;
console.log(JSON.stringify({...result.stats,checkOnly,stale},null,2));
