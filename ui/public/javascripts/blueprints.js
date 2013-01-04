// TODO - how do you do modern javascript stuff now?
var snap_templates = function() {
    // private methods & values.

    // Public methods go here.
    return {
        // Clones a given template
        // args is an object with the following:
        //   id - the template id
        //   location - the location to clone into
        //   success -  A callback to use on success (optional)
        //   error - A callback to use on failure (optional)
        clone_template: function(args) {
            var config = {
                url: '/api/templates/clone',
                type: 'POST',
                dataType: 'json',
                contentType: 'application/json',
                data:  JSON.stringify({ location: args.location, template: args.id, name: args.name })
            };
            if(args.success) config.success = args.success;
            if(args.error) config.error = args.error;

            $.ajax(config);
        },

        //  Grabs the current templates and passes them to a handling function.
        //    handler -  A function that accepts an array of Tempalte metadata json
        //    error - A function called if there is any failure.
        get_templates: function(args) {
            var config = {
                url: "/api/templates/list",
                type: "GET",
                dataType: "json",
                contentType: 'application/json'
            }
            if(args.success) config.success = args.success;
            if(args.error) config.error = args.error;
            
            $.ajax(config);
        }
    };
}();