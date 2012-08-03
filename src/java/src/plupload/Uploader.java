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
import javax.swing.filechooser.FileNameExtensionFilter;

import netscape.javascript.JSObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import plupload.PluploadMulti.Event;

/**
 * An uploader instance.  Used so you can have multiple uploaders running from
 * a single applet.
 *
 * This class is optimized by proguard. Unused members are striped by
 * proguard.
 *
 * Therefore members to be used from JS should be specified in build.xml
 */
public class Uploader {
	
	private static Log log = LogFactory.getLog(Uploader.class);
	
	private PluploadMulti parentInstance;
	public PluploadFileMulti current_file;
	public JFileChooser dialog;
	public boolean dialog_open = false;
	public String url;

	public String uploader_id;
	public Map<String, PluploadFileMulti> files;
	
	public Uploader(PluploadMulti parentInstance, String url) {
		this(parentInstance, url, null);
	}
	
	@SuppressWarnings("unchecked")
	public Uploader(PluploadMulti parentInstance, String url, String id) {
		super();
		
		if (id != null)
			this.uploader_id = id;
		else
			this.uploader_id = UUID.randomUUID().toString();
		
		this.url = url;
		this.parentInstance = parentInstance;
		
		files = new HashMap<String, PluploadFileMulti>();

//		try {
//			dialog = new JFileChooser();
//			dialog.setMultiSelectionEnabled(true);
//		} catch (AccessControlException e) {
//			JSObject
//					.getWindow(parentInstance)
//					.eval(
//							"alert('Please approve the digital signature of the applet. Close the browser and start over')");
//		}
		
		try {
			AccessController.doPrivileged(new PrivilegedExceptionAction() {
				public Object run() throws IOException, Exception {
					dialog = new JFileChooser();
					dialog.setMultiSelectionEnabled(true);
					return null;
				}
			});
		} catch (PrivilegedActionException e) {
			Exception ex = e.getException();
			if (ex instanceof IOException) {
				publishIOError(ex);
			} else if (ex instanceof Exception) {
				publishError(ex);
			}
		}
		
		info("Created new uploader");
	}
	
	private void debug(String msg)
	{
		log.debug("[Uploader] id [" + uploader_id + "]: " + msg);
	}
	
	private void info(String msg)
	{
		log.info("[Uploader] id [" + uploader_id + "]: " + msg);
	}
	
	private void warn(String msg)
	{
		log.warn("[Uploader] id [" + uploader_id + "]: " + msg);
	}
	
	private void error(String msg)
	{
		log.error("[Uploader] id [" + uploader_id + "]: " + msg);
	}
	
	@SuppressWarnings("unchecked")
	public void setFileFilter(final String description, final String[] filters) {
		info("Adding file filter: " + description + " " + Arrays.toString(filters));
		try {
			AccessController.doPrivileged(new PrivilegedExceptionAction() {
				public Object run() throws IOException, Exception {
					FileNameExtensionFilter filter = new FileNameExtensionFilter(description, filters);
					dialog.setFileFilter(filter);
					return null;
				}
			});
		} catch (PrivilegedActionException e) {
			Exception ex = e.getException();
			if (ex instanceof IOException) {
				publishIOError(ex);
			} else if (ex instanceof Exception) {
				publishError(ex);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void setMultiSelection(final Boolean enabled) {
		info("Setting multiselection enabled to " + enabled);
		try {
			AccessController.doPrivileged(new PrivilegedExceptionAction() {
				public Object run() throws IOException, Exception {
					dialog.setMultiSelectionEnabled(enabled);
;					return null;
				}
			});
		} catch (PrivilegedActionException e) {
			Exception ex = e.getException();
			if (ex instanceof IOException) {
				publishIOError(ex);
			} else if (ex instanceof Exception) {
				publishError(ex);
			}
		}
	}

	// LiveConnect calls from JS
	@SuppressWarnings("unchecked")
	public void uploadFile(final String file_id, final String cookie, final int chunk_size, 
			final int retries) {
		final PluploadFileMulti file = files.get(file_id);
		info("Uploading file: file id: " + file.id + " url: " + url + " cookie: " + cookie + " chunk_size: " + chunk_size + " retries: " + retries);
		if (file != null) {
			current_file = file;
		}

		try {
			// Because of LiveConnect our security privileges are degraded
			// elevate them again.
			AccessController.doPrivileged(new PrivilegedExceptionAction() {
				public Object run() throws IOException, Exception {
					file.upload(url, chunk_size, retries, cookie);
					return null;
				}
			});
		} catch (PrivilegedActionException e) {
			Exception ex = e.getException();
			if (ex instanceof IOException) {
				publishIOError(ex);
			} else {
				publishError(ex);
			}
		}
	}

	public void removeFile(String id) {
		info("Removing file: file id: " + id);
		files.remove(id);
	}

	public void clearFiles() {
		info("Clearing all files");
		files.clear();
	}

	@SuppressWarnings("unchecked")
	public void openFileDialog() {
		info("Opening file dialog");
		if (dialog_open) {
			// FIXME: bring openDialog to front
			return;
		}
		dialog_open = true;
		AccessController.doPrivileged(new PrivilegedAction() {
			public Object run() {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						int file_chose_return_value = dialog
								.showOpenDialog(parentInstance);
						log.debug("openDialog finished");
						// blocks until file selected
						if (file_chose_return_value == JFileChooser.APPROVE_OPTION) {
							if (dialog.isMultiSelectionEnabled()) {
								for (File f : dialog.getSelectedFiles()) {
									// Wiredness: If PluploadFileMulti extends Thread
									// it just stopped here in my production
									// environment
									processSelectedFile(f);
									}
								}
								else {
									processSelectedFile(dialog.getSelectedFile());
								}
						}
						dialog_open = false;
					}
				});
				return null;
			}
		});
	}
	
	private synchronized void processSelectedFile(File f) {
		// get a unique id for the file.  Needs to be
		// unique across mutliple uploaders.
		String uuid = UUID.randomUUID().toString();
		uuid = uploader_id + "-" + uuid;
		
		PluploadFileMulti file = new PluploadFileMulti(uuid, f);
		selectEvent(file);
	}

	private void publishIOError(Exception e) {
		info("Publishing IO error: " + e.getMessage());
		parentInstance.publishEvent(uploader_id, Event.IO_ERROR, new PluploadError(e.getMessage(), current_file.id));
	}

	private void publishError(Exception e) {
		info("Publishing error: " + e.getMessage());
		parentInstance.publishEvent(uploader_id, Event.GENERIC_ERROR, new PluploadError(e.getMessage(), current_file.id));
	}

	private void selectEvent(PluploadFileMulti file) {
		info("Processing select event for file " + file.getName() + "[" + file.id + "]");
		// handles file add from file chooser
		files.put(file.id + "", file);

		file.addFileUploadListener(new FileUploadListenerMulti() {

			@Override
			public void uploadProcess(PluploadFileMulti file) {
				parentInstance.publishEvent(uploader_id, Event.UPLOAD_PROCESS, file);
			}

			@Override
			public void ioError(IOException e) {
				publishIOError(e);

			}

			@Override
			public void genericError(Exception e) {
				publishError(e);
			}
		});

		parentInstance.publishEvent(uploader_id, Event.SELECT_FILE, file.toString());
	}
}
