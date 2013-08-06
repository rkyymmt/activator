define(['text!./templatelist.html', 'webjars!knockout', 'core/widget', 'core/utils'], function(template, ko, Widget, utils) {

	function getlist() {
		return $.ajax({
			url: '/api/templates/list',
			type: 'GET',
			dataType: 'json'
		});
	};

	function Template(config) {
		for(key in config) {
			this[key] = config[key];
		}
		var self = this;
		self.archetype = ko.computed(function() {
			if(self.tags.length > 0) {
				return '['+self.tags[0]+']';
			}
			return '';
		});
		self.hasTags = function(tagList) {
			var flags = {};
			for(var i = 0; i < self.tags.length; ++i) {
				for(var j = 0; j < tagList.length; ++j) {
					if(self.tags[i] == tagList[j]) {
						flags[tagList[j]] = true;
					}
				}
			}
			for(var j = 0; j < tagList.length; ++j) {
				if(!flags[tagList[j]]) return false;
			}
			return true;
		}
	}

	var defaultSort = function(l,r) {
		// locale based sorting.
		var l_name = l && l.name || '';
		var r_name = r && r.name || '';
		return l.name.localeCompare(r_name);
	};

	var datePublishedSort = function(l,r) {
		return r.timestamp - l.timestamp;
	};

	return utils.Class(Widget, {
		id: 'template-list-widget',
		template: template,
		init: function(config) {
			var self = this;
			if(config.onTemplateSelected) self.onTemplateSelected = config.onTemplateSelected;
			self.allTemplates = ko.observableArray([]);
			self.load();
			self.searchQuery = ko.observable('');
			self.sortFunctionName = ko.observable("byName");
			self.sortFunction = ko.computed(function() {
				switch(self.sortFunctionName()) {
					case 'byDate':
						return datePublishedSort;
					default:
						return defaultSort;
				}
			});
			// Here's the tag filtering
			self.possibleTags = ko.computed(function() {
				var templates = self.allTemplates();
				var tags = {};
				for(var i = 0; i < templates.length; ++i) {
					var t = templates[i];
					for(var j = 0; j < t.tags.length; ++j) {
						var tag = t.tags[j];
						tags[tag] = tag
					}
				}
				// Now construct an array?
				var tagArray = [];
				for(key in tags) {
					tagArray.push(key);
				}
				return tagArray;
			});
			// We do the text search first on templates, because we have a delay from user input.
			// This is fine for typing, but looks odd when used the the form selection panel.
			self.searchedTemplates = ko.computed(function() {
				var templates = self.allTemplates();
				var search = self.searchQuery().toLowerCase();
				if(search != '') {
					return ko.utils.arrayFilter(templates, function(templates) {
						// TODO - Search title or name?  The website currently uses
						// the name when it describes what to type.
						return templates.name.toLowerCase().indexOf(search) >= 0;
					});
				}
				return templates;
			}).extend({ throttle: 300 });

			// this little bit of magic can filter the displayed templates
			// by the tags they allow.
			// Note: This may be slow for many templates.  We'll have to
			// investigate and improve speed later.
			self.selectedTags = ko.observableArray([]);
			self.filteredTemplates = ko.computed(function() {
				var templates = self.searchedTemplates();
				var tags = self.selectedTags();
				if(tags.length == 0) return templates;
				return ko.utils.arrayFilter(templates, function(t) {
					return t.hasTags(tags);
				});
			});
			//  This uses our configured sorting function and filtered list
			// of templates to actually pick what is displayed on the screen.
			self.shownTemplates = ko.computed(function() {
				return self.filteredTemplates().sort(self.sortFunction());
			});
			// Bind the base function with ourselves.
			// This is a hack because knockout isn't so good with callbacks binding.
			this.selectTemplate = this.selectTemplate.bind(self);

		},
		selectTemplate: function(template) {
			console.log('Template selected', template);
			if(this.onTemplateSelected) this.onTemplateSelected(template);
		},
		load: function() {
			var self = this;
			getlist().done(function(values){
				self.allTemplates(ko.utils.arrayMap(values, function(template) {
					return new Template(template);
				}));
			});
		}
	});
});
