// Minimal NBT reader/writer, no dependencies.
//
// The reader exists so the writer can be checked against a real vanilla
// structure file rather than against a reading of the documentation: decode
// one of Mojang's .nbt, compare its shape to ours, and round-trip our own.
//
// Values are tagged explicitly -- {t: 'int', v: 3} -- because NBT distinguishes
// byte/short/int/long/float/double and JavaScript does not.
const zlib = require('zlib');

const T = {
  END: 0, BYTE: 1, SHORT: 2, INT: 3, LONG: 4, FLOAT: 5, DOUBLE: 6,
  BYTE_ARRAY: 7, STRING: 8, LIST: 9, COMPOUND: 10, INT_ARRAY: 11, LONG_ARRAY: 12,
};
const NAME = Object.fromEntries(Object.entries(T).map(([k, v]) => [v, k.toLowerCase()]));

// --- helpers to build tagged values ---------------------------------------
const b = (v) => ({ t: 'byte', v });
const i = (v) => ({ t: 'int', v });
const s = (v) => ({ t: 'string', v });
const list = (of, v) => ({ t: 'list', of, v });
const comp = (v) => ({ t: 'compound', v });

// --- writing ---------------------------------------------------------------
class Writer {
  constructor() { this.parts = []; }
  push(buf) { this.parts.push(buf); }
  u8(n) { const x = Buffer.alloc(1); x.writeUInt8(n & 0xff); this.push(x); }
  i8(n) { const x = Buffer.alloc(1); x.writeInt8(n); this.push(x); }
  i16(n) { const x = Buffer.alloc(2); x.writeInt16BE(n); this.push(x); }
  i32(n) { const x = Buffer.alloc(4); x.writeInt32BE(n); this.push(x); }
  i64(n) { const x = Buffer.alloc(8); x.writeBigInt64BE(BigInt(n)); this.push(x); }
  f32(n) { const x = Buffer.alloc(4); x.writeFloatBE(n); this.push(x); }
  f64(n) { const x = Buffer.alloc(8); x.writeDoubleBE(n); this.push(x); }
  str(v) { const x = Buffer.from(v, 'utf8'); this.i16(x.length); this.push(x); }
  done() { return Buffer.concat(this.parts); }
}

const typeId = (val) => {
  switch (val.t) {
    case 'byte': return T.BYTE;
    case 'short': return T.SHORT;
    case 'int': return T.INT;
    case 'long': return T.LONG;
    case 'float': return T.FLOAT;
    case 'double': return T.DOUBLE;
    case 'byteArray': return T.BYTE_ARRAY;
    case 'string': return T.STRING;
    case 'list': return T.LIST;
    case 'compound': return T.COMPOUND;
    case 'intArray': return T.INT_ARRAY;
    case 'longArray': return T.LONG_ARRAY;
    default: throw new Error(`unknown tag type ${val.t}`);
  }
};

function writePayload(w, val) {
  switch (val.t) {
    case 'byte': w.i8(val.v); break;
    case 'short': w.i16(val.v); break;
    case 'int': w.i32(val.v); break;
    case 'long': w.i64(val.v); break;
    case 'float': w.f32(val.v); break;
    case 'double': w.f64(val.v); break;
    case 'string': w.str(val.v); break;
    case 'byteArray': w.i32(val.v.length); for (const n of val.v) w.i8(n); break;
    case 'intArray': w.i32(val.v.length); for (const n of val.v) w.i32(n); break;
    case 'longArray': w.i32(val.v.length); for (const n of val.v) w.i64(n); break;
    case 'list': {
      // An empty list still needs an element type; vanilla writes END for those,
      // which is also how decode() reports them back (of: 'end').
      const elem = val.v.length ? typeId(val.v[0])
        : (val.of && val.of !== 'end' ? typeId({ t: val.of }) : T.END);
      w.u8(elem);
      w.i32(val.v.length);
      for (const e of val.v) writePayload(w, e);
      break;
    }
    case 'compound': {
      for (const [k, v] of Object.entries(val.v)) {
        w.u8(typeId(v)); w.str(k); writePayload(w, v);
      }
      w.u8(T.END);
      break;
    }
    default: throw new Error(`cannot write ${val.t}`);
  }
}

function encode(rootCompound, { gzip = true, rootName = '' } = {}) {
  const w = new Writer();
  w.u8(T.COMPOUND); w.str(rootName);
  writePayload(w, rootCompound);
  const raw = w.done();
  return gzip ? zlib.gzipSync(raw, { level: 9 }) : raw;
}

// --- reading ---------------------------------------------------------------
class Reader {
  constructor(buf) { this.b = buf; this.o = 0; }
  u8() { return this.b.readUInt8(this.o++); }
  i8() { return this.b.readInt8(this.o++); }
  i16() { const v = this.b.readInt16BE(this.o); this.o += 2; return v; }
  i32() { const v = this.b.readInt32BE(this.o); this.o += 4; return v; }
  i64() { const v = this.b.readBigInt64BE(this.o); this.o += 8; return Number(v); }
  f32() { const v = this.b.readFloatBE(this.o); this.o += 4; return v; }
  f64() { const v = this.b.readDoubleBE(this.o); this.o += 8; return v; }
  str() { const n = this.i16(); const v = this.b.toString('utf8', this.o, this.o + n); this.o += n; return v; }
}

function readPayload(r, type) {
  switch (type) {
    case T.BYTE: return { t: 'byte', v: r.i8() };
    case T.SHORT: return { t: 'short', v: r.i16() };
    case T.INT: return { t: 'int', v: r.i32() };
    case T.LONG: return { t: 'long', v: r.i64() };
    case T.FLOAT: return { t: 'float', v: r.f32() };
    case T.DOUBLE: return { t: 'double', v: r.f64() };
    case T.STRING: return { t: 'string', v: r.str() };
    case T.BYTE_ARRAY: { const n = r.i32(); const a = []; for (let k = 0; k < n; k++) a.push(r.i8()); return { t: 'byteArray', v: a }; }
    case T.INT_ARRAY: { const n = r.i32(); const a = []; for (let k = 0; k < n; k++) a.push(r.i32()); return { t: 'intArray', v: a }; }
    case T.LONG_ARRAY: { const n = r.i32(); const a = []; for (let k = 0; k < n; k++) a.push(r.i64()); return { t: 'longArray', v: a }; }
    case T.LIST: {
      const et = r.u8(); const n = r.i32(); const a = [];
      for (let k = 0; k < n; k++) a.push(readPayload(r, et));
      return { t: 'list', of: NAME[et], v: a };
    }
    case T.COMPOUND: {
      const o = {};
      for (;;) {
        const tt = r.u8();
        if (tt === T.END) break;
        const name = r.str();
        o[name] = readPayload(r, tt);
      }
      return { t: 'compound', v: o };
    }
    default: throw new Error(`unknown tag id ${type}`);
  }
}

function decode(buf) {
  const raw = buf[0] === 0x1f && buf[1] === 0x8b ? zlib.gunzipSync(buf) : buf;
  const r = new Reader(raw);
  const type = r.u8();
  if (type !== T.COMPOUND) throw new Error(`root is ${NAME[type]}, expected compound`);
  r.str(); // root name, conventionally empty
  return readPayload(r, T.COMPOUND);
}

/** Strips the type tags, for readable dumps. */
function plain(val) {
  switch (val.t) {
    case 'compound': return Object.fromEntries(Object.entries(val.v).map(([k, v]) => [k, plain(v)]));
    case 'list': return val.v.map(plain);
    default: return val.v;
  }
}

module.exports = { encode, decode, plain, b, i, s, list, comp, T };
