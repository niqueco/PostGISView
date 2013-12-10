package ar.com.lichtmaier.postgis.view;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;

import org.postgis.PGgeometry;

public class QueryTableModel extends AbstractTableModel
{
	private static final long serialVersionUID = 1L;

	private String[] headers;
	private Class<?>[] types;
	
	private List<Object[]> rows = new ArrayList<Object[]>();

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
		new SwingWorker<Void, Object[]>() {
	
			private String[] newHeaders;

			@Override
			protected Void doInBackground() throws Exception
			{
				Connection c = main.getConnection();
				ResultSet rs = null;
				try
				{
					rs = c.createStatement().executeQuery(query);
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
				} catch(SQLException e)
				{
					e.printStackTrace();
				} finally
				{
					if(rs != null)
						rs.close();
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
			};
		}.execute();
	}

	public PGgeometry getGeo(int r)
	{
		for(int i = 0 ; i < getColumnCount() ; i++)
			if(getColumnClass(i) == PGgeometry.class)
				return (PGgeometry)rows.get(r)[i];
		return null;
	}
}
