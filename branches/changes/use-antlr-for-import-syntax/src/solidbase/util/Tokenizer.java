/*--
 * Copyright 2010 Ren� M. de Bloois
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

package solidbase.util;

import java.io.Reader;

import solidbase.core.CommandFileException;


/**
 * This is a tokenizer for a language like SQL. It maintains the current line number, it is case insensitive, and it ignores whitespace.
 * 
 * @author Ren� M. de Bloois
 */
public class Tokenizer
{
	/**
	 * The reader used to read from and push back characters.
	 */
	protected PushbackReader in;


	/**
	 * Constructs a new instance of the Tokenizer.
	 * 
	 * @param in The input.
	 * @param lineNumber The current line number.
	 */
	public Tokenizer( Reader in, int lineNumber )
	{
		this.in = new PushbackReader( in, lineNumber );
	}

	/**
	 * Is the given character a whitespace?
	 * 
	 * @param ch The character to check.
	 * @return True if the characters is whitespace, false otherwise.
	 */
	static protected boolean isWhitespace( int ch )
	{
		switch( ch )
		{
			case '\n':
			case '\f':
			case '\r':
			case '\t':
			case ' ':
				return true;
		}
		return false;
	}

	/**
	 * Is the given character a special character?
	 * 
	 * @param ch The character to check.
	 * @return True if the characters is a special character, false otherwise.
	 */
	static protected boolean isSpecial( int ch )
	{
		switch( ch )
		{
			case '!':
			case '"':
			case '#':
			case '%':
			case '&':
			case '\'':
			case '(':
			case ')':
			case '*':
			case '+':
			case ',':
			case '-':
			case '.':
			case ':':
			case ';':
			case '<':
			case '=':
			case '>':
			case '?':
			case '@':
			case '[':
			case '\\':
			case ']':
			case '^':
			case '_':
			case '`':
			case '~':
				return true;
		}
		return false;
	}

	/**
	 * Returns the next token from the input. The preceding whitespace is also contained separately in {@link Token#getWhiteSpace()}.
	 * 
	 * @return A token from the input. Null if there are no more tokens available.
	 */
	public Token get()
	{
		// Read whitespace
		StringBuilder whiteSpace = new StringBuilder();
		int ch = this.in.read();
		while( ch != -1 && isWhitespace( ch ) )
		{
			whiteSpace.append( (char)ch );
			ch = this.in.read();
		}

		// Read a string enclosed by ' or "
		if( ch == '\'' || ch == '"' )
		{
			StringBuilder result = new StringBuilder( 32 );
			int quote = ch;
			while( true )
			{
				result.append( (char)ch );

				ch = this.in.read();
				if( ch == -1 )
					throw new CommandFileException( "Unexpected EOF", this.in.getLineNumber() );
				if( ch == '\n' ) // \r are filtered out by the PushbackReader
					throw new CommandFileException( "Unexpected EOL", this.in.getLineNumber() );
				if( ch == quote )
				{
					result.append( (char)ch );
					ch = this.in.read();
					if( ch != quote ) // Double '' or "" do not end the string
					{
						this.in.push( ch );
						break;
					}
				}
			}
			return new Token( result.toString(), whiteSpace.toString() );
		}

		if( isSpecial( ch ) )
			return new Token( String.valueOf( (char)ch ), whiteSpace.toString() );

		// Collect all characters until whitespace or special character
		StringBuilder result = new StringBuilder( 16 );
		do
		{
			result.append( (char)ch );
			ch = this.in.read();
		}
		while( ch != -1 && !isWhitespace( ch ) && !isSpecial( ch ) );

		// Push back the last character
		this.in.push( ch );

		// Return the result
		if( result.length() == 0 )
			return null;
		return new Token( result.toString(), whiteSpace.toString() );
	}

	/**
	 * A token that matches one of the expected tokens. Throws a {@link CommandFileException} if a token is encountered
	 * that does not match the given expected tokens.
	 * 
	 * @param expected The expected tokens.
	 * @return One of the expected tokens.
	 */
	public Token get( String... expected )
	{
		if( expected.length == 0 )
			throw new IllegalArgumentException( "Specify one ore more expected tokens" );

		Token token = get();

		for( String exp : expected )
			if( token.equals( exp ) )
				return token;

		// Raise exception

		int lineNumber = this.in.getLineNumber();
		if( token.isNewline() )
			lineNumber--;

		if( expected.length == 1 )
			throw new CommandFileException( "Expecting [" + expected[ 0 ] + "], not [" + token + "]", lineNumber );

		StringBuilder b = new StringBuilder( "Expecting one of" );
		for( String exp : expected )
		{
			b.append( " [" );
			b.append( exp );
			b.append( ']' );
		}
		b.append( ", not [" );
		b.append( token );
		b.append( "]" );
		throw new CommandFileException( b.toString(), lineNumber );
	}

	/**
	 * Returns a newline token. Throws a {@link CommandFileException} if another token is found.
	 * 
	 * @return The newline token.
	 */
	public Token getNewline()
	{
		// Read whitespace
		StringBuilder whiteSpace = new StringBuilder();
		int ch = this.in.read();
		while( ch != -1 && ch != '\n' && isWhitespace( ch ) )
		{
			whiteSpace.append( (char)ch );
			ch = this.in.read();
		}

		// Check newline
		if( ch == -1 )
			throw new CommandFileException( "Unexpected EOF", this.in.getLineNumber() );
		if( ch != '\n' )
			throw new CommandFileException( "Expecting NEWLINE, not [" + (char)ch + "]", this.in.getLineNumber() );

		// Return the result
		return new Token( String.valueOf( (char)ch ), whiteSpace.toString() );
	}

	/**
	 * Push back a token.
	 * 
	 * @param token The token to push back.
	 */
	public void push( Token token )
	{
		this.in.push( token.getValue() );
		this.in.push( token.getWhiteSpace() );
	}

	/**
	 * Returns the current line number.
	 * 
	 * @return The current line number.
	 */
	public int getLineNumber()
	{
		return this.in.getLineNumber();
	}

	/**
	 * Returns the underlying reader. But only if the back buffer is empty, otherwise an IllegalStateException is thrown.
	 * 
	 * @return The underlying reader.
	 */
	public Reader getReader()
	{
		return this.in.getReader();
	}


	/**
	 * A token. The token is case insensitive, so the {@link #equals(String)} does a case insensitive comparison.
	 * 
	 * @author Ren� M. de Bloois
	 */
	static public class Token
	{
		/**
		 * The value of the token.
		 */
		protected String value;

		/**
		 * The whitespace encountered before the token.
		 */
		protected String whiteSpace;

		/**
		 * Constructs a new token.
		 * 
		 * @param value The value of the token.
		 * @param whiteSpace The whitespace encountered before the token.
		 */
		public Token( String value, String whiteSpace )
		{
			this.value = value;
			this.whiteSpace = whiteSpace;
		}

		/**
		 * Returns the value of token.
		 * 
		 * @return The value of token.
		 */
		public String getValue()
		{
			return this.value;
		}

		/**
		 * Returns the whitespace encountered before the token.
		 * 
		 * @return The whitespace encountered before the token.
		 */
		public String getWhiteSpace()
		{
			return this.whiteSpace;
		}

		/**
		 * Is this token a newline?
		 * 
		 * @return True if this token is a newline, false otherwise.
		 */
		public boolean isNewline()
		{
			return this.value.charAt( 0 ) == '\n'; // Assume that if char 0 is a newline then the whole string is just the newline
		}

		/**
		 * Does a case insensitive comparison with the given string.
		 * 
		 * @param s A string to compare the value of this token with.
		 * @return True if the value of this token and the given string are equal (ignoring case), false otherwise.
		 */
		public boolean equals( String s )
		{
			return this.value.equalsIgnoreCase( s );
		}

		/**
		 * The length of the value of this token.
		 * 
		 * @return Length of the value of this token.
		 */
		public int length()
		{
			return this.value.length();
		}

		@Override
		public String toString()
		{
			return this.value;
		}
	}
}