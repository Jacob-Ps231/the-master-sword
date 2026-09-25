// PNG reader/writer, 8-bit, no interlace, with palette support.
const fs=require('fs'),zlib=require('zlib');
exports.read=function(f){const b=fs.readFileSync(f);let p=8,ih,id=[],pal=null,trns=null;
while(p<b.length){const l=b.readUInt32BE(p),t=b.toString('ascii',p+4,p+8),d=b.slice(p+8,p+8+l);
 if(t=='IHDR')ih=d; else if(t=='IDAT')id.push(d); else if(t=='PLTE')pal=d; else if(t=='tRNS')trns=d;
 p+=12+l;}
const w=ih.readUInt32BE(0),h=ih.readUInt32BE(4),depth=ih[8],ct=ih[9];
if(depth!=8)throw new Error('depth '+depth+' unsupported');
const bpp={0:1,2:3,3:1,4:2,6:4}[ct];if(!bpp)throw new Error('color type '+ct);
const raw=zlib.inflateSync(Buffer.concat(id)),st=w*bpp,out=Buffer.alloc(w*h*4);let prev=Buffer.alloc(st),q=0;
const paeth=(a,b2,c)=>{const pp=a+b2-c,pa=Math.abs(pp-a),pb=Math.abs(pp-b2),pc=Math.abs(pp-c);return pa<=pb&&pa<=pc?a:pb<=pc?b2:c;};
for(let y=0;y<h;y++){const ft=raw[q++],line=Buffer.from(raw.slice(q,q+st));q+=st;
 for(let i=0;i<st;i++){const a=i>=bpp?line[i-bpp]:0,up=prev[i],c=i>=bpp?prev[i-bpp]:0;let v=line[i];
  if(ft==1)v+=a; else if(ft==2)v+=up; else if(ft==3)v+=(a+up)>>1; else if(ft==4)v+=paeth(a,up,c);
  line[i]=v&255;}
 for(let x=0;x<w;x++){const s=x*bpp,o=(y*w+x)*4;
  if(ct==3){const idx=line[s];out[o]=pal[idx*3];out[o+1]=pal[idx*3+1];out[o+2]=pal[idx*3+2];out[o+3]=trns&&idx<trns.length?trns[idx]:255;}
  else if(ct==0){out[o]=out[o+1]=out[o+2]=line[s];out[o+3]=255;}
  else if(ct==4){out[o]=out[o+1]=out[o+2]=line[s];out[o+3]=line[s+1];}
  else {out[o]=line[s];out[o+1]=line[s+1];out[o+2]=line[s+2];out[o+3]=ct==6?line[s+3]:255;}}
 prev=line;}
return {w,h,px:out};};
function crc(b){let cr=~0;for(const x of b){let c=(cr^x)&255;for(let k=0;k<8;k++)c=c&1?(c>>>1)^0xEDB88320:c>>>1;cr=(cr>>>8)^c;}return ~cr>>>0;}
exports.write=function(f,w,h,px){const ch=(t,d)=>{const l=Buffer.alloc(4);l.writeUInt32BE(d.length);const td=Buffer.concat([Buffer.from(t),d]);const c=Buffer.alloc(4);c.writeUInt32BE(crc(td));return Buffer.concat([l,td,c]);};
const ih=Buffer.alloc(13);ih.writeUInt32BE(w,0);ih.writeUInt32BE(h,4);ih[8]=8;ih[9]=6;
const raw=Buffer.alloc((w*4+1)*h);for(let y=0;y<h;y++){raw[y*(w*4+1)]=0;px.copy(raw,y*(w*4+1)+1,y*w*4,(y+1)*w*4);}
fs.writeFileSync(f,Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),ch('IHDR',ih),ch('IDAT',zlib.deflateSync(raw,{level:9})),ch('IEND',Buffer.alloc(0))]));};
