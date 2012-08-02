package plupload;

import java.io.File;
import java.io.IOException;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import netscape.javascript.JSObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.SystemLog;

/**
 * Plupload Java runtime for use when one applet will support multiple
 * uploaders.
 *
 * This class is optimized by proguard. Unused members are striped by
 * proguard.
 *
 * Therefore members to be used from JS should be specified in build.xml
 */
public class PluploadMulti extends Applet2 {
	
	private static Log log = LogFactory.getLog(PluploadMulti.class);
	
	private Map<String, Uploader> uploaders;
	
	private String plupload_id;
	
	@Override
	public void init() {
		super.init();
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			e.printStackTrace();
		}

		SystemLog.currentLogLevel = Integer.parseInt(getParameter("log_level", /*LOG_LEVEL_ERROR*/"5"));
		
		uploaders = new HashMap<String, Uploader>();
		plupload_id = getParameter("id");
		log.debug("init[" + plupload_id + "]");
		publishEvent(plupload_id, Event.INIT);
	}
	
	private Uploader getUploader(String id) {
		Uploader u = null;
		
		if (uploaders != null && !uploaders.isEmpty())
		{
			u = uploaders.get(id);
			log.debug("getUploader: found uploader for id " + id);
		}
		else
		{
			log.debug("getUploader: no uploader for id " + id);
		}
		
		return u;
	}
	
	@SuppressWarnings("unchecked")
	public void addUploader() {
		Uploader u = new Uploader(this);
		log.debug("Adding uploader " + u.uploader_id + " to collection of uploaders");
		uploaders.put(u.uploader_id, u);
		publishEvent(plupload_id, Event.ADDED_UPLOADER, "", u.uploader_id);
	}

	@SuppressWarnings("unchecked")
	public void setFileFilter(String uploader_id, final String description, final String[] filters) {
		log.debug("setFileFilter[" + uploader_id + "]: " + description + " " + Arrays.toString(filters));
		final Uploader u = getUploader(uploader_id);
		u.setFileFilter(description, filters);
	}

	// LiveConnect calls from JS
	@SuppressWarnings("unchecked")
	public void uploadFile(String uploader_id, final String id, final String url,
			final String cookie, final int chunk_size, final int retries) {
		log.debug("uploadFile[" + uploader_id + "]: file id: " + id + " url: " + url + " cookie: " + cookie + " chunk_size: " + chunk_size + " retries: " + retries);
		final Uploader u = getUploader(uploader_id);
		u.uploadFile(id, url, cookie, chunk_size, retries);
	}

	public void removeFile(String uploader_id, String id) {
		log.debug("removeFile[" + uploader_id + "]");
		final Uploader u = getUploader(uploader_id);
		u.removeFile(id);
	}

	public void clearFiles(String uploader_id) {
		log.debug("clearFiles[" + uploader_id + "]");
		final Uploader u = getUploader(uploader_id);
		u.clearFiles();
	}

	@SuppressWarnings("unchecked")
	public void openFileDialog(String uploader_id) {
		log.debug("openFileDialog[" + uploader_id + "]");
		final Uploader u = getUploader(uploader_id);
		u.openFileDialog();
	}
	
	public void publishEvent(String uploader_id, Event e, Object ... args) {
		// is this really the way to do this?
		// prepend args with upload id.
		log.debug("publishEvent[" + uploader_id + "]: " + e.getName());
		Object[] new_args = new Object[args.length + 2];
		new_args[0] = plupload_id;
		System.arraycopy(args, 0, new_args, 1, args.length);
		new_args[new_args.length -1] = uploader_id;
		// calls to superclass
		publishEvent(e.getName(), new_args);
	}
	
	// Events
	public enum Event {
		CLICK("Click"), 
		INIT("Init"),
		ADDED_UPLOADER("AddedUploader"),
		SELECT_FILE("SelectFiles"), 
		UPLOAD_PROCESS("UploadProcess"), 
		UPLOAD_CHUNK_COMPLETE("UploadChunkComplete"), 
		SKIP_UPLOAD_CHUNK_COMPLETE("SkipUploadChunkComplete"), 
		IO_ERROR("IOError"), 
		GENERIC_ERROR("GenericError");

		private String name;

		Event(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}
}
