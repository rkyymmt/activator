define(window.serverAppModel.pluginIds, function() {
  var plugins = [].slice.call(arguments);
  var result = {};
  $.each(plugins, function(idx, plugin) {
    result[plugin.id] = plugin;
  });
  return {
    lookup: result,
    list: plugins
  };
});

