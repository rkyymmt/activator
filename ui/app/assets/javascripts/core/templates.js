define([], function() {
  var ko = req('vendors/knockout-2.2.1.debug');
  var templates = {};
  // Register a template (by text) with the template engine.
  function registerTemplate(id, text) {
    templates[id] = text;
    return id;
  }
  //define a template source that simply treats the template name as its content
  ko.templateSources.stringTemplate = function(template, templates) {
    this.templateName = template;
    this.templates = templates;
  }
  // Add the API the templates use.
  ko.utils.extend(ko.templateSources.stringTemplate.prototype, {
    data: function(key, value) {
      console.log("data", key, value, this.templateName);
      this.templates._data = this.templates._data || {};
      this.templates._data[this.templateName] = this.templates._data[this.templateName] || {};
      if (arguments.length === 1) {
        return this.templates._data[this.templateName][key];
      }
      this.templates._data[this.templateName][key] = value;
    },
    text: function(value) {
      console.log("text", value, this.templateName)
      if (arguments.length === 0) {
        return this.templates[this.templateName];
      }
      this.templates[this.templateName] = value;
    }
  });

  //modify an existing templateEngine to work with string templates
  function createStringTemplateEngine(templateEngine, templates) {
    templateEngine.makeTemplateSource = function(template) {
      return new ko.templateSources.stringTemplate(template, templates);
    }
    return templateEngine;
  }

  // Register us immediately.
  ko.setTemplateEngine(createStringTemplateEngine(new ko.nativeTemplateEngine(), templates));

  return {
    registerTemplate: registerTemplate,
    templates: templates
  };
});
