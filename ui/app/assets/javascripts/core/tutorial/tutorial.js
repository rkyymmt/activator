define(['css!./tutorial', 'text!./tutorial.html', 'text!./page.html', 'core/pluginapi'], function(css, template, pageTemplate, api){

	var ko = api.ko;

	var Page = api.Class(api.Widget, {
		id: 'page-widget',
		template: pageTemplate,
		init: function(parameters) {
			var self = this;
			this.index = parameters.index;
			this.content = parameters.content.innerHTML;
			this.title = $(parameters.content).find("h1,h2").text();
			this.tutorial = parameters.tutorial;
			// first time on each page, we always want to scroll to 0
			this.lastScrollTop = 0;
			this.active = ko.computed(function() {
				var result = self === self.tutorial.currentPage();
				console.log("page " + self.index + " active=" + result);
				return result;
			}, this);
		},
		activate: function() {
			this.tutorial.select(this);
			// hide the menu if it was open
			this.tutorial.showingIndex(false);
		}
	});

	function getTutorial(config) {
		if(serverAppModel && serverAppModel.hasLocalTutorial === "true") {
			config.data = {
					location: serverAppModel.location + '/tutorial/index.html'
			};
			$.ajax("/api/local/show", config);
		} else if (serverAppModel && serverAppModel.template && serverAppModel.template.id) {
			$.ajax("/api/templates/"+serverAppModel.template.id+"/tutorial/index.html",config);
		}
	}

	var Tutorial = api.Class(api.Widget, {
		id: 'tutorial-widget',
		template: template,
		init: function(parameters) {
			var self = this;
			self.node = null;
			self.pages = ko.observableArray([]);
			self.currentPage = ko.observable(null);
			self.havePages = ko.computed(function() {
				return self.pages().length > 0;
			}, self);
			self.havePrevious = ko.computed(function() {
				return self.havePages() &&
					self.currentPage() !== self.pages()[0];
			}, self);
			self.haveNext = ko.computed(function() {
				var length = self.pages().length;
				return self.havePages() &&
					self.currentPage() !== self.pages()[length - 1];
			}, self);
			self.previousClass = ko.computed(function() {
				if (self.havePrevious())
					return "";
				else
					return "disabled";
			}, self);
			self.nextClass = ko.computed(function() {
				if (self.haveNext())
					return "";
				else
					return "disabled";
			}, self);
			self.currentTitle = ko.computed(function() {
				var page = self.currentPage();
				if (page === null)
					return "";
				else
					return page.title;
			}, self);
			self.showingIndex = ko.observable(false);
			self.indexMenuClass = ko.computed(function() {
				if (self.showingIndex())
					return "open";
				else
					return "";
			}, self);
			self.hasLocalTutorial = serverAppModel && serverAppModel.hasLocalTutorial === "true";
			self.loadTutorial();
		},
		loadTutorial: function() {
			var self = this;
			getTutorial({
				success: function(data){
					// parseHTML dumps the <html> <head> and <body> tags it looks like
					// so we'll get back a list with <title> and some <div> and some
					// text nodes.
					var htmlNodes = $.parseHTML(data);
					self.pages.removeAll();
					$(htmlNodes).filter("div").each(function(i,el){
						self.pages.push(new Page({ index: i+1, content: el, tutorial: self }));
					});
					if (self.havePages())
						self.select(self.pages()[0]);
					else
						self.select(null);
				}
			});
		},
		select: function(item) {
			var old = this.currentPage();
			if (old) {
				// save the page's scroll position
				old.lastScrollTop = $(this.node).find('article')[0].scrollTop;
			}
			if (item) {
				console.log("selecting page " + item.index + ": " + item.title);
				this.currentPage(item);
				// restore the page's scroll position
				$(this.node).find('article')[0].scrollTop = item.lastScrollTop;
			} else if (item === null) {
				console.log("unselecting all pages");
				this.currentPage(null);
			} else {
				console.error("Invalid page to select: ", item);
			}
		},
		selectNext: function() {
			this.showingIndex(false);

			var length = this.pages().length;
			var i = 0;
			for (; i < length; ++i) {
				if (this.pages()[i] === this.currentPage())
					break;
			}
			if (i < length)
				this.select(this.pages()[i + 1]);
		},
		selectPrevious: function() {
			this.showingIndex(false);

			var length = this.pages().length;
			var i = length - 1;
			for (; i >= 0; --i) {
				if (this.pages()[i] === this.currentPage())
					break;
			}
			if (i > 0)
				this.select(this.pages()[i - 1]);
		},
		toggleIndexMenu: function() {
			this.showingIndex(!this.showingIndex());
		},
		onRender: function(children) {
			var tuts = $(children[0]).parent();
			tuts.on("click", "header .collapse", function(event) {
				tuts.toggleClass("collapsed");
				$('body').toggleClass("right-collapsed")
			});
			this.node = tuts;
		}
	});

	return Tutorial;
});
