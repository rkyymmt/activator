;(function(global){

    function identity(a){ return a }
    function noop(){}

    function Action(act) {
        function A(ƒ, c) {
            this.ƒ = ƒ || identity
            this.complete = c || noop
        }

        A.Empty = new A()
        A.prototype.zero = function(){ return A.Empty }
        A.prototype.unit = function(v){
            return new A(function(v2, next){
                next(v)
            })
        }

        A.prototype.flatmap = function(ƒ){
            var me = this
            return new A(function(v, next){
                me.onComplete(function (v2) {
                    ƒ.call(me, v2).onComplete(next)._do(v2)
                })._do(v)
            }, noop)
        }

        A.prototype.clone = function(){
            var o = {}
            for (var x in M.fn) o[x] = M.fn[x]
            return o
        }

        A.prototype.map = function(ƒ){
            var me = this
            return this.flatmap(function(v){
                return me.unit(ƒ.call(me, v))
            })
        }

        A.prototype.filter = function(predicate){
            return this.flatmap(function(v){
                if(predicate(v))
                    return this.unit(v)
                else
                    return this.zero()
            })
        }

        A.prototype.onComplete = function(ƒ){
            var me = this
            return new A(this.ƒ, function(v){
                //try {
                me.complete.call(me, v)
                ƒ.call(me, v)
                //} catch(e) {
                //    !me.err || me.err(e)
                //}
            })
        }

        A.prototype._do = function(v){
            this.ƒ(v, this.complete)
        }

        A.prototype.apply = A.prototype._do

        A.prototype.then = function(a){
            var me = this
              , r = new A(function(v, next){
                    me.onComplete(function(v2){
                        a.ƒ(v2, next)
                    })._do(v)
                }, a.complete)
            if (arguments.length > 1){
                return r.then.apply(r,[].slice.call(arguments,1))
            } else {
                return r
            }
        }

        // Sort of promises
        A.prototype.await = function(a){
            var me = this
            return new A(function(v, next){
                me.onComplete(function(v2){
                    a.ƒ(v2, next)
                })._do(v)
            }, a.complete)
        }

        A.prototype.awaitAll = function(a){
            var me = this
              , funcs = a instanceof Array?a:[].slice.call(arguments)
              , count = funcs.length

            return new A(function(v, next){
                var results = []
                  , done = 0
                me.onComplete(function(v2){
                    function notifier(i) {
                        return function(result) {
                            done += 1
                            results[i] = result
                            if (done === count) {
                                next(results)
                            }
                        }
                    }
                    for (var i = 0; i < count; i++) {
                        funcs[i].ƒ.call(me,v2,notifier(i))
                    }
                })._do(v)
            }, a.complete)

        }

        // C'est completement null, \TODO
        A.prototype.fail = function(ƒ){
            this.err = ƒ
            return this
        }

        A.prototype.match = function(m){
            return this.then(m.action())
        }

        A.prototype.when = function(ƒ){
            ƒ(this)
        }

        return new A(act)
    }

    global.Action = Action
    global.Do = Action(function(v,n){
            n(v)
        })

    // Matchers
    var Match = global.Match = (function(){
        function M(ts, lambda, def){
            this.predicates = ts || []
            this.lambda = lambda || identity
            this.def = def || Action() //returned valued if matched
        }

        M.prototype._new = M

        M.prototype.action = function(){
            var u = Action(function(v, n){ n(v) }),
                ac = this
            return u.flatmap(function(v){
                for(var i = 0; i < ac.predicates.length; i++){
                    var p = ac.predicates[i]
                    if(p.predicate(ac.lambda(v))){
                        return p.action
                    }
                }
                return ac.def
            })
        }

        M.prototype.test = function(ƒ, a){
            return new this._new(this.predicates.concat([{
                predicate: ƒ,
                action: a
            }]), this.lambda, this.def)
        }

        M.prototype.value = function(r, a){
            return this.test(function(v){
                return v === r
            }, a)
        }

        M.prototype.array = function(as, a){
            return this.test(function(vs){
                if(vs.length != as.length)
                    return false
                for(var i = 0; i < vs.length; i++)
                    if(vs[i] !== as[i]) return false
                return true
            }, a)
        }

        M.prototype.regex = function(reg, a){
            return this.test(function(v){
                return reg.test(v)
            }, a)
        }

        M.prototype.on = function(lambda){
            var me = this
            return new this._new(this.predicates, function(v){
                return lambda(me.lambda(v))
            }, this.def)
        }

        M.prototype.dft = function(def){
            return new this._new(this.predicates, this.lambda, def)
        }

        M.prototype.specialized = function(obj){
            function S(){
                this.constructor.apply(this, arguments)
            }
            S.prototype = new M()
            S.prototype._new = S
            for(var k in obj)
                S.prototype[k] = obj[k]
            return new S()
        }

        return new M()
    })()

// End of closure
})(window)

