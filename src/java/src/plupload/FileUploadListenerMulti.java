package plupload;

import java.io.IOException;

public interface FileUploadListenerMulti {
	
	public void uploadProcess(PluploadFileMulti file);

	public void ioError(IOException e);
	
	public void genericError(Exception e);
	
}
