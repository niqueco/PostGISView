package ar.com.lichtmaier.postgis.view;

import javax.swing.JFrame;

public class MainWindow extends JFrame
{
	private static final long serialVersionUID = 1L;

	public MainWindow()
	{
		super("PostGIS view");
		
		setContentPane(new Main());
		
		pack();
		
		setLocationByPlatform(true);
		
		setVisible(true);
	}

	public static void main(String[] args)
	{
		MainWindow view = new MainWindow();
		view.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

}
