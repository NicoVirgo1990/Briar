package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxPairingState;
import org.briarproject.bramble.api.mailbox.MailboxPairingTask;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxSettingsManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.DbExpectations;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.briarproject.bramble.test.PredicateMatcher;
import org.jmock.Expectations;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MailboxPairingTaskImplTest extends BrambleMockTestCase {

	private final Executor executor = new ImmediateExecutor();
	private final TransactionManager db =
			context.mock(TransactionManager.class);
	private final CryptoComponent crypto = context.mock(CryptoComponent.class);
	private final MailboxApi api = context.mock(MailboxApi.class);
	private final MailboxSettingsManager mailboxSettingsManager =
			context.mock(MailboxSettingsManager.class);
	private final MailboxPairingTaskFactory factory =
			new MailboxPairingTaskFactoryImpl(executor, db, crypto, api,
					mailboxSettingsManager);

	private final String onion = getRandomString(64);
	private final byte[] onionBytes = getRandomBytes(32);
	private final MailboxAuthToken setupToken =
			new MailboxAuthToken(getRandomId());
	private final MailboxAuthToken ownerToken =
			new MailboxAuthToken(getRandomId());
	private final String validPayload = getValidPayload();
	private final MailboxProperties setupProperties =
			new MailboxProperties(onion, setupToken, true);
	private final MailboxProperties ownerProperties =
			new MailboxProperties(onion, ownerToken, true);

	@Test
	public void testInitialQrCodeReceivedState() {
		MailboxPairingTask task =
				factory.createPairingTask(getRandomString(42));
		task.addObserver(state ->
				assertTrue(state instanceof MailboxPairingState.QrCodeReceived)
		);
	}

	@Test
	public void testInvalidQrCode() {
		MailboxPairingTask task1 =
				factory.createPairingTask(getRandomString(42));
		task1.run();
		task1.addObserver(state ->
				assertTrue(state instanceof MailboxPairingState.InvalidQrCode)
		);

		MailboxPairingTask task2 =
				factory.createPairingTask(getRandomString(65));
		task2.run();
		task2.addObserver(state ->
				assertTrue(state instanceof MailboxPairingState.InvalidQrCode)
		);
	}

	@Test
	public void testSuccessfulPairing() throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).encodeOnionAddress(onionBytes);
			will(returnValue(onion));
			oneOf(api).setup(with(matches(setupProperties)));
			will(returnValue(ownerToken));
		}});
		Transaction txn = new Transaction(null, false);
		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(mailboxSettingsManager).setOwnMailboxProperties(
					with(txn), with(matches(ownerProperties)));
		}});

		AtomicInteger i = new AtomicInteger(0);
		MailboxPairingTask task = factory.createPairingTask(validPayload);
		task.addObserver(state -> {
			if (i.get() == 0) {
				assertEquals(MailboxPairingState.QrCodeReceived.class,
						state.getClass());
			} else if (i.get() == 1) {
				assertEquals(MailboxPairingState.Pairing.class,
						state.getClass());
			} else if (i.get() == 2) {
				assertEquals(MailboxPairingState.Paired.class,
						state.getClass());
			} else fail("Unexpected change of state " + state.getClass());
			i.getAndIncrement();
		});
		task.run();
	}

	@Test
	public void testAlreadyPaired() throws Exception {
		testApiException(new MailboxApi.MailboxAlreadyPairedException(),
				MailboxPairingState.MailboxAlreadyPaired.class);
	}

	@Test
	public void testMailboxApiException() throws Exception {
		testApiException(new MailboxApi.ApiException(),
				MailboxPairingState.AssertionError.class);
	}

	@Test
	public void testApiIOException() throws Exception {
		testApiException(new IOException(),
				MailboxPairingState.ConnectionError.class);
	}

	private void testApiException(Exception e,
			Class<? extends MailboxPairingState> s) throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).encodeOnionAddress(onionBytes);
			will(returnValue(onion));
			oneOf(api).setup(with(matches(setupProperties)));
			will(throwException(e));
		}});

		MailboxPairingTask task = factory.createPairingTask(validPayload);
		task.run();
		task.addObserver(state -> assertEquals(state.getClass(), s));
	}

	@Test
	public void testDbException() throws Exception {
		context.checking(new Expectations() {{
			oneOf(crypto).encodeOnionAddress(onionBytes);
			will(returnValue(onion));
			oneOf(api).setup(with(matches(setupProperties)));
			will(returnValue(ownerToken));
		}});
		Transaction txn = new Transaction(null, false);
		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(false), withDbRunnable(txn));
			oneOf(mailboxSettingsManager).setOwnMailboxProperties(
					with(txn), with(matches(ownerProperties)));
			will(throwException(new DbException()));
		}});

		MailboxPairingTask task = factory.createPairingTask(validPayload);
		task.run();
		task.addObserver(state -> assertEquals(state.getClass(),
				MailboxPairingState.AssertionError.class));
	}

	private String getValidPayload() {
		byte[] payloadBytes = ByteBuffer.allocate(65)
				.put((byte) 32) // 1
				.put(onionBytes) // 32
				.put(setupToken.getBytes()) // 32
				.array();
		//noinspection CharsetObjectCanBeUsed
		return new String(payloadBytes, Charset.forName("ISO-8859-1"));
	}

	private PredicateMatcher<MailboxProperties> matches(MailboxProperties p2) {
		return new PredicateMatcher<>(MailboxProperties.class, p1 ->
				p1.getAuthToken().equals(p2.getAuthToken()) &&
						p1.getOnionAddress().equals(p2.getOnionAddress()) &&
						p1.isOwner() == p2.isOwner());
	}

}
