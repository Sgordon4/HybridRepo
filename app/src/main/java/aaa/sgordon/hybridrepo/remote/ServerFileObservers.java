package aaa.sgordon.hybridrepo.remote;

import android.util.Log;

import aaa.sgordon.hybridrepo.MyApplication;
import aaa.sgordon.hybridrepo.remote.types.RJournal;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class ServerFileObservers {

	private final List<SFileObservable> listeners;
	private Thread longpollThread;

	private static final String TAG = "Gal.SRepo.Obs";


	public ServerFileObservers() {
		listeners = new ArrayList<>();
	}


	public void addObserver(SFileObservable observer) {
		listeners.add(observer);
	}
	public void removeObserver(SFileObservable observer) {
		listeners.remove(observer);
	}


	public void notifyObservers(int journalID, RJournal file) {
		for (SFileObservable listener : listeners) {
			listener.onFileUpdate(journalID, file);
		}
	}


	public void startListening(int journalID, UUID accountUID) {
		//If we're already listening, do nothing
		if(longpollThread != null && !longpollThread.isInterrupted() && longpollThread.isAlive())
			return;

		//Otherwise start perpetually longpolling the server for new journal entries
		Runnable runnable = () -> {
			int latestID = journalID;
			while (!Thread.currentThread().isInterrupted()) {
				if(!MyApplication.doesDeviceHaveInternet()) {
					Log.v(TAG, "No internet, longpoll sleeping...");

					try { Thread.sleep(15000); }
					catch (InterruptedException e) { throw new RuntimeException(e); }
					continue;
				}


				try {

					latestID = longpoll(latestID);

				}
				catch (TimeoutException e) {
					//This is supposed to happen, restart the poll
				}
				catch (SocketTimeoutException e) {
					//Server is down, wait and restart the poll
					Log.w(TAG, "Server is down, cannot longpoll. Sleeping...");

					try { Thread.sleep(15000); }
					catch (InterruptedException ex) { throw new RuntimeException(ex); }
				}
				catch (IOException e) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException ex) {
						throw new RuntimeException(ex);
					}
					//throw new RuntimeException(e);
				}
			}
		};
		// Create and start the thread
		longpollThread = new Thread(runnable);
		longpollThread.start();
	}

	public void stopListening() {
		//If we're already listening, interrupt
		if(longpollThread != null && !longpollThread.isInterrupted() && longpollThread.isAlive())
			longpollThread.interrupt();
	}


	//Check the server for new journal entries. Returns the largest journal ID found.
	public int longpoll(int journalID) throws IOException, TimeoutException {
		//Try to get any new journal entries. The request is designed to hang until new data is made
		List<RJournal> entries = RemoteRepo.getInstance().longpollJournalEntriesAfter(journalID);

		//If we get any entries back, notify the observers
		for(RJournal entry : entries) {
			int objJournalID = entry.journalid;
			notifyObservers(objJournalID, entry);
		}


		//If we have any new data, return the largest journalID from the bunch (will always be last)
		if(!entries.isEmpty()) {
			return entries.get(entries.size() - 1).journalid;
		}

		//If no new data was found, don't update the latest journalID
		return journalID;
	}



	public interface SFileObservable {
		void onFileUpdate(int journalID, RJournal file);
	}
}
