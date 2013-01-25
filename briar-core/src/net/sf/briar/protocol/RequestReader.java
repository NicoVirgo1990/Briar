package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.protocol.Types.REQUEST;

import java.io.IOException;
import java.util.BitSet;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.StructReader;

class RequestReader implements StructReader<Request> {

	public Request readStruct(Reader r) throws IOException {
		Consumer counting = new CountingConsumer(MAX_PACKET_LENGTH);
		r.addConsumer(counting);
		r.readStructId(REQUEST);
		// There may be up to 7 bits of padding at the end of the bitmap
		int padding = r.readUint7();
		if(padding > 7) throw new FormatException();
		// Read the bitmap
		byte[] bitmap = r.readBytes(MAX_PACKET_LENGTH);
		r.removeConsumer(counting);
		// Convert the bitmap into a BitSet
		int length = bitmap.length * 8 - padding;
		BitSet b = new BitSet(length);
		for(int i = 0; i < bitmap.length; i++) {
			for(int j = 0; j < 8 && i * 8 + j < length; j++) {
				byte bit = (byte) (128 >> j);
				if((bitmap[i] & bit) != 0) b.set(i * 8 + j);
			}
		}
		return new Request(b, length);
	}
}
