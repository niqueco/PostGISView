package ar.com.lichtmaier.postgis.view;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import org.postgis.Geometry;
import org.postgis.LinearRing;
import org.postgis.MultiPolygon;

import com.jhlabs.map.proj.MercatorProjection;
import com.jhlabs.map.proj.Projection;

public class MapPanel extends JPanel
{
	private static final long serialVersionUID = 1L;

	private Geometry geo;
	private List<Shape> shapes = new ArrayList<>();

	private final Point2D.Double minCorner = new Point2D.Double(), maxCorner = new Point2D.Double();
	
	private Projection projection;
	
	final private JLabel coordsLabel = new JLabel();

	private AffineTransform transform;

	class ZoomInAction extends AbstractAction
	{
		private static final long serialVersionUID = 1L;

		ZoomInAction()
		{
			super("+", new ImageIcon(MapPanel.class.getResource("zoom-in.png"), "Zoom in"));
			putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke('+'));
			setEnabled(false);
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			applyZoom(1.25);
		}
	}
	public Action zoomInAction = new ZoomInAction();

	class ZoomOutAction extends AbstractAction
	{
		private static final long serialVersionUID = 1L;

		ZoomOutAction()
		{
			super("-", new ImageIcon(MapPanel.class.getResource("zoom-out.png"), "Zoom out"));
			setEnabled(false);
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			applyZoom(1.0 / 1.25);
		}
	}
	public Action zoomOutAction = new ZoomOutAction();

	
	public MapPanel()
	{
		setOpaque(true);
		setBackground(Color.WHITE);
		
		add(coordsLabel);
		
		projection = new MercatorProjection();
		projection.initialize();
		System.out.println(projection);
		
		final MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e)
			{
				if(geo == null)
				{
					coordsLabel.setText(null);
					return;
				}
				try
				{
					Point2D.Double p = tr(e);
					projection.inverseTransform(p, p);
					coordsLabel.setText(p.getX() + ", " + p.getY());
				} catch(NoninvertibleTransformException e1)
				{
					coordsLabel.setText("Error! " + e1.getLocalizedMessage());
				}
			}

			private Point2D.Double tr(MouseEvent e) throws NoninvertibleTransformException
			{
				return (Point2D.Double)transform.inverseTransform(new Point2D.Double(e.getX(), e.getY()), null);
			}
			
			int dragStartX;
			int dragStartY;
			AffineTransform dragStartTransform;
			
			@Override
			public void mousePressed(MouseEvent e)
			{
				dragStartX = e.getX();
				dragStartY = e.getY();
				dragStartTransform = transform;
			}
			
			@Override
			public void mouseDragged(MouseEvent e)
			{
				AffineTransform t = AffineTransform.getTranslateInstance(e.getX() - dragStartX, e.getY() - dragStartY);
				t.concatenate(dragStartTransform);
				transform = t;
				repaint();
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e)
			{
				if(geo == null)
					return;
				final double r = e.getPreciseWheelRotation();
				applyZoom(Math.pow(1.2, -Math.signum(r)) * Math.abs(r));
				e.consume();
			}
		};
		addMouseMotionListener(mouse);
		addMouseListener(mouse);
		addMouseWheelListener(mouse);
	}

	public void setGeo(Geometry geo)
	{
		if(geo == this.geo)
			return;
		zoomInAction.setEnabled(geo != null);
		zoomOutAction.setEnabled(geo != null);
		setCursor(Cursor.getPredefinedCursor(geo != null ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
		this.geo = geo;
		calculateBoundingBox();
		shapes.clear();
		calculateShapes(geo);
		calculateTransform();
		repaint();
	}

	private void calculateShapes(Geometry g)
	{
		if(g instanceof org.postgis.Polygon)
		{
			process((org.postgis.Polygon)g);
		} else if(g instanceof MultiPolygon)
		{
			MultiPolygon multi = (MultiPolygon)g;
			for(org.postgis.Polygon poly : multi.getPolygons())
				process(poly);
		}
	}

	private void process(org.postgis.Polygon poly)
	{
		LinearRing ring = poly.getRing(0);
		Path2D.Float path = new Path2D.Float();
		Point2D.Double point = new Point2D.Double();
		final org.postgis.Point[] points = ring.getPoints();
		projection.transform(points[0].x, points[0].y, point);
		path.moveTo(point.x, point.y);
		for(int i = 1 ; i < points.length ; i++)
		{
			org.postgis.Point p = points[i];
			projection.transform(p.x, p.y, point);
			path.lineTo(point.x, point.y);
		}
		shapes.add(new Area(path));
	}

	private void calculateBoundingBox()
	{
		if(geo == null)
			return;
		int n = geo.numPoints();
		double xmin = Double.MAX_VALUE;
		double ymin = Double.MAX_VALUE;
		double xmax = -Double.MAX_VALUE;
		double ymax = -Double.MAX_VALUE;
		for(int i = 0 ; i < n ; i++)
		{
			org.postgis.Point point = geo.getPoint(i);
			if(point.x < xmin)
				xmin = point.x;
			if(point.x > xmax)
				xmax = point.x;
			if(point.y < ymin)
				ymin = point.y;
			if(point.y > ymax)
				ymax = point.y;
		}
		System.out.println("min=" + xmin + ", " + ymin);
		System.out.println("max=" + xmax+ ", " + ymax);
		projection.transform(xmin, ymin, minCorner);
		projection.transform(xmax, ymax, maxCorner);
		System.out.println("min=" + minCorner);
		System.out.println("max=" + maxCorner);
	}

	@Override
	protected void paintComponent(Graphics gg)
	{
		super.paintComponent(gg);
		
		if(geo == null)
			return;
		
		Graphics2D g = (Graphics2D)gg.create();
		
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		g.setStroke(new BasicStroke((int)(2 / transform.getScaleX()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.transform(transform);

		for(Shape shape : shapes)
			g.draw(shape);
	}
	
	void calculateTransform()
	{
		double widthMeters = maxCorner.x - minCorner.x;
		double heightMeters = maxCorner.y - minCorner.y;
		transform = AffineTransform.getScaleInstance(1, -1);
		transform.translate(0, -getHeight());
		double scale = Math.min(((double)getHeight()) / heightMeters, ((double)getWidth()) / widthMeters);
		transform.scale(scale, scale);
		transform.translate(-minCorner.x, -minCorner.y);
	}

	private void applyZoom(final double zoomFactor)
	{
		final AffineTransform zoom = AffineTransform.getTranslateInstance(getWidth() / 2, getHeight() / 2);
		zoom.scale(zoomFactor, zoomFactor);
		zoom.translate(-getWidth() / 2, -getHeight() / 2);
		transform.preConcatenate(zoom);
		repaint();
	}
}
