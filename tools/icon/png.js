// Minimal PNG reader/writer (8-bit, no interlace), no dependencies.
const fs=require('fs'),zlib=require('zlib');
exports.read=function(f){const b=fs.readFileSync(f);let p=8,ih,id=[];
while(p<b.length){const l=b.readUInt32BE(p),t=b.toString('ascii',p+4,p+8),d=b.slice(p+8,p+8+l);if(t=='IHDR')ih=d;if(t=='IDAT')id.push(d);p+=12+l;}
const w=ih.readUInt32BE(0),h=ih.readUInt32BE(4),ct=ih[9],bpp={6:4,2:3,0:1,4:2}[ct];
if(ih[8]!=8)throw 'depth';const raw=zlib.inflateSync(Buffer.concat(id)),out=Buffer.alloc(w*h*4),st=w*bpp;let prev=Buffer.alloc(st),q=0;
for(let y=0;y<h;y++){const ft=raw[q++],line=Buffer.from(raw.slice(q,q+st));q+=st;
for(let i=0;i<st;i++){const a=i>=bpp?line[i-bpp]:0,up=prev[i],c=i>=bpp?prev[i-bpp]:0;let v=line[i];
switch(ft){case 1:v+=a;break;case 2:v+=up;break;case 3:v+=(a+up)>>1;break;case 4:{const pp=a+up-c,pa=Math.abs(pp-a),pb=Math.abs(pp-up),pc=Math.abs(pp-c);v+=(pa<=pb&&pa<=pc)?a:(pb<=pc?up:c);}}
line[i]=v&255;}
for(let x=0;x<w;x++){const s=x*bpp,o=(y*w+x)*4;if(bpp>=3){out[o]=line[s];out[o+1]=line[s+1];out[o+2]=line[s+2];out[o+3]=bpp==4?line[s+3]:255;}else{out[o]=out[o+1]=out[o+2]=line[s];out[o+3]=bpp==2?line[s+1]:255;}}
prev=line;}return {w,h,px:out};};
function crc(b){let c,cr=~0;for(const x of b){c=(cr^x)&255;for(let k=0;k<8;k++)c=c&1?(c>>>1)^0xEDB88320:c>>>1;cr=(cr>>>8)^c;}return ~cr>>>0;}
exports.write=function(f,w,h,px){const ch=(t,d)=>{const l=Buffer.alloc(4);l.writeUInt32BE(d.length);const td=Buffer.concat([Buffer.from(t),d]);const c=Buffer.alloc(4);c.writeUInt32BE(crc(td));return Buffer.concat([l,td,c]);};
const ih=Buffer.alloc(13);ih.writeUInt32BE(w,0);ih.writeUInt32BE(h,4);ih[8]=8;ih[9]=6;
const raw=Buffer.alloc((w*4+1)*h);for(let y=0;y<h;y++){raw[y*(w*4+1)]=0;px.copy(raw,y*(w*4+1)+1,y*w*4,(y+1)*w*4);}
fs.writeFileSync(f,Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),ch('IHDR',ih),ch('IDAT',zlib.deflateSync(raw)),ch('IEND',Buffer.alloc(0))]));};
