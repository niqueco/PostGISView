package ar.com.lichtmaier.postgis.view;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.prefs.Preferences;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.postgis.Geometry;
import org.postgis.PGgeometry;

public class Main extends JPanel
{
	private static final long serialVersionUID = 1L;

	private Connection conn;

	static Preferences prefs = Preferences.userNodeForPackage(Main.class);
	
	class ExecuteQueryAction extends AbstractAction
	{
		private static final long serialVersionUID = 1L;

		ExecuteQueryAction()
		{
			super("Execute query");
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			final String query = queryField.getText();

			resultsModel.runQuery(Main.this, query);
		}
	}
	final Action executeQueryAction = new ExecuteQueryAction();

	final private JTextField queryField = new JTextField(prefs.get("query", "select entity, point, shape from geo_entities where point is not null and shape is not null limit 10"));

	private final QueryTableModel resultsModel = new QueryTableModel();

	public Main()
	{
		setLayout(new BorderLayout());
		final JTable table = new JTable();
		table.setModel(resultsModel);
		final MapPanel mapPanel = new MapPanel();
		final MapInfoPanel mapInfoPanel = new MapInfoPanel(this, mapPanel);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e)
			{
				if(e.getValueIsAdjusting())
					return;
				mapPanel.reset();
				mapInfoPanel.setGeo(null);
				int columnCount = resultsModel.getColumnCount();
				for(int r : table.getSelectedRows())
				{
					r = table.convertRowIndexToModel(r);
					for(int col = 0 ; col < columnCount ; col++)
						if(resultsModel.getColumnClass(col) == PGgeometry.class)
						{
							PGgeometry x = (PGgeometry)resultsModel.getValueAt(r, col);
							Geometry geo = (x != null) ? x.getGeometry() : null;
							mapPanel.addGeo(geo);
							if(!(geo instanceof org.postgis.Point))
								mapInfoPanel.setGeo(geo);
						}
				}
			}
		});
		table.setAutoCreateRowSorter(true);
		JPanel right = new JPanel();
		right.setLayout(new BorderLayout());
		right.add(mapPanel, BorderLayout.CENTER);
		JToolBar mapToolbar = new JToolBar();
		mapToolbar.setFloatable(false);
		mapToolbar.add(mapPanel.zoomInAction);
		mapToolbar.add(mapPanel.zoomOutAction);
		right.add(mapToolbar, BorderLayout.NORTH);
		right.add(mapInfoPanel, BorderLayout.SOUTH);
		add(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(table), right), BorderLayout.CENTER);
		JToolBar toolbar = new JToolBar();
		toolbar.setFloatable(false);
		toolbar.add(queryField);
		toolbar.add(executeQueryAction);
		add(toolbar, BorderLayout.NORTH);
		queryField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e)
			{
				prefs.put("query", queryField.getText());
			}
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				prefs.put("query", queryField.getText());
			}
			@Override
			public void changedUpdate(DocumentEvent e) { }
		});
		try
		{
			Class.forName("org.postgresql.Driver");
		} catch(ClassNotFoundException e1)
		{
			e1.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error loading PostgreSQL driver class: " + e1.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	public Connection getConnection() throws SQLException
	{
		synchronized(this)
		{
			if(conn == null || !conn.isValid(5))
				conn = DriverManager.getConnection("jdbc:postgresql://turing.wolfram.com/geolookup", "geolookup", "geo.lookup");
			try
			{
				((org.postgresql.PGConnection)conn).addDataType("geometry",Class.forName("org.postgis.PGgeometry"));
			} catch(ClassNotFoundException e)
			{
				throw new RuntimeException(e);
			}
		}
		return conn;
	}
}
