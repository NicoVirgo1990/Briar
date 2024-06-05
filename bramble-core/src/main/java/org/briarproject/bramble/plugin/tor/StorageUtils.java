package org.briarproject.bramble.plugin.tor;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class StorageUtils
{
	public byte[] readFromStorage(String name)
	{
		byte[] dataArray = null;
		try
		{
			File myFile = new File("/data/user/0/org.briarproject.briar.android.debug/app_AR/" + name);
			FileInputStream fis = new FileInputStream(myFile);
			dataArray = new byte[fis.available()];
			fis.close();
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return dataArray;
	}

	public String readFile(String name)
	{

		String everything = "";
		try
		{
			BufferedReader br = new BufferedReader(new FileReader("/data/user/0/org.briarproject.briar.android.debug/app_AR/" + name));
			try
			{
				StringBuilder sb = new StringBuilder();
				String line = br.readLine();

				while (line != null)
				{
					sb.append(line);
					sb.append(System.lineSeparator());
					line = br.readLine();
				}
				everything = sb.toString();
			}
			finally
			{
				br.close();
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		return everything;
	}

	public String openFileToString(byte[] bytes)
	{
		String fileString = "";
		try
		{
			fileString = "";

			for (int i = 0; i < bytes.length; i++)
			{
				fileString += (char) bytes[i];
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return fileString;
	}
}
