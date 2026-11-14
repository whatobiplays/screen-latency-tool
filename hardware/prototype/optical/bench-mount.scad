// Dimensioned noncontact bridge. Units mm. This file contains no external dependencies.
// The board remains nominal 1.6; received_board_t may range from 1.4 to the 2.0 holder envelope.
part="assembly";
glass_h=32;
received_board_t=1.6;
bundle_d=3.0;
$fn=48;
B=glass_h+45;
BY=36;
T=received_board_t;
spacer_h=T+0.10;
slide_x=[-40.77,77.23];
carrier_bolt_x=[-60.77,97.23];
slide_bottom=B-5;
stop_pin=slide_bottom>=69?65:55;
shim_h=slide_bottom-(stop_pin+4);
assert(glass_h>=20 && glass_h<=38);
assert(T>=1.4 && T<=2.0);
assert(bundle_d>=2.5 && bundle_d<=4.0);
module bore(h,d=6.8){cylinder(h=h,d=d);}
module ybore(h,d=6.8){rotate([-90,0,0])bore(h,d);}
module beam(){difference(){translate([-130,BY-20,100])cube([260,40,25]);
 for(x=[-120,120])for(y=[BY-10,BY+10]){translate([x,y,99])bore(27);translate([x,y,119])bore(7,14);}
 for(x=slide_x){translate([x,BY+5,99])bore(27);translate([x,BY+5,119])bore(7,14);}
}}
module tower_right(){difference(){union(){translate([110,BY-70,0])cube([70,140,12]);translate([110,BY-20,12])cube([20,40,88]);translate([128,40,52])cube([18,17.2,8]);translate([48,27,78])saddle_lower_local();}
 for(y=[BY-10,BY+10]){translate([120,y,79])bore(23);translate([113.8,y-6.4,87.2])cube([17.2,12.8,5.8]);}
}}
module shroud_local(){difference(){translate([6.77,10.5,-30])cylinder(h=18,r1=19,r2=10);
 translate([6.77,10.5,-30.01])cylinder(h=18.02,r1=17.005,r2=7.995);}}
module carrier_local(){difference(){union(){
 // Two 17.7 x 16 side members; crossbars sit above trimmed backside leads.
 translate([-18,-.8,-16])cube([17.7,109.8,16]);translate([50.3,-.8,-16])cube([17.7,109.8,16]);
 for(y=[8,101]){
 translate([-18,y,5])cube([86,8,10]);
 for(x=[-18,60])translate([x,y,-16])cube([8,8,31]);
 }
 for(y=[24,40]){
 translate([-.5,y,-2])cube([1.5,4,2]);translate([49,y,-2])cube([1.5,4,2]);
 }
 // Edge stops touch only the outline; nominal XY opening 50.6 x 44.6.
 for(x=[0,49]){
 for(y=[-.8,44.3]){translate([x-.5,y,-2])cube([2,.5,2]);translate([x,y,0])cube([1,.5,T+.15]);}
 }
 // Rear ears are separated from retainer heads and horizontal slide bolts.
 translate([-62,101,9])cube([44,8,8]);translate([68,101,9])cube([44,8,8]);
 // Stand-carried shroud arm: all material remains outside the nominal response cone.
 translate([-25,10.5,-14])cube([8,5.5,29]);translate([-25,8.5,-14])cube([22,4,2]);
 // Closed outboard cable tunnel with room for a radius-10 centreline turn.
 translate([48,7,-24])cube([46,8,14]);
 translate([70,7,-24])cube([24,23,14]);
 translate([82,6,-24])cube([12,24,14]);
 translate([66,14,-14])cube([16,4,10]);
 // Integral lower saddle, 20 mm contact length; head pockets keep metal above d=30.
 translate([74,30,-26])cube([28,20,8]);
 }
 for(x=[-10,60])for(y=[26,42])translate([x,y,-17])bore(18);
 for(x=[-54,104])translate([x,105,8])bore(10);
 // Tunnel voids join continuously; they are not three sharp 8 mm elbows.
 translate([47,9,-22])cube([45,4,10]);translate([72,9,-22])cube([20,20,10]);translate([84,5,-22])cube([8,32,10]);
 translate([88,29,-18])ybore(22,bundle_d-.10);
 for(x=[78,98]){translate([x,40,-27])bore(10);translate([x,40,-27])bore(6,14);}
 }
 shroud_local();
}
module spacer_left_local(){difference(){translate([-18,20,0])cube([17.7,12,spacer_h]);translate([-10,26,-1])bore(spacer_h+2);}}
module cap_left_local(){difference(){union(){translate([-18,20,spacer_h])cube([17.7,12,4]);translate([-.5,24,spacer_h])cube([1.5,4,2]);}translate([-10,26,spacer_h-1])bore(6);}}
module clip_group_local(){for(y=[0,16]){
 translate([0,y,0]){spacer_left_local();cap_left_local();}
 translate([50,y,0])mirror([1,0,0]){spacer_left_local();cap_left_local();}
}}
module bracket_at(x){translate([x,0,0])difference(){union(){translate([-10,BY-20,92])cube([20,40,8]);translate([-10,BY+15,42])cube([20,8,50]);}
 translate([0,BY+5,91])bore(10);
 for(z=[55,65,75,84])translate([0,BY+14,z])ybore(10);
}}
module slide_at(x,s){translate([x,BY+23,slide_bottom])difference(){union(){translate([-15,0,0])cube([30,8,45]);
 // Outward-offset foot avoids the orthogonal slot bolt at every allowed height.
 translate([s*20-12,0,2])cube([24,46,12]);}
 hull(){translate([0,-1,5])ybore(10);translate([0,-1,35])ybore(10);}
 translate([s*20,35.5,1])bore(14);
}}
module stop_at(x){translate([x,BY+29,stop_pin])difference(){union(){
 translate([-10,-6,-4])cube([20,12,8]);
 // Captive shim pocket: front is bounded by the fixed web; side/rear fences clear the slide by .3.
 for(q=[-11.5,10.3])translate([q,-6,2])cube([1.2,9.5,shim_h+1.5]);
 translate([-11.5,2.3,2])cube([23,1.2,shim_h+1.5]);
 }translate([0,-7,0])ybore(14);}}
module shim_at(x){if(shim_h>0)translate([x-10,BY+23,stop_pin+4])cube([20,8,shim_h]);}
module saddle_lower_local(){difference(){translate([74,30,-26])cube([28,20,8]);
 translate([88,29,-18])ybore(22,bundle_d-.1);
 for(x=[78,98]){translate([x,40,-27])bore(10);translate([x,40,-27])bore(6,14);}
}}
module saddle_top_local(){difference(){translate([74,30,-18])cube([28,20,8]);translate([88,29,-18])ybore(22,bundle_d-.1);for(x=[78,98])translate([x,40,-19])bore(10);}}
module local_frame(){translate([-6.77,-10.5,B])children();}
module board_and_components(){local_frame(){
 cube([50,44,T]);
 translate([6.77,10.5,-9.4])cylinder(h=9.4,d=5.9);
 translate([30.4,15.3,-13])cube([10.1,5.4,13]);
 translate([42.0,5.8,-8])cube([4.9,10.4,8]);
 for(q=[[11.8,8.5],[24,8]])translate([q[0]-2,q[1]-2,-2])cube([4,4,2]);
}}
// Fastener envelopes use OD13 x2 washers, OD12 x5 heads, AF10.2 x5.5 nuts and full-length M6 shafts.
module washer(){difference(){cylinder(h=2,d=13);translate([0,0,-.1])bore(2.2,6.6);}}
module nut(){difference(){cylinder(h=5.5,r=10.2/sqrt(3),$fn=6);translate([0,0,-.1])bore(5.7,6.1);}}
module fastener_down(L,G,bottom_washer=true,captive_top=0){
 cylinder(h=5,d=12);translate([0,0,-L])bore(L,6);
 translate([0,0,-2])washer();
 if(bottom_washer){translate([0,0,-G-4])washer();translate([0,0,-G-9.5])nut();}
 else translate([0,0,captive_top-5.5])nut();
}
hardware_id=-1;
// [position, rotation, shaft length, material grip, lower washer, captive nut upper plane]
fasteners=concat(
 [for(x=[126,146]) [[x,67,55],[180,0,0],30,11,true,0]],
 [for(x=[-120,120])for(y=[BY-10,BY+10]) [[x,y,121],[0,0,0],40,26,false,-28]],
 [for(x=slide_x) [[x,BY+5,121],[0,0,0],40,27,true,0]],
 [for(i=[0:1])let(x=slide_x[i]) [[x,BY+13,84],[90,0,0],40,16,true,0]],
 [for(i=[0:1])let(x=slide_x[i]) [[x,BY+13,stop_pin],[90,0,0],40,20,true,0]],
 [for(i=[0:1])let(x=slide_x[i],s=i==0?-1:1) [[x+s*20,BY+58.5,B+19],[0,0,0],35,20,true,0]],
 [for(x=[-10,60])for(y=[26,42]) [[x-6.77,y-10.5,B+spacer_h+6],[0,0,0],35,spacer_h+20,true,0]],
 [for(x=[78,98]) [[x-6.77,29.5,B-23],[180,0,0],30,11,true,0]]
);
module hardware(){for(i=[0:len(fasteners)-1])if(hardware_id<0 || hardware_id==i)let(f=fasteners[i])translate(f[0])rotate(f[1])fastener_down(f[2],f[3],f[4],f[5]);}
module group(i){
 if(i==0)beam();else if(i==1)tower_right();else if(i==2)mirror([1,0,0])tower_right();
 else if(i==3)local_frame()carrier_local();else if(i==4)local_frame()clip_group_local();
 else if(i==5)for(i=[0:1])slide_at(slide_x[i],i==0?-1:1);
 else if(i==6)for(x=slide_x)bracket_at(x);
 else if(i==7)for(x=slide_x){stop_at(x);shim_at(x);}
 else if(i==8){local_frame()saddle_top_local();translate([48,27,78])saddle_top_local();}else if(i==9)hardware();
 else if(i==10)board_and_components();
}
module plastics(){for(i=[0:8])group(i);}
module sentinel(){translate([1000,1000,1000])cube(1);}
if(part=="beam")translate([130,20-BY,-100])beam();
else if(part=="tower")translate([-110,70-BY,0])tower_right();
else if(part=="carrier")translate([62,1,30])carrier_local();
else if(part=="retainer")translate([18,-20,-spacer_h])cap_left_local();
else if(part=="spacer")translate([18,-20,0])spacer_left_local();
else if(part=="bracket")translate([10,20-BY,-42])bracket_at(0);
else if(part=="slide_left")translate([32,-BY-23,-slide_bottom])slide_at(0,-1);
else if(part=="slide_right")translate([15,-BY-23,-slide_bottom])slide_at(0,1);
else if(part=="stop")translate([11.5,6-BY-29,4-stop_pin])stop_at(0);
else if(part=="shim")translate([10,-BY-23,-stop_pin-4])shim_at(0);
else if(part=="saddle_top")translate([-74,-30,18])saddle_top_local();
else if(part=="hardware")hardware();
else if(part=="interference"){sentinel();intersection(){hardware();plastics();}}
else if(part=="pair_check"){sentinel();intersection(){group(check_a);group(check_b);}}
else if(part=="assembly"){for(i=[0:10])color(i==9?"silver":i==10?"green":i==11?"orange":"gray")group(i);}
