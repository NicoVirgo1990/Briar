package net.sf.briar.serial;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.briar.api.Bytes;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.StructReader;
import net.sf.briar.api.serial.Reader;

// This class is not thread-safe
class ReaderImpl implements Reader {

	private static final byte[] EMPTY_BUFFER = new byte[] {};

	private final InputStream in;
	private final Collection<Consumer> consumers = new ArrayList<Consumer>(0);

	private StructReader<?>[] structReaders = new StructReader<?>[] {};
	private boolean hasLookahead = false, eof = false;
	private byte next, nextNext;
	private byte[] buf = null;
	private int maxStringLength = Integer.MAX_VALUE;
	private int maxBytesLength = Integer.MAX_VALUE;

	ReaderImpl(InputStream in) {
		this.in = in;
	}

	public boolean eof() throws IOException {
		if(!hasLookahead) readLookahead(true);
		return eof;
	}

	private byte readLookahead(boolean eofAcceptable) throws IOException {
		assert !eof;
		// If one or two lookahead bytes have been read, feed the consumers
		if(hasLookahead) consumeLookahead();
		// Read a lookahead byte
		int i = in.read();
		if(i == -1) {
			if(!eofAcceptable) throw new FormatException();
			eof = true;
		}
		next = (byte) i;
		// If necessary, read another lookahead byte
		if(next == Tag.STRUCT) {
			i = in.read();
			if(i == -1) throw new FormatException();
			nextNext = (byte) i;
		}
		hasLookahead = true;
		return next;
	}

	private void consumeLookahead() throws IOException {
		assert hasLookahead;
		for(Consumer c : consumers) {
			c.write(next);
			if(next == Tag.STRUCT) c.write(nextNext);
		}
		hasLookahead = false;
	}

	public void close() throws IOException {
		buf = null;
		in.close();
	}

	public void setMaxStringLength(int length) {
		maxStringLength = length;
	}

	public void resetMaxStringLength() {
		maxStringLength = Integer.MAX_VALUE;
	}

	public void setMaxBytesLength(int length) {
		maxBytesLength = length;
	}

	public void resetMaxBytesLength() {
		maxBytesLength = Integer.MAX_VALUE;
	}

	public void addConsumer(Consumer c) {
		consumers.add(c);
	}

	public void removeConsumer(Consumer c) {
		if(!consumers.remove(c)) throw new IllegalArgumentException();
	}

	public void addStructReader(int id, StructReader<?> r) {
		if(id < 0 || id > 255) throw new IllegalArgumentException();
		if(structReaders.length < id + 1) {
			int len = Math.min(256, Math.max(id + 1, structReaders.length * 2));
			StructReader<?>[] newStructReaders = new StructReader<?>[len];
			System.arraycopy(structReaders, 0, newStructReaders, 0,
					structReaders.length);
			structReaders = newStructReaders;
		}
		structReaders[id] = r;
	}

	public void removeStructReader(int id) {
		if(id < 0 || id > structReaders.length)
			throw new IllegalArgumentException();
		structReaders[id] = null;
	}

	public boolean hasBoolean() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.FALSE || next == Tag.TRUE;
	}

	public boolean readBoolean() throws IOException {
		if(!hasBoolean()) throw new FormatException();
		consumeLookahead();
		return next == Tag.TRUE;
	}

	public boolean hasUint7() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next >= 0;
	}

	public byte readUint7() throws IOException {
		if(!hasUint7()) throw new FormatException();
		consumeLookahead();
		return next;
	}

	public boolean hasInt8() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.INT8;
	}

	public byte readInt8() throws IOException {
		if(!hasInt8()) throw new FormatException();
		readLookahead(false);
		consumeLookahead();
		return next;
	}

	public boolean hasInt16() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.INT16;
	}

	public short readInt16() throws IOException {
		if(!hasInt16()) throw new FormatException();
		byte b1 = readLookahead(false);
		byte b2 = readLookahead(false);
		consumeLookahead();
		return (short) (((b1 & 0xFF) << 8) | (b2 & 0xFF));
	}

	public boolean hasInt32() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.INT32;
	}

	public int readInt32() throws IOException {
		if(!hasInt32()) throw new FormatException();
		consumeLookahead();
		return readInt32Bits();
	}

	private int readInt32Bits() throws IOException {
		readIntoBuffer(4);
		return ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16) |
				((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
	}

	private void readIntoBuffer(int length) throws IOException {
		if(buf == null || buf.length < length) buf = new byte[length];
		readIntoBuffer(buf, length);
	}

	private void readIntoBuffer(byte[] b, int length) throws IOException {
		assert !hasLookahead;
		int offset = 0;
		while(offset < length) {
			int read = in.read(b, offset, length - offset);
			if(read == -1) {
				eof = true;
				break;
			}
			offset += read;
		}
		if(offset < length) throw new FormatException();
		// Feed the hungry mouths
		for(Consumer c : consumers) c.write(b, 0, length);
	}

	public boolean hasInt64() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.INT64;
	}

	public long readInt64() throws IOException {
		if(!hasInt64()) throw new FormatException();
		consumeLookahead();
		return readInt64Bits();
	}

	private long readInt64Bits() throws IOException {
		readIntoBuffer(8);
		return ((buf[0] & 0xFFL) << 56) | ((buf[1] & 0xFFL) << 48) |
				((buf[2] & 0xFFL) << 40) | ((buf[3] & 0xFFL) << 32) |
				((buf[4] & 0xFFL) << 24) | ((buf[5] & 0xFFL) << 16) |
				((buf[6] & 0xFFL) << 8) | (buf[7] & 0xFFL);
	}

	public boolean hasIntAny() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next >= 0 || next == Tag.INT8 || next == Tag.INT16
				|| next == Tag.INT32 || next == Tag.INT64;
	}

	public long readIntAny() throws IOException {
		if(!hasIntAny()) throw new FormatException();
		if(next >= 0) return readUint7();
		if(next == Tag.INT8) return readInt8();
		if(next == Tag.INT16) return readInt16();
		if(next == Tag.INT32) return readInt32();
		if(next == Tag.INT64) return readInt64();
		throw new IllegalStateException();
	}

	public boolean hasFloat32() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.FLOAT32;
	}

	public float readFloat32() throws IOException {
		if(!hasFloat32()) throw new FormatException();
		consumeLookahead();
		return Float.intBitsToFloat(readInt32Bits());
	}

	public boolean hasFloat64() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.FLOAT64;
	}

	public double readFloat64() throws IOException {
		if(!hasFloat64()) throw new FormatException();
		consumeLookahead();
		return Double.longBitsToDouble(readInt64Bits());
	}

	public boolean hasString() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.STRING
				|| (next & Tag.SHORT_MASK) == Tag.SHORT_STRING;
	}

	public String readString() throws IOException {
		return readString(maxStringLength);
	}

	public String readString(int maxLength) throws IOException {
		if(!hasString()) throw new FormatException();
		consumeLookahead();
		int length;
		if(next == Tag.STRING) length = readLength();
		else length = 0xFF & next ^ Tag.SHORT_STRING;
		if(length > maxLength) throw new FormatException();
		if(length == 0) return "";
		readIntoBuffer(length);
		return new String(buf, 0, length, "UTF-8");
	}

	private int readLength() throws IOException {
		if(!hasLength()) throw new FormatException();
		if(next >= 0) return readUint7();
		if(next == Tag.INT8) return readInt8();
		if(next == Tag.INT16) return readInt16();
		if(next == Tag.INT32) return readInt32();
		throw new IllegalStateException();
	}

	private boolean hasLength() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next >= 0 || next == Tag.INT8 || next == Tag.INT16
				|| next == Tag.INT32;
	}

	public boolean hasBytes() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.BYTES || (next & Tag.SHORT_MASK) == Tag.SHORT_BYTES;
	}

	public byte[] readBytes() throws IOException {
		return readBytes(maxBytesLength);
	}

	public byte[] readBytes(int maxLength) throws IOException {
		if(!hasBytes()) throw new FormatException();
		consumeLookahead();
		int length;
		if(next == Tag.BYTES) length = readLength();
		else length = 0xFF & next ^ Tag.SHORT_BYTES;
		if(length > maxLength) throw new FormatException();
		if(length == 0) return EMPTY_BUFFER;
		byte[] b = new byte[length];
		readIntoBuffer(b, length);
		return b;
	}

	public boolean hasList() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.LIST
				|| (next & Tag.SHORT_MASK) == Tag.SHORT_LIST;
	}

	public <E> List<E> readList(Class<E> e) throws IOException {
		if(!hasList()) throw new FormatException();
		consumeLookahead();
		if(next == Tag.LIST) {
			List<E> list = new ArrayList<E>();
			while(!hasEnd()) list.add(readObject(e));
			readEnd();
			return Collections.unmodifiableList(list);
		} else {
			int length = 0xFF & next ^ Tag.SHORT_LIST;
			return readList(e, length);
		}
	}

	private <E> List<E> readList(Class<E> e, int length) throws IOException {
		assert length >= 0;
		if(length == 0) return Collections.emptyList();
		List<E> list = new ArrayList<E>();
		for(int i = 0; i < length; i++) list.add(readObject(e));
		return Collections.unmodifiableList(list);
	}

	private boolean hasEnd() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.END;
	}

	private void readEnd() throws IOException {
		if(!hasLookahead) throw new IllegalStateException();
		if(!hasEnd()) throw new FormatException();
		consumeLookahead();
	}

	private Object readObject() throws IOException {
		if(hasStruct()) return readStruct();
		if(hasBoolean()) return Boolean.valueOf(readBoolean());
		if(hasUint7()) return Byte.valueOf(readUint7());
		if(hasInt8()) return Byte.valueOf(readInt8());
		if(hasInt16()) return Short.valueOf(readInt16());
		if(hasInt32()) return Integer.valueOf(readInt32());
		if(hasInt64()) return Long.valueOf(readInt64());
		if(hasFloat32()) return Float.valueOf(readFloat32());
		if(hasFloat64()) return Double.valueOf(readFloat64());
		if(hasString()) return readString();
		if(hasBytes()) return new Bytes(readBytes());
		if(hasList()) return readList(Object.class);
		if(hasMap()) return readMap(Object.class, Object.class);
		if(hasNull()) {
			readNull();
			return null;
		}
		throw new FormatException();
	}

	private boolean hasStruct() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.STRUCT
				|| (next & Tag.SHORT_STRUCT_MASK) == Tag.SHORT_STRUCT;
	}

	private Object readStruct() throws IOException {
		if(!hasStruct()) throw new FormatException();
		int id;
		if(next == Tag.STRUCT) id = 0xFF & nextNext;
		else id = 0xFF & next ^ Tag.SHORT_STRUCT;
		return readStruct(id, Object.class);
	}

	private <T> T readObject(Class<T> t) throws IOException {
		try {
			Object o = readObject();
			// If this is a small integer type and we're expecting a larger
			// integer type, promote before casting
			if(o instanceof Byte) {
				if(Short.class.isAssignableFrom(t))
					return t.cast(Short.valueOf((Byte) o));
				if(Integer.class.isAssignableFrom(t))
					return t.cast(Integer.valueOf((Byte) o));
				if(Long.class.isAssignableFrom(t))
					return t.cast(Long.valueOf((Byte) o));
			} else if(o instanceof Short) {
				if(Integer.class.isAssignableFrom(t))
					return t.cast(Integer.valueOf((Short) o));
				if(Long.class.isAssignableFrom(t))
					return t.cast(Long.valueOf((Short) o));
			} else if(o instanceof Integer) {
				if(Long.class.isAssignableFrom(t))
					return t.cast(Long.valueOf((Integer) o));
			}
			return t.cast(o);
		} catch(ClassCastException e) {
			throw new FormatException();
		}
	}

	public boolean hasListStart() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.LIST;
	}

	public void readListStart() throws IOException {
		if(!hasListStart()) throw new FormatException();
		consumeLookahead();
	}

	public boolean hasListEnd() throws IOException {
		return hasEnd();
	}

	public void readListEnd() throws IOException {
		readEnd();
	}

	public boolean hasMap() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.MAP
				|| (next & Tag.SHORT_MASK) == Tag.SHORT_MAP;
	}

	public <K, V> Map<K, V> readMap(Class<K> k, Class<V> v)	throws IOException {
		if(!hasMap()) throw new FormatException();
		consumeLookahead();
		if(next == Tag.MAP) {
			Map<K, V> m = new HashMap<K, V>();
			while(!hasEnd()) {
				if(m.put(readObject(k), readObject(v)) != null)
					throw new FormatException(); // Duplicate key
			}
			readEnd();
			return Collections.unmodifiableMap(m);
		} else {
			int size = 0xFF & next ^ Tag.SHORT_MAP;
			return readMap(k, v, size);
		}
	}

	private <K, V> Map<K, V> readMap(Class<K> k, Class<V> v, int size)
			throws IOException {
		assert size >= 0;
		if(size == 0) return Collections.emptyMap();
		Map<K, V> m = new HashMap<K, V>();
		for(int i = 0; i < size; i++) {
			if(m.put(readObject(k), readObject(v)) != null)
				throw new FormatException(); // Duplicate key
		}
		return Collections.unmodifiableMap(m);
	}

	public boolean hasMapStart() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.MAP;
	}

	public void readMapStart() throws IOException {
		if(!hasMapStart()) throw new FormatException();
		consumeLookahead();
	}

	public boolean hasMapEnd() throws IOException {
		return hasEnd();
	}

	public void readMapEnd() throws IOException {
		readEnd();
	}

	public boolean hasNull() throws IOException {
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		return next == Tag.NULL;
	}

	public void readNull() throws IOException {
		if(!hasNull()) throw new FormatException();
		consumeLookahead();
	}

	public boolean hasStruct(int id) throws IOException {
		if(id < 0 || id > 255) throw new IllegalArgumentException();
		if(!hasLookahead) readLookahead(true);
		if(eof) return false;
		if(next == Tag.STRUCT)
			return id == (0xFF & nextNext);
		else if((next & Tag.SHORT_STRUCT_MASK) == Tag.SHORT_STRUCT)
			return id == (0xFF & next ^ Tag.SHORT_STRUCT);
		else return false;
	}

	public <T> T readStruct(int id, Class<T> t) throws IOException {
		if(!hasStruct(id)) throw new FormatException();
		if(id < 0 || id >= structReaders.length) throw new FormatException();
		StructReader<?> s = structReaders[id];
		if(s == null) throw new FormatException();
		try {
			return t.cast(s.readStruct(this));
		} catch(ClassCastException e) {
			throw new FormatException();
		}
	}

	public void readStructId(int id) throws IOException {
		if(!hasStruct(id)) throw new FormatException();
		consumeLookahead();
	}
}
