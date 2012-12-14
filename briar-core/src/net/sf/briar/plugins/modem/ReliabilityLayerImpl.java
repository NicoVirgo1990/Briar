package net.sf.briar.plugins.modem;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

class ReliabilityLayerImpl implements ReliabilityLayer {

	private static final int TICK_INTERVAL = 500; // Milliseconds

	private static final Logger LOG =
			Logger.getLogger(ReliabilityLayerImpl.class.getName());

	private final Executor executor;
	private final WriteHandler writeHandler;
	private final BlockingQueue<byte[]> writes;

	private volatile Receiver receiver = null;
	private volatile SlipDecoder decoder = null;
	private volatile ReceiverInputStream inputStream = null;
	private volatile SenderOutputStream outputStream = null;
	private volatile boolean running = false;

	ReliabilityLayerImpl(Executor executor, WriteHandler writeHandler) {
		this.executor = executor;
		this.writeHandler = writeHandler;
		writes = new LinkedBlockingQueue<byte[]>();
	}

	public void start() {
		SlipEncoder encoder = new SlipEncoder(this);
		final Sender sender = new Sender(encoder);
		receiver = new Receiver(sender);
		decoder = new SlipDecoder(receiver);
		inputStream = new ReceiverInputStream(receiver);
		outputStream = new SenderOutputStream(sender);
		running = true;
		executor.execute(new Runnable() {
			public void run() {
				long now = System.currentTimeMillis();
				long next = now + TICK_INTERVAL;
				try {
					while(running) {
						byte[] b = null;
						while(now < next && b == null) {
							b = writes.poll(next - now, MILLISECONDS);
							now = System.currentTimeMillis();
						}
						if(b == null) {
							sender.tick();
							while(next <= now) next += TICK_INTERVAL;
						} else {
							if(b.length == 0) return; // Poison pill
							writeHandler.handleWrite(b);
						}
					}
				} catch(InterruptedException e) {
					if(LOG.isLoggable(WARNING))
						LOG.warning("Interrupted while waiting to write");
					Thread.currentThread().interrupt();
					running = false;
				} catch(IOException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					running = false;
				}
			}
		});
	}

	public void stop() {
		running = false;
		receiver.invalidate();
		writes.add(new byte[0]); // Poison pill
	}

	public InputStream getInputStream() {
		return inputStream;
	}

	public OutputStream getOutputStream() {
		return outputStream;
	}

	// The transport calls this method to pass data up to the SLIP decoder
	public void handleRead(byte[] b) throws IOException {
		if(!running) throw new IOException("Connection closed");
		decoder.handleRead(b);
	}

	// The SLIP encoder calls this method to pass data down to the transport
	public void handleWrite(byte[] b) throws IOException {
		if(!running) throw new IOException("Connection closed");
		if(b.length > 0) writes.add(b);
	}
}
