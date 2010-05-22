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

package solidbase.core;

import java.io.File;
import java.io.IOException;
import org.testng.Assert;
import org.testng.annotations.Test;

import solidbase.core.FatalException;
import solidbase.core.PatchFile;
import solidbase.util.RandomAccessLineReader;

public class MissingBlock
{
	@Test
	public void testBasic() throws IOException
	{
		RandomAccessLineReader ralr = new RandomAccessLineReader( new File( "testpatch-missingblock.sql" ) );
		PatchFile patchFile = new PatchFile( ralr );
		try
		{
			patchFile.read();
			Assert.fail( "Expected an exception" );
		}
		catch( FatalException e )
		{
			patchFile.close();
			Assert.assertTrue( e.getMessage().contains( "not found" ) );
		}
	}

	@Test
	public void testMissingInitBlock() throws IOException
	{
		RandomAccessLineReader ralr = new RandomAccessLineReader( new File( "testpatch-missinginitblock.sql" ) );
		PatchFile patchFile = new PatchFile( ralr );
		try
		{
			patchFile.read();
			Assert.fail( "Expected an exception" );
		}
		catch( FatalException e )
		{
			patchFile.close();
			Assert.assertTrue( e.getMessage().contains( "not found" ) );
			Assert.assertFalse( e.getMessage().contains( "null" ) );
		}
	}
}
