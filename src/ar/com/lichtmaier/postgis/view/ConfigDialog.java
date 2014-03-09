package ar.com.lichtmaier.postgis.view;

import java.awt.*;
import java.awt.event.ActionEvent;

import javax.swing.*;

public class ConfigDialog extends JDialog
{
	private static final long serialVersionUID = 1L;

	final private JTextField host = new JTextField(Main.prefs.get("host", null), 20);
	final private JTextField database = new JTextField(Main.prefs.get("database", null), 20);
	final private JTextField user = new JTextField(Main.prefs.get("user", null), 8);
	final private JPasswordField password = new JPasswordField(Main.prefs.get("password", null), 8);

	public ConfigDialog(JComponent parent)
	{
		super(SwingUtilities.getWindowAncestor(parent), "Database configuration", ModalityType.DOCUMENT_MODAL);

		setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.BASELINE_LEADING;
		c.insets = new Insets(3, 3, 3, 3);
		JLabel label;
		int y = 0;

		c.gridy = y++;
		c.weightx = 0;
		label = new JLabel("Host:");
		label.setDisplayedMnemonic('h');
		label.setLabelFor(host);
		add(label, c);
		c.weightx = 1;
		add(host, c);

		c.gridy = y++;
		c.weightx = 0;
		label = new JLabel("Database:");
		label.setDisplayedMnemonic('d');
		label.setLabelFor(database);
		add(label, c);
		c.weightx = 1;
		add(database, c);

		c.gridy = y++;
		c.weightx = 0;
		label = new JLabel("User:");
		label.setDisplayedMnemonic('u');
		label.setLabelFor(user);
		add(label, c);
		c.weightx = 1;
		add(user, c);

		c.gridy = y++;
		c.weightx = 0;
		label = new JLabel("Password:");
		label.setDisplayedMnemonic('p');
		label.setLabelFor(password);
		add(label, c);
		c.weightx = 1;
		add(password, c);
		
		c.gridy = y++;
		c.anchor = GridBagConstraints.BASELINE_TRAILING;
		c.gridwidth = GridBagConstraints.REMAINDER;
		
		JPanel buttons = new JPanel();
		buttons.setLayout(new FlowLayout());
		JButton okButton = new JButton(new AbstractAction("Ok") {
			private static final long serialVersionUID = 1L;
			@Override
			public void actionPerformed(ActionEvent e)
			{
				Main.prefs.put("host", host.getText());
				Main.prefs.put("database", database.getText());
				Main.prefs.put("user", user.getText());				
				char[] pw = password.getPassword();
				Main.prefs.put("password", new String(pw));
				setVisible(false);
			}
		});
		buttons.add(okButton);
		buttons.add(new JButton(new AbstractAction("Cancel") {
			private static final long serialVersionUID = 1L;
			@Override
			public void actionPerformed(ActionEvent e)
			{
				setVisible(false);
			}
		}));
		add(buttons, c);
		
		getRootPane().setDefaultButton(okButton);
		
		pack();
		Dimension ps = getPreferredSize();
		setMinimumSize(ps);
		setLocationRelativeTo(parent);
		setVisible(true);
	}
}
