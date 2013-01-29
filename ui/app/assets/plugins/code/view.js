define(["text!./viewWrapper.html", "text!./viewDefault.html", "./imageView", "./codeView", "./browse"], function(viewOuter, defaultTemplate, ImageView, CodeView, DirView) {

	var ko = req('vendors/knockout-2.2.1.debug'),
		key = req('vendors/keymage.min');

	// Default view for when we don't know which other to use.
	var DefaultView = Widget({
		id: 'code-default-view',
		template: defaultTemplate,
		init: function(args) {
			this.filename = args.file;
		},
		afterRender: function(a,b,c){
			console.log('abc', a,b,c)
		}
	});

	// Fetch utility


	function browse(location) {
		return $.ajax({
			url: '/api/local/browse',
			type: 'GET',
			dataType: 'json',
			data: {
				location: location
			}
		});
	}
	var FileBrowser = Widget({
		id: 'file-browser-widget',
		template: viewOuter,
		init: function(args) {
			var self = this;
			// TODO - Detect bad url?
			self.path = args.args.path;
			self.next = args.args.url + "/" + args.args.next;
			self.url = args.args.url;
			self.filename = args.file;
			self.fileLoc = serverAppModel.location + (self.filename ? ('/' + self.filename) : '');
			self.fileLoadUrl = '/api/local/show?location=' + self.fileLoc; // TODO - URL encoded
			self.title = args.args.url || 'Root /';
			self.subView = ko.observable(new DefaultView(args));
			// Loaded via ajax
			self.filetype = ko.observable("unknown");

			self.pageType = ko.computed(function(o) {
				return self.filetype() == "directory" || self.filetype() == "unknown" ? "browser" : "viewer"
			}, self);

			self.dataIndex = ko.computed(function() {
				return self.filetype() == "directory" || self.filetype() == "unknown" ? "1" : "-1"
			});

			// Now load the widget data.
			self.load();
		},
		bindKeys: {
			init: function(view, valueAccessor, allBindingsAccessor, viewModel, bindingContext) {
				// TODO : Refine key api
				var keyscope = bindingContext.$parent.args.path.replace(/\//g, ".");

				function keyHandler(e) {
					var target = $("dd.active, li.active", view);

					if(e.keyIdentifier == "Up") {
						target = target.prev().getOrElse("dd:last, li:last", view);
					} else if (e.keyIdentifier == "Down") {
						target = target.next().getOrElse("dd:first, li:first", view);
					} else if (!target.length) return false;

					target.addClass("active").siblings().removeClass("active");

					// Open link
					var link = target.find("a");
					window.location.hash = link.attr("href");
					// To autoscroll to link
					link[0].focus();
				}

				key(keyscope, 'up', keyHandler, {
					preventDefault: true
				});
				key(keyscope, 'down', keyHandler, {
					preventDefault: true
				});
				key(keyscope, 'right', function(e){
					keyHandler({keyIdentifier:"Right"});
					// Call root bindings
					key.bindings.right.handlers.forEach(function(f){
						f(e);
					});
				},{
					preventDefault: true
				});

			}
		},
		load: function() {
			var self = this;
			browse(self.fileLoc).done(function(datas) {
				self.filetype(datas.type);
				// Check to see if we need to further load...
				if(datas.type == 'code') {
					self.subView(new CodeView(self));
				} else if(datas.type == 'image') {
					self.subView(new ImageView(self));
				} else if(datas.type == 'directory') {
					self.subView(new DirView(self, datas));
				}
			});
		}
	});

	return FileBrowser;
});
