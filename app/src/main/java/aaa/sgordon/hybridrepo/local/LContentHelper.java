package aaa.sgordon.hybridrepo.local;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.Utilities;
import aaa.sgordon.hybridrepo.local.types.LContent;

//TODO Move the content out of the data dir in case the app is deleted

public class LContentHelper {
	private static final String TAG = "Hyb.Local.Cont";
	private static final String contentDir = "content";


	//WARNING: This method does not create the file or parent directory, it only provides the location
	@NonNull
	private static File getContentLocationOnDisk(@NonNull String hash) {
		//Starting out of the app's data directory...
		Context context = MyApplication.getAppContext();
		String appDataDir = context.getApplicationInfo().dataDir;

		//Content is stored in a content subdirectory
		File contentRoot = new File(appDataDir, contentDir);

		//With each content file named by its SHA256 hash
		return new File(contentRoot, hash);
	}


	//---------------------------------------------------------------------------------------------


	//WARNING: The file at the end of this uri may not exist
	@NonNull
	public static Uri getContentUri(@NonNull String name) {
		File contents = getContentLocationOnDisk(name);
		return Uri.fromFile(contents);
	}


	public static LContent writeContents(@NonNull String name, @NonNull byte[] contents) throws IOException {
		File destinationFile = getContentLocationOnDisk(name);

		if(!destinationFile.exists()) {
			Files.createDirectories(destinationFile.toPath().getParent());
			Files.createFile(destinationFile.toPath());
		}


		try(OutputStream out = Files.newOutputStream(destinationFile.toPath());
			DigestOutputStream dos = new DigestOutputStream(out, MessageDigest.getInstance("SHA-256"))) {

			dos.write(contents);
			String fileHash = Utilities.bytesToHex(dos.getMessageDigest().digest());

			return new LContent(name, fileHash, contents.length);
		}
		catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
	}


	public static LContent writeContents(@NonNull String name, @NonNull Uri source) throws IOException {
		File destinationFile = getContentLocationOnDisk(name);

		if(!destinationFile.exists()) {
			Files.createDirectories(destinationFile.toPath().getParent());
			Files.createFile(destinationFile.toPath());
		}


		//Write the source data to the destination file
		try (InputStream in = new URL(source.toString()).openStream();
			 FileOutputStream out = new FileOutputStream(destinationFile);
			 DigestOutputStream dos = new DigestOutputStream(out, MessageDigest.getInstance("SHA-256"))) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
				dos.write(dataBuffer, 0, bytesRead);
			}

			String fileHash = Utilities.bytesToHex(dos.getMessageDigest().digest());
			int fileSize = (int) destinationFile.length();

			return new LContent(name, fileHash, fileSize);
		}
		catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
	}


	public static void deleteContents(@NonNull String name) {
		File contentFile = getContentLocationOnDisk(name);
		boolean del = contentFile.delete();
	}
}
