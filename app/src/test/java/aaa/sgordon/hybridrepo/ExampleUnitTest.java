package aaa.sgordon.hybridrepo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.UUID;

import aaa.sgordon.hybridrepo.hybrid.ContentsNotFoundException;
import aaa.sgordon.hybridrepo.hybrid.HybridAPI;
import aaa.sgordon.hybridrepo.remote.RemoteRepo;
import aaa.sgordon.hybridrepo.remote.types.RFile;

public class ExampleUnitTest {
	@Test
	public void addition_isCorrect() {
		assertEquals(4, 2 + 2);
	}
}