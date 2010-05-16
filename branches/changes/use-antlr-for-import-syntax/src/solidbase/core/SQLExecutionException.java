/*--
 * Copyright 2009 Ren� M. de Bloois
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

package solidbase.core;

import java.sql.SQLException;


/**
 * An {@link SQLException} has occurred during execution of a {@link Command}. As a subclass of {@link FatalException}
 * the message of this exception will be presented to the user, not the stack trace.
 * 
 * @author Ren� M. de Bloois
 */
public class SQLExecutionException extends FatalException
{
	/**
	 * The command that caused the {@link SQLException}.
	 */
	private Command command;

	/**
	 * The {@link SQLException}.
	 */
	private SQLException sqlException;

	/**
	 * Constructor.
	 * 
	 * @param command The command that caused the {@link SQLException}.
	 * @param sqlException The {@link SQLException}.
	 */
	public SQLExecutionException( Command command, SQLException sqlException )
	{
		super( null );

		Assert.notNull( command );
		Assert.notNull( sqlException );

		this.command = command;
		this.sqlException = sqlException;
	}

	/**
	 * Loops through all the exceptions contained in the {@link SQLException} and combines all messages and SQLStates into one String.
	 * 
	 * @return all messages and SQLStates from the {@link SQLException} combined into one string.
	 * @see SQLException#getNextException()
	 */
	public String getSQLErrorMessages()
	{
		StringBuilder result = new StringBuilder();
		SQLException e = this.sqlException;
		while( true )
		{
			result.append( e.getSQLState() );
			result.append( ": " );
			result.append( e.getMessage() );
			e = e.getNextException();
			if( e == null )
				break;
			result.append( "\n" );
		}
		return result.toString();
	}

	@Override
	public String getMessage()
	{
		String command = this.command.getCommand();
		if( command.length() > 1000 )
			command = command.substring( 0, 1000 ) + "...";

		return getSQLErrorMessages() + "\nWhile executing line " + this.command.getLineNumber() + ": " + command;
	}
}
