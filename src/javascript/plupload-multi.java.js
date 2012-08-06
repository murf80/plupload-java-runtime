/*global plupload:false, escape:false, alert:false */
/*jslint evil: true */

(function($, plupload){

  var uploadAppletInstances = {};
  var appletLoaded = false;
  var multi_upload_instance_files_cache = {};

  plupload.applet = {

    pluploadjavatrigger : function(eventname, id, fileobjstring, multi_upload_instance_id) {
      // FF / Safari mac breaks down if it's not detached here
      // can't do java -> js -> java
      setTimeout(function() {
      	appletLoaded = true;
          var uploader = uploadAppletInstances[id], i, args;
          var file = fileobjstring ? eval('(' + fileobjstring + ')') : "";
          if (uploader) {
            uploader.trigger('applet:' + eventname, file, multi_upload_instance_id);
          }
      }, 0);
    }
  };

  plupload.runtimes.Applet = plupload.addRuntime("java", {

    /**
     * Returns supported features for the Java runtime.
     *
     * @return {Object} Name/value object with supported features.
     */

    getFeatures : function() {

      return {
        java: applet.hasVersion('1.5'),
        chunks: true,
        progress: true
      };
    },

    init : function(uploader, callback) {

      var pluploadApplet,
          appletContainer,
          appletVars,
          initialized,
          waitCount = 0,
          container = document.body,
          features = this.getFeatures(),
          url = uploader.settings.java_applet_url,
          log_level;
          
      // error for upload canceled.  Here so we don't have to modify plupload.js
      UPLOAD_CANCELED = -913;
          
      var log_level = 5;
      if (uploader.settings.log_level !== undefined)
      {
        log_level = uploader.settings.log_level;
      }

      if(!features.java){
        callback({success : false});
        return;
      }

      function getApplet() {
        if(!pluploadApplet){
          pluploadApplet = document.getElementById(uploader.id);
        }
        return pluploadApplet;
      }

      function waitForAppletToLoadIn5SecsErrorOtherwise() {
        // Wait for applet init in 5 secs.
        if (waitCount++ > 5000) {
          callback({success : false});
          return;
        }
        if (!initialized) {
          setTimeout(waitForAppletToLoadIn5SecsErrorOtherwise, 1);
        }
      }

      uploadAppletInstances[uploader.id] = uploader;
      
      if (!appletLoaded) {
	      appletContainer = document.createElement('div');
	      appletContainer.id = uploader.id + '_applet_container';
	      appletContainer.className = 'plupload applet';
	
	      plupload.extend(appletContainer.style, {
	        // move the 1x1 pixel out of the way.
	        position : 'absolute',
	        left: '-9999px',
	        zIndex : -1
	      });
      }
      
      uploader.bind("Applet:AddedUploader", function(up, file, multi_upload_instance_id) {
        var filters = up.settings.filters;
        var extensions = [];
        var description = "";

        if(filters.length > 0){
          // On the Java side we can only set one filter
          // pick the first description and add all extensions
          description = filters[0].title;
          for(var i = 0, len = filters.length; i < len; i++){
              var filter = filters[i];
              var filterExtensions = filter.extensions.split(',');
              for(var j = 0; j < filterExtensions.length; j++){
                  extensions.push(filterExtensions[j]);
              }
          }
          getApplet().setFileFilter(multi_upload_instance_id, description, extensions);
          
          var multi_selection = true;
	      if (up.settings.multi_selection !== undefined)
	      {
	        multi_selection = up.settings.multi_selection;
	      }
      
          getApplet().setMultiSelection(multi_upload_instance_id, multi_selection);
          
          up.trigger('UploaderAdded', multi_upload_instance_id);
        }
      });
      
      uploader.bind("Applet:RemovedUploader", function(up, file, multi_upload_instance_id) {
          delete multi_upload_instance_files_cache[multi_upload_instance_id];
          
          up.trigger('UploaderRemoved', multi_upload_instance_id);
      });

      uploader.bind("Applet:Init", function() {
        initialized = true;
        callback({success : true});
      });
      
      // allow users to override cache value
      var cache_version = "20062012";
      if (uploader.settings.cache_version !== undefined)
      {
        cache_version = uploader.settings.cache_version;
      }

      if (!appletLoaded) {
        document.body.appendChild(appletContainer);

        applet.inject(appletContainer, {
          archive: url,
          cache_archive: url,
          cache_version: cache_version,
          id: escape(uploader.id),
          code: 'plupload.PluploadMulti',
          callback: 'plupload.applet.pluploadjavatrigger',
          log_level: log_level
        });
      }
      
      uploader.bind("AddUploader", function(up, multi_upload_instance_id, uploader_url){
        getApplet().addUploader(uploader_url, multi_upload_instance_id);
      });
      
      uploader.bind("CancelUpload", function(up, multi_upload_instance_id, file_id){
        getApplet().cancelUpload(multi_upload_instance_id, file_id);
      });
      
      uploader.bind("RemoveUploader", function(up, multi_upload_instance_id){
        getApplet().removeUploader(multi_upload_instance_id);
      });
      
      uploader.bind("UploadFiles", function(up, multi_upload_instance_id) {
          var files = multi_upload_instance_files_cache[multi_upload_instance_id];
          
          for (i = files.length - 1; i >= 0; i--) {
            up.trigger('UploadFile', files[i], multi_upload_instance_id);
          }
      });

      uploader.bind("UploadFile", function(up, file, multi_upload_instance_id) {
        getApplet().uploadFile(multi_upload_instance_id, file.id, document.cookie, up.settings.chunk_size || 0, up.settings.retries || 3);
      });

      uploader.bind("SelectFiles", function(up, multi_upload_instance_id){
        getApplet().openFileDialog(multi_upload_instance_id);
      });

      uploader.bind("Applet:UploadProcess", function(up, javaFile, multi_upload_instance_id) {
        //var file = up.getFile(javaFile.id),
        var file = new plupload.File(javaFile.id, javaFile.name, javaFile.size)
        var finished = javaFile.chunk === javaFile.chunks;

        if (file.status != plupload.FAILED) {
          file.loaded = javaFile.loaded;
          file.size = javaFile.size;
          file.percent = file.size > 0 ? Math.ceil(file.loaded / file.size * 100) : 100;
          up.trigger('MultiUploadProgress', file, multi_upload_instance_id);
        }
        else{
          alert("uploadProcess status failed");
        }

        if (finished) {
          file.status = plupload.DONE;
          up.trigger('FileUploaded', file, {
            response : "File uploaded"},
            multi_upload_instance_id
          );
        }
      });
      
      uploader.bind("FileUploaded", function(up, file, response, multi_upload_instance_id){
        getApplet().removeUploader(multi_upload_instance_id);
      });

      uploader.bind("Applet:SelectFiles", function(up, file, multi_upload_instance_id) {
        var files = multi_upload_instance_files_cache[multi_upload_instance_id];
        
        if (!files)
        	files = [];
        
        files.push(new plupload.File(file.id, file.name, file.size));

        // Trigger FilesAdded event if we added any
        if (files.length) {
          multi_upload_instance_files_cache[multi_upload_instance_id] = files;
          uploader.trigger("FilesAdded", files, multi_upload_instance_id);
        }
      });

      uploader.bind("Applet:GenericError", function(up, err, multi_upload_instance_id) {
        uploader.trigger('Error', {
          code : plupload.GENERIC_ERROR,
          message : 'Generic error.',
          details : err.message,
          file : uploader.getFile(err.id)},
          multi_upload_instance_id
        );
      });

      uploader.bind("Applet:IOError", function(up, err, multi_upload_instance_id) {
        uploader.trigger('Error', {
          code : plupload.IO_ERROR,
          message : 'IO error.',
          details : err.message,
          file : uploader.getFile(err.id)},
          multi_upload_instance_id
        );
      });
      
      uploader.bind("Applet:UploadCanceledError", function(up, err, multi_upload_instance_id) {
        uploader.trigger('Error', {
          code : UPLOAD_CANCELED,
          message : err.message,
          details : err.message,
          file : uploader.getFile(err.id)},
          multi_upload_instance_id
        );
      });

      uploader.bind("FilesRemoved", function(up, files) {
        for (var i = 0, len = files.length; i < len; i++) {
          getApplet().removeFile(up.id, files[i].id);
        }
      });

      waitForAppletToLoadIn5SecsErrorOtherwise();

    }// end object arg
  });// end add runtime
})(window, plupload);
