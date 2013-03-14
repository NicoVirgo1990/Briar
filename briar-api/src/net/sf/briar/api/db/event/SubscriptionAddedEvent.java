package net.sf.briar.api.db.event;

import net.sf.briar.api.messaging.Group;

/** An event that is broadcast when the user subscribes to a group. */
public class SubscriptionAddedEvent extends DatabaseEvent {

	private final Group group;

	public SubscriptionAddedEvent(Group group) {
		this.group = group;
	}

	public Group getGroup() {
		return group;
	}
}
