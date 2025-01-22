package aaa.sgordon.hybridrepo.hybrid.jobs.zoning;

import java.util.UUID;

import aaa.sgordon.hybridrepo.hybrid.database.HZone;
import aaa.sgordon.hybridrepo.hybrid.database.HZoningDAO;
import aaa.sgordon.hybridrepo.hybrid.database.HybridHelpDatabase;

public class Zoning {
	private static final String TAG = "Hyb.Zone";
	HZoningDAO dao;

	public static Zoning getInstance() {
		return Zoning.SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final Zoning INSTANCE = new Zoning();
	}
	private Zoning() {
		dao = HybridHelpDatabase.getInstance().getZoningDao();
	}



	public HZone get(UUID fileUID) {
		return dao.get(fileUID);
	}

	public void put(HZone zone) {
		dao.put(zone);
	}

	public void delete(UUID fileUID) {
		dao.delete(fileUID);
	}


	//---------------------------------------------------------------------------------------------

}
