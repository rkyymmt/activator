define(['text!./templatelist.html', 'vendors/knockout-2.2.1.debug', 'core/widget', 'core/utils'], function(template, ko, Widget, utils) {

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
		// TODO - put this on the tempalte class directly
		self.hasTag = function(tagList) {
			for(var i = 0; i < self.tags.length; ++i) {
				for(var j = 0; j < tagList.length; ++j) {
					if(self.tags[i] == tagList[j]) {
						return true;
					}
				}
			}
			return false;
		}
	}
	var defaultSort = function(l,r) {
		return l.name < r.name;
	};

	var datePublishedSort = function(l,r) {
		return l.timestamp < r.timestamp;
	};

	return utils.Class(Widget, {
		id: 'template-list-widget',
		template: template,
		init: function(config) {
			var self = this;
			if(config.onTemplateSelected) self.onTemplateSelected = config.onTemplateSelected;
			self.allTemplates = ko.observableArray([]);
			self.load();
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
			// this little bit of magic can filter the displayed templates
			// by the tags they allow.
			// Note: This may be slow for many templates.  We'll have to
			// investigate and improve speed later.
			self.selectedTags = ko.observableArray([]);
			self.filteredTemplates = ko.computed(function() {
				var templates = self.allTemplates();
				var tags = self.selectedTags();
				if(tags.length == 0) return templates;
				return ko.utils.arrayFilter(templates, function(t) {
					return t.hasTag(tags);
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
