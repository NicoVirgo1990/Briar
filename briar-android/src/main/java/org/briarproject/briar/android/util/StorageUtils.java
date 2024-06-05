package org.briarproject.briar.android.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import android.content.Context;
import android.util.Log;

import org.briarproject.briar.android.BriarApplicationImpl;

public class StorageUtils
{
	public String storeOnStorage(String content, String fileName)
	{
		InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
		File yourDir = BriarApplicationImpl.getInstance().getDir("AR", Context.MODE_PRIVATE);

		File[] fileList = yourDir.listFiles();
		if (yourDir != null && fileList != null)
		{
		}
		else
		{
			yourDir.mkdirs();
		}
		File file = new File(yourDir + "/" + fileName);
		try
		{
			file.createNewFile();
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(getBytesFromInputStream(inputStream));
			fos.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return file.getPath();
	}

	public byte[] getBytesFromInputStream(InputStream is)
	{
		try
		{
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			byte[] buffer = new byte[0xFFFF];

			for (int len; (len = is.read(buffer)) != -1;)
				os.write(buffer, 0, len);

			os.flush();

			return os.toByteArray();
		}
		catch (IOException e)
		{
			return null;
		}
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
}
