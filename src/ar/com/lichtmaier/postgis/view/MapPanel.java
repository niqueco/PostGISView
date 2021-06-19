package ar.com.lichtmaier.postgis.view;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import javax.swing.*;

import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GlobalCoordinates;
import org.postgis.*;

import com.jhlabs.map.proj.MercatorProjection;
import com.jhlabs.map.proj.Projection;

public class MapPanel extends JPanel
{
	private static final long serialVersionUID = 1L;

	final private List<Geometry> geos = new ArrayList<>();
	final private List<Shape> shapes = new ArrayList<>();
	final private List<Point2D.Double> pointMarkers = new ArrayList<>();

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

	private List<Point2D.Double> errors = new ArrayList<>();
	
	public MapPanel()
	{
		setOpaque(true);
		setBackground(Color.WHITE);
		
		add(coordsLabel);
		
		projection = new MercatorProjection();
		projection.initialize();
		
		final MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e)
			{
				if(geos.isEmpty() || transform == null)
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
				if(geos.isEmpty())
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

	public void addGeo(Geometry geo)
	{
		if(geos.contains(geo))
			return;
		zoomInAction.setEnabled(true);
		zoomOutAction.setEnabled(true);
		setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		geos.add(geo);
		calculateBoundingBox();
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
		} else if(g instanceof org.postgis.Point)
		{
			org.postgis.Point point = (org.postgis.Point)g;
			pointMarkers.add(projection.transform(point.x, point.y, new Point2D.Double()));
		} else if(g instanceof GeometryCollection)
		{
			GeometryCollection gc = (GeometryCollection)g;
			for(Geometry x : gc.getGeometries())
				calculateShapes(x);
		} else if(g instanceof LineString) 
		{
			LineString ls = (LineString)g;
			process(ls);
		} else if(g instanceof MultiLineString)
		{
			MultiLineString mls = (MultiLineString)g;
			for(LineString ls : mls.getLines())
				process(ls);
		} else
		{
			System.err.println("unknown geometry: " + g + " class: " + g.getClass());
		}
	}

	private void process(LineString ls)
	{
		Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
		path.append(pointComposedGeomToPath(ls, false), false);
		shapes.add(path);
	}

	private void process(org.postgis.Polygon poly)
	{
		Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
		for(int r = 0; r < poly.numRings() ; r++)
			path.append(pointComposedGeomToPath(poly.getRing(r), true), false);
		shapes.add(new Area(path));
	}

	private Path2D.Double pointComposedGeomToPath(PointComposedGeom ring, boolean close)
	{
		Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
		final org.postgis.Point[] points = ring.getPoints();
		Point2D.Double startPoint = new Point2D.Double(), point = new Point2D.Double();
		projection.transform(points[0].x, points[0].y, startPoint);
		path.moveTo(startPoint.x, startPoint.y);
		for(int i = 1 ; i < points.length ; i++)
		{
			org.postgis.Point p = points[i];
			projection.transform(p.x, p.y, point);
			path.lineTo(point.x, point.y);
		}
		if(close)
			path.lineTo(startPoint.x, startPoint.y);
		return path;
	}

	private void calculateBoundingBox()
	{
		if(geos.isEmpty())
			return;
		double xmin = Double.MAX_VALUE;
		double ymin = Double.MAX_VALUE;
		double xmax = -Double.MAX_VALUE;
		double ymax = -Double.MAX_VALUE;
		for(Geometry geo : geos)
		{
			int shapePoints = geo.numPoints();
			for(int i = 0 ; i < shapePoints ; i++)
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
		}
		GlobalCoordinates p1 = new GlobalCoordinates(ymin, xmin);
		GlobalCoordinates p2 = new GlobalCoordinates(ymax, xmax);
		double diagonal = Math.max(GeodeticCalculator.calculateGeodeticCurve(Ellipsoid.WGS84, p1, p2).getEllipsoidalDistance(), 10000);
		p1 = GeodeticCalculator.calculateEndingGlobalCoordinates(Ellipsoid.WGS84, p1, 180 + 45, diagonal * 0.2);
		p2 = GeodeticCalculator.calculateEndingGlobalCoordinates(Ellipsoid.WGS84, p2, 45, diagonal * 0.2);
		projection.transform(p1.getLongitude(), p1.getLatitude(), minCorner);
		projection.transform(p2.getLongitude(), p2.getLatitude(), maxCorner);
	}

	@Override
	protected void paintComponent(Graphics gg)
	{
		super.paintComponent(gg);
		
		if(geos.isEmpty())
			return;
		
		Graphics2D g = (Graphics2D)gg.create();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		g.transform(transform);
		g.setStroke(new BasicStroke((int)(2 / transform.getScaleX()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		Rectangle clip = g.getClipBounds();
		try
		{
			Point2D.Double p = new Point2D.Double(clip.getX(), clip.getMaxY());
			projection.inverseTransform(p, p);
			final int zoom = getOSMZoom();
			final Tile orig = Tile.fromCoords(p, zoom);
			Tile t = orig;
			int dx = 0, dy = 0;
			do {
				t.getCorner1(p);
				projection.transform(p, p);
				Point2D.Double q = t.getCorner2(null);
				projection.transform(q, q);
				final Future<Image> f = t.getImage(this);
				if(f.isDone())
				{
					g.drawImage(f.get(), (int)p.x, (int)p.y, (int)(q.x - p.x), (int)(q.y - p.y), null);
					g.drawRect((int)p.x, (int)p.y, (int)(q.x - p.x), (int)(q.y - p.y));
				}
				dx++;
				t = new Tile(orig.x + dx, orig.y + dy, zoom);
				t.getCorner1(p);
				projection.transform(p, p);
				if(p.x > clip.getMaxX())
				{
					dx = 0;
					dy++;
					t = new Tile(orig.x + dx, orig.y + dy, zoom);
					t.getCorner1(p);
					projection.transform(p, p);
					if(p.y < clip.getMinY())
						break;
				}
				transform.transform(p, p);
			} while(true);
		} catch(Exception e)
		{
			e.printStackTrace();
		}

		for(Shape shape : shapes)
			g.draw(shape);
		g.setColor(Color.RED);
		int w = (int)(30.0 / transform.getScaleX());
		int h = (int)Math.abs(30.0 / transform.getScaleY());
		for(Point2D.Double p : errors)
			g.drawArc((int)p.x - (w / 2), (int)p.y - (h / 2), w, h, 0, 360);
		w/=10;
		h/=10;
		g.setColor(Color.BLUE);
		for(Point2D.Double p : pointMarkers)
			g.drawArc((int)p.x - (w / 2), (int)p.y - (h / 2), w, h, 0, 360);
	}

	final static private double log2 = Math.log(2);
	
	private int getOSMZoom()
	{
		try
		{
			Point2D.Double p = new Point2D.Double(0, 0);
			transform.inverseTransform(p, p);
			projection.inverseTransform(p, p);
			Point2D.Double q = new Point2D.Double(getWidth(), getHeight());
			transform.inverseTransform(q, q);
			projection.inverseTransform(q, q);
			double latrange = (q.x - p.x);
			double longrange = (p.y - q.y);
			return (int)Math.max((Math.floor(Math.log((360.0/longrange) * (getWidth() / 256.0)) / log2)),
				Math.floor((Math.log((180.0/latrange) * (getHeight() / 256.0)) / log2)));
		} catch(NoninvertibleTransformException e)
		{
			throw new RuntimeException(e);
		}
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

	public void tileLoaded(Tile tile, BufferedImage imagen)
	{
		Point2D.Double p = tile.getCorner1(null);
		projection.transform(p, p);
		Point2D.Double q = tile.getCorner2(null);
		projection.transform(q, q);
		transform.transform(p, p);
		transform.transform(q, q);
		repaint((int)p.x, (int)p.y, (int)(q.x - p.x), (int)(q.y - p.y));
	}

	public void addError(double lon, double lat)
	{
		Point2D.Double p = new Point2D.Double(lon, lat);
		projection.transform(p, p);
		errors.add(p);
	}

	public void reset()
	{
		geos.clear();
		shapes.clear();
		pointMarkers.clear();
		errors.clear();
		zoomInAction.setEnabled(false);
		zoomOutAction.setEnabled(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		repaint();
	}
}
