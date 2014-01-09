package org.briarproject.android.groups;

import static android.text.InputType.TYPE_CLASS_TEXT;
import static android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.inputmethod.InputMethodManager.HIDE_IMPLICIT_ONLY;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.android.util.CommonLayoutParams.MATCH_MATCH;
import static org.briarproject.android.util.CommonLayoutParams.WRAP_WRAP;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.R;
import org.briarproject.android.contact.SelectContactsDialog;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.util.LayoutUtils;
import org.briarproject.api.Contact;
import org.briarproject.api.ContactId;
import org.briarproject.api.android.DatabaseUiExecutor;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupFactory;

import roboguice.activity.RoboFragmentActivity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class CreateGroupActivity extends RoboFragmentActivity
implements OnEditorActionListener, OnClickListener, NoContactsDialog.Listener,
SelectContactsDialog.Listener {

	private static final Logger LOG =
			Logger.getLogger(CreateGroupActivity.class.getName());

	private EditText nameEntry = null;
	private RadioGroup radioGroup = null;
	private RadioButton visibleToAll = null, visibleToSome = null;
	private Button createButton = null;
	private ProgressBar progress = null;
	private NoContactsDialog noContactsDialog = null;
	private SelectContactsDialog selectContactsDialog = null;

	// Fields that are accessed from background threads must be volatile
	@Inject private volatile GroupFactory groupFactory;
	@Inject private volatile DatabaseComponent db;
	@Inject @DatabaseUiExecutor private volatile Executor dbUiExecutor;
	@Inject private volatile LifecycleManager lifecycleManager;
	private volatile Collection<ContactId> selected = Collections.emptyList();

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(MATCH_MATCH);
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		int pad = LayoutUtils.getPadding(this);

		TextView chooseName = new TextView(this);
		chooseName.setGravity(CENTER);
		chooseName.setTextSize(18);
		chooseName.setPadding(pad, pad, pad, 0);
		chooseName.setText(R.string.choose_forum_name);
		layout.addView(chooseName);

		nameEntry = new EditText(this) {
			@Override
			protected void onTextChanged(CharSequence text, int start,
					int lengthBefore, int lengthAfter) {
				enableOrDisableCreateButton();
			}
		};
		nameEntry.setId(1);
		nameEntry.setMaxLines(1);
		nameEntry.setInputType(TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_CAP_SENTENCES);
		nameEntry.setOnEditorActionListener(this);
		layout.addView(nameEntry);

		radioGroup = new RadioGroup(this);
		radioGroup.setOrientation(VERTICAL);

		visibleToAll = new RadioButton(this);
		visibleToAll.setId(2);
		visibleToAll.setText(R.string.forum_visible_to_all);
		visibleToAll.setOnClickListener(this);
		radioGroup.addView(visibleToAll);

		visibleToSome = new RadioButton(this);
		visibleToSome.setId(3);
		visibleToSome.setText(R.string.forum_visible_to_some);
		visibleToSome.setOnClickListener(this);
		radioGroup.addView(visibleToSome);
		layout.addView(radioGroup);

		createButton = new Button(this);
		createButton.setLayoutParams(WRAP_WRAP);
		createButton.setText(R.string.create_button);
		createButton.setOnClickListener(this);
		layout.addView(createButton);

		progress = new ProgressBar(this);
		progress.setLayoutParams(WRAP_WRAP);
		progress.setIndeterminate(true);
		progress.setVisibility(GONE);
		layout.addView(progress);

		setContentView(layout);

		FragmentManager fm = getSupportFragmentManager();
		Fragment f = fm.findFragmentByTag("NoContactsDialog");
		if(f == null) noContactsDialog = new NoContactsDialog();
		else noContactsDialog = (NoContactsDialog) f;
		noContactsDialog.setListener(this);

		f = fm.findFragmentByTag("SelectContactsDialog");
		if(f == null) selectContactsDialog = new SelectContactsDialog();
		else selectContactsDialog = (SelectContactsDialog) f;
		selectContactsDialog.setListener(this);
	}

	private void enableOrDisableCreateButton() {
		if(nameEntry == null || radioGroup == null || createButton == null)
			return; // Activity not created yet
		boolean nameNotEmpty = nameEntry.getText().length() > 0;
		boolean visibilitySelected = radioGroup.getCheckedRadioButtonId() != -1;
		createButton.setEnabled(nameNotEmpty && visibilitySelected);
	}

	public boolean onEditorAction(TextView textView, int actionId, KeyEvent e) {
		validateName();
		return true;
	}

	public void onClick(View view) {
		if(view == visibleToAll) {
			enableOrDisableCreateButton();
		} else if(view == visibleToSome) {
			loadContacts();
		} else if(view == createButton) {
			if(!validateName()) return;
			final String name = nameEntry.getText().toString();
			final boolean all = visibleToAll.isChecked();
			final Collection<ContactId> visible =
					Collections.unmodifiableCollection(selected);
			// Replace the button with a progress bar
			createButton.setVisibility(GONE);
			progress.setVisibility(VISIBLE);
			// Create and store the group in a background thread
			dbUiExecutor.execute(new Runnable() {
				public void run() {
					try {
						lifecycleManager.waitForDatabase();
						Group g = groupFactory.createGroup(name);
						long now = System.currentTimeMillis();
						db.addGroup(g);
						if(all) db.setVisibleToAll(g.getId(), true);
						else db.setVisibility(g.getId(), visible);
						long duration = System.currentTimeMillis() - now;
						if(LOG.isLoggable(INFO))
							LOG.info("Storing group took " + duration + " ms");
					} catch(DbException e) {
						if(LOG.isLoggable(WARNING))
							LOG.log(WARNING, e.toString(), e);
					} catch(InterruptedException e) {
						if(LOG.isLoggable(INFO))
							LOG.info("Interrupted while waiting for database");
						Thread.currentThread().interrupt();
					}
					runOnUiThread(new Runnable() {
						public void run() {
							finish();
						}
					});
				}
			});
		}
	}


	private void loadContacts() {
		dbUiExecutor.execute(new Runnable() {
			public void run() {
				try {
					lifecycleManager.waitForDatabase();
					long now = System.currentTimeMillis();
					Collection<Contact> contacts = db.getContacts();
					long duration = System.currentTimeMillis() - now;
					if(LOG.isLoggable(INFO))
						LOG.info("Load took " + duration + " ms");
					displayContacts(contacts);
				} catch(DbException e) {
					if(LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for database");
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	private void displayContacts(final Collection<Contact> contacts) {
		runOnUiThread(new Runnable() {
			public void run() {
				FragmentManager fm = getSupportFragmentManager();
				if(contacts.isEmpty()) {
					noContactsDialog.show(fm, "NoContactsDialog");
				} else {
					selectContactsDialog.setContacts(contacts);
					selectContactsDialog.show(fm, "SelectContactsDialog");
				}
			}
		});
	}

	private boolean validateName() {
		if(nameEntry.getText().toString().equals("")) return false;
		// Hide the soft keyboard
		Object o = getSystemService(INPUT_METHOD_SERVICE);
		((InputMethodManager) o).toggleSoftInput(HIDE_IMPLICIT_ONLY, 0);
		return true;
	}

	public void contactCreationSelected() {
		startActivity(new Intent(this, AddContactActivity.class));
	}

	public void contactCreationCancelled() {
		enableOrDisableCreateButton();
	}

	public void contactsSelected(Collection<ContactId> selected) {
		this.selected = selected;
		enableOrDisableCreateButton();
	}

	public void contactSelectionCancelled() {
		enableOrDisableCreateButton();
	}
}