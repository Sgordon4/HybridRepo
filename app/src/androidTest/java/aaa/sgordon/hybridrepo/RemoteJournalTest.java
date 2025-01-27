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
import java.lang.reflect.Field;
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
import java.util.List;
import java.util.UUID;

import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;
import aaa.sgordon.hybridrepo.remote.connectors.JournalConnector;
import aaa.sgordon.hybridrepo.remote.types.RFile;
import aaa.sgordon.hybridrepo.remote.types.RJournal;

public class RemoteJournalTest {
	private static final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

	private static final Path emptyFile = Paths.get(context.getDataDir().toString(), "temp", "empty.txt");
	private static final String emptyChecksum = RFile.defaultChecksum;

	private static final Uri externalUri_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	private static final Path smallFile = Paths.get(context.getDataDir().toString(), "temp", "smallFile.txt");
	private static final String smallChecksum = "35C461DEE98AAD4739707C6CCA5D251A1617BFD928E154995CA6F4CE8156CFFC";

	private static final UUID accountUID = UUID.fromString("b16fe0ba-df94-4bb6-ad03-aab7e47ca8c3");
	private static final RemoteRepo rRepo = RemoteRepo.getInstance();


	@BeforeAll
	public static void setUp() throws IOException, NoSuchFieldException, IllegalAccessException {
		//The queries server-side exclude all journals created by our deviceuid.
		//Therefore we have to change the deviceuid we send over or we will receive no data for the tests.
		Field deviceuid = JournalConnector.class.getDeclaredField("deviceUID");
		deviceuid.setAccessible(true);
		deviceuid.set(rRepo.journalConn, UUID.randomUUID());


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
	public void setUpFile() {
		fileUID = UUID.randomUUID();
		props = new RFile(fileUID, accountUID);
	}



	@Test
	public void testJournalCreation() throws FileAlreadyExistsException, ContentsNotFoundException, FileNotFoundException, ConnectException {
		List<RJournal> journals = rRepo.getAllChangesFor(0, accountUID,  new UUID[]{fileUID});
		assert journals.isEmpty();

		//Test journals are created for creating a file
		props = rRepo.createFile(props);
		journals = rRepo.getAllChangesFor(0, accountUID, new UUID[]{fileUID});
		assert journals.size() == 1;

		//Test journals are NOT created for re-creating a file
		Assertions.assertThrows(FileAlreadyExistsException.class, () -> rRepo.createFile(props));
		journals = rRepo.getAllChangesFor(0, accountUID, new UUID[]{fileUID});
		assert journals.size() == 1;



		//Test journals are created for setting checksum information
		props.checksum = smallChecksum;
		props.filesize = (int) smallFile.toFile().length();
		rRepo.putContentProps(props, emptyChecksum);
		journals = rRepo.getAllChangesFor(0, accountUID, new UUID[]{fileUID});
		assert journals.size() == 2;

		//Test journals are created for setting checksum information more than once
		props.checksum = emptyChecksum;
		props.filesize = (int) emptyFile.toFile().length();
		rRepo.putContentProps(props, smallChecksum);
		journals = rRepo.getAllChangesFor(0, accountUID,  new UUID[]{fileUID});
		assert journals.size() == 3;



		//Test journals are created for setting attribute information
		props.userattr = new Gson().fromJson("{\"testkey\":\"testvalue\"}", JsonObject.class);;
		RFile changedAttr = rRepo.putAttributeProps(props, props.attrhash);
		journals = rRepo.getAllChangesFor(0, accountUID,  new UUID[]{fileUID});
		assert journals.size() == 4;

		//Test journals are created for setting attribute information more than once
		props.userattr = new Gson().fromJson("{}", JsonObject.class);;
		rRepo.putAttributeProps(props, changedAttr.attrhash);
		journals = rRepo.getAllChangesFor(0, accountUID,  new UUID[]{fileUID});
		assert journals.size() == 5;



		//Test journals are created for setting timestamp information
		props.accesstime = Instant.now().getEpochSecond();
		props.modifytime = Instant.now().getEpochSecond();
		rRepo.putTimestamps(props);
		journals = rRepo.getAllChangesFor(0, accountUID,  new UUID[]{fileUID});
		assert journals.size() == 6;

		//Test journals are created for setting attribute information more than once
		props.accesstime = Instant.now().getEpochSecond();
		rRepo.putTimestamps(props);
		journals = rRepo.getAllChangesFor(0, accountUID,  new UUID[]{fileUID});
		assert journals.size() == 7;
	}



	@Test
	public void testGetLatestChanges() throws FileAlreadyExistsException, ContentsNotFoundException, FileNotFoundException, ConnectException {
		//Create a file
		props = rRepo.createFile(props);

		//Update the contents
		props.checksum = smallChecksum;
		props.filesize = (int) smallFile.toFile().length();
		rRepo.putContentProps(props, emptyChecksum);

		//Update the attributes
		props.userattr = new Gson().fromJson("{\"testkey\":\"testvalue\"}", JsonObject.class);;
		rRepo.putAttributeProps(props, props.attrhash);



		//Grab the latest journal for this fileUID
		List<RJournal> journals = rRepo.getLatestChangesFor(0, accountUID,  new UUID[]{fileUID});
		assert journals.size() == 1;

		//Make sure it has info about the attribute change
		assert journals.get(0).changes.get("attrhash") != null;



		//Do the same with a second file
		RFile second = new RFile(UUID.randomUUID(), accountUID);
		second = rRepo.createFile(second);

		//Update the contents
		second.checksum = smallChecksum;
		second.filesize = (int) smallFile.toFile().length();
		rRepo.putContentProps(second, emptyChecksum);

		//Update the attributes
		second.userattr = new Gson().fromJson("{\"testkey\":\"testvalue\"}", JsonObject.class);;
		rRepo.putAttributeProps(second, second.attrhash);



		//Grab the latest journals for the two fileUIDs
		journals = rRepo.getLatestChangesFor(0, accountUID, new UUID[]{fileUID, second.fileuid});
		assert journals.size() == 2;

		//Make sure both have info about the attribute change
		assert journals.get(0).changes.get("attrhash") != null;
		assert journals.get(1).changes.get("attrhash") != null;



		//Make sure grabbing journals for fileUIDs that don't exist doesn't give extra data
		journals = rRepo.getLatestChangesFor(0, accountUID,  new UUID[]{fileUID, second.fileuid, UUID.randomUUID()});
		assert journals.size() == 2;

		journals = rRepo.getLatestChangesFor(0, accountUID,  new UUID[]{UUID.randomUUID()});
		assert journals.isEmpty();
	}



	@Test
	public void testGetAllChanges() throws FileAlreadyExistsException, ContentsNotFoundException, FileNotFoundException, ConnectException {
		//Create a file
		props = rRepo.createFile(props);

		//Update the contents
		props.checksum = smallChecksum;
		props.filesize = (int) smallFile.toFile().length();
		rRepo.putContentProps(props, emptyChecksum);

		//Update the attributes
		props.userattr = new Gson().fromJson("{\"testkey\":\"testvalue\"}", JsonObject.class);;
		rRepo.putAttributeProps(props, props.attrhash);



		//Grab all journals for this fileUID
		List<RJournal> journals = rRepo.getAllChangesFor(0, accountUID,  new UUID[]{fileUID});
		assert journals.size() == 3;

		//Make sure each journal references the correct change
		assert journals.get(0).changes.get("createtime") != null;
		assert journals.get(1).changes.get("checksum") != null;
		assert journals.get(2).changes.get("attrhash") != null;



		//Do the same with a second file
		RFile second = new RFile(UUID.randomUUID(), accountUID);
		second = rRepo.createFile(second);

		//Update the contents
		second.checksum = smallChecksum;
		second.filesize = (int) smallFile.toFile().length();
		rRepo.putContentProps(second, emptyChecksum);

		//Update the attributes
		second.userattr = new Gson().fromJson("{\"testkey\":\"testvalue\"}", JsonObject.class);;
		rRepo.putAttributeProps(second, second.attrhash);



		//Grab the latest journals for the two fileUIDs
		journals = rRepo.getAllChangesFor(0, accountUID, new UUID[]{fileUID, second.fileuid});
		assert journals.size() == 6;

		//Make sure both have info about the attribute change
		assert journals.get(0).changes.get("createtime") != null;
		assert journals.get(1).changes.get("checksum") != null;
		assert journals.get(2).changes.get("attrhash") != null;

		assert journals.get(3).changes.get("createtime") != null;
		assert journals.get(4).changes.get("checksum") != null;
		assert journals.get(5).changes.get("attrhash") != null;



		//Make sure grabbing journals for fileUIDs that don't exist doesn't give extra data
		journals = rRepo.getAllChangesFor(0, accountUID, new UUID[]{fileUID, second.fileuid, UUID.randomUUID()});
		assert journals.size() == 6;

		journals = rRepo.getAllChangesFor(0, accountUID, new UUID[]{UUID.randomUUID()});
		assert journals.isEmpty();
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