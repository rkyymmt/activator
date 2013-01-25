define(['css!./tutorial.css','text!./tutorial.html'], function(css, template){

$("body > aside").html(template).addClass("tutorial");

return {
	init: function(){}
}

});
