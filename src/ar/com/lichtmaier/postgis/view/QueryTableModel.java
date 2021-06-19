package ar.com.lichtmaier.postgis.view;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;

public class QueryTableModel extends AbstractTableModel
{
	private static final long serialVersionUID = 1L;

	private String[] headers;
	private Class<?>[] types;
	
	private List<Object[]> rows = new ArrayList<>();

	@Override
	public int getRowCount()
	{
		return rows.size();
	}

	@Override
	public int getColumnCount()
	{
		return headers == null ? 0 : headers.length;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex)
	{
		return rows.get(rowIndex)[columnIndex];
	}
	
	@Override
	public String getColumnName(int column)
	{
		return headers[column];
	}

	@Override
	public Class<?> getColumnClass(int columnIndex)
	{
		return types[columnIndex];
	}
	
	void runQuery(final Main main, final String query)
	{
		main.executeQueryAction.setEnabled(false);
		new SwingWorker<Void, Object[]>() {
	
			private String[] newHeaders;

			@Override
			protected Void doInBackground() throws Exception
			{
				Connection c = main.getConnection();
				try(Statement stmt = c.createStatement())
				{
					ResultSet rs = stmt.executeQuery(query);
					ResultSetMetaData md = rs.getMetaData();
					final int columnCount = md.getColumnCount();
					newHeaders = new String[columnCount];
					for(int i = 0 ; i < columnCount ; i++)
						newHeaders[i] = md.getColumnLabel(i + 1);
					while(!isCancelled() && rs.next())
					{
						Object[] row = new Object[columnCount];
						for(int i = 0 ; i < columnCount ; i++)
							row[i] = rs.getObject(i + 1);
						publish(row);
					}
				}
				return null;
			}
			
			@Override
			protected void process(java.util.List<Object[]> chunks)
			{
				boolean reseted = false;
				if(newHeaders != null)
				{
					headers = newHeaders;
					newHeaders = null;
					rows.clear();
					reseted = true;
					types = null;
				}
				for(Object[] row : chunks)
				{
					if(types == null)
					{
						types = new Class[row.length];
						for(int i = 0 ; i < row.length ; i++)
							types[i] = row[i] == null ? Object.class : row[i].getClass();
					}
					rows.add(row);
				}
				if(reseted)
					fireTableStructureChanged();
				else
					fireTableDataChanged();
			};
			
			@Override
			protected void done()
			{
				main.executeQueryAction.setEnabled(true);
				try
				{
					get();
				} catch(Exception e)
				{
					e.printStackTrace();
					JOptionPane.showMessageDialog(main, e.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			};
		}.execute();
	}
}
