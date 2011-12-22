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

import java.math.BigDecimal;

import solidbase.core.CommandFileException;


/**
 * This is a tokenizer for CSV. It maintains the current line number, and it ignores whitespace.
 *
 * @author Ren� M. de Bloois
 */
public class JSONTokenizer
{
	/**
	 * The reader used to read from and push back characters.
	 */
	protected PushbackReader in;

	/**
	 * Buffer for the result.
	 */
	protected StringBuilder result = new StringBuilder( 256 );


	/**
	 * Constructs a new instance of the Tokenizer.
	 *
	 * @param in The input.
	 */
	public JSONTokenizer( LineReader in )
	{
		this.in = new PushbackReader( in );
	}

	public Token get()
	{
		return get( false );
	}

	/**
	 * Returns the next token from the input.
	 *
	 * @return A token from the input. Null if there are no more tokens available.
	 */
	public Token get( boolean newLines )
	{
		StringBuilder result = this.result;
		result.setLength( 0 );

		while( true )
		{
			int ch = this.in.read();
			if( ch == -1 )
				return Token.EOI;
			if( newLines && ( ch == '\n' || ch == '\r' ) )
			{
				if( ch == '\r' )
				{
					ch = this.in.read();
					if( ch != '\n' )
						this.in.push( ch );
				}
				return Token.NEWLINE;
			}
			switch( ch )
			{
				// Whitespace
				case ' ':
				case '\t':
				case '\n':
				case '\r':
					continue;
				case ',':
					return Token.VALUE_SEPARATOR;
				case ':':
					return Token.NAME_SEPARATOR;
				case '[':
					return Token.BEGIN_ARRAY;
				case ']':
					return Token.END_ARRAY;
				case '{':
					return Token.BEGIN_OBJECT;
				case '}':
					return Token.END_OBJECT;
				case '"':
					while( true )
					{
						ch = this.in.read();
						if( ch == -1 )
							throw new CommandFileException( "Missing \"", this.in.getLocation() );
						if( ch == '"' )
							break;
						if( ch == '\\' )
						{
							ch = this.in.read();
							if( ch == -1 )
								throw new CommandFileException( "Incomplete escape sequence", this.in.getLocation() );
							switch( ch )
							{
								case 'b': ch = '\b'; break;
								case 'f': ch = '\f'; break;
								case 'n': ch = '\n'; break;
								case 'r': ch = '\r'; break;
								case 't': ch = '\t'; break;
								case '\"': break;
								case '\\': break;
								case 'u':
									char[] codePoint = new char[ 4 ];
									for( int i = 0; i < 4; i++ )
									{
										ch = this.in.read();
										codePoint[ i ] = (char)ch;
										if( !( ch >= '0' && ch <= '9' ) )
											throw new CommandFileException( "Illegal escape sequence: \\u" + String.valueOf( codePoint, 0, i + 1 ), this.in.getLocation() );
									}
									ch = Integer.valueOf( String.valueOf( codePoint ), 16 );
									break;
								default:
									throw new CommandFileException( "Illegal escape sequence: \\" + ( ch >= 0 ? (char)ch : "" ), this.in.getLocation() );
							}
						}
						result.append( (char)ch );
					}
					return new Token( Token.STRING, result.toString() );
				case '+':
				case '-':
					result.append( (char)ch );
					ch = this.in.read();
					if( !( ch >= '0' && ch <= '9' ) )
						throw new CommandFileException( "Invalid number", this.in.getLocation() );
					//$FALL-THROUGH$
				case '0': case '1': case '2': case '3': case '4':
				case '5': case '6': case '7': case '8': case '9':
					while( ch >= '0' && ch <= '9' )
					{
						result.append( (char)ch );
						ch = this.in.read();
					}
					if( ch == '.' )
					{
						result.append( (char)ch );
						ch = this.in.read();
						if( !( ch >= '0' && ch <= '9' ) )
							throw new CommandFileException( "Invalid number", this.in.getLocation() );
						while( ch >= '0' && ch <= '9' )
						{
							result.append( (char)ch );
							ch = this.in.read();
						}
					}
					if( ch == 'E' || ch == 'e' )
					{
						result.append( (char)ch );
						ch = this.in.read();
						if( ch == '+' || ch == '-' )
						{
							result.append( (char)ch );
							ch = this.in.read();
						}
						if( !( ch >= '0' && ch <= '9' ) )
							throw new CommandFileException( "Invalid number", this.in.getLocation() );
						while( ch >= '0' && ch <= '9' )
						{
							result.append( (char)ch );
							ch = this.in.read();
						}
					}
					this.in.push( ch );
					return new Token( Token.NUMBER, new BigDecimal( result.toString() ) );
				case 'a': case 'b': case 'c': case 'd': case 'e':
				case 'f': case 'g': case 'h': case 'i': case 'j':
				case 'k': case 'l': case 'm': case 'n': case 'o':
				case 'p': case 'q': case 'r': case 's': case 't':
				case 'u': case 'v': case 'w': case 'x': case 'y':
				case 'z':
					while( ch >= 'a' && ch <= 'z' )
					{
						result.append( (char)ch );
						ch = this.in.read();
					}
					this.in.push( ch );

					String keyword = result.toString();
					if( keyword.equals( "false" ) )
						return Token.FALSE;
					if( keyword.equals( "null" ) )
						return Token.NULL;
					if( keyword.equals( "true" ) )
						return Token.TRUE;

					throw new CommandFileException( "Unexpected keyword " + keyword, this.in.getLocation() );
				default:
					throw new CommandFileException( "Unexpected character '" + (char)ch + "'", this.in.getLocation() );
			}
		}
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
	 * Returns the current file location.
	 *
	 * @return The current file location.
	 */
	public FileLocation getLocation()
	{
		return this.in.getLocation();
	}

	/**
	 * Returns the underlying reader. But only if the back buffer is empty, otherwise an IllegalStateException is thrown.
	 *
	 * @return The underlying reader.
	 */
	public LineReader getReader()
	{
		return this.in.getReader();
	}


	/**
	 * A CSV token.
	 *
	 * @author Ren� M. de Bloois
	 */
	static public class Token
	{
		static final protected char STRING = '"';
		static final protected char NUMBER = '0';

		/** A end-of-input token */
		static final protected Token EOI = new Token( (char)0 );
		static final protected Token NEWLINE = new Token( '\n' );

		static final protected Token BEGIN_ARRAY = new Token( '[' );
		static final protected Token END_ARRAY = new Token( ']' );
		static final protected Token BEGIN_OBJECT = new Token( '{' );
		static final protected Token END_OBJECT = new Token( '}' );
		static final protected Token NAME_SEPARATOR = new Token( ':' );
		static final protected Token VALUE_SEPARATOR = new Token( ',' );

		static final protected Token FALSE = new Token( 'b', Boolean.FALSE );
		static final protected Token NULL = new Token( 'n' );
		static final protected Token TRUE = new Token( 'b', Boolean.TRUE );

		/**
		 * The type of the token.
		 */
		private char type;

		/**
		 * The value of the token.
		 */
		private Object value;

		/**
		 * Constructs a new token.
		 *
		 * @param type The type of the token.
		 */
		private Token( char type )
		{
			this.type = type;
		}

		/**
		 * Constructs a new token.
		 *
		 * @param value The value of the token.
		 */
		protected Token( String value )
		{
			this.value = value;
		}

		protected Token( char type, Object value )
		{
			this.type = type;
			this.value = value;
		}

		/**
		 * Returns the value of token.
		 *
		 * @return The value of token.
		 */
		public Object getValue()
		{
			if( this.type == 'n' )
				return null;
			if( this.value == null )
				throw new IllegalStateException( "Value is null" );
			return this.value;
		}

		/**
		 * Is this token the end-of-input token?
		 *
		 * @return True if this token is the end-of-input token, false otherwise.
		 */
		public boolean isEndOfInput()
		{
			return this.type == 0;
		}

		public boolean isNewLine()
		{
			return this.type == '\n';
		}

		/**
		 * Is this token the end-of-input token?
		 *
		 * @return True if this token is the end-of-input token, false otherwise.
		 */
		public boolean isBeginArray()
		{
			return this.type == '[';
		}

		public boolean isEndArray()
		{
			return this.type == ']';
		}

		public boolean isBeginObject()
		{
			return this.type == '{';
		}

		public boolean isEndObject()
		{
			return this.type == '}';
		}

		public boolean isNameSeparator()
		{
			return this.type == ':';
		}

		public boolean isValueSeparator()
		{
			return this.type == ',';
		}

		public boolean isString()
		{
			return this.type == STRING;
		}

		public boolean isNumber()
		{
			return this.type == NUMBER;
		}

		public boolean isBoolean()
		{
			return this.type == 'b';
		}

		public boolean isNull()
		{
			return this.type == 'n';
		}

		@Override
		public String toString()
		{
			if( this.value != null )
				return this.value.toString();
			if( this.type == 0 )
				return "End-of-input";
			return String.valueOf( this.type );
		}
	}

	public void close()
	{
		this.in.close();
	}
}