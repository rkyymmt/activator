define(['css!./code.css','text!./browse.html'], function(css, template){

	var ko = req('vendors/knockout-2.2.1.debug'),
                key = req('vendors/keymage.min');

	var FileModel = Class({
		init: function(config) {
			this.name = config.name;
			this.location = config.location;
			this.isDirectory = config.isDirectory;
			this.mimeType = config.mimeType;
			var path = ['code'];
      var relative = config.location.replace(serverAppModel.location, "");
      if(relative[0] == '/') { relative = relative.slice(1); }
      // TODO - Is it ok to drop browse history when viewing a file?
      this.url = 'code/' + relative;
		},
		select: function() {
		  window.location.hash = this.url;
		}
  });

        var Browser = Widget({
    id: 'code-browser-view',
    template: template,
    dataIndex: 1,
		init: function(parameters, datas){
			this.url = parameters.fileLoc;
      var children = datas.children || [];
			this.tree = ko.observableArray($.map(children, function(config) {
        return new FileModel(config);
      }));
		},
		render: function(parameters){
			console.log('params', parameters);

			var view = $(template + " ");
			this.view = view[0];

			var url = "./" + parameters.args.path.replace(/^code\/?/,"")
			this.load(url);
			this.title("Browse: "+url)

			this.view.id = parameters.args.path.replace("/", "");
			ko.applyBindings(this, this.view);

			// TODO : Refine key api
			key(parameters.url.replace("/","."), 'up', function(e){
				var target = $("dd.active, li.active", view)
					.prev()
					.getOrElse("dd:last, li:last", view)
					.addClass("active")

				target
					.siblings()
					.removeClass("active");

				// To autoscroll to link
				target.find("a")[0].focus();
			}, {preventDefault: true});
			key(parameters.url.replace("/","."), 'down', function(e){
				var target = $("dd.active, li.active", view)
					.next()
					.getOrElse("dd:first, li:first", view)
					.addClass("active")

				target
					.siblings()
					.removeClass("active");

				// To autoscroll to link
				target.find("a")[0].focus();
			}, {preventDefault: true});
			key(parameters.url.replace("/","."), 'left', function(e){
				var target = $("nav a", view).eq(-2)
						.getOrElse("nav a:first-child", view)
					window.location.hash = target.attr("href")
			});
			key(parameters.url.replace("/","."), 'right', function(e){
				var target = $("dd.active, li.active", view)
					.trigger("click");
			});

			return view;
		},
    onRender: function(domElements) {
      console.log(domElements)
    }
	});
	return Browser;

})

