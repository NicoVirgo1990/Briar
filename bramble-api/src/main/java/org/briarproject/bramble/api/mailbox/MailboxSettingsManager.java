package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
public interface MailboxSettingsManager {

	@Nullable
	MailboxProperties getOwnMailboxProperties(Transaction txn)
			throws DbException;

	void setOwnMailboxProperties(Transaction txn, MailboxProperties p)
			throws DbException;
}