import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
 
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

class Watchit {

public static void main(String args[]) {
	WatchFile();
}

	public static void WatchFile() {
		try {
			WatchService watcher = FileSystems.getDefault().newWatchService();
			
			Path dir = Paths.get(".");
			dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			
			while (true) {
			WatchKey key;
				try {
					// wait for a key to be available
					key = watcher.take();
				} catch (InterruptedException ex) {
					return;
				}
			 
				for (WatchEvent<?> event : key.pollEvents()) {
					// get event type
					WatchEvent.Kind<?> kind = event.kind();
			 
					// get file name
					@SuppressWarnings("unchecked")
					WatchEvent<Path> ev = (WatchEvent<Path>) event;
					Path fileName = ev.context();
			 
					if (kind == OVERFLOW) {
						System.err.println("Overflow");
						continue;
					} else if (kind == ENTRY_CREATE) {
			 
						// process create event
						System.err.println("Create " + fileName);
			 
					} else if (kind == ENTRY_DELETE) {
			 
						// process delete event
						System.err.println("Delete " + fileName);
			 
					} else if (kind == ENTRY_MODIFY) {
			 
						// process modify event
						System.err.println("Modify " + fileName);

					}
				}
			 
				// IMPORTANT: The key must be reset after processed
				boolean valid = key.reset();
				if (!valid) {
					break;
				}
			}
		} catch (IOException ex) {
            System.err.println(ex);
        }
	}
}