package ar.com.lichtmaier.postgis.view;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import org.postgis.Geometry;
import org.postgis.PGgeometry;

public class MapInfoPanel extends JPanel
{
	private static final long serialVersionUID = 1L;

	private Geometry geo;
	final private Main main;
	final private MapPanel mapPanel;

	final JLabel validLabel = new JLabel();

	public MapInfoPanel(Main main, MapPanel mapPanel)
	{
		this.main = main;
		this.mapPanel = mapPanel;
		
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

	final static private Pattern coordsPattern = Pattern.compile("\\[(-?\\d+(?:\\.\\d+)?) (-?\\d+(?:\\.\\d+)?)]");

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
				PreparedStatement ps = c.prepareStatement("select ST_IsValidReason(?)");
				try
				{
					ps.setObject(1, new PGgeometry(geo));
					ResultSet rs = ps.executeQuery();
					if(rs.next())
					{
						r.put("valid", rs.getString(1));
					}
					return r;
				} finally
				{
					ps.close();
				}
			}
			
			@Override
			protected void done()
			{
				try
				{
					final String validText = (String)get().get("valid");
					validLabel.setText(validText);
					Matcher m = coordsPattern.matcher(validText);
					if(m.find())
					{
						double lon = Double.parseDouble(m.group(1));
						double lat = Double.parseDouble(m.group(2));
						mapPanel.addError(lon, lat);
					}
				} catch(Exception e)
				{
					e.printStackTrace();
					JOptionPane.showMessageDialog(MapInfoPanel.this, e.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				}
			}
		};
		sw.execute();
	}
}
