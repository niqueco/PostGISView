package ar.com.lichtmaier.postgis.view;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.postgis.Geometry;
import org.postgis.PGgeometry;

public class Main extends JPanel
{
	private static final long serialVersionUID = 1L;

	private Connection conn;
	
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
	final private Action executeQueryAction = new ExecuteQueryAction();

	final private JTextField queryField = new JTextField("select entity, shape from geo where paclet='Country' limit 10");

	private final QueryTableModel resultsModel = new QueryTableModel();

	public Main()
	{
		setLayout(new BorderLayout());
		final JTable table = new JTable();
		table.setModel(resultsModel);
		final MapPanel mapPanel = new MapPanel();
		final MapInfoPanel mapInfoPanel = new MapInfoPanel(this);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e)
			{
				int selectedRow = table.getSelectedRow();
				Geometry geo;
				if(selectedRow == -1)
				{
					geo = null;
				} else
				{
					int r = table.convertRowIndexToModel(selectedRow);
					final PGgeometry x = resultsModel.getGeo(r);
					geo = (x != null) ? x.getGeometry() : null;
				}
				mapPanel.setGeo(geo);
				mapInfoPanel.setGeo(geo);
			}
		} );
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
