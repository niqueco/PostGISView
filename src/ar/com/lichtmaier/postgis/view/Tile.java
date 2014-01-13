package ar.com.lichtmaier.postgis.view;

import java.awt.Image;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.concurrent.*;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

public class Tile
{
	public final int x, y, zoom;
	
	public Tile(int x, int y, int zoom)
	{
		this.x = x;
		this.y = y;
		this.zoom = zoom;
	}

	public static Tile fromCoords(final double lat, final double lon, final int zoom)
	{
		int xtile = (int)Math.floor((lon + 180) / 360 * (1 << zoom));
		int ytile = (int)Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1
				/ Math.cos(Math.toRadians(lat)))
				/ Math.PI)
				/ 2 * (1 << zoom));
		if(xtile < 0)
			xtile = 0;
		if(xtile >= (1 << zoom))
			xtile = ((1 << zoom) - 1);
		if(ytile < 0)
			ytile = 0;
		if(ytile >= (1 << zoom))
			ytile = ((1 << zoom) - 1);
		return new Tile(xtile, ytile, zoom);
	}

	public static Tile fromCoords(Point2D.Double coords, int zoom)
	{
		return fromCoords(coords.y, coords.x, zoom);
	}

	private transient static int i = 0;

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + x;
		result = prime * result + y;
		result = prime * result + zoom;
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		Tile other = (Tile)obj;
		if(x != other.x)
			return false;
		if(y != other.y)
			return false;
		if(zoom != other.zoom)
			return false;
		return true;
	}

	public URL getUrl()
	{
		try
		{
			return new URL("http://" + ("abc".charAt(i % 3)) + ".tile.openstreetmap.org/" + zoom + "/" + x
					+ "/" + y + ".png");
		} catch(MalformedURLException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	public Point2D.Double getCorner1(Point2D.Double p)
	{
		if( p == null)
			p = new Point2D.Double();
		p.x = getLon();
		p.y = getLat();
		return p;
	}

	public Point2D.Double getCorner2(Point2D.Double p)
	{
		if( p == null)
			p = new Point2D.Double();
		p.x = getEndLon();
		p.y = getEndLat();
		return p;
	}

	double getLon()
	{
		return tile2lon(x, zoom);
	}

	double getLat()
	{
		return tile2lat(y, zoom);
	}

	double getEndLon()
	{
		return tile2lon(x+1, zoom);
	}

	double getEndLat()
	{
		return tile2lat(y+1, zoom);
	}

	static double tile2lon(int x, int zoom)
	{
		return x / Math.pow(2.0, zoom) * 360.0 - 180;
	}

	static double tile2lat(int y, int zoom)
	{
		double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, zoom);
		return Math.toDegrees(Math.atan(Math.sinh(n)));
	}

	static LinkedHashMap<Tile, Future<Image>> imageCache = new LinkedHashMap<Tile, Future<Image>>() {
		private static final long serialVersionUID = 1L;
		protected boolean removeEldestEntry(java.util.Map.Entry<Tile,Future<Image>> eldest) { return size() > 200; };
	};

	public Future<Image> getImage(final MapPanel mp) throws IOException
	{
		Future<Image> image = imageCache.get(this);
		final File f = getCachedFilename();
		if(image == null)
		{
			if(f.canRead())
				image = new CompletedFuture<Image>(ImageIO.read(f));
			else
			{
				image = pool.submit(new Callable<Image>() {
					@Override
					public Image call() throws Exception
					{
						InputStream in = getUrl().openStream();
						f.getParentFile().mkdirs();
						in = new TeeInputStream(in, new FileOutputStream(f));
						ImageInputStream iis = ImageIO.createImageInputStream(in);
						BufferedImage imagen = ImageIO.read(iis);
						mp.tileLoaded(Tile.this, imagen);
						return imagen;
					}
				});
			}
			imageCache.put(this, image);
		}
		return image;
	}

	protected File getCachedFilename()
	{
		return new File(getCacheDir(), "tile-"+x+'-'+y+'-'+zoom + ".png");
	}

	static ExecutorService pool = new ThreadPoolExecutor(0, 6,
                        60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

	@Override
	public String toString()
	{
		return "Tile [x=" + x + ", y=" + y + ", zoom=" + zoom + "]";
	}

	static File getCacheDir()
	{
		String home = System.getProperty("user.home");
		if(System.getProperty("os.name").equals("Linux"))
			return new File(home, ".cache/PostGISView");
		return new File(home, ".PostGISView" + File.separator + "cache");
	}
}

class CompletedFuture<T> implements Future<T>
{
	final private T data;

	public CompletedFuture(T data)
	{
		this.data = data;
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning)
	{
		return false;
	}

	@Override
	public boolean isCancelled()
	{
		return false;
	}

	@Override
	public boolean isDone()
	{
		return true;
	}

	@Override
	public T get() throws InterruptedException, ExecutionException
	{
		return data;
	}

	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
	{
		return data;
	}
}
