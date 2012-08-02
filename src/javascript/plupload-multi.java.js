/*global plupload:false, escape:false, alert:false */
/*jslint evil: true */

(function($, plupload){

  var uploadAppletInstances = {};
  var appletLoaded = false;

  plupload.applet = {

    pluploadjavatrigger : function(eventname, id, fileobjstring, uploaderInstanceId) {
      // FF / Safari mac breaks down if it's not detached here
      // can't do java -> js -> java
      setTimeout(function() {
      	appletLoaded = true;
          var uploader = uploadAppletInstances[id], i, args;
          var file = fileobjstring ? eval('(' + fileobjstring + ')') : "";
          if (uploader) {
            uploader.trigger('applet:' + eventname, file, uploaderInstanceId);
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
      
      uploader.bind("Applet:AddedUploader", function(up, file, uploaderInstanceId) {
        var filters = uploader.settings.filters;
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
          getApplet().setFileFilter(uploaderInstanceId, description, extensions);
          
          up.trigger('UploaderAdded', uploaderInstanceId);
        }
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
      
      uploader.bind("AddUploader", function(up){
        getApplet().addUploader();
      });

      uploader.bind("UploadFile", function(up, file) {
          var settings = up.settings,
              abs_url = location.protocol + '//' + location.host;

          if(settings.url.charAt(0) === "/"){
            abs_url += settings.url;
          }
          else if(settings.url.slice(0,4) === "http"){
            abs_url = settings.url;
          }
          else{
            // relative
            abs_url += location.pathname.slice(0, location.pathname.lastIndexOf('/')) + '/' + settings.url;
          }

          getApplet().uploadFile(up.id, file.id, abs_url, document.cookie, settings.chunk_size || 0, settings.retries || 3);
      });

      uploader.bind("SelectFiles", function(up, uploaderInstanceId){
        getApplet().openFileDialog(uploaderInstanceId);
      });

      uploader.bind("Applet:UploadProcess", function(up, javaFile, uploaderInstanceId) {
        var file = up.getFile(javaFile.id),
            finished = javaFile.chunk === javaFile.chunks;

        if (file.status != plupload.FAILED) {
          file.loaded = javaFile.loaded;
          file.size = javaFile.size;
          up.trigger('UploadProgress', file, uploaderInstanceId);
        }
        else{
          alert("uploadProcess status failed");
        }

        if (finished) {
          file.status = plupload.DONE;
          up.trigger('FileUploaded', file, {
            response : "File uploaded"
          });
        }
      });

      uploader.bind("Applet:SelectFiles", function(up, file, uploaderInstanceId) {
        var i, files = [], id;

        files.push(new plupload.File(file.id, file.name, file.size));

        // Trigger FilesAdded event if we added any
        if (files.length) {
          uploader.trigger("FilesAdded", files, uploaderInstanceId);
        }
      });

      uploader.bind("Applet:GenericError", function(up, err) {
        uploader.trigger('Error', {
          code : plupload.GENERIC_ERROR,
          message : 'Generic error.',
          details : err.message,
          file : uploader.getFile(err.id)
        });
      });

      uploader.bind("Applet:IOError", function(up, err) {
        uploader.trigger('Error', {
          code : plupload.IO_ERROR,
          message : 'IO error.',
          details : err.message,
          file : uploader.getFile(err.id)
        });
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
