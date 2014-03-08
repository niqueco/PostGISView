package ar.com.lichtmaier.postgis.view;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TeeInputStream extends FilterInputStream
{
	final private OutputStream out;

	protected TeeInputStream(InputStream in, OutputStream out)
	{
		super(in);
		this.out = out;
	}
	
	@Override
	public int read() throws IOException
	{
		final int b = super.read();
		out.write(b);
		return b;
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException
	{
		final int n = super.read(b, off, len);
		if(n > 0)
			out.write(b, off, n);
		return n;
	}

	@Override
	public long skip(long n) throws IOException
	{
		throw new RuntimeException("please don't");
	}
	
	@Override
	public void close() throws IOException
	{
		super.close();
		out.close();
	}
}
