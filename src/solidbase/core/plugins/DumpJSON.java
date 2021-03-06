/*--
 * Copyright 2011 Ren� M. de Bloois
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang.StringUtils;

import solidbase.core.Command;
import solidbase.core.CommandListener;
import solidbase.core.CommandProcessor;
import solidbase.core.FatalException;
import solidbase.core.SourceException;
import solidbase.core.SystemException;
import solidbase.util.Assert;
import solidbase.util.Counter;
import solidbase.util.FixedCounter;
import solidbase.util.JDBCSupport;
import solidbase.util.JSONArray;
import solidbase.util.JSONObject;
import solidbase.util.JSONWriter;
import solidbase.util.SQLTokenizer;
import solidbase.util.SQLTokenizer.Token;
import solidbase.util.TimedCounter;
import solidstack.io.DeferringWriter;
import solidstack.io.FileResource;
import solidstack.io.Resource;
import solidstack.io.Resources;
import solidstack.io.SourceReaders;
import solidstack.script.scopes.AbstractScope;
import solidstack.script.scopes.UndefinedException;
import funny.Symbol;


/**
 * This plugin executes EXPORT CSV statements.
 *
 * @author Ren� M. de Bloois
 * @since Aug 12, 2011
 */
// TODO To compressed file
public class DumpJSON implements CommandListener
{
	static private final Pattern triggerPattern = Pattern.compile( "\\s*DUMP\\s+JSON\\s+.*", Pattern.DOTALL | Pattern.CASE_INSENSITIVE );


	//@Override
	// TODO Escape dynamic file names, because illegal characters may be generated
	// TODO Export multiple tables to a single file. If no PK than sort on all columns. Schema name for import or not?
	public boolean execute( CommandProcessor processor, Command command, boolean skip ) throws SQLException
	{
		if( !triggerPattern.matcher( command.getCommand() ).matches() )
			return false;

		if( command.isTransient() )
		{
			/* DUMP JSON DATE_CREATED ON | OFF */

			SQLTokenizer tokenizer = new SQLTokenizer( SourceReaders.forString( command.getCommand(), command.getLocation() ) );

			// TODO Maybe DUMP JSON CONFIG or DUMP JSON SET
			// TODO What about other configuration settings?
			tokenizer.get( "DUMP" );
			tokenizer.get( "JSON" );
			tokenizer.get( "DATE_CREATED" ); // FIXME This should be CREATED_DATE
			Token t = tokenizer.get( "ON", "OFF" );
			tokenizer.get( (String)null );

			// TODO I think we should have a scope that is restricted to the current file and a scope that gets inherited when running or including another file.
			AbstractScope scope = processor.getContext().getScope();
			scope.set( Symbol.apply( "solidbase.dump_json.dateCreated" ), t.eq( "ON" ) ); // TODO Make this a constant

			return true;
		}

		if( skip )
			return true;

		Parsed parsed = parse( command );

		AbstractScope scope = processor.getContext().getScope();
		boolean dateCreated;
		try
		{
			Object object = scope.get( Symbol.apply( "solidbase.dump_json.dateCreated" ) );
			dateCreated = object instanceof Boolean && (Boolean)object;
		}
		catch( UndefinedException e )
		{
			dateCreated = true;
		}

		Resource jsvResource = new FileResource( new File( parsed.fileName ) ); // Relative to current folder

		try
		{
			OutputStream out = jsvResource.getOutputStream();
			if( parsed.gzip )
				out = new BufferedOutputStream( new GZIPOutputStream( out, 65536 ), 65536 ); // TODO Ctrl-C, close the outputstream?

			JSONWriter jsonWriter = new JSONWriter( out );
			try
			{
				Statement statement = processor.createStatement();
				try
				{
					ResultSet result = statement.executeQuery( parsed.query );
					ResultSetMetaData metaData = result.getMetaData();

					// Define locals

					int columns = metaData.getColumnCount();
					int[] types = new int[ columns ];
					String[] names = new String[ columns ];
					boolean[] ignore = new boolean[ columns ];
					FileSpec[] fileSpecs = new FileSpec[ columns ];
					String schemaNames[] = new String[ columns ];
					String tableNames[] = new String[ columns ];

					// Analyze metadata

					for( int i = 0; i < columns; i++ )
					{
						int col = i + 1;
						String name = metaData.getColumnName( col ).toUpperCase();
						types[ i ] = metaData.getColumnType( col );
						if( types[ i ] == Types.DATE && parsed.dateAsTimestamp )
							types[ i ] = Types.TIMESTAMP;
						names[ i ] = name;
						if( parsed.columns != null )
						{
							ColumnSpec columnSpec = parsed.columns.get( name );
							if( columnSpec != null )
								if( columnSpec.skip )
									ignore[ i ] = true;
								else
									fileSpecs[ i ] = columnSpec.toFile;
						}
						if( parsed.coalesce != null && parsed.coalesce.notFirst( name ) )
							ignore[ i ] = true;
						// TODO STRUCT serialize
						// TODO This must be optional and not the default
						else if( types[ i ] == 2002 || JDBCSupport.toTypeName( types[ i ] ) == null )
							ignore[ i ] = true;
						tableNames[ i ] = StringUtils.upperCase( StringUtils.defaultIfEmpty( metaData.getTableName( col ), null ) );
						schemaNames[ i ] = StringUtils.upperCase( StringUtils.defaultIfEmpty( metaData.getSchemaName( col ), null ) );
					}

					if( parsed.coalesce != null )
						parsed.coalesce.bind( names );

					// Write header

					JSONObject properties = new JSONObject();
					properties.set( "version", "1.0" );
					properties.set( "format", "record-stream" );
					properties.set( "description", "SolidBase JSON Data Dump File" );
					properties.set( "createdBy", new JSONObject( "product", "SolidBase", "version", "2.0.0" ) );

					if( dateCreated )
					{
						SimpleDateFormat format = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );
						properties.set( "createdDate", format.format( new Date() ) );
					}

					if( parsed.binaryFileName != null )
					{
						// TODO FIXME Should be wrapped in a SourceException: solidbase.solidstack.io.FatalURISyntaxException: java.net.URISyntaxException: Illegal character in path at index 1: &{folder}/JIADHOCCH
						Resource binResource = Resources.getResource( parsed.binaryFileName );
						Resource resource = Resources.getResource( parsed.fileName );
						properties.set( "binaryFile", binResource.getPathFrom( resource ).toString() );
					}

					JSONArray fields = new JSONArray();
					properties.set( "fields", fields );
					for( int i = 0; i < columns; i++ )
						if( !ignore[ i ] )
						{
							JSONObject field = new JSONObject();
							field.set( "schemaName", schemaNames[ i ] );
							field.set( "tableName", tableNames[ i ] );
							field.set( "name", names[ i ] );
							field.set( "type", JDBCSupport.toTypeName( types[ i ] ) ); // TODO Better error message when type is not recognized, for example Oracle's 2007 for a user type
							FileSpec spec = fileSpecs[ i ];
							if( spec != null && !spec.generator.isDynamic() )
							{
								Resource fileResource = new FileResource( spec.generator.fileName );
								field.set( "file", fileResource.getPathFrom( jsvResource ).toString() );
							}
							fields.add( field );
						}

					FileSpec binaryFile = parsed.binaryFileName != null ? new FileSpec( true, parsed.binaryFileName, 0 ) : null;

					jsonWriter.writeFormatted( properties, 120 );
					jsonWriter.getWriter().write( '\n' );

					Counter counter = null;
					if( parsed.logRecords > 0 )
						counter = new FixedCounter( parsed.logRecords );
					else if( parsed.logSeconds > 0 )
						counter = new TimedCounter( parsed.logSeconds );

					try
					{
						while( result.next() )
						{
							Object[] values = new Object[ columns ];
							for( int i = 0; i < values.length; i++ )
								values[ i ] = JDBCSupport.getValue( result, types, i );

							if( parsed.coalesce != null )
								parsed.coalesce.coalesce( values );

							JSONArray array = new JSONArray();
							for( int i = 0; i < columns; i++ )
								if( !ignore[ i ] )
								{
									Object value = values[ i ];
									if( value == null )
									{
										array.add( null );
										continue;
									}

									// TODO 2 columns can't be written to the same dynamic filename

									FileSpec spec = fileSpecs[ i ];
									if( spec != null ) // The column is redirected to its own file
									{
										String relFileName = null;
										int startIndex;
										if( spec.binary )
										{
											if( spec.generator.isDynamic() )
											{
												String fileName = spec.generator.generateFileName( result );
												Resource fileResource = new FileResource( fileName );
												spec.out = fileResource.getOutputStream();
												spec.index = 0;
												relFileName = fileResource.getPathFrom( jsvResource ).toString();
											}
											else if( spec.out == null )
											{
												String fileName = spec.generator.generateFileName( result );
												Resource fileResource = new FileResource( fileName );
												spec.out = fileResource.getOutputStream();
											}
											if( value instanceof Blob )
											{
												InputStream in = ( (Blob)value ).getBinaryStream();
												startIndex = spec.index;
												byte[] buf = new byte[ 4096 ];
												for( int read = in.read( buf ); read >= 0; read = in.read( buf ) )
												{
													spec.out.write( buf, 0, read );
													spec.index += read;
												}
												in.close();
											}
											else if( value instanceof byte[] )
											{
												startIndex = spec.index;
												spec.out.write( (byte[])value );
												spec.index += ( (byte[])value ).length;
											}
											else
												throw new SourceException( names[ i ] + " (" + value.getClass().getName() + ") is not a binary column. Only binary columns like BLOB, RAW, BINARY VARYING can be written to a binary file", command.getLocation() );
											if( spec.generator.isDynamic() )
											{
												spec.out.close();
												JSONObject ref = new JSONObject();
												ref.set( "file", relFileName );
												ref.set( "size", spec.index - startIndex );
												array.add( ref );
											}
											else
											{
												JSONObject ref = new JSONObject();
												ref.set( "index", startIndex );
												ref.set( "length", spec.index - startIndex );
												array.add( ref );
											}
										}
										else
										{
											if( spec.generator.isDynamic() )
											{
												String fileName = spec.generator.generateFileName( result );
												Resource fileResource = new FileResource( fileName );
												spec.writer = new DeferringWriter( spec.threshold, fileResource, jsonWriter.getEncoding() );
												spec.index = 0;
												relFileName = fileResource.getPathFrom( jsvResource ).toString();
											}
											else if( spec.writer == null )
											{
												String fileName = spec.generator.generateFileName( result );
												Resource fileResource = new FileResource( fileName );
												spec.writer = new OutputStreamWriter( fileResource.getOutputStream(), jsonWriter.getEncoding() );
											}
											if( value instanceof Blob || value instanceof byte[] )
												throw new SourceException( names[ i ] + " is a binary column. Binary columns like BLOB, RAW, BINARY VARYING cannot be written to a text file", command.getLocation() );
											if( value instanceof Clob )
											{
												Reader in = ( (Clob)value ).getCharacterStream();
												startIndex = spec.index;
												char[] buf = new char[ 4096 ];
												for( int read = in.read( buf ); read >= 0; read = in.read( buf ) )
												{
													spec.writer.write( buf, 0, read );
													spec.index += read;
												}
												in.close();
											}
											else
											{
												String val = value.toString();
												startIndex = spec.index;
												spec.writer.write( val );
												spec.index += val.length();
											}
											if( spec.generator.isDynamic() )
											{
												DeferringWriter writer = (DeferringWriter)spec.writer;
												if( writer.isBuffered() )
													array.add( writer.clearBuffer() );
												else
												{
													JSONObject ref = new JSONObject();
													ref.set( "file", relFileName );
													ref.set( "size", spec.index - startIndex );
													array.add( ref );
												}
												writer.close();
											}
											else
											{
												JSONObject ref = new JSONObject();
												ref.set( "index", startIndex );
												ref.set( "length", spec.index - startIndex );
												array.add( ref );
											}
										}
									}
									else if( value instanceof Clob )
										array.add( ( (Clob)value ).getCharacterStream() );
									else if( binaryFile != null && ( value instanceof Blob || value instanceof byte[] ) )
									{
										if( binaryFile.out == null )
										{
											String fileName = binaryFile.generator.generateFileName( null );
											Resource fileResource = new FileResource( fileName );
											binaryFile.out = fileResource.getOutputStream();
											if( parsed.binaryGzip )
												binaryFile.out = new BufferedOutputStream( new GZIPOutputStream( binaryFile.out, 65536 ), 65536 ); // TODO Ctrl-C, close the outputstream?
										}
										int startIndex = binaryFile.index;
										if( value instanceof Blob )
										{
											InputStream in = ( (Blob)value ).getBinaryStream();
											byte[] buf = new byte[ 4096 ];
											for( int read = in.read( buf ); read >= 0; read = in.read( buf ) )
											{
												binaryFile.out.write( buf, 0, read );
												binaryFile.index += read;
											}
											in.close();
										}
										else
										{
											binaryFile.out.write( (byte[])value );
											binaryFile.index += ( (byte[])value ).length;
										}
										JSONObject ref = new JSONObject();
										ref.set( "index", startIndex );
										ref.set( "length", binaryFile.index - startIndex );
										array.add( ref );
									}
									else
										array.add( value );
								}

							for( ListIterator< Object > i = array.iterator(); i.hasNext(); )
							{
								Object value = i.next();
								if( value instanceof java.sql.Date || value instanceof java.sql.Time || value instanceof java.sql.Timestamp || value instanceof java.sql.RowId )
									i.set( value.toString() );
							}
							jsonWriter.write( array );
							jsonWriter.getWriter().write( '\n' );

							if( counter != null && counter.next() )
								processor.getProgressListener().println( "Exported " + counter.total() + " records." );
						}
						if( counter != null && counter.needFinal() )
							processor.getProgressListener().println( "Exported " + counter.total() + " records." );
					}
					finally
					{
						// Close files that have been left open
						for( FileSpec fileSpec : fileSpecs )
							if( fileSpec != null )
							{
								if( fileSpec.out != null )
									fileSpec.out.close();
								if( fileSpec.writer != null )
									fileSpec.writer.close();
							}
						if( binaryFile != null && binaryFile.out != null )
							binaryFile.out.close();
					}
				}
				finally
				{
					processor.closeStatement( statement, true );
				}
			}
			finally
			{
				jsonWriter.close();
			}
		}
		catch( IOException e )
		{
			throw new SystemException( e );
		}

		return true;
	}


	/**
	 * Parses the given command.
	 *
	 * @param command The command to be parsed.
	 * @return A structure representing the parsed command.
	 */
	static protected Parsed parse( Command command )
	{
		/*
		DUMP JSON
		DATE AS TIMESTAMP
		COALESCE "<col1>", "<col2>"
		LOG EVERY n RECORDS|SECONDS
		FILE "file" GZIP
		BINARY FILE "file" GZIP
		COLUMN col1, col2 TO BINARY|TEXT FILE "file" THRESHOLD n
		*/

		Parsed result = new Parsed();

		SQLTokenizer tokenizer = new SQLTokenizer( SourceReaders.forString( command.getCommand(), command.getLocation() ) );

		tokenizer.get( "DUMP" );
		tokenizer.get( "JSON" );

		Token t = tokenizer.get( "DATE", "COALESCE", "LOG", "FILE" );

		if( t.eq( "DATE" ) )
		{
			tokenizer.get( "AS" );
			tokenizer.get( "TIMESTAMP" );

			result.dateAsTimestamp = true;

			t = tokenizer.get( "COALESCE", "LOG", "FILE" );
		}

		while( t.eq( "COALESCE" ) )
		{
			if( result.coalesce == null )
				result.coalesce = new Coalescer();

			t = tokenizer.get();
			if( !t.isString() )
				throw new SourceException( "Expecting column name enclosed in double quotes, not [" + t + "]", tokenizer.getLocation() );
			result.coalesce.first( t.stripQuotes() );

			t = tokenizer.get( "," );
			do
			{
				t = tokenizer.get();
				if( !t.isString() )
					throw new SourceException( "Expecting column name enclosed in double quotes, not [" + t + "]", tokenizer.getLocation() );
				result.coalesce.next( t.stripQuotes() );

				t = tokenizer.get();
			}
			while( t.eq( "," ) );

			result.coalesce.end();
		}

		tokenizer.expect( t, "LOG", "FILE" );

		if( t.eq( "LOG" ) )
		{
			tokenizer.get( "EVERY" );
			t = tokenizer.get();
			if( !t.isNumber() )
				throw new SourceException( "Expecting a number, not [" + t + "]", tokenizer.getLocation() );

			int interval = Integer.parseInt( t.getValue() );
			t = tokenizer.get( "RECORDS", "SECONDS" );
			if( t.eq( "RECORDS" ) )
				result.logRecords = interval;
			else
				result.logSeconds = interval;

			tokenizer.get( "FILE" );
		}

		t = tokenizer.get();
		if( !t.isString() )
			throw new SourceException( "Expecting filename enclosed in double quotes, not [" + t + "]", tokenizer.getLocation() );
		result.fileName = t.stripQuotes();

		t = tokenizer.get();
		if( t.eq( "GZIP" ) )
		{
			result.gzip = true;
			t = tokenizer.get();
		}

		if( t.eq( "BINARY" ) )
		{
			tokenizer.get( "FILE" );
			t = tokenizer.get();
			if( !t.isString() )
				throw new SourceException( "Expecting filename enclosed in double quotes, not [" + t + "]", tokenizer.getLocation() );
			result.binaryFileName = t.stripQuotes();

			t = tokenizer.get();
			if( t.eq( "GZIP" ) )
			{
				result.binaryGzip = true;
				t = tokenizer.get();
			}
		}

		if( t.eq( "COLUMN" ) )
		{
			result.columns = new HashMap< String, ColumnSpec >();
			while( t.eq( "COLUMN" ) )
			{
				List< Token > columns = new ArrayList< Token >();
				columns.add( tokenizer.get() );
				t = tokenizer.get();
				while( t.eq( "," ) )
				{
					columns.add( tokenizer.get() );
					t = tokenizer.get();
				}
				tokenizer.push( t );

				ColumnSpec columnSpec;
				t = tokenizer.get( "TO", "SKIP" );
				if( t.eq( "TO" ) )
				{
					t = tokenizer.get( "BINARY", "TEXT" );
					boolean binary = t.eq( "BINARY" );
					tokenizer.get( "FILE" );
					t = tokenizer.get();
					String fileName = t.getValue();
					if( !fileName.startsWith( "\"" ) )
						throw new SourceException( "Expecting filename enclosed in double quotes, not [" + t + "]", tokenizer.getLocation() );
					fileName = fileName.substring( 1, fileName.length() - 1 );

					t = tokenizer.get();
					int threshold = 0;
					if( t.eq( "THRESHOLD" ) )
					{
						threshold = Integer.parseInt( tokenizer.get().getValue() );
						t = tokenizer.get();
					}

					columnSpec = new ColumnSpec( false, new FileSpec( binary, fileName, threshold ) );
				}
				else
				{
					columnSpec = new ColumnSpec( true, null );
					t = tokenizer.get();
				}

				for( Token column : columns )
					result.columns.put( column.getValue().toUpperCase(), columnSpec );
			}
		}
		tokenizer.push( t );

		result.query = tokenizer.getRemaining();

		return result;
	}


	//@Override
	public void terminate()
	{
		// Nothing to clean up
	}


	/**
	 * A parsed command.
	 *
	 * @author Ren� M. de Bloois
	 */
	static protected class Parsed
	{
		/** The file path to export to */
		protected String fileName;
		protected boolean gzip;

		protected String binaryFileName;
		protected boolean binaryGzip;

		/** The query */
		protected String query;

		protected boolean dateAsTimestamp;

		/** Which columns need to be coalesced */
		protected Coalescer coalesce;

		protected int logRecords;
		protected int logSeconds;

		protected Map<String, ColumnSpec> columns;
	}


	static protected class FileSpec
	{
		protected boolean binary;
		protected int threshold;

		protected FileNameGenerator generator;
		protected OutputStream out;
		protected Writer writer;
		protected int index;

		protected FileSpec( boolean binary, String fileName, int threshold )
		{
			this.binary = binary;
			this.threshold = threshold;
			this.generator = new FileNameGenerator( fileName );
		}
	}


	static protected class ColumnSpec
	{
		protected boolean skip;
		protected FileSpec toFile;

		protected ColumnSpec( boolean skip, FileSpec toFile )
		{
			this.skip = skip;
			this.toFile = toFile;
		}
	}


	static protected class FileNameGenerator
	{
		protected final Pattern pattern = Pattern.compile( "\\?(\\d+)" );
		protected String fileName;
		protected boolean generic;

		protected FileNameGenerator( String fileName )
		{
			this.fileName = fileName;
			this.generic = this.pattern.matcher( fileName ).find();
		}

		protected boolean isDynamic()
		{
			return this.generic;
		}

		protected String generateFileName( ResultSet resultSet )
		{

			Matcher matcher = this.pattern.matcher( this.fileName );
			StringBuffer result = new StringBuffer();
			while( matcher.find() )
			{
				int index = Integer.parseInt( matcher.group( 1 ) );
				try
				{
					matcher.appendReplacement( result, resultSet.getString( index ) );
				}
				catch( SQLException e )
				{
					throw new SystemException( e );
				}
			}
			matcher.appendTail( result );
			return result.toString();
		}
	}


	static protected class Coalescer
	{
//		protected Set< String > first = new HashSet();
		protected Set< String > next = new HashSet< String >();
		protected List< List< String > > names = new ArrayList< List<String> >();
		protected List< List< Integer > > indexes = new ArrayList< List<Integer> >();
		protected List< String > temp;
		protected List< Integer > temp2;

		public void first( String name )
		{
//			this.first.add( name );

			Assert.isNull( this.temp );
			this.temp = new ArrayList< String >();
			this.temp.add( name );
			this.temp2 = new ArrayList< Integer >();
			this.temp2.add( null );
		}

		public void next( String name )
		{
			this.next.add( name );

			Assert.notNull( this.temp );
			this.temp.add( name );
			this.temp2.add( null );
		}

		public void end()
		{
			this.names.add( this.temp );
			this.indexes.add( this.temp2 );
			this.temp = null;
			this.temp2 = null;
		}

		public boolean notFirst( String name )
		{
			return this.next.contains( name );
		}

		public void bind( String[] names )
		{
			for( int i = 0; i < this.names.size(); i++ )
			{
				List< String > nams = this.names.get( i );
				List< Integer > indexes = this.indexes.get( i );
				for( int j = 0; j < nams.size(); j++ )
				{
					String name = nams.get( j );
					int found = -1;
					for( int k = 0; k < names.length; k++ )
						if( name.equals( names[ k ] ) )
						{
							found = k;
							break;
						}
					if( found < 0 )
						throw new FatalException( "Coalesce column " + name + " not in result set" );
					indexes.set( j, found );
				}
			}
		}

		public void coalesce( Object[] values )
		{
			for( int i = 0; i < this.indexes.size(); i++ )
			{
				List< Integer > indexes = this.indexes.get( i );
				int firstIndex = indexes.get( 0 );
				if( values[ firstIndex ] == null )
				{
					Object found = null;
					for( int j = 1; j < indexes.size(); j++ )
					{
						found = values[ indexes.get( j ) ];
						if( found != null )
							break;
					}
					values[ firstIndex ] = found;
				}
			}
		}
	}
}
