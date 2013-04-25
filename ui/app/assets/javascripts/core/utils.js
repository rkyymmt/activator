define([], function() {

	// does the browser have __proto__ ?
	// IE adds it in 11, others should have it.
	var __proto__Works = (function() {
		function F() {}
		F.prototype = { foo: 42 };
		var anF = new F;
		return ('__proto__' in anF && anF.__proto__ === F.prototype &&
				({ __proto__ : F.prototype }).foo === 42);
	})();

	var noOp = function() {};

	// either:
	//    var MyClass = Class(Base, { init: function() {}, foo: function() {} });
	// OR
	//    var MyClass = Class({ init: function() {}, foo: function() {} });
	// "init" is automatically invoked on construct and chained up.
	// All init() in the hierarchy get the same args so subtypes can add parameters
	// but not remove any. Just taking one param which is an object full of keywords
	// is the simplest solution.
	//
	// If "classInit" is present it's invoked anytime a new subtype is created,
	// on the final subtype prototype, and also chained up.
	// This is a class constructor which could be used for one-time registration
	// or the like.
	var Class = function() {
		var baseclass;
		var o;
		if (arguments.length == 2) {
			baseclass = arguments[0];
			o = arguments[1];
		} else if (arguments.length == 1) {
			baseclass = Object;
			o = arguments[0];
		} else {
			throw new Error("wrong number of parameters", arguments);
		}

		function M() {
			// If you call the constructor directly (with no 'new') then we make
			// a subtype of ourselves with a singleton instance.
			// So if you only want a singleton, you do var instance = Foo({}) instead of making
			// Class(Foo, {}) and then "new"-ing it.
			// (when called with no constructor, "this" will be something random, not
			// an instance of ourselves).
			if (!(this instanceof M)) {
				if (arguments.length < 1)
					throw new Error("no class object provided for singleton (did you omit 'new'?)");
				return Singleton(M, arguments[0]);
			} else {
				if (!('init' in this)) {
					console.error("no init in ", this);
					throw new Error("no init in " + this);
				}
				this.init.apply(this, [].slice.call(arguments, 0))
			}
		}

		// proto is our eventual M.prototype
		var proto;
		if (__proto__Works) // IE < 11 is the problem here
			proto = { __proto__ : baseclass.prototype };
		else
			proto = $.extend({}, baseclass.prototype);

		var baseInit = null;
		var subInit = null;
		var baseClassInit = null;
		var subClassInit = null;

		// pull out init before we merge the subtype overrides
		if ('init' in proto)
			baseInit = proto.init;
		if ('init' in o)
			subInit = o.init;
		if ('classInit' in proto)
			baseClassInit = proto.classInit;
		if ('classInit' in o)
			subClassInit = o.classInit;

		// merge in the subtype (this may overwrite init)
		M.prototype = $.extend(proto, o);

		// build an init method that chains up if needed; if only
		// one init method was present, it should already be
		// in the prototype, so leave it there.
		if (baseInit && subInit && baseInit !== noOp) {
			M.prototype.init = function() {
				var args = [].slice.call(arguments, 0)
				baseInit.apply(this, args);
				subInit.apply(this, args);
			};
		}

		// avoid checking for this on every object construct
		if (!('init' in M.prototype)) {
			M.prototype.init = noOp;
		}

		// build a classInit that chains up if needed; if only
		// one classInit was present, it should already be in
		// the prototype, so leave it there.
		if (baseClassInit && subClassInit && baseClassInit !== noOp) {
			M.prototype.classInit = function(proto) {
				baseClassInit(proto);
				subClassInit(proto);
			};
		}

		if (!('classInit' in M.prototype)) {
			M.prototype.classInit = noOp;
		}

		// now invoke the classInit() (this should be the only
		// time it's invoked on this prototype).
		// Call it without a "this" anyone might rely on.
		var classInit = M.prototype.classInit;
		classInit(M.prototype);

		return M;
	};

	var Singleton = function(base, o) {
		var ctor = Class(base, o);
		return new ctor();
	};

	/*  TODO
	 *   - comment out test suite once we test on IE
	 *   - remove this little test suite once we have somewhere reasonable to put it
	 */
	function assert(t) {
		if (!t)
			throw new Error("fail");
	}

	var B = Class({
		init: function(a,b) {
			this.a = a;
			this.b = b;
			this.name = "B";
			this.count = 1;
		},
		foo: function() {
			return "B";
		}
	});

	var S1 = Class(B, {
		init: function(a, b) {
			this.a1 = a;
			this.b1 = b;
			this.name = "S1";
			this.count += 1;
		},
		classInit: function(proto) {
			proto.classValue = 42;
		},
		foo: function() {
			return "S1";
		}
	});

	var S2 = Class(S1, {
		init: function(a, b) {
			this.a2 = a;
			this.b2 = b;
			this.name = "S2";
			this.count += 1;
		},
		classInit: function(proto) {
			proto.classValue = 43;
		},
		foo: function() {
			return "S2";
		}
	});

	var b = new B(1,2);
	var s1 = new S1(3,4);
	var s2 = new S2(5,6);

	assert(b.name == "B");
	assert(b.a == 1);
	assert(b.b == 2);
	assert(b.foo() == "B");
	assert(b.count == 1)

	assert(s1.name == "S1");
	assert(s1.a1 == 3);
	assert(s1.b1 == 4);
	assert(s1.foo() == "S1");
	assert(s1.count == 2);
	assert(s1.classValue == 42);

	assert(s2.name == "S2");
	assert(s2.a2 == 5);
	assert(s2.b2 == 6);
	assert(s2.foo() == "S2");
	assert(s2.count == 3);
	assert(s2.classValue == 43);
	/* TODO end of test suite to remove */

	return {
		Class: Class,
		Singleton: Singleton
	}
});
