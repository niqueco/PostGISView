package ar.com.lichtmaier.postgis.view;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import org.postgis.Geometry;
import org.postgis.PGgeometry;

public class MapInfoPanel extends JPanel
{
	private static final long serialVersionUID = 1L;

	private Geometry geo;
	final private Main main;

	final JLabel validLabel = new JLabel();

	public MapInfoPanel(Main main)
	{
		this.main = main;
		
		setLayout(new GridBagLayout());
		
		GridBagConstraints c = new GridBagConstraints();

		c.fill = GridBagConstraints.NONE;
		add(new JLabel("Valid:"), c);
		c.fill = GridBagConstraints.HORIZONTAL;
		add(validLabel, c);
	}

	public Geometry getGeo()
	{
		return geo;
	}

	public void setGeo(final Geometry geo)
	{
		if(geo == this.geo)
			return;
		this.geo = geo;
		validLabel.setText(null);
		if(geo == null)
			return;
		SwingWorker<Map<String,Object>, Void> sw = new SwingWorker<Map<String,Object>, Void> () {
			@Override
			protected Map<String, Object> doInBackground() throws Exception
			{
				Map<String, Object> r = new HashMap<String, Object>();
				Connection c = main.getConnection();
				try(PreparedStatement ps = c.prepareStatement("select ST_IsValidReason(?)"))
				{
					ps.setObject(1, new PGgeometry(geo));
					ResultSet rs = ps.executeQuery();
					if(rs.next())
					{
						r.put("valid", rs.getString(1));
					}
					return r;
				}
			}
			
			@Override
			protected void done()
			{
				try
				{
					validLabel.setText((String)get().get("valid"));
				} catch(Exception e)
				{
					e.printStackTrace();
				}
			}
		};
		sw.execute();
	}
}
