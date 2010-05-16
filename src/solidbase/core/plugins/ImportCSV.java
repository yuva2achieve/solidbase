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

package solidbase.core.plugins;

import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import solidbase.core.Assert;
import solidbase.core.Command;
import solidbase.core.CommandFileException;
import solidbase.core.CommandListener;
import solidbase.core.CommandProcessor;
import solidbase.core.SystemException;

import com.mindprod.csv.CSVReader;


/**
 * This plugin executes IMPORT CSV statements.
 *
 * <blockquote><pre>
 * IMPORT CSV INTO tablename
 * "xxxx1","yyyy1","zzzz1"
 * "xxxx2","yyyy2","zzzz2"
 * GO
 * </pre></blockquote>
 * 
 * This plugin will transform the CSV lines into INSERT INTO tablename VALUES ("xxxx","yyyy","zzzz").
 *
 * @author Ren� M. de Bloois
 * @since Dec 2, 2009
 */
// TODO Make this more strict, like assert that the number of values stays the same in the CSV data
public class ImportCSV extends CommandListener
{
	static private final Pattern triggerPattern = Pattern.compile( "IMPORT\\s+CSV.*", Pattern.DOTALL | Pattern.CASE_INSENSITIVE );

	static private final Pattern importPattern = Pattern.compile(
			"IMPORT\\s+CSV" +
			"(\\s+SEPARATED\\s+BY\\s+(\\S|TAB))?" + // group 1 + 2
			"(\\s+PREPEND\\s+LINENUMBER)?" + // group 3
			"(\\s+USING\\s+(PLBLOCK|VALUESLIST))?" + // group 4 + 5
			"\\s+INTO\\s+(\\S+)" + // group 6
			"(\\s*\\(\\s*(\\S+(\\s*,\\s*\\S+)*)\\s*\\))?" + // group 7 + 8 + 9
			"\\s*\\n(.*)", // group 10
			Pattern.DOTALL | Pattern.CASE_INSENSITIVE );

	static private final String syntax = "IMPORT CSV [SEPARATED BY TAB|<char>] [PREPEND LINENUMBER] [USING PLBLOCK|VALUESLIST] INTO <table> [(<colums>)] <newline> <data>";

	@Override
	public boolean execute( CommandProcessor processor, Command command ) throws SQLException
	{
		if( command.isTransient() )
			return false;

		Matcher matcher = triggerPattern.matcher( command.getCommand() );
		if( !matcher.matches() )
			return false;

		matcher = importPattern.matcher( command.getCommand() );
		if( !matcher.matches() )
			throw new CommandFileException( "Syntax error processing IMPORT CSV command, should match " + syntax, command.getLineNumber() );

		// Determine separator
		String sep = matcher.group( 2 );
		char separator;
		if( sep == null )
			separator = ',';
		else if( sep.equalsIgnoreCase( "TAB" ) )
			separator = '\t';
		else
		{
			Assert.isTrue( sep.length() == 1, "Seperator should be 1 character long", command.getLineNumber() );
			separator = sep.charAt( 0 );
		}

		// Collect the rest
		boolean prependLineNumber = matcher.group( 3 ) != null;
		String as = matcher.group( 5 );
		boolean asBlock = as != null && as.equalsIgnoreCase( "PLBLOCK" );
		boolean asValues = as != null && as.equalsIgnoreCase( "VALUESLIST" );
		String tableName = matcher.group( 6 );
		String columnList = matcher.group( 8 );
		String data = matcher.group( 10 );

		String[] columns = null;
		if( columnList != null )
			columns = columnList.split( "\\s*,\\s*" );

		try
		{
			// Initialize csv reader & read first line
			CSVReader reader = new CSVReader( new StringReader( data ), separator, '"', "#", true, false, true );
			int lineNumber = command.getLineNumber();
			String[] line;
			try
			{
				line = reader.getAllFieldsInLine();
			}
			catch( EOFException e )
			{
				return true; // Nothing to insert and nothing to clean up
			}

			if( columns != null )
				if( columns.length != ( prependLineNumber ? line.length + 1 : line.length ) )
					throw new CommandFileException( "Number of specified columns does not match the number of values in the first line of data", command.getLineNumber() );

			// Get connection and initialize commit flag
			Connection connection = processor.getCurrentDatabase().getConnection();
			Assert.isFalse( connection.getAutoCommit(), "Autocommit should be false" );
			PreparedStatement statement = null;
			boolean commit = false;
			try
			{
				if( asBlock )
				{
					StringBuilder sql = new StringBuilder();
					sql.append( "BEGIN\n" );
					while( line != null )
					{
						lineNumber++;
						sql.append( "INSERT INTO " );
						sql.append( tableName );
						if( columns != null )
						{
							sql.append( " (" );
							sql.append( columns[ 0 ] );
							for( int i = 1; i < columns.length; i++ )
							{
								sql.append( ',' );
								sql.append( columns[ i ] );
							}
							sql.append( ')' );
						}
						sql.append( " VALUES (" );
						if( prependLineNumber )
						{
							sql.append( lineNumber );
							sql.append( ',' );
						}
						for( int i = 0; i < line.length; i++ )
						{
							if( i > 0 )
								sql.append( ',' );
							sql.append( '\'' );
							sql.append( escape( line[ i ] ) );
							sql.append( '\'' );
						}
						sql.append( ");\n" );
						try
						{
							line = reader.getAllFieldsInLine();
						}
						catch( EOFException e )
						{
							line = null;
						}
					}
					sql.append( "END;\n" );
					String s = sql.toString();
					command.setCommand( s );
					statement = connection.prepareStatement( sql.toString() );
					statement.executeUpdate();
				}
				else if( asValues )
				{
					StringBuilder sql = new StringBuilder();
					sql.append( "INSERT INTO " );
					sql.append( tableName );
					if( columns != null )
					{
						sql.append( " (" );
						sql.append( columns[ 0 ] );
						for( int i = 1; i < columns.length; i++ )
						{
							sql.append( ',' );
							sql.append( columns[ i ] );
						}
						sql.append( ')' );
					}
					sql.append( " VALUES\n" );
					boolean comma = false;
					while( line != null )
					{
						lineNumber++;
						if( comma )
							sql.append( ',' );
						sql.append( '(' );
						if( prependLineNumber )
						{
							sql.append( lineNumber );
							sql.append( ',' );
						}
						for( int i = 0; i < line.length; i++ )
						{
							if( i > 0 )
								sql.append( ',' );
							sql.append( '\'' );
							sql.append( escape( line[ i ] ) );
							sql.append( '\'' );
						}
						sql.append( ")\n" );
						try
						{
							line = reader.getAllFieldsInLine();
						}
						catch( EOFException e )
						{
							line = null;
						}
						comma = true;
					}
					sql.append( '\n' );
					String s = sql.toString();
					command.setCommand( s );
					statement = connection.prepareStatement( sql.toString() );
					statement.executeUpdate();
				}
				else
				{
					StringBuilder sql = new StringBuilder().append( "INSERT INTO " );
					sql.append( tableName );
					if( columns != null )
					{
						sql.append( " (" );
						sql.append( columns[ 0 ] );
						for( int i = 1; i < columns.length; i++ )
						{
							sql.append( ',' );
							sql.append( columns[ i ] );
						}
						sql.append( ')' );
					}
					sql.append( " VALUES (?" );
					if( prependLineNumber )
						sql.append( ",?" );
					for( int i = line.length; i > 1; i-- )
						sql.append( ",?" );
					sql.append( ')' );
					statement = connection.prepareStatement( sql.toString() );
					while( line != null )
					{
						lineNumber++;
						int i = 0;
						if( prependLineNumber )
							statement.setInt( ++i, lineNumber );
						for( String field : line )
							statement.setString( ++i, field );
						statement.executeUpdate();
						try
						{
							line = reader.getAllFieldsInLine();
						}
						catch( EOFException e )
						{
							line = null;
						}
					}
				}
				commit = true;
			}
			finally
			{
				if( statement != null )
					statement.close();
				if( commit )
					connection.commit();
				else
					connection.rollback();
			}
		}
		catch( IOException e )
		{
			throw new SystemException( e );
		}

		return true;
	}

	/**
	 * Converts ' to ''.
	 * 
	 * @param s The sql to escape.
	 * @return Escaped sql.
	 */
	protected String escape( String s )
	{
		return s.replaceAll( "'", "''" );
	}
}
