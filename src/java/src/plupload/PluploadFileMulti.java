package plupload;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.utils.URIUtils;

public class PluploadFileMulti /*extends Thread*/{

	private static Log log = LogFactory.getLog(PluploadFileMulti.class);
	public static int DEFAULT_CHUNK_SIZE = 1048576; // 1MB
	
	public String id;
	
	private int chunk;			// current chunk number
	private int chunk_size;
	private long chunks;		// chunks uploaded at client
	private int chunk_server; 	// chunks uploaded at server
	private long loaded;		// bytes uploaded
	
	private String name;
	private long size;	
	
	private boolean overwrite = false;
	private File file;
	private byte[] buffer;
	private URI uri;
	private String md5hex_server_total;
	private String md5hex_total;
	private MessageDigest md5_total;
	private String md5hex_chunk;
	private List<FileUploadListenerMulti> file_upload_listeners = new ArrayList<FileUploadListenerMulti>();
	private InputStream stream;
	private HttpUploader uploader;
	private boolean cancel_upload;

	public PluploadFileMulti(String id, File file) {
		this.id = id;
		this.name = file.getName().replace("\"", "\\\"").replace("'", "\\'");
		this.size = file.length();
		this.file = file;
		
		try {
			md5_total = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("WTF, no MD5?");
		}
	}
	
	private void debug(String msg)
	{
		log.debug("[PluploadFileMulti] file id [" + id + "]: " + msg);
	}
	
	private void info(String msg)
	{
		log.info("[PluploadFileMulti] file id [" + id + "]: " + msg);
	}
	
	private void warn(String msg)
	{
		log.warn("[PluploadFileMulti] file id [" + id + "]: " + msg);
	}
	
	private void error(String msg)
	{
		log.error("[PluploadFileMulti] file id [" + id + "]: " + msg);
	}

	protected void prepare(String upload_uri, int chunk_size, int retries, String cookie)
			throws URISyntaxException, IOException {
		info("Preparing to fetch info from " + upload_uri);
		if(size == 0){
			throw new IOException("Dude, file is empty!");
		}
		
		uri = new URI(upload_uri);
		uploader = new HttpUploader(retries, cookie);
		
		if(chunk_size > 0){
			this.chunk_size = chunk_size;
		}
		else{
			this.chunk_size = DEFAULT_CHUNK_SIZE; 
		}
		
		stream = new BufferedInputStream(new FileInputStream(file), chunk_size);
		chunks = (size + chunk_size - 1) / chunk_size;
		buffer = new byte[chunk_size];
		chunk = 0;
		loaded = 0;
	}

	private void doUpload() throws ClientProtocolException, UnsupportedEncodingException, IOException, ParseException, URISyntaxException, NoSuchAlgorithmException, UploadCanceledException {	
		if(overwrite){
			uploadChunks();			
		}
		else {
			Map<String, String> result = uploader.probe(getProbeUri());	
			String server_status = result.get("status");
			info("Server status is " + server_status);
			
			if (server_status.equals("uploading")) {
				onFileUploading(result); 
			} else if (server_status.equals("unknown")) {
				uploadChunks();
			} else if (server_status.equals("finished")) {
				throw new IOException("A file with that name is already uploaded to server!");
			} else {
				System.err.println("WTF?");
			}
		}
	}
	
	private void onFileUploading(Map<String, String> result) throws IOException, NoSuchAlgorithmException, ParseException, URISyntaxException, UploadCanceledException{
		info("Server status is " + result);
		chunk_server = Integer.parseInt(result.get("chunk"));
		int chunks_server = Integer.parseInt(result.get("chunks"));
		if (chunks_server == chunks) { 
			md5hex_server_total = result.get("md5");
			skipUploadedChunks();
			if(!checkIntegrity()){
				info("Integrity check of partial upload failed, overwriting file");
				overwrite = true;
				doUpload();
				return;
			}
			else
			{
				info("Integrity check of partial upload succeeded, resuming upload");
			}
			uploadChunks();
		}
		else {
			info("File modified starting over");
			// file is modified for sure, so don't run through all the chunks
			uploadChunks();
		}
	}
	
	public void upload(String upload_uri, int chunk_size, int retries, String cookie)
			throws IOException, NoSuchAlgorithmException, URISyntaxException, ParseException {
		info("Uploading file " + id + " " + upload_uri + " " + chunk_size + " " + retries + " " + cookie);
		prepare(upload_uri, chunk_size, retries, cookie);
		
		Thread uploadThread = new Thread(){
			
			@Override
			public void run(){
				try{
					doUpload();
				}
				catch(UploadCanceledException e){
					uploadCanceledErrorAction(e);
				}
				catch(IOException e){
					e.printStackTrace();
					ioErrorAction(e);
				}
				catch(Exception e){
					e.printStackTrace();
					genericErrorAction(e);
				}
			}
		};
		uploadThread.start();
	}
	
	public void cancelUpload() {
		if (uploader != null)
		{
			info("Canceling upload for file " + name + " (" + id + ")");
			cancel_upload = true;
		}
	}

	public void skipUploadedChunks() throws IOException {
		info("Skipping already uploaded chunks");
		while(chunk <= chunk_server){
			int bytes_read = stream.read(buffer);
			// the finished check and integrity check is done in JS
			
			md5_total.update(buffer, 0, bytes_read);
			
			loaded += bytes_read;
			chunk++;
			
			uploadProcessAction();
		}
	}

	public boolean checkIntegrity() {
		info("Checking integrity");
		return HashUtil.hexdigest(md5_total, true).equals(md5hex_server_total);
	}

	public void uploadChunks() throws NoSuchAlgorithmException,
			ClientProtocolException, URISyntaxException, IOException,
			UploadCanceledException {
		while(chunk != chunks){
			
			if (cancel_upload) {
				throw new UploadCanceledException("User canceled upload");
			}
			
			int bytes_read = stream.read(buffer);
			MessageDigest md5_chunk = MessageDigest.getInstance("MD5");

			md5_chunk.update(buffer, 0, bytes_read);
			md5_total.update(buffer, 0, bytes_read);

			md5hex_total = HashUtil.hexdigest(md5_total, true);
			md5hex_chunk = HashUtil.hexdigest(md5_chunk);

			info("Uploading chunk " + chunk + " of " + chunks);
			uploader.sendChunk(buffer, bytes_read, chunk, chunks, name,
					getUploadUri());

			loaded += bytes_read;
			chunk++;

			uploadProcessAction();
		}
	}

	public URI getUploadUri() throws URISyntaxException {
		String params = HttpUploader.getQueryParams(chunk, chunks, chunk_size,
				md5hex_total, md5hex_chunk, name);
		String query = uri.getQuery() != null ? uri.getQuery() + "&" + params
				: params;
		URI upload_uri = URIUtils.createURI(uri.getScheme(), uri.getHost(), uri
				.getPort(), uri.getPath(), query, null);
		return upload_uri;
	}

	public URI getProbeUri() throws URISyntaxException,
			UnsupportedEncodingException {
		String params = "name=" + URLEncoder.encode(name, "UTF-8");
		String query = uri.getQuery() != null ? uri.getQuery() + "&" + params
				: params;
		return URIUtils.createURI(uri.getScheme(), uri.getHost(),
				uri.getPort(), uri.getPath(), query, null);
	}

	public void addFileUploadListener(FileUploadListenerMulti listener) {
		file_upload_listeners.add(listener);
	}

	private void uploadProcessAction() {
		for (FileUploadListenerMulti f : file_upload_listeners) {
			f.uploadProcess(this);
		}
	}
	
	private void uploadCanceledErrorAction(UploadCanceledException e){
		for(FileUploadListenerMulti f : file_upload_listeners){
			f.uploadCanceled(e);
		}
	}
	
	private void ioErrorAction(IOException e){
		for(FileUploadListenerMulti f : file_upload_listeners){
			f.ioError(e);
		}
	}
	
	private void genericErrorAction(Exception e) {
		for(FileUploadListenerMulti f : file_upload_listeners){
			f.genericError(e);
		}
	}
	
	public String toString(){
		return "{\"chunk\":" + chunk + 
		",\"chunks\":" + chunks + 
		",\"chunkServer\":" + chunk_server +
		",\"name\":\"" + name + "\"" +
		",\"loaded\":" + loaded + 
		",\"size\":" + size + 
		",\"id\":\"" + id + "\"" +
		"}";
	}
	
	public String getName()
	{
		return this.name;
	}

}
