package aaa.sgordon.hybridrepo;

import android.content.Context;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;
import aaa.sgordon.hybridrepo.remote.types.RFile;


public class ExampleInstrumentedTest {
	private static final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

	private static final Path emptyFile = Paths.get(context.getDataDir().toString(), "temp", "empty.txt");
	private static final String emptyChecksum = RFile.defaultChecksum;

	private static final Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	private static final Path smallFile = Paths.get(context.getDataDir().toString(), "temp", "smallFile.txt");
	private static final String smallChecksum = "35C461DEE98AAD4739707C6CCA5D251A1617BFD928E154995CA6F4CE8156CFFC";

	private static final UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	private static final RemoteRepo rRepo = RemoteRepo.getInstance();


	@BeforeAll
	public static void setUp() throws IOException {
		//Put empty content to Remote if it does not already exist

		try {
			rRepo.getContentProps(emptyChecksum);
		} catch (ContentsNotFoundException e) {
			createEmptyFile();
			rRepo.uploadData(emptyChecksum, emptyFile.toFile());
		}

		//Put the test content to Remote if it does not already exist
		try {
			rRepo.getContentProps(smallChecksum);
		} catch (ContentsNotFoundException e) {
			create1MBFile();
			rRepo.uploadData(smallChecksum, smallFile.toFile());
		}
	}


	private UUID fileUID;
	private RFile props;
	@BeforeEach
	public void setUpFile() throws FileAlreadyExistsException, ContentsNotFoundException, ConnectException {
		fileUID = UUID.randomUUID();

		props = new RFile(fileUID, accountUID);
		props = rRepo.createFile(props);
	}




	@Test
	public void testCreate() {
		//Make sure trying to re-create the same file fails
		Assertions.assertThrows(FileAlreadyExistsException.class, () -> rRepo.createFile(props));
	}


	@Test
	public void testUpdateContents() throws ContentsNotFoundException, FileNotFoundException, ConnectException {
		//Try updating the file content info to the same thing it currently is
		rRepo.putContentProps(props, props.checksum);

		//Try actually updating the file contents
		String oldChecksum = props.checksum;
		props.checksum = smallChecksum;
		props.filesize = (int) smallFile.toFile().length();
		RFile returnedFile = rRepo.putContentProps(props, oldChecksum);
		props.checksum = returnedFile.checksum;
		props.filesize = returnedFile.filesize;

		//Try it again but with an incorrect hash
		String fakeChecksum = props.checksum.replace('3', '4');
		Assertions.assertThrows(IllegalStateException.class, () -> rRepo.putContentProps(props, fakeChecksum));

		//Try a completely different fileuid
		props.fileuid = UUID.randomUUID();
		Assertions.assertThrows(FileNotFoundException.class, () -> rRepo.putContentProps(props, fakeChecksum));
		props.fileuid = fileUID;
	}


	@Test
	public void testUpdateAttributes() throws ConnectException, FileNotFoundException {
		//Try updating the file content info to the same thing it currently is
		rRepo.putAttributeProps(props, props.attrhash);

		//Try actually updating the file attributes
		props.userattr = new Gson().fromJson("{\"testkey\":\"testvalue\"}", JsonObject.class);
		RFile returnedFile = rRepo.putAttributeProps(props, props.attrhash);
		props.attrhash = returnedFile.attrhash;

		//Try it again but with an incorrect hash
		String fakeAttrHash = props.attrhash.replace('3', '4');
		Assertions.assertThrows(IllegalStateException.class, () -> rRepo.putAttributeProps(props, fakeAttrHash));

		//Try a completely different fileuid
		props.fileuid = UUID.randomUUID();
		Assertions.assertThrows(FileNotFoundException.class, () -> rRepo.putAttributeProps(props, fakeAttrHash));
		props.fileuid = fileUID;
	}



	@Test
	public void testUpdateTimestamps() throws FileNotFoundException, ConnectException {
		//Try updating the file content info
		rRepo.putTimestamps(props);

		//Try it again but with different times
		props.modifytime = Instant.now().getEpochSecond();
		rRepo.putTimestamps(props);

		//Try a completely different fileuid
		props.fileuid = UUID.randomUUID();
		Assertions.assertThrows(FileNotFoundException.class, () -> rRepo.putTimestamps(props));
		props.fileuid = fileUID;
	}






	//---------------------------------------------------------------------------------------------
	//---------------------------------------------------------------------------------------------


	private static void createEmptyFile() throws IOException {
		if(emptyFile.toFile().exists())
			return;

		Files.createDirectories(emptyFile.getParent());
		Files.createFile(emptyFile);
	}

	private static void create1MBFile() throws IOException {
		if(smallFile.toFile().exists())
			return;

		Files.createDirectories(smallFile.getParent());
		Files.createFile(smallFile);

		URL largeUrl = new URL(externalUri_1MB.toString());
		try (BufferedInputStream in = new BufferedInputStream(largeUrl.openStream());
			 DigestInputStream dis = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"));
			 FileOutputStream fileOutputStream = new FileOutputStream(smallFile.toFile())) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = dis.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}

			String checksum = Utilities.bytesToHex(dis.getMessageDigest().digest());
		}
		catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}