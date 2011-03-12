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

package solidbase.runner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import solidbase.Version;
import solidbase.core.Database;
import solidbase.core.Factory;
import solidbase.core.PatchProcessor;
import solidbase.core.ProgressListener;
import solidbase.core.SQLProcessor;
import solidbase.util.Resource;

/**
 * The runner contains the logic to execute upgrade files and SQL files and is used by the Ant tasks and Maven plugins.
 *
 * @author Ren� M. de Bloois
 */
public class Runner
{
	/**
	 * The progress listener.
	 */
	protected ProgressListener listener;

	/**
	 * The named database connections.
	 */
	protected Map< String, Connection > connections = new HashMap< String, Connection >();

	/**
	 * SQL files to execute.
	 */
	protected List< Resource > sqlFiles;

	/**
	 * Upgrade file to execute.
	 */
	protected Resource upgradeFile;

	/**
	 * The target to upgrade to.
	 */
	protected String upgradeTarget;

	/**
	 * Is a downgrade allowed during database upgrade?
	 */
	protected boolean downgradeAllowed;


	/**
	 * Sets the progress listener.
	 *
	 * @param listener The progress listener.
	 */
	public void setProgress( ProgressListener listener )
	{
		this.listener = listener;
	}

	/**
	 * Sets a connection to use.
	 *
	 * @param connection A connection to use.
	 */
	public void setDatabase( Connection connection )
	{
		this.connections.put( connection.getName(), connection );
	}

	/**
	 * Set SQL files to execute.
	 *
	 * @param sqlFiles SQL files to execute.
	 */
	public void setSQLFiles( List< Resource > sqlFiles )
	{
		this.sqlFiles = sqlFiles;
	}

	/**
	 * Set SQL file to execute.
	 *
	 * @param sqlFile SQL file to execute.
	 */
	public void setSQLFile( Resource sqlFile )
	{
		this.sqlFiles = new ArrayList< Resource >();
		this.sqlFiles.add( sqlFile );
	}

	/**
	 * Set the upgrade file.
	 *
	 * @param upgradeFile The upgrade file.
	 */
	public void setUpgradeFile( Resource upgradeFile )
	{
		this.upgradeFile = upgradeFile;
	}

	/**
	 * Set the upgrade target.
	 *
	 * @param upgradeTarget The upgrade target.
	 */
	public void setUpgradeTarget( String upgradeTarget )
	{
		this.upgradeTarget = upgradeTarget;
	}

	/**
	 * Set if a downgrade is allowed during upgrade.
	 *
	 * @param downgradeallowed Downgrade allowed during upgrade?
	 */
	public void setDowngradeAllowed( boolean downgradeallowed )
	{
		this.downgradeAllowed = downgradeallowed;
	}

	/**
	 * Execute the SQL files.
	 */
	public void executeSQL()
	{
		if( this.listener == null )
			throw new IllegalStateException( "ProgressListener not set" );

		this.listener.println( Version.getInfo() );
		this.listener.println( "" );

		SQLProcessor processor = new SQLProcessor( this.listener );

		Connection def = this.connections.get( "default" );
		if( def == null )
			throw new IllegalArgumentException( "Missing 'default' connection." );

		for( Connection connection : this.connections.values() )
			processor.addDatabase(
					new Database(
							connection.getName(),
							connection.getDriver() == null ? def.driver : connection.getDriver(),
							connection.getUrl() == null ? def.url : connection.getUrl(),
							connection.getUsername(),
							connection.getPassword(),
							this.listener
					)
			);

		try
		{
			boolean first = true;
			for( Resource resource : this.sqlFiles )
			{
				processor.setSQLSource( Factory.openSQLFile( resource, this.listener ).getSource() );
				if( first )
				{
					this.listener.println( "Connecting to database..." ); // TODO Let the database say that (for example the default connection)
					first = false;
				}
				processor.process();
			}
		}
		finally
		{
			processor.end();
		}

		this.listener.println( "" );
	}

	/**
	 * Upgrade the database.
	 */
	public void upgrade()
	{
		if( this.listener == null )
			throw new IllegalStateException( "ProgressListener not set" );

		this.listener.println( Version.getInfo() );
		this.listener.println( "" );

		PatchProcessor processor = new PatchProcessor( this.listener );

		Connection def = this.connections.get( "default" );
		if( def == null )
			throw new IllegalArgumentException( "Missing 'default' connection." );

		for( Connection connection : this.connections.values() )
			processor.addDatabase(
					new Database(
							connection.getName(),
							connection.getDriver() == null ? def.driver : connection.getDriver(),
							connection.getUrl() == null ? def.url : connection.getUrl(),
							connection.getUsername(),
							connection.getPassword(),
							this.listener
					)
			);

		processor.setPatchFile( Factory.openPatchFile( this.upgradeFile, this.listener ) );
		try
		{
			processor.init();
			this.listener.println( "Connecting to database..." );
			this.listener.println( processor.getVersionStatement() );
			processor.patch( this.upgradeTarget, this.downgradeAllowed ); // TODO Print this target
			this.listener.println( "" );
			this.listener.println( processor.getVersionStatement() );
		}
		finally
		{
			processor.end();
		}
	}
}
