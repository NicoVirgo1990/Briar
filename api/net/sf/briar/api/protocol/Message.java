package net.sf.briar.api.protocol;

public interface Message {

	/** Returns the message's unique identifier. */
	MessageId getId();

	/**
	 * Returns the message's parent, or MessageId.NONE if this is the first
	 * message in a thread.
	 */
	MessageId getParent();

	/** Returns the group to which the message belongs. */
	GroupId getGroup();

	/** Returns the message's author. */
	AuthorId getAuthor();

	/** Returns the message's subject line. */
	String getSubject();

	/** Returns the timestamp created by the message's author. */
	long getTimestamp();

	/** Returns the length of the serialised message in bytes. */
	int getLength();

	/** Returns the serialised message. */
	byte[] getSerialised();

	/** Returns the offset of the message body within the serialised message. */
	int getBodyStart();

	/** Returns the length of the message body in bytes. */
	int getBodyLength();
}