define(['css!./tutorial.css', 'core/pluginapi'], function(css, api){

	var ko = api.ko,
		key = api.key;

	function Page(index, content) {
		var title = $(content).find("h1,h2").text();
		return {
			index: index,
			title: title,
			link: "<span class=\"step\">"+index+"</span> "+title,
			content: content.innerHTML
		}
	}

	function Tutorials(){
		var self = this;
		self.pages = ko.observableArray([]);
		self.currentPage = ko.observable();
		self.select = function(item){
			self.currentPage(item);
		};
		if (serverAppModel && serverAppModel.blueprint && serverAppModel.blueprint.id){
			$.ajax("/api/templates/"+serverAppModel.blueprint.id+"/tutorial/index.html",{
				success: function(data){
					$(data).filter("div").each(function(i,el){
						console.log("eL>", el)
						self.pages.push(new Page(i+1,el));
					});
					setTimeout(function(){
						$("aside.tutorial nav a.previous").trigger("click");
					},100);
					//self.currentPage( pages()[0] );
				}
			});
		}
	}

	// BAAAAAAD
	// BAAAAAAD
	// BAAAAAAD
	// BAAAAAAD
	var tuts = $("aside.tutorial");
	function display(target){
		tuts.find(".previous").attr("disabled", false);
		tuts.find(".next").attr("disabled", false);
		if (target >= tuts.find("ul li").length - 1) {
			tuts.find(".next").attr("disabled", true);
		} else if (target <= 0) {
			tuts.find(".previous").attr("disabled", true);
		}
		tuts.attr("data-step", target);
		var active = tuts.find("article > div").eq(target).show()
		active.siblings().hide();
		var title = active.find("h1,h2").text();
		tuts.find("h1").text(title)
	}
	tuts.on("click", "nav a", function(e){
		var target = parseFloat(tuts.attr("data-step"));
		if (e.target.className == "previous" && target > 0){
			target--;
		} else if (e.target.className == "next" && target < tuts.find("ul li").length -1 ){
			target++;
		}
		display(target);
	});
	tuts.find("h1").click(function(){
		tuts.find("ul").toggleClass("open");
	});
	tuts.on("click","ul, ul li", function(e){
		tuts.find("ul").toggleClass("open");
		display( $(e.currentTarget).index() )
	});
	// BAAAAAAD
	// BAAAAAAD
	// BAAAAAAD
	// BAAAAAAD

	return new Tutorials();
});
