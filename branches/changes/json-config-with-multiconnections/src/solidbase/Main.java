/*--
 * Copyright 2006 Ren� M. de Bloois
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package solidbase;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import solidbase.config.Configuration;
import solidbase.config.Connection;
import solidbase.core.Database;
import solidbase.core.Patcher;
import solidbase.core.SystemException;


/**
 *
 * @author Ren� M. de Bloois
 * @since Apr 1, 2006 7:18:27 PM
 */
public class Main
{
	static public Console console;
	static private int pass = 1;


	static protected void printCurrentVersion( Console console )
	{
		String version = Patcher.getCurrentVersion();
		String target = Patcher.getCurrentTarget();
		int statements = Patcher.getCurrentStatements();

		if( version == null )
		{
			if( target != null )
				console.println( "The database has no version yet, incompletely patched to version \"" + target + "\" (" + statements + " statements successful)." );
			else
				console.println( "The database has no version yet." );
		}
		else
		{
			if( target != null )
				console.println( "Current database version is \"" + version + "\", incompletely patched to version \"" + target + "\" (" + statements + " statements successful)." );
			else
				console.println( "Current database version is \"" + version + "\"." );
		}
	}


	static protected String list( Collection list )
	{
		StringBuffer buffer = new StringBuffer();

		boolean first = true;
		for( Iterator iter = list.iterator(); iter.hasNext(); )
		{
			Object object = iter.next();
			if( first )
				first = false;
			else
				buffer.append( ", " );
			buffer.append( object );
		}

		return buffer.toString();
	}


	static public void main( String... args )
	{
		try
		{
			main0( args );
		}
		catch( Throwable t )
		{
			console.println();

			if( t instanceof SystemException )
				if( t.getCause() != null )
					t = t.getCause();

			if( t instanceof SQLException )
				console.println( "SQLState: " + ( (SQLException)t ).getSQLState() );

			console.printStacktrace( t );

			System.exit( 1 );
		}
	}


	// Used for testing
	static public void main0( String... args ) throws Exception
	{
		if( console == null )
			console = new Console();

		// Configure the commandline options

		Options options = new Options();
		options.addOption( "verbose", false, "be extra verbose" );
		// TODO Rename to 'batchmode, keep 'fromant' hidden
		options.addOption( "fromant", false, "adds newlines after input requests" );
		options.addOption( "dumplog", true, "export historical patch results to an xml file" );
		options.addOption( "driver", true, "sets the jdbc driverclass" );
		options.addOption( "url", true, "sets the url of the database" );
		options.addOption( "username", true, "sets the default username to patch with" );
		options.addOption( "password", true, "sets the password of the default username" );
		options.addOption( "target", true, "sets the target version" );
		options.addOption( "patchfile", true, "sets the patch file" );
		options.addOption( "jsonconfigfile", true, "specify a configfile in JSON format" );
		// TODO Add driverjar option

		options.getOption( "dumplog" ).setArgName( "filename" );
		options.getOption( "driver" ).setArgName( "classname" );
		options.getOption( "url" ).setArgName( "url" );
		options.getOption( "username" ).setArgName( "username" );
		options.getOption( "password" ).setArgName( "password" );
		options.getOption( "target" ).setArgName( "targetversion" );
		options.getOption( "patchfile" ).setArgName( "patchfile" );
		options.getOption( "jsonconfigfile" ).setArgName( "configfile" );

		// Read the commandline options

		CommandLine line;
		try
		{
			line = new GnuParser().parse( options, args );
		}
		catch( ParseException e )
		{
			console.println( e.getMessage() );
			new HelpFormatter().printHelp( "solidbase", options, true );
			return;
		}

		boolean verbose = line.hasOption( "verbose" );
		boolean exportlog = line.hasOption( "dumplog" );
		console.fromAnt = line.hasOption( "fromant" );

		// Validate the commandline options

		if( line.hasOption( "driver" ) || line.hasOption( "url" ) || line.hasOption( "username" ) )
		{
			boolean valid = true;
			if( !line.hasOption( "driver" ) )
			{
				console.println( "Missing driver option" );
				valid = false;
			}
			if( !line.hasOption( "url" ) )
			{
				console.println( "Missing url option" );
				valid = false;
			}
			if( !line.hasOption( "username" ) )
			{
				console.println( "Missing user option" );
				valid = false;
			}
			if( !valid )
			{
				new HelpFormatter().printHelp( "solidbase", options, true );
				return;
			}
		}

		//

		Progress progress = new Progress( console, verbose );
		Configuration configuration = new Configuration( progress, pass, line.getOptionValue( "driver" ), line.getOptionValue( "url" ), line.getOptionValue( "username" ), line.getOptionValue( "password" ), line.getOptionValue( "target" ), line.getOptionValue( "patchfile" ), line.getOptionValue( "jsonconfigfile" ) );

		if( pass == 1 )
		{
			reload( args, configuration.getDriverJars(), verbose );
			return;
		}

		console.println( "SolidBase v" + configuration.getVersion() );
		console.println( "(C) 2006-2009 Ren� M. de Bloois" );
		console.println();

		Patcher.setCallBack( progress );
		String patchFile;
		String target;
		if( configuration.getConfigVersion() == 2 )
		{
			solidbase.config.Database selectedDatabase;
			if( configuration.getDatabases().size() == 0 )
			{
				console.println( "There are no databases configured." );
				return;
			}
			else if( configuration.getDatabases().size() > 1 )
			{
				console.println( "Available database:" );
				for( solidbase.config.Database database : configuration.getDatabases() )
					if( database.getDescription() != null )
						console.println( "    " + database.getName() + " (" + database.getDescription() + ")" );
					else
						console.println( "    " + database.getName() );
				console.print( "Select a database from the above: " );
				String input = console.input();
				selectedDatabase = configuration.getDatabase( input );
				console.println();
			}
			else
				selectedDatabase = configuration.getDatabases().get( 0 );

			solidbase.config.Application selectedApplication;
			if( selectedDatabase.getApplications().size() > 1 )
			{
				console.println( "Available applications in database '" + selectedDatabase.getName() + "':" );
				for( solidbase.config.Application application : selectedDatabase.getApplications() )
					if( application.getDescription() != null )
						console.println( "    " + application.getName() + " (" + application.getDescription() + ")" );
					else
						console.println( "    " + application.getName() );
				console.print( "Select an application from the above: " );
				String input = console.input();
				selectedApplication = selectedDatabase.getApplication( input );
				console.println();
			}
			else
				selectedApplication = selectedDatabase.getApplications().get( 0 );

			Patcher.setDefaultConnection( new Database( selectedDatabase.getDriver(), selectedDatabase.getUrl(), selectedApplication.getUserName(), selectedApplication.getPassword() ) );
			for( Connection connection : selectedApplication.getConnections() )
				Patcher.addConnection( connection );

			patchFile = selectedApplication.getPatchFile();
			target = selectedApplication.getTarget();
			console.println( "Connecting to database '" + selectedDatabase.getName() + "', application '" + selectedApplication.getName() + "'..." );
		}
		else
		{
			Patcher.setDefaultConnection( new Database( configuration.getDBDriver(), configuration.getDBUrl(), configuration.getUser(), configuration.getPassWord() ) );
			patchFile = configuration.getPatchFile();
			target = configuration.getTarget();
			if( patchFile == null )
				patchFile = "dbpatch.sql";
			console.println( "Connecting to database..." );
		}

		printCurrentVersion( console );

		if( exportlog )
		{
			Patcher.logToXML( line.getOptionValue( "dumplog" ) );
			return;
		}

		Patcher.openPatchFile( patchFile );
		try
		{
			if( target != null )
				Patcher.patch( target ); // TODO Print this target
			else
			{
				// Need linked set because order is important
				LinkedHashSet< String > targets = Patcher.getTargets( false, null );
				if( targets.size() > 0 )
				{
					console.println( "Possible targets are: " + list( targets ) );
					console.print( "Input target version: " );
					String input = console.input();
					Patcher.patch( input );
				}
				else
					console.println( "There are no possible targets." );
				// TODO Distinguish between uptodate and no possible path
			}
			console.emptyLine();
			printCurrentVersion( console );
		}
		finally
		{
			Patcher.closePatchFile();
		}
	}


	static protected void reload( String[] args, List< String > jars, boolean verbose ) throws Exception
	{
		if( jars == null || jars.isEmpty() )
		{
			// No need to add a new classloader
			pass2( args );
			return;
		}

		if( verbose )
			console.println( "Extending classpath" );

		// Add the driver jar(s) to the classpath
		URLClassLoader classLoader = (URLClassLoader)Main.class.getClassLoader();
		URL[] orig = classLoader.getURLs();
		URL[] urls;
		urls = new URL[ orig.length + jars.size() ];
		System.arraycopy( orig, 0, urls, 0, orig.length );
		int i = orig.length;
		for( String jar : jars )
		{
			File driverJarFile = new File( jar );
			urls[ i++ ] = driverJarFile.toURI().toURL();
			if( verbose )
				console.println( "Adding jar to classpath: " + urls[ i - 1 ] );
		}

		if( verbose )
			console.println();

		// Create a new classloader with the new classpath
		classLoader = new URLClassLoader( urls, Main.class.getClassLoader().getParent() );

		// Execute the main class through the new classloader with reflection
		Class main = classLoader.loadClass( "solidbase.Main" );
		Method method = main.getDeclaredMethod( "pass2", String[].class );
		method.invoke( method, (Object)args );
	}


	static public void pass2( String[] args ) throws Exception
	{
		pass = 2;
		main0( args );
	}
}
