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
		info("Initialized applet with id " + plupload_id);
		publishEvent(plupload_id, Event.INIT);
	}
	
	
	private void debug(String msg)
	{
		log.debug("[PluploadMulti] applet id [" + plupload_id + "]: " + msg);
	}
	
	private void info(String msg)
	{
		log.info("[PluploadMulti] applet id [" + plupload_id + "]: " + msg);
	}
	
	private void warn(String msg)
	{
		log.warn("[PluploadMulti] applet id [" + plupload_id + "]: " + msg);
	}
	
	private void error(String msg)
	{
		log.error("[PluploadMulti] applet id [" + plupload_id + "]: " + msg);
	}
	
	private Uploader getUploader(String id) {
		Uploader u = null;
		
		if (uploaders != null && !uploaders.isEmpty())
		{
			u = uploaders.get(id);
			info("Found uploader with id " + u.uploader_id + " and url " + u.url);
		}
		else
		{
			error("Could not find uploader with id " + id);
		}
		
		return u;
	}
	
	public void addUploader(String url, String uploader_id) {
		Uploader u = new Uploader(this, url, uploader_id);
		uploaders.put(u.uploader_id, u);
		info("Added uploader " + u.uploader_id + " for url " + u.url + ", there are now " + uploaders.size() + " uploaders");
		publishEvent(plupload_id, Event.ADDED_UPLOADER, "", u.uploader_id);
	}
	
	public void addUploader(String url) {
		addUploader(url, null);
	}
	
	public void removeUploader(String uploader_id) {
		final Uploader u = getUploader(uploader_id);
		if (u != null) {
			uploaders.remove(u.uploader_id);
			info("Removing uploader " + u.uploader_id + " for url " + u.url + ", there are now " + uploaders.size() + " uploaders");
			publishEvent(plupload_id, Event.REMOVED_UPLOADER, "", u.uploader_id);
		}
	}

	public void setFileFilter(String uploader_id, final String description, final String[] filters) {
		info("Adding file filter to uploader with id " + uploader_id + ": " + description + " " + Arrays.toString(filters));
		final Uploader u = getUploader(uploader_id);
		
		if (u != null)
			u.setFileFilter(description, filters);
	}
	
	public void setMultiSelection(String uploader_id, final Boolean enabled) {
		info("Setting multiselection enabled to " + enabled + " for uploader with id " + uploader_id);
		final Uploader u = getUploader(uploader_id);
		
		if (u != null)
			u.setMultiSelection(enabled);
	}

	// LiveConnect calls from JS
	@SuppressWarnings("unchecked")
	public void uploadFile(String uploader_id, final String file_id, final String cookie, 
			final int chunk_size, final int retries) {
		info("Uploading file for uploader with id " + uploader_id + ": file id: " + file_id + " cookie: " + cookie + " chunk_size: " + chunk_size + " retries: " + retries);
		final Uploader u = getUploader(uploader_id);
		
		if (u != null)
			u.uploadFile(file_id, cookie, chunk_size, retries);
	}

	public void removeFile(String uploader_id, String id) {
		info("Removing file from uploader with id " + uploader_id + ": file id: " + id);
		final Uploader u = getUploader(uploader_id);
		
		if (u != null)
			u.removeFile(id);
	}

	public void clearFiles(String uploader_id) {
		info("Clearing all files from uploader with id " + uploader_id);
		final Uploader u = getUploader(uploader_id);
		
		if (u != null)
			u.clearFiles();
	}

	public void openFileDialog(String uploader_id) {
		info("Opening file dialog for uploader with id " + uploader_id);
		final Uploader u = getUploader(uploader_id);
		
		if (u != null)
			u.openFileDialog();
	}
	
	public void publishEvent(String uploader_id, Event e, Object ... args) {
		// is this really the way to do this?
		// prepend args with upload id.
		info("Publishing event for uploader with id " + uploader_id + ": event name " + e.getName());
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
		REMOVED_UPLOADER("RemovedUploader"),
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
