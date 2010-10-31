package solidbase.http.hyperdb;

import java.net.ServerSocket;
import java.net.Socket;

import solidbase.http.CompressionFilter;
import solidbase.http.DefaultServlet;
import solidbase.http.Dispatcher;
import solidbase.http.Handler;
import solidbase.http.TestServlet;

public class Main
{
	/**
	 * @param args
	 */
	public static void main( String[] args )
	{
		Dispatcher.registerServlet( "/table:([^/]*)", "tablename", new TableServlet() );
		Dispatcher.registerServlet( "/tables", new TablesServlet() );
		Dispatcher.registerServlet( "/test", new TestServlet() );
		Dispatcher.registerServlet( "/styles.css", new StylesServlet() );
		Dispatcher.registerServlet( "", new RootServlet() );
		Dispatcher.registerServlet( ".*", new DefaultServlet() );

		Dispatcher.registerFilter( ".*", new CompressionFilter() );

		try
		{
			ServerSocket server = new ServerSocket( 80 );
			while( true )
			{
				Socket socket = server.accept();
				Handler handler = new Handler( socket );
				handler.start();
			}
		}
		catch( Throwable t )
		{
			t.printStackTrace( System.err );
		}
	}
}
