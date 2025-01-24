package aaa.sgordon.hybridrepo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.UUID;

import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;
import aaa.sgordon.hybridrepo.remote.types.RFile;


public class ExampleInstrumentedTest {

	@Test
	public void testRemoteBasics() throws ContentsNotFoundException, ConnectException {
		// Context of the app under test.
		//Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();


		RemoteRepo rRepo = RemoteRepo.getInstance();

		RFile newFile = new RFile(UUID.randomUUID(), UUID.randomUUID());
		rRepo.createFile(newFile);
	}
}